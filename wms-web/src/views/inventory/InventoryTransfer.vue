<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { toastError } from '@/utils/error'
import PageHeading from '@/components/common/PageHeading.vue'
import InventoryRowPicker from '@/components/form/InventoryRowPicker.vue'
import { inventoryApi } from '@/api/inventory'
import { locationApi } from '@/api/basedata'
import { fetchAllOptions } from '@/utils/loadAllOptions'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const form = reactive({ source: null, toLocationId: null, qty: 1, remark: '' })

const submitting = ref(false)
const loadingSource = ref(false)
const locationLoading = ref(false)
const locationOptions = ref([])
const result = ref(null)

/** 从库存列表点「移库」进来时锁住源库存，不让它被改错 */
const sourceLocked = ref(Boolean(route.query.inventoryId))

const rules = {
  source: [{ required: true, message: '请选择要移库的库存行', trigger: 'change' }],
  toLocationId: [{ required: true, message: '请选择目标库位', trigger: 'change' }],
  qty: [{ required: true, message: '请输入移库数量', trigger: 'blur' }],
}

const availableQty = computed(() => Number(form.source?.availableQty ?? 0))
const maxQty = computed(() => Math.max(1, availableQty.value))

/* ------------------------------ 初始化 ------------------------------ */

async function loadSource(id) {
  loadingSource.value = true
  try {
    form.source = await inventoryApi.detail(id)
  } catch (error) {
    toastError(error, '库存行加载失败')
    form.source = null
  } finally {
    loadingSource.value = false
  }
}

if (route.query.inventoryId) {
  loadSource(route.query.inventoryId)
}

/* ------------------------------ 目标库位 ------------------------------ */

/*
 * 目标库位只能是「同一个仓库 + 启用状态」的库位，源库位自己不算。
 * 跨仓库搬货不是移库，那是一次出库 + 一次入库（中间有运输在途），要走单据。
 */
watch(
  () => form.source?.warehouseId,
  async (warehouseId) => {
    form.toLocationId = null
    locationOptions.value = []
    if (!warehouseId) return

    locationLoading.value = true
    try {
      const list = await fetchAllOptions((params) =>
        locationApi.page({ ...params, warehouseId, status: 'ENABLED' }),
      )
      locationOptions.value = list
        .filter((item) => item.id !== form.source?.locationId)
        .map((item) => ({
          label: `${item.code} ${item.name}`,
          value: item.id,
          capacity: item.capacity,
          usedCapacity: item.usedCapacity,
        }))
    } catch (error) {
      toastError(error, '库位列表加载失败')
    } finally {
      locationLoading.value = false
    }
  },
)

function locationHint(option) {
  if (!option.capacity) return '不限容量'
  const free = option.capacity - (option.usedCapacity || 0)
  return `剩余容量 ${free} / ${option.capacity}`
}

/* ------------------------------ 提交 ------------------------------ */

function resetAll() {
  form.source = null
  form.toLocationId = null
  form.qty = 1
  form.remark = ''
  result.value = null
  sourceLocked.value = false
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const qty = Number(form.qty)
  if (qty > availableQty.value) {
    ElMessage.warning(`可用数量只有 ${availableQty.value}，移不了 ${qty}`)
    return
  }

  submitting.value = true
  try {
    const fromRow = form.source
    await inventoryApi.transfer({
      fromLocationId: fromRow.locationId,
      toLocationId: form.toLocationId,
      skuId: fromRow.skuId,
      batchNo: fromRow.batchNo,
      qty,
      remark: form.remark,
    })

    // 移完再查一次源行：界面上显示的数字必须是数据库里的真实值，不是「我以为减掉了」
    const refreshed = await inventoryApi.detail(fromRow.id)

    result.value = {
      from: { code: fromRow.locationCode, before: fromRow.qty, after: refreshed.qty, qty },
      to: locationOptions.value.find((item) => item.value === form.toLocationId),
    }
    ElMessage.success(`已从 ${fromRow.locationCode} 移出 ${qty} 件`)
    form.source = refreshed
    form.qty = 1
    form.remark = ''
    form.toLocationId = null
  } catch (error) {
    toastError(error, '移库失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <PageHeading
      title="库存移库"
      description="把某个库位的一批货挪到同仓库的另一个库位。转出转入在一个事务里，不会出现「货从 A 走了但没到 B」"
    />

    <div class="layout">
      <section class="card">
        <header class="card__head">
          <h3>移库单</h3>
          <el-button v-if="sourceLocked" link type="primary" @click="sourceLocked = false">
            重新选择源库存
          </el-button>
        </header>

        <el-form
          ref="formRef"
          v-loading="loadingSource"
          :model="form"
          :rules="rules"
          label-position="top"
          class="form"
        >
          <el-form-item label="源库存行（库位 + 商品 + 批次）" prop="source">
            <InventoryRowPicker
              v-model="form.source"
              scope="available"
              :disabled="sourceLocked"
            />
          </el-form-item>

          <div v-if="form.source" class="source">
            <div class="source__title">
              <span class="mono">{{ form.source.locationCode }}</span>
              <span>{{ form.source.warehouseName }}</span>
            </div>
            <div class="source__sku">
              {{ form.source.skuCode }} · {{ form.source.skuName }} ·
              {{ form.source.batchNo || '无批次' }}
            </div>
            <div class="source__stats">
              <div><em>在库</em><strong>{{ form.source.qty ?? 0 }}</strong></div>
              <div><em>锁定</em><strong>{{ form.source.lockedQty ?? 0 }}</strong></div>
              <div><em>冻结</em><strong>{{ form.source.frozenQty ?? 0 }}</strong></div>
              <div><em>可用</em><strong class="is-brand">{{ availableQty }}</strong></div>
            </div>
          </div>

          <el-form-item label="目标库位（同仓库，仅启用）" prop="toLocationId">
            <el-select
              v-model="form.toLocationId"
              placeholder="请选择要移入的库位"
              filterable
              :loading="locationLoading"
              :disabled="!form.source"
              style="width: 100%"
            >
              <el-option
                v-for="item in locationOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <div class="option">
                  <span>{{ item.label }}</span>
                  <em>{{ locationHint(item) }}</em>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="移库数量" prop="qty">
            <el-input-number
              v-model="form.qty"
              :min="1"
              :max="maxQty"
              :step="1"
              :disabled="!form.source"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              placeholder="例如：拣货位补货 / 货放错位置要挪回去"
            />
          </el-form-item>

          <div class="actions">
            <el-button @click="resetAll">清空</el-button>
            <el-button type="primary" :loading="submitting" @click="submit">确认移库</el-button>
          </div>
        </el-form>
      </section>

      <aside class="side">
        <section v-if="result" class="card card--result">
          <h3>移库成功</h3>
          <p class="result__line">
            <span class="mono">{{ result.from.code }}</span>
            在库 {{ result.from.before }} → <strong>{{ result.from.after }}</strong>
          </p>
          <p class="result__line">
            <span class="mono">{{ result.to?.label }}</span>
            移入 <strong>+{{ result.from.qty }}</strong>
          </p>
          <div class="result__actions">
            <el-button size="small" @click="resetAll">再移一笔</el-button>
            <el-button size="small" type="primary" @click="router.push('/inventory/transactions')">
              查看流水
            </el-button>
          </div>
        </section>

        <section class="card card--note">
          <h3>为什么移库要写两条流水</h3>
          <ul>
            <li>
              <strong>转出 -n、转入 +n</strong>：库存是「按库位存」的，一次移库在账上就是两笔变动，
              两条流水才记得清货是从哪走的、到哪去了。
            </li>
            <li>
              <strong>两条一起成功或一起失败</strong>：服务端用同一个事务包住，
              转入那一步失败（比如目标库位容量不够、被停用）时，转出会自动回滚。
            </li>
            <li>
              <strong>冻结和锁定的货挪不动</strong>：可用数量不够就直接拒绝（BR-005），
              不然会把已经被订单占住的货搬走。
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

.source {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.source__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.source__title .mono {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-600);
}

.source__sku {
  font-size: 13px;
  color: var(--text-primary);
}

.source__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.source__stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.source__stats em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.source__stats strong {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.source__stats .is-brand {
  color: var(--brand-600);
}

.option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.option em {
  font-size: 11.5px;
  font-style: normal;
  color: var(--text-placeholder);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ---------- 右侧 ---------- */
.card--result h3 {
  margin-bottom: 10px;
  color: var(--success-600);
}

.result__line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text-regular);
}

.result__line strong {
  color: var(--text-primary);
}

.result__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
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

.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}
</style>