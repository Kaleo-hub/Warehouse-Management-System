<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toastError } from '@/utils/error'
import PageHeading from '@/components/common/PageHeading.vue'
import { inventoryApi } from '@/api/inventory'
import { useIdempotentKey } from '@/composables/useIdempotentKey'

const checking = ref(false)
const fixing = ref(false)
const result = ref(null)
const lastRunAt = ref('')
/** 幂等键：每次点「开始对账 / 按流水修正」算一次动作 */
const idempotentKey = useIdempotentKey()

const mismatches = computed(() => result.value?.mismatches ?? [])
const hasMismatch = computed(() => Number(result.value?.mismatchCount ?? 0) > 0)

/**
 * 这一批不一致是不是已经全修完了。
 *
 * 为什么需要它：autoFix 返回的是「扫描时发现的那批行 + 每行的修正结果」，
 * 所以修完之后 mismatchCount 依然是 1（它说的是「当时查到几条」）。
 * 界面上如果照搬这个数字，就会出现「横幅说发现 1 条不符、表格里每行都写着已修正」
 * 这种自相矛盾的画面，还会诱导人再点一次修正。
 */
const allFixed = computed(() => {
  const list = mismatches.value
  return list.length > 0 && list.every((item) => item.fixResult === 'FIXED')
})

const bannerTone = computed(() => (hasMismatch.value && !allFixed.value ? 'warn' : 'ok'))

const bannerTitle = computed(() => {
  if (allFixed.value) return '已按流水修正 ' + result.value.fixedCount + ' 条，账已平'
  if (hasMismatch.value) return '发现 ' + result.value.mismatchCount + ' 条库存余额与流水不一致'
  return '账是平的'
})

const bannerText = computed(() => {
  if (allFixed.value) {
    return '下面列的是本次修正记录（余额已按流水重算，没有写库存流水）。点「再对一次」可以复查。'
  }
  if (hasMismatch.value) {
    return '下面这些行的余额和「流水累加出来的值」对不上。以流水为准（流水只增不改不删，是事实记录；余额是可以重新推出来的缓存），确认后可以按流水把余额修正回来。'
  }
  return result.value.checkedCount + ' 条库存的余额都等于它所有流水的累加值，不需要处理。'
})

function stamp() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(
    now.getHours(),
  )}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

/**
 * 对账 = 按流水把余额重算一遍，和 inventory 表上的值比对。
 * 默认只检查不改数（autoFix=false）—— 先让人看见「哪里不平」，再由人决定要不要修。
 * 这是刻意的：拉回余额会影响账，属于要人负责的动作，不该自动悄悄做掉。
 */
async function runCheck() {
  if (checking.value) return
  checking.value = true
  try {
    result.value = await inventoryApi.reconcile(false, idempotentKey.value)
    idempotentKey.reset()
    lastRunAt.value = stamp()
    if (!hasMismatch.value) {
      ElMessage.success(`对账完成：${result.value.checkedCount} 条库存全部与流水一致`)
    } else {
      ElMessage.warning(`发现 ${result.value.mismatchCount} 条库存与流水不一致`)
    }
  } catch (error) {
    toastError(error, '对账失败')
  } finally {
    checking.value = false
  }
}

async function runFix() {
  if (fixing.value) return
  try {
    await ElMessageBox.confirm(
      `将按流水重算这 ${result.value.mismatchCount} 条库存的余额（以流水为准）。` +
        '修正的是派生出来的余额，不会写库存流水，确认继续？',
      '按流水修正余额',
      { type: 'warning', confirmButtonText: '确认修正', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  fixing.value = true
  try {
    const data = await inventoryApi.reconcile(true, idempotentKey.value)
    idempotentKey.reset()
    result.value = data
    lastRunAt.value = stamp()
    if (data.fixedCount > 0) {
      ElMessage.success(`已按流水修正 ${data.fixedCount} 条`)
    } else {
      ElMessage.info('没有需要修正的记录')
    }
  } catch (error) {
    toastError(error, '修正失败')
  } finally {
    fixing.value = false
  }
}

function fixResultTag(text) {
  if (text === 'FIXED') return { type: 'success', label: '已修正' }
  if (text === 'FAILED') return { type: 'danger', label: '修正失败' }
  return { type: 'info', label: '仅检查' }
}

/*
 * 三个数量各自是「余额 vs 流水」的一对值。
 * 以前是 6 列平铺（余额在库 / 流水在库 / 余额锁定 / 流水锁定 …），
 * 12 列的表又宽又难读，眼睛得来回找哪两个是一对。
 * 改成「余额 → 流水」的对照单元格后只剩 9 列，差异一眼就能看见 ——
 * 这个页面的全部意义就是看差异，展示方式应该围着它转。
 */
const compareFields = {
  qtyCompare: { balance: 'balanceQty', calculated: 'calculatedQty' },
  lockedCompare: { balance: 'balanceLockedQty', calculated: 'calculatedLockedQty' },
  frozenCompare: { balance: 'balanceFrozenQty', calculated: 'calculatedFrozenQty' },
}

function isDiff(row, prop) {
  const field = compareFields[prop]
  return row[field.balance] !== row[field.calculated]
}

const columns = [
  { prop: 'locationCode', label: '库位', width: 120 },
  { prop: 'skuCode', label: 'SKU', width: 110 },
  { prop: 'skuName', label: '商品名称', minWidth: 140 },
  { prop: 'batchNo', label: '批次', width: 110 },
  { prop: 'qtyCompare', label: '在库（余额 → 流水）', width: 160, align: 'right' },
  { prop: 'lockedCompare', label: '锁定（余额 → 流水）', width: 160, align: 'right' },
  { prop: 'frozenCompare', label: '冻结（余额 → 流水）', width: 160, align: 'right' },
  { prop: 'fixResult', label: '结果', width: 96 },
  { prop: 'remark', label: '说明', minWidth: 170 },
]
</script>

<template>
  <div>
    <PageHeading
      title="库存对账"
      description="以流水为准，把每个「库位 + 商品 + 批次」的余额重算一遍并比对（BR-009）"
    >
      <el-button type="primary" :loading="checking" @click="runCheck">开始对账</el-button>
    </PageHeading>

    <section class="stats">
      <div class="stat">
        <em>扫描库存行</em>
        <strong>{{ result?.checkedCount ?? '—' }}</strong>
      </div>
      <div class="stat" :class="{ 'is-warn': hasMismatch }">
        <em>账不平</em>
        <strong>{{ result?.mismatchCount ?? '—' }}</strong>
      </div>
      <div class="stat">
        <em>本次修正</em>
        <strong>{{ result?.fixedCount ?? '—' }}</strong>
      </div>
      <div class="stat stat--time">
        <em>最近一次</em>
        <strong>{{ lastRunAt || '还没跑过' }}</strong>
      </div>
    </section>

    <section v-if="result" class="banner" :class="'banner--' + bannerTone">
      <div class="banner__text">
        <strong>{{ bannerTitle }}</strong>
        <p>{{ bannerText }}</p>
      </div>
      <div class="banner__actions">
        <el-button v-if="hasMismatch && !allFixed" type="warning" :loading="fixing" @click="runFix">
          按流水修正
        </el-button>
        <el-button v-else :loading="checking" @click="runCheck">再对一次</el-button>
      </div>
    </section>

    <section v-if="result" class="panel">
      <header class="panel__head">
        <h3>不一致明细</h3>
        <span class="panel__hint">箭头左边是余额，右边是按流水重算的值，红字就是不一致的那一侧</span>
      </header>

      <el-table :data="mismatches" stripe>
        <el-table-column
          v-for="column in columns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :align="column.align || 'left'"
        >
          <template #default="{ row }">
            <template v-if="column.prop === 'batchNo'">
              <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
            </template>
            <template v-else-if="column.prop === 'fixResult'">
              <el-tag :type="fixResultTag(row.fixResult).type" size="small" disable-transitions>
                {{ fixResultTag(row.fixResult).label }}
              </el-tag>
            </template>
            <template v-else-if="compareFields[column.prop]">
              <span class="pair">
                <em class="num" :class="{ 'is-diff': isDiff(row, column.prop) }">
                  {{ row[compareFields[column.prop].balance] }}
                </em>
                <span class="pair__arrow">→</span>
                <em class="num pair__calc">{{ row[compareFields[column.prop].calculated] }}</em>
              </span>
            </template>
            <template v-else>
              <span>{{ row[column.prop] }}</span>
            </template>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <p>没有账不平的库存</p>
            <em>余额和流水完全一致，这正是正常状态</em>
          </div>
        </template>
      </el-table>
    </section>

    <section class="note">
      <h3>对账到底在对什么</h3>
      <div class="note__grid">
        <div>
          <strong>余额是缓存，流水是账本</strong>
          <p>
            inventory.qty 是当前快照，inventory_transaction 是每一笔变动的流水。
            同一件事被记了两遍，就一定会有对不上的时候：程序 bug、有人直接改库、宕机丢写。
            对账就是拿账本重算一遍，看看缓存还对不对。
          </p>
        </div>
        <div>
          <strong>为什么以流水为准</strong>
          <p>
            流水只增不改不删，改不了；余额随时可以被静默改掉。
            缓存和它的来源不一致时，相信来源（BR-009）。
          </p>
        </div>
        <div>
          <strong>修正为什么不写流水</strong>
          <p>
            修的是派生的余额，没有货进出仓库，不是一次库存变动。
            如果给修正也写一条流水，账本的累加值就被改了，下一次对账又会不平，永远收敛不了 ——
            这个坑是用测试跑出来的。
          </p>
        </div>
        <div>
          <strong>为什么默认只检查不改</strong>
          <p>
            拉回余额会影响账，属于要人负责的动作。所以默认只告警，
            人工确认后才执行修正，执行完还会在结果里留下每一条的修正迹象。
          </p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

@media (max-width: 900px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.stat em {
  font-size: 11.5px;
  font-style: normal;
  color: var(--text-secondary);
}

.stat strong {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.stat.is-warn strong {
  color: var(--warning-600);
}

.stat--time strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}

.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 14px 18px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.banner--ok {
  border-color: var(--success-300, #86efac);
  background: var(--success-50, #f0fdf4);
}

.banner--warn {
  border-color: var(--warning-300, #fcd34d);
  background: var(--warning-50, #fffbeb);
}

/* 按钮和文字之间用 flex 的 gap 控间距，别让 Element 的 margin 再叠一层 */
.banner__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.banner__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.banner__text strong {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--text-primary);
}

.banner__text p {
  max-width: 760px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.panel {
  margin-bottom: 14px;
  overflow: hidden;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--divider);
}

.panel__head h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.panel__hint {
  font-size: 12px;
  color: var(--text-placeholder);
}

/* ---------- 余额 → 流水 对照 ---------- */
.pair {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.pair__arrow {
  font-size: 11px;
  color: var(--text-placeholder);
}

.pair .num {
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-style: normal;
  color: var(--text-regular);
  font-variant-numeric: tabular-nums;
}

.pair .num.is-diff {
  font-weight: 700;
  color: var(--danger-600);
}

.pair .num.pair__calc {
  color: var(--text-secondary);
}

.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.muted {
  color: var(--text-placeholder);
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 30px 0;
}

.empty p {
  font-size: 13.5px;
  color: var(--text-regular);
}

.empty em {
  font-size: 12px;
  font-style: normal;
  color: var(--text-placeholder);
}

.note {
  padding: 18px;
  background: var(--bg-surface-2);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.note h3 {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.note__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 900px) {
  .note__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.note__grid div {
  padding: 12px 14px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.note__grid strong {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text-primary);
}

.note__grid p {
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-secondary);
}
</style>