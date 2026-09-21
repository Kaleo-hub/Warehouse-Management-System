<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { locationApi } from '@/api/basedata'
import { inventoryApi, transferOrderApi } from '@/api/inventory'
import { useIdempotentKey } from '@/composables/useIdempotentKey'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { toastError } from '@/utils/error'

const router = useRouter()
const app = useAppStore()
const formRef = ref(null)
const submitting = ref(false)
const loadingInventory = ref(false)
const locations = ref([])
const inventoryRows = ref([])
const selected = reactive({})
const moveQty = reactive({})
const idempotentKey = useIdempotentKey()
const form = reactive({ fromLocationId: null, toLocationId: null, remark: '' })

const rules = {
  fromLocationId: [{ required: true, message: '请选择移出库位', trigger: 'change' }],
  toLocationId: [{ required: true, message: '请选择移入库位', trigger: 'change' }],
}

const selectedItems = computed(() =>
  inventoryRows.value
    .filter((row) => selected[row.id])
    .map((row) => ({
      skuId: row.skuId,
      batchNo: row.batchNo || '',
      qty: Number(moveQty[row.id] || 0),
      remark: '',
    })),
)

const locationLabel = (id) => {
  const item = locations.value.find((row) => row.id === id)
  return item ? `${item.code} · ${item.name}` : `#${id}`
}

const toOptions = computed(() =>
  locations.value.filter((item) => item.id !== form.fromLocationId),
)

async function loadLocations() {
  try {
    locations.value = (await fetchAllOptions((params) =>
      locationApi.page({ ...params, warehouseId: app.currentWarehouseId || undefined }),
    )).filter((item) => item.status === 'ENABLED')
    if (form.fromLocationId && !locations.value.some((item) => item.id === form.fromLocationId)) {
      form.fromLocationId = null
    }
    if (form.toLocationId && !locations.value.some((item) => item.id === form.toLocationId)) {
      form.toLocationId = null
    }
  } catch (error) {
    toastError(error, '库位加载失败')
  }
}

watch(() => app.currentWarehouseId, loadLocations)
onMounted(loadLocations)

watch(() => form.fromLocationId, async (locationId) => {
  Object.keys(selected).forEach((key) => delete selected[key])
  Object.keys(moveQty).forEach((key) => delete moveQty[key])
  inventoryRows.value = []
  if (!locationId) return
  if (form.toLocationId === locationId) form.toLocationId = null
  loadingInventory.value = true
  try {
    const data = await inventoryApi.page({ page: 1, size: 100, locationId })
    inventoryRows.value = (data?.records ?? []).filter((row) => Number(row.availableQty) > 0)
  } catch (error) {
    toastError(error, '源库位库存加载失败')
  } finally {
    loadingInventory.value = false
  }
})

function toggleRow(row, checked) {
  selected[row.id] = checked
  if (checked && (!moveQty[row.id] || moveQty[row.id] > row.availableQty)) {
    moveQty[row.id] = Math.min(1, row.availableQty)
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitting.value) return
  const items = selectedItems.value
  if (!items.length) {
    ElMessage.warning('请至少选择一条库存明细')
    return
  }
  if (items.some((item) => item.qty <= 0)) {
    ElMessage.warning('移库数量必须大于 0')
    return
  }
  const invalid = inventoryRows.value.find(
    (row) => selected[row.id] && Number(moveQty[row.id]) > Number(row.availableQty),
  )
  if (invalid) {
    ElMessage.warning(`批次 ${invalid.batchNo || '无批次'} 的移库数量超过可用量`)
    return
  }

  submitting.value = true
  try {
    const id = await transferOrderApi.create(
      { ...form, items },
      idempotentKey.value,
    )
    idempotentKey.reset()
    ElMessage.success('移库单已创建')
    router.replace(`/inventory/transfer/${id}`)
  } catch (error) {
    toastError(error, '创建移库单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <PageHeading title="新建移库单" description="选择同一仓库的移出/移入库位，再勾选要移动的 SKU 批次">
      <el-button :icon="ArrowLeft" @click="router.push('/inventory/transfer')">返回列表</el-button>
    </PageHeading>

    <section class="card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="移出库位" prop="fromLocationId">
              <el-select v-model="form.fromLocationId" filterable placeholder="选择源库位" style="width: 100%">
                <el-option v-for="item in locations" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="移入库位" prop="toLocationId">
              <el-select v-model="form.toLocationId" filterable placeholder="选择目标库位" style="width: 100%">
                <el-option v-for="item in toOptions" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="255" placeholder="可填写移库原因" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="section-head">
        <div>
          <h3>可选库存</h3>
          <p v-if="form.fromLocationId">{{ locationLabel(form.fromLocationId) }}</p>
          <p v-else>先选择移出库位</p>
        </div>
        <span class="hint">只显示可用量大于 0 的批次</span>
      </div>

      <el-table v-loading="loadingInventory" :data="inventoryRows" border>
        <el-table-column label="选择" width="70" align="center">
          <template #default="{ row }">
            <el-checkbox :model-value="!!selected[row.id]" @change="(value) => toggleRow(row, value)" />
          </template>
        </el-table-column>
        <el-table-column prop="skuCode" label="SKU 编码" width="140" />
        <el-table-column prop="skuName" label="商品名称" min-width="160" />
        <el-table-column label="批次" width="150">
          <template #default="{ row }"><span class="mono">{{ row.batchNo || '无批次' }}</span></template>
        </el-table-column>
        <el-table-column prop="qty" label="在库" width="90" align="right" />
        <el-table-column prop="availableQty" label="可用" width="90" align="right" />
        <el-table-column label="移库数量" width="160" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="moveQty[row.id]"
              :min="1"
              :max="Number(row.availableQty)"
              :disabled="!selected[row.id]"
              size="small"
              controls-position="right"
            />
          </template>
        </el-table-column>
      </el-table>

      <div class="actions">
        <el-button @click="router.push('/inventory/transfer')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建移库单</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.card {
  padding: 22px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin: 10px 0 14px;
}
.section-head h3 { font-size: 15px; color: var(--text-primary); }
.section-head p { margin-top: 4px; font-size: 12px; color: var(--text-secondary); }
.hint { font-size: 12px; color: var(--text-placeholder); }
.mono { font-family: var(--font-mono); font-size: 12.5px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
.actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>
