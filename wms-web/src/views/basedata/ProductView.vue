<script setup>
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { productApi } from '@/api/basedata'
import { STATUS_OPTIONS, STATUS_TAG } from '@/constants/options'

const columns = [
  { prop: 'skuCode', label: 'SKU 编码', width: 140, mono: true },
  { prop: 'name', label: '商品名称', minWidth: 180 },
  { prop: 'spec', label: '规格', width: 130 },
  { prop: 'unit', label: '单位', width: 80, align: 'center' },
  { prop: 'barcode', label: '条码', width: 160, mono: true },
  { prop: 'weightG', label: '重量(g)', width: 100, align: 'right' },
  { prop: 'shelfLifeDays', label: '保质期(天)', width: 110, align: 'right' },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
]

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: 'SKU / 名称 / 条码' },
  { prop: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS },
]

const formFields = [
  { prop: 'skuCode', label: 'SKU 编码', required: true, maxlength: 32, help: '全局唯一，等同商品的业务主键' },
  { prop: 'name', label: '商品名称', required: true, maxlength: 128 },
  { prop: 'spec', label: '规格', maxlength: 64, help: '例如 500ml/瓶' },
  { prop: 'unit', label: '计量单位', required: true, maxlength: 16, help: '例如 瓶 / 包 / 件' },
  { prop: 'barcode', label: '条形码', maxlength: 64 },
  { prop: 'weightG', label: '重量（克）', type: 'number', min: 0, max: 100000000 },
  { prop: 'shelfLifeDays', label: '保质期（天）', type: 'number', min: 0, max: 36500 },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: STATUS_OPTIONS,
    defaultValue: 'ENABLED',
  },
]
</script>

<template>
  <div>
    <PageHeading title="商品管理" description="维护 SKU 主数据，入库、库存、出库都以此为准" />
    <CrudTable
      :api="productApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      entity-name="商品"
    />
  </div>
</template>