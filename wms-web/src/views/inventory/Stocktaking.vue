<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toastError } from '@/utils/error'
import PageHeading from '@/components/common/PageHeading.vue'
import InventoryRowPicker from '@/components/form/InventoryRowPicker.vue'
import { inventoryApi } from '@/api/inventory'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const form = reactive({ source: null, actualQty: null, remark: '' })

const loadingSource = ref(false)
const submitting = ref(false)
const result = ref(null)

/**
 * 盘点填的是「实盘数量」，不是差异。
 * 前端算出差异只是给操作人一个即时反馈（数错了当场能看出来），
 * 真正入库的那个差异由后端拿系统数量现算 —— 这个数字直接决定库存，不给前端算。
 */
const systemQty = computed(() => Number(form.source?.qty ?? 0))
const diffQty = computed(() => {
  if (form.actualQty === null || form.actualQty === undefined || form.actualQty === '') return 0
  return Number(form.actualQty) - systemQty.value
})

const diffTone = computed(() => {
  if (!diffQty.value) return 'same'
  return diffQty.value > 0 ? 'gain' : 'loss'
})

const diffText = computed(() => {
  if (!diffQty.value) return '账实相符'
  return diffQty.value > 0 ? `盘盈 ${diffQty.value} 件` : `盘亏 ${-diffQty.value} 件`
})

const lockedQty = computed(() => Number(form.source?.lockedQty ?? 0))
const frozenQty = computed(() => Number(form.source?.frozenQty ?? 0))
const minActualQty = computed(() => lockedQty.value + frozenQty.value)

async function loadSource(id) {
  loadingSource.value = true
  try {
    const row = await inventoryApi.detail(id)
    form.source = row
    form.actualQty = Number(row.qty ?? 0)
  } catch (error) {
    toastError(error, '库存行加载失败')
  } finally {
    loadingSource.value = false
  }
}

if (route.query.inventoryId) {
  loadSource(route.query.inventoryId)
}

function onSourceChange(row) {
  result.value = null
  form.actualQty = row ? Number(row.qty ?? 0) : null
}

async function submit() {
  if (submitting.value) return
  if (!form.source) {
    ElMessage.warning('请先选择要盘点的库存行')
    return
  }
  if (form.actualQty === null || form.actualQty === undefined || form.actualQty === '') {
    ElMessage.warning('请填写实盘数量')
    return
  }
  if (Number(form.actualQty) < minActualQty.value) {
    ElMessage.warning(
      `这个库位有 ${lockedQty.value} 件被订单锁定、${frozenQty.value} 件被冻结，` +
        `实盘数量不可能低于 ${minActualQty.value}`,
    )
    return
  }

  if (diffQty.value !== 0) {
    try {
      await ElMessageBox.confirm(
        `系统数量 ${systemQty.value}，实盘 ${form.actualQty}，${
          diffQty.value > 0 ? `盘盈 ${diffQty.value}` : `盘亏 ${-diffQty.value}`
        } 件。库存会按实盘数量调整，确认提交？`,
        '确认盘点差异',
        { type: 'warning', confirmButtonText: '确认调整', cancelButtonText: '再数一遍' },
      )
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    const data = await inventoryApi.stocktake({
      locationId: form.source.locationId,
      skuId: form.source.skuId,
      batchNo: form.source.batchNo,
      actualQty: Number(form.actualQty),
      remark: form.remark,
    })
    result.value = data

    // 盘完立刻回查一次，界面上的数字必须来自数据库
    const refreshed = await inventoryApi.detail(form.source.id)
    form.source = refreshed
    form.actualQty = Number(refreshed.qty ?? 0)
    form.remark = ''

    if (!data.changed) {
      ElMessage.success('账实相符，库存没有变动')
    } else {
      ElMessage.success(data.diffQty > 0 ? `盘盈 ${data.diffQty} 件已入库` : `盘亏 ${-data.diffQty} 件已调整`)
    }
  } catch (error) {
    toastError(error, '盘点失败')
  } finally {
    submitting.value = false
  }
}

function resetAll() {
  form.source = null
  form.actualQty = null
  form.remark = ''
  result.value = null
}
</script>

<template>
  <div>
    <PageHeading
      title="库存盘点"
      description="填实盘数量，系统算差异：多了记盘盈、少了记盘亏，账实相符就不写流水"
    />

    <div class="layout">
      <section class="card">
        <header class="card__head">
          <h3>盘点单</h3>
          <el-button v-if="route.query.inventoryId" link type="primary" @click="resetAll">
            重新选择
          </el-button>
        </header>

        <el-form ref="formRef" v-loading="loadingSource" :model="form" label-position="top">
          <el-form-item label="盘点对象（库位 + 商品 + 批次）" required>
            <InventoryRowPicker v-model="form.source" scope="qty" @update:model-value="onSourceChange" />
          </el-form-item>

          <div v-if="form.source" class="target">
            <div class="target__title">
              <span class="mono">{{ form.source.locationCode }}</span>
              <span>{{ form.source.warehouseName }}</span>
            </div>
            <div class="target__sku">
              {{ form.source.skuCode }} · {{ form.source.skuName }} ·
              {{ form.source.batchNo || '无批次' }}
            </div>
            <div class="target__stats">
              <div><em>系统数量</em><strong>{{ systemQty }}</strong></div>
              <div><em>已锁定</em><strong>{{ lockedQty }}</strong></div>
              <div><em>已冻结</em><strong>{{ frozenQty }}</strong></div>
            </div>
          </div>

          <el-form-item label="实盘数量" required>
            <el-input-number
              v-model="form.actualQty"
              :min="0"
              :step="1"
              :disabled="!form.source"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>

          <div v-if="form.source" class="diff" :class="`diff--${diffTone}`">
            <span class="diff__label">差异</span>
            <strong class="diff__value">
              {{ diffQty > 0 ? `+${diffQty}` : diffQty }}
            </strong>
            <span class="diff__text">{{ diffText }}</span>
          </div>

          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              placeholder="例如：货架角落发现一箱没登记的货"
            />
          </el-form-item>

          <div class="actions">
            <el-button @click="resetAll">清空</el-button>
            <el-button type="primary" :loading="submitting" @click="submit">提交盘点</el-button>
          </div>
        </el-form>
      </section>

      <aside class="side">
        <section v-if="result" class="card card--result">
          <h3>盘点结果</h3>
          <div class="result__stats">
            <div><em>系统数量</em><strong>{{ result.systemQty }}</strong></div>
            <div><em>实盘数量</em><strong>{{ result.actualQty }}</strong></div>
            <div>
              <em>差异</em>
              <strong :class="result.diffQty > 0 ? 'is-gain' : result.diffQty < 0 ? 'is-loss' : ''">
                {{ result.diffQty > 0 ? `+${result.diffQty}` : result.diffQty }}
              </strong>
            </div>
          </div>
          <p class="result__note">
            {{
              !result.changed
                ? '账实相符，没有产生库存变动，也没有写流水。'
                : result.diffQty > 0
                  ? '已按盘盈写入库存，并留了一条 STOCKTAKING_GAIN 流水。'
                  : '已按盘亏调整库存，并留了一条 STOCKTAKING_LOSS 流水。'
            }}
          </p>
          <el-button size="small" type="primary" @click="router.push('/inventory/transactions')">
            查看流水
          </el-button>
        </section>

        <section class="card card--note">
          <h3>盘点为什么传「实盘数量」</h3>
          <ul>
            <li>
              <strong>差异由后端算</strong>：前端传「实盘 120」而不是「盘盈 20」。
              这个差异直接决定库存，不该由前端算完再告诉后端（算错、造假都拦不住）。
            </li>
            <li>
              <strong>账实相符不写流水</strong>：流水记的是「发生过的变动」，
              0 变动没有信息量，写进去只会让账本变脏、让对账变慢。
            </li>
            <li>
              <strong>盘亏盘不掉被占用的货</strong>：实盘数量小于「锁定 + 冻结」时会被拒绝 ——
              说明还有货被订单占着或冻着，实物不可能一件不剩。
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 14px;
  align-items: start;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

.card {
  padding: 18px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.target {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.target__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.target__title .mono {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-600);
}

.target__sku {
  font-size: 13px;
  color: var(--text-primary);
}

.target__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.target__stats em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.target__stats strong {
  display: block;
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.diff {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.diff__label {
  font-size: 12px;
  color: var(--text-secondary);
}

.diff__value {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.diff__text {
  font-size: 12.5px;
  color: var(--text-secondary);
}

.diff--same .diff__value {
  color: var(--text-placeholder);
}

.diff--gain {
  border-color: var(--success-300, #86efac);
  background: var(--success-50, #f0fdf4);
}

.diff--gain .diff__value,
.diff--gain .diff__text {
  color: var(--success-600);
}

.diff--loss {
  border-color: var(--danger-300, #fca5a5);
  background: var(--danger-50, #fef2f2);
}

.diff--loss .diff__value,
.diff--loss .diff__text {
  color: var(--danger-600);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ---------- 右侧 ---------- */
.card--result h3 {
  margin-bottom: 12px;
  color: var(--success-600);
}

.result__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.result__stats em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.result__stats strong {
  display: block;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.result__stats .is-gain {
  color: var(--success-600);
}

.result__stats .is-loss {
  color: var(--danger-600);
}

.result__note {
  margin-bottom: 12px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.card--note {
  background: var(--bg-surface-2);
}

.card--note h3 {
  margin-bottom: 10px;
}

.card--note ul {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-left: 16px;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-secondary);
  list-style: disc;
}

.card--note strong {
  color: var(--text-primary);
}
</style>