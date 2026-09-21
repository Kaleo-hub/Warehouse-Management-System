<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { triggerDownload } from '@/utils/format'
import { toastError } from '@/utils/error'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inventoryApi } from '@/api/inventory'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { TX_TYPE, TX_TYPE_OPTIONS } from '@/constants/inbound'

/** CrudTable 需要 { page } 形状，这里只做一层适配；保持对象引用稳定 */
const transactionApi = { page: (params) => inventoryApi.transactions(params) }
const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()
onMounted(loadWarehouses)


const columns = [
  { prop: 'txNo', label: '流水号', width: 195, mono: true },
  { prop: 'txType', label: '变动类型', width: 120, tag: TX_TYPE },
  { prop: 'skuCode', label: 'SKU 编码', width: 130, mono: true },
  { prop: 'skuName', label: '商品名称', minWidth: 170 },
  { prop: 'locationCode', label: '库位', width: 130, mono: true },
  { prop: 'batchNo', label: '批次号', width: 120 },
  { prop: 'qtyChange', label: '数量变动', width: 110, align: 'right' },
  { prop: 'qtyBefore', label: '变动前', width: 90, align: 'right' },
  { prop: 'qtyAfter', label: '变动后', width: 90, align: 'right' },
  { prop: 'sourceNo', label: '来源单号', width: 175, mono: true },
  { prop: 'operatorName', label: '操作人', width: 110 },
  { prop: 'createTime', label: '发生时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: 'SKU / 商品名 / 流水号' },
  { prop: 'warehouseId', label: '仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'txType', label: '变动类型', type: 'select', options: TX_TYPE_OPTIONS },
  { prop: 'sourceNo', label: '来源单号', placeholder: '入库单号 / 出库单号' },
  {
    prop: 'createRange',
    label: '发生时间',
    type: 'daterange',
    beginProp: 'beginTime',
    endProp: 'endTime',
  },
]

/** 正数绿色 + 号，负数红色，0 灰色 */
function changeClass(value) {
  const num = Number(value) || 0
  if (num > 0) return 'is-plus'
  if (num < 0) return 'is-minus'
  return 'is-zero'
}

function signed(value) {
  const num = Number(value) || 0
  return num > 0 ? `+${num}` : String(num)
}
/* ---------------- 导出 ---------------- */

const exporting = ref(false)

/**
 * 导出当前筛选结果。
 * 分页参数丢掉：导的是「所有匹配的行」，不是当前这一页。
 * 这也是最容易误解的地方 —— 用户看到 10 行、导出来 5000 行，要能解释清楚。
 */
async function handleExport(params) {
  exporting.value = true
  try {
    const { page, size, ...filters } = params
    const blob = await inventoryApi.exportTransactions(filters)
    triggerDownload(blob, `库存流水_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功，单次最多 5000 行')
  } catch (error) {
    toastError(error, '导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div>
    <PageHeading
      title="库存流水"
      description="流水只增不改不删，每一次库存变动都必须能在这里追溯（BR-008）"
    />

    <CrudTable
      :api="transactionApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      entity-name="流水"
    >
      <template #toolbar="{ params }">
        <el-button :icon="Download" :loading="exporting" @click="handleExport(params)">
          导出 Excel
        </el-button>
      </template>

      <template #col-batchNo="{ row }">
        <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
      </template>

      <template #col-qtyChange="{ row }">
        <span class="num" :class="changeClass(row.qtyChange)">{{ signed(row.qtyChange) }}</span>
      </template>

      <template #col-qtyBefore="{ row }">
        <span class="num muted">{{ row.qtyBefore ?? 0 }}</span>
      </template>

      <template #col-qtyAfter="{ row }">
        <span class="num">{{ row.qtyAfter ?? 0 }}</span>
      </template>

      <template #col-sourceNo="{ row }">
        <span :class="row.sourceNo ? 'mono' : 'muted'">{{ row.sourceNo || '-' }}</span>
      </template>
    </CrudTable>
  </div>
</template>

<style scoped>
.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.muted {
  color: var(--text-placeholder);
}

.num {
  font-family: var(--font-mono);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.is-plus {
  font-weight: 600;
  color: var(--success-600);
}

.is-minus {
  font-weight: 600;
  color: var(--danger-600);
}

.is-zero {
  color: var(--text-placeholder);
}
</style>
