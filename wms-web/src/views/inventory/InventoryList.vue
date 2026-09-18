<script setup>
import { computed, onMounted } from 'vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inventoryApi } from '@/api/inventory'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'

const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()

onMounted(loadWarehouses)

const columns = [
  { prop: 'warehouseName', label: '仓库', minWidth: 150 },
  { prop: 'locationCode', label: '库位', width: 140, mono: true },
  { prop: 'skuCode', label: 'SKU 编码', width: 140, mono: true },
  { prop: 'skuName', label: '商品名称', minWidth: 180 },
  { prop: 'batchNo', label: '批次号', width: 130 },
  { prop: 'qty', label: '在库', width: 90, align: 'right' },
  { prop: 'lockedQty', label: '锁定', width: 90, align: 'right' },
  { prop: 'frozenQty', label: '冻结', width: 90, align: 'right' },
  { prop: 'availableQty', label: '可用', width: 100, align: 'right' },
  { prop: 'inboundTime', label: '入库时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: 'SKU / 商品名 / 库位' },
  { prop: 'warehouseId', label: '仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'batchNo', label: '批次号', placeholder: '精确匹配' },
])
</script>

<template>
  <div>
    <PageHeading
      title="库存查询"
      description="可用 = 在库 − 锁定 − 冻结。库存只能由业务动作改变，这里不提供直接修改"
    />

    <CrudTable
      :api="inventoryApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      entity-name="库存"
    >
      <template #col-batchNo="{ row }">
        <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
      </template>

      <template #col-qty="{ row }">
        <span class="num">{{ row.qty ?? 0 }}</span>
      </template>
      <template #col-lockedQty="{ row }">
        <span class="num" :class="{ 'is-zero': !row.lockedQty }">{{ row.lockedQty ?? 0 }}</span>
      </template>
      <template #col-frozenQty="{ row }">
        <span class="num" :class="{ 'is-zero': !row.frozenQty }">{{ row.frozenQty ?? 0 }}</span>
      </template>
      <template #col-availableQty="{ row }">
        <span class="num num--strong">{{ row.availableQty ?? 0 }}</span>
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
  color: var(--text-regular);
  font-variant-numeric: tabular-nums;
}

.num--strong {
  font-weight: 700;
  color: var(--brand-600);
}

.is-zero {
  color: var(--text-placeholder);
}
</style>