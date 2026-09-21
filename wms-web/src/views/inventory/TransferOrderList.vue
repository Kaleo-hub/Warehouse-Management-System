<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { transferOrderApi } from '@/api/inventory'
import { TRANSFER_ORDER_STATUS, TRANSFER_ORDER_STATUS_OPTIONS } from '@/constants/inventory'

const router = useRouter()
const tableRef = ref(null)
const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()
onMounted(loadWarehouses)


const columns = [
  { prop: 'orderNo', label: '移库单号', width: 180, mono: true },
  { prop: 'warehouseName', label: '仓库', minWidth: 140 },
  { prop: 'fromLocationCode', label: '移出库位', width: 140, mono: true },
  { prop: 'toLocationCode', label: '移入库位', width: 140, mono: true },
  { prop: 'status', label: '状态', width: 100, tag: TRANSFER_ORDER_STATUS },
  { prop: 'totalQty', label: '总数量', width: 100 },
  { prop: 'transferTime', label: '执行时间', width: 175, mono: true, type: 'datetime' },
  { prop: 'createTime', label: '创建时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'warehouseId', label: '仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'keyword', label: '关键字', placeholder: '单号 / 库位' },
  { prop: 'status', label: '单据状态', type: 'select', options: TRANSFER_ORDER_STATUS_OPTIONS },
])
</script>

<template>
  <div>
    <PageHeading title="移库单" description="同仓库位之间移动多条 SKU / 批次，确认后一次事务过账">
      <el-button :icon="RefreshRight" @click="router.push('/inventory/transfer/quick')">
        快速移库
      </el-button>
      <el-button type="primary" :icon="Plus" @click="router.push('/inventory/transfer/create')">
        新建移库单
      </el-button>
    </PageHeading>

    <CrudTable
      ref="tableRef"
      :api="transferOrderApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="100"
      entity-name="移库单"
      @row-click="(row) => router.push(`/inventory/transfer/${row.id}`)"
    >
      <template #row-actions="{ row }">
        <el-button link type="primary" @click.stop="router.push(`/inventory/transfer/${row.id}`)">
          详情
        </el-button>
      </template>
    </CrudTable>
  </div>
</template>

<style scoped>
.mono { font-family: var(--font-mono); font-size: 12.5px; }
</style>
