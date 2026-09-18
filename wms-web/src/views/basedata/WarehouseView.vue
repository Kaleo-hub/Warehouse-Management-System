<script setup>
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { warehouseApi } from '@/api/basedata'
import { STATUS_OPTIONS, STATUS_TAG } from '@/constants/options'

const columns = [
  { prop: 'code', label: '仓库编码', width: 140, mono: true },
  { prop: 'name', label: '仓库名称', minWidth: 160 },
  { prop: 'address', label: '仓库地址', minWidth: 220 },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
  { prop: 'remark', label: '备注', minWidth: 140 },
  { prop: 'createTime', label: '创建时间', width: 180, mono: true, type: 'datetime' },
]

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码 / 名称 / 地址' },
  { prop: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS },
]

const formFields = [
  {
    prop: 'code',
    label: '仓库编码',
    required: true,
    maxlength: 32,
    help: '全局唯一，保存后不建议修改',
    rules: [{ max: 32, message: '编码最长 32 个字符', trigger: 'blur' }],
  },
  { prop: 'name', label: '仓库名称', required: true, maxlength: 64 },
  { prop: 'address', label: '仓库地址', maxlength: 255 },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: STATUS_OPTIONS,
    defaultValue: 'ENABLED',
  },
  { prop: 'remark', label: '备注', type: 'textarea', maxlength: 255 },
]
</script>

<template>
  <div>
    <PageHeading title="仓库管理" description="维护仓库主数据，后续所有库区、库位都挂在仓库下" />
    <CrudTable
      :api="warehouseApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      entity-name="仓库"
    />
  </div>
</template>