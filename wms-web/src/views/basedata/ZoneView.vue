<script setup>
import { computed, onMounted } from 'vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { zoneApi } from '@/api/basedata'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { STATUS_OPTIONS, STATUS_TAG, ZONE_TYPE_OPTIONS, ZONE_TYPE_TAG } from '@/constants/options'

const { options: warehouseOptions, load: loadWarehouses, labelOf } = useWarehouseOptions()

onMounted(loadWarehouses)

const columns = [
  { prop: 'code', label: '库区编码', width: 120, mono: true },
  { prop: 'name', label: '库区名称', minWidth: 150 },
  { prop: 'warehouseName', label: '所属仓库', minWidth: 190 },
  { prop: 'zoneType', label: '库区类型', width: 110, tag: ZONE_TYPE_TAG },
  { prop: 'sortNo', label: '排序', width: 80, align: 'center' },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
  { prop: 'createTime', label: '创建时间', width: 180, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '编码 / 名称' },
  {
    prop: 'warehouseId',
    label: '所属仓库',
    type: 'select',
    options: warehouseOptions.value,
  },
  { prop: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS },
])

const formFields = computed(() => [
  {
    prop: 'warehouseId',
    label: '所属仓库',
    type: 'select',
    required: true,
    options: warehouseOptions.value,
  },
  { prop: 'code', label: '库区编码', required: true, maxlength: 32, help: '同一仓库下唯一，例如 A / B / R' },
  { prop: 'name', label: '库区名称', required: true, maxlength: 64 },
  {
    prop: 'zoneType',
    label: '库区类型',
    type: 'select',
    required: true,
    options: ZONE_TYPE_OPTIONS,
    defaultValue: 'STORAGE',
  },
  { prop: 'sortNo', label: '排序号', type: 'number', min: 0, defaultValue: 1 },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: STATUS_OPTIONS,
    defaultValue: 'ENABLED',
  },
])
</script>

<template>
  <div>
    <PageHeading title="库区管理" description="库区是仓库内部的功能分区，决定库位用途与拣货策略" />
    <CrudTable
      :api="zoneApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      entity-name="库区"
    >
      <template #col-warehouseName="{ row }">
        <span>{{ row.warehouseName || labelOf(row.warehouseId) }}</span>
      </template>
    </CrudTable>
  </div>
</template>