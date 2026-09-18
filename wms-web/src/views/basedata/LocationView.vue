<script setup>
import { computed, onMounted, ref } from 'vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { locationApi, zoneApi } from '@/api/basedata'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import {
  LOCATION_TYPE_OPTIONS,
  LOCATION_TYPE_TAG,
  STATUS_OPTIONS,
  STATUS_TAG,
} from '@/constants/options'

const { options: warehouseOptions, load: loadWarehouses, labelOf: warehouseLabel } =
  useWarehouseOptions()

const zones = ref([])

onMounted(async () => {
  loadWarehouses()
  try {
    const data = await zoneApi.page({ page: 1, size: 200 })
    zones.value = data?.records ?? data?.list ?? []
  } catch {
    zones.value = []
  }
})

function zonesOf(warehouseId) {
  if (!warehouseId) return []
  return zones.value
    .filter((item) => item.warehouseId === warehouseId)
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
}

function zoneLabel(zoneId) {
  const zone = zones.value.find((item) => item.id === zoneId)
  return zone ? `${zone.name}（${zone.code}）` : `#${zoneId}`
}

const columns = [
  { prop: 'code', label: '库位编码', width: 150, mono: true },
  { prop: 'name', label: '库位名称', minWidth: 170 },
  { prop: 'warehouseName', label: '所属仓库', minWidth: 175 },
  { prop: 'zoneName', label: '所属库区', minWidth: 175 },
  { prop: 'locationType', label: '库位类型', width: 110, tag: LOCATION_TYPE_TAG },
  { prop: 'capacity', label: '容量', width: 90, align: 'right' },
  { prop: 'usedCapacity', label: '已用', width: 90, align: 'right' },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '库位编码 / 名称' },
  { prop: 'warehouseId', label: '所属仓库', type: 'select', options: warehouseOptions.value },
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
  { prop: 'zoneId', label: '所属库区', required: true, help: '先选择仓库，再选择该仓库下的库区' },
  { prop: 'code', label: '库位编码', required: true, maxlength: 64, help: '同一仓库下唯一，建议用 A-01-01-01 这种可读格式' },
  { prop: 'name', label: '库位名称', required: true, maxlength: 64 },
  {
    prop: 'locationType',
    label: '库位类型',
    type: 'select',
    required: true,
    options: LOCATION_TYPE_OPTIONS,
    defaultValue: 'RACK',
  },
  {
    prop: 'capacity',
    label: '容量上限',
    type: 'number',
    min: 0,
    defaultValue: 1000,
    help: '填 0 表示不限制容量（例如临时暂存位）',
  },
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
    <PageHeading title="库位管理" description="库位是库存的最小落位单元，编码规则直接影响上架与拣货效率" />
    <CrudTable
      :api="locationApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      entity-name="库位"
    >
      <template #col-warehouseName="{ row }">
        <span>{{ row.warehouseName || warehouseLabel(row.warehouseId) }}</span>
      </template>
      <template #col-zoneName="{ row }">
        <span>{{ row.zoneName || zoneLabel(row.zoneId) }}</span>
      </template>

      <template #form-warehouseId="{ form }">
        <el-select
          v-model="form.warehouseId"
          placeholder="请选择所属仓库"
          clearable
          style="width: 100%"
          @change="form.zoneId = undefined"
        >
          <el-option
            v-for="option in warehouseOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </template>

      <template #form-zoneId="{ form }">
        <el-select
          v-model="form.zoneId"
          :placeholder="form.warehouseId ? '请选择所属库区' : '请先选择所属仓库'"
          :disabled="!form.warehouseId"
          clearable
          style="width: 100%"
        >
          <el-option
            v-for="option in zonesOf(form.warehouseId)"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </template>
    </CrudTable>
  </div>
</template>