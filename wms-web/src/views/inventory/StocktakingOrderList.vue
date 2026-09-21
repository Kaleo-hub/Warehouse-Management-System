<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { stocktakingOrderApi } from '@/api/inventory'
import {
  STOCKTAKING_ORDER_STATUS,
  STOCKTAKING_ORDER_STATUS_OPTIONS,
} from '@/constants/inventory'

const router = useRouter()
const tableRef = ref(null)

const columns = [
  { prop: 'orderNo', label: '盘点单号', width: 180, mono: true },
  { prop: 'warehouseName', label: '仓库', minWidth: 140 },
  { prop: 'locationCode', label: '盘点库位', width: 150, mono: true },
  { prop: 'status', label: '状态', width: 100, tag: STOCKTAKING_ORDER_STATUS },
  { prop: 'countedItems', label: '录入进度', width: 130 },
  { prop: 'diffItems', label: '差异行', width: 90 },
  { prop: 'gainQty', label: '盘盈', width: 90 },
  { prop: 'lossQty', label: '盘亏', width: 90 },
  { prop: 'createTime', label: '创建时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '单号 / 库位' },
  { prop: 'status', label: '单据状态', type: 'select', options: STOCKTAKING_ORDER_STATUS_OPTIONS },
])
</script>

<template>
  <div>
    <PageHeading title="盘点单" description="按库位生成库存快照，逐行录入实盘数量后一次性过账">
      <el-button :icon="RefreshRight" @click="router.push('/inventory/stocktaking/quick')">
        快速盘点
      </el-button>
      <el-button type="primary" :icon="Plus" @click="router.push('/inventory/stocktaking/create')">
        新建盘点单
      </el-button>
    </PageHeading>

    <CrudTable
      ref="tableRef"
      :api="stocktakingOrderApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="100"
      entity-name="盘点单"
      @row-click="(row) => router.push(`/inventory/stocktaking/${row.id}`)"
    >
      <template #col-countedItems="{ row }">
        <span class="mono">{{ row.countedItems ?? 0 }}/{{ row.totalItems ?? 0 }}</span>
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click.stop="router.push(`/inventory/stocktaking/${row.id}`)">
          详情
        </el-button>
      </template>
    </CrudTable>
  </div>
</template>

<style scoped>
.mono { font-family: var(--font-mono); font-size: 12.5px; }
</style>