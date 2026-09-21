<script setup>
import { computed, onMounted } from 'vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { partnerApi } from '@/api/basedata'
import { PARTNER_TYPE_OPTIONS, PARTNER_TYPE_TAG, STATUS_OPTIONS, STATUS_TAG } from '@/constants/options'

onMounted(() => {})

const columns = [
  { prop: 'partnerType', label: '类型', width: 100, tag: PARTNER_TYPE_TAG },
  { prop: 'code', label: '编码', width: 130, mono: true },
  { prop: 'name', label: '名称', minWidth: 200 },
  { prop: 'contactName', label: '联系人', width: 120 },
  { prop: 'contactPhone', label: '联系电话', width: 150, mono: true },
  { prop: 'address', label: '地址', minWidth: 200 },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
  { prop: 'createTime', label: '创建时间', width: 180, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '编码 / 名称 / 联系人' },
  { prop: 'partnerType', label: '类型', type: 'select', options: PARTNER_TYPE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS },
])

const formFields = computed(() => [
  {
    prop: 'partnerType',
    label: '类型',
    type: 'select',
    required: true,
    options: PARTNER_TYPE_OPTIONS,
    defaultValue: 'SUPPLIER',
  },
  {
    prop: 'code',
    label: '编码',
    required: true,
    maxlength: 32,
    disabledOnEdit: true,
    help: '全局唯一（供应商和客户共用一套编码），创建后不建议修改',
  },
  { prop: 'name', label: '名称', required: true, maxlength: 128, help: '填工商登记的全称，避免同一家出现多种写法' },
  { prop: 'contactName', label: '联系人', maxlength: 50 },
  { prop: 'contactPhone', label: '联系电话', maxlength: 32 },
  { prop: 'address', label: '地址', type: 'textarea', maxlength: 255 },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: STATUS_OPTIONS,
    defaultValue: 'ENABLED',
    help: '停用后不能再被新单据引用，但历史单据不受影响',
  },
  { prop: 'remark', label: '备注', type: 'textarea', maxlength: 255 },
])
</script>

<template>
  <div>
    <PageHeading
      title="往来单位"
      description="供应商与客户主数据。单据引用这里的数据，而不是手打名字 —— 否则同一家会出现多种写法，按单位统计就废了"
    />
    <CrudTable
      :api="partnerApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      entity-name="往来单位"
    />
  </div>
</template>