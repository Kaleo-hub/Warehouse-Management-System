<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { locationApi, zoneApi } from '@/api/basedata'
import { inventoryApi } from '@/api/inventory'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { toastError } from '@/utils/error'
import {
  LOCATION_TYPE_OPTIONS,
  LOCATION_TYPE_TAG,
  STATUS_OPTIONS,
  STATUS_TAG,
} from '@/constants/options'

const route = useRoute()

const { options: warehouseOptions, load: loadWarehouses, labelOf: warehouseLabel } =
  useWarehouseOptions()

const zones = ref([])

/* ---------------- 库位占用率 ---------------- */

/** 占用率百分比。capacity=0 表示不限容量，返回 0（不画进度条） */
function usagePercent(row) {
  const cap = Number(row.capacity) || 0
  if (cap <= 0) return 0
  return Math.min(100, Math.round(((Number(row.usedCapacity) || 0) / cap) * 100))
}

function usageText(row) {
  if (Number(row.capacity) <= 0) return '不限'
  return usagePercent(row) + '%'
}

/** ≥90% 标危险、≥70% 标警告 —— 让"快满了"一眼能看出来 */
function usageClass(row) {
  if (Number(row.capacity) <= 0) return 'is-free'
  const p = usagePercent(row)
  if (p >= 90) return 'is-danger'
  if (p >= 70) return 'is-warning'
  return ''
}

/* ---------------- 库位库存下钻 ---------------- */

const stockVisible = ref(false)
const stockLoading = ref(false)
const stockRows = ref([])
const currentLocation = ref(null)

async function openStock(row) {
  currentLocation.value = row
  stockRows.value = []
  stockVisible.value = true
  stockLoading.value = true
  try {
    const data = await inventoryApi.page({ locationId: row.id, page: 1, size: 100 })
    stockRows.value = data?.records ?? data?.list ?? []
  } catch (error) {
    toastError(error, '加载库位库存失败')
  } finally {
    stockLoading.value = false
  }
}

const stockTotal = computed(() =>
  stockRows.value.reduce((sum, r) => sum + (Number(r.qty) || 0), 0),
)

/** 深链：带着 stockLocationId 进来，说明是从库区抽屉点「商品」过来的，直接把库存抽屉打开 */
async function openStockById(id) {
  try {
    openStock(await locationApi.detail(id))
  } catch (error) {
    toastError(error, '库位加载失败')
  }
}

onMounted(async () => {
  loadWarehouses()
  if (route.query.stockLocationId) {
    openStockById(route.query.stockLocationId)
  }
  try {
    /*
     * 库区下拉要按仓库过滤，所以这里把全部库区拉回来本地过滤。
     * 用 fetchAllOptions 分页拉取 —— 之前写的 size: 200 超过了后端上限 100，
     * 请求直接 10002 失败，而这个 catch 又把错误吞了，
     * 结果「所属库区」下拉一直是空的，界面上还看不到任何提示。
     */
    zones.value = await fetchAllOptions((params) => zoneApi.page(params))
  } catch (error) {
    console.error('[locationView] 加载库区列表失败', error)
    toastError(error, '库区列表加载失败')
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
  { prop: 'usageRate', label: '占用率', width: 150 },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '库位编码 / 名称' },
  { prop: 'warehouseId', label: '所属仓库', type: 'select', options: warehouseOptions.value },
  {
    prop: 'zoneId',
    label: '所属库区',
    type: 'select',
    // 库区不多，直接全列出来（标签带上编码），省掉「选了仓库才能选库区」的联动
    options: zones.value.map((item) => ({ label: item.name + '（' + item.code + '）', value: item.id })),
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
    <PageHeading
      title="库位管理"
      description="库位是库存的最小落位单元。占用率 ≥90% 会标红，点「库存」看该库位放了什么"
    />
    <CrudTable
      :api="locationApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      :initial-query="{ zoneId: route.query.zoneId }"
      :action-width="200"
      entity-name="库位"
    >
      <template #col-warehouseName="{ row }">
        <span>{{ row.warehouseName || warehouseLabel(row.warehouseId) }}</span>
      </template>
      <template #col-zoneName="{ row }">
        <span>{{ row.zoneName || zoneLabel(row.zoneId) }}</span>
      </template>

      <template #col-usageRate="{ row }">
        <div class="usage">
          <div class="usage__track">
            <span
              class="usage__fill"
              :class="usageClass(row)"
              :style="{ width: usagePercent(row) + '%' }"
            ></span>
          </div>
          <span class="usage__text" :class="usageClass(row)">{{ usageText(row) }}</span>
        </div>
      </template>

      <template #row-actions-extra="{ row }">
        <el-button link type="primary" @click.stop="openStock(row)">库存</el-button>
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

    <!-- 库位库存下钻：这个库位现在放了什么 -->
    <el-drawer
      v-model="stockVisible"
      :title="`库位库存 · ${currentLocation?.code || ''}`"
      size="620px"
    >
      <div v-loading="stockLoading" class="stock">
        <div class="stock__head">
          <div class="stock__loc">
            <strong>{{ currentLocation?.name || '-' }}</strong>
            <span>{{ currentLocation?.warehouseName }} / {{ currentLocation?.zoneName }}</span>
          </div>
          <div class="stock__stat">
            <span>在库合计</span>
            <strong>{{ stockTotal.toLocaleString('zh-CN') }}</strong>
            <em>件 · {{ stockRows.length }} 个批次</em>
          </div>
        </div>

        <el-table :data="stockRows" size="small" border>
          <el-table-column prop="skuCode" label="SKU 编码" width="120" />
          <el-table-column prop="skuName" label="商品名称" min-width="150" />
          <el-table-column label="批次号" width="130">
            <template #default="{ row }">
              <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="在库" width="80" align="right">
            <template #default="{ row }"><span class="num">{{ row.qty }}</span></template>
          </el-table-column>
          <el-table-column label="锁定" width="70" align="right">
            <template #default="{ row }"><span class="num">{{ row.lockedQty }}</span></template>
          </el-table-column>
          <el-table-column label="冻结" width="70" align="right">
            <template #default="{ row }"><span class="num">{{ row.frozenQty }}</span></template>
          </el-table-column>
          <el-table-column label="可用" width="80" align="right">
            <template #default="{ row }"><span class="num num--strong">{{ row.availableQty }}</span></template>
          </el-table-column>

          <template #empty>
            <div class="stock__empty">
              <p>这个库位是空的</p>
              <em>上架任务把货放进来之后，这里就能看到了</em>
            </div>
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
/* ---------- 占用率 ---------- */
.usage { display: flex; align-items: center; gap: 8px; }
.usage__track { flex: 1 1 auto; height: 6px; overflow: hidden; border-radius: 999px; background: var(--bg-surface-3); }
.usage__fill { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-500), var(--accent-500)); transition: width var(--duration-slow) var(--ease-out); }
.usage__fill.is-warning { background: linear-gradient(90deg, #f59e0b, #f97316); }
.usage__fill.is-danger { background: linear-gradient(90deg, #ef4444, #dc2626); }
.usage__fill.is-free { width: 0 !important; }
.usage__text { flex: 0 0 auto; min-width: 34px; font-family: var(--font-mono); font-size: 11.5px; color: var(--text-secondary); }
.usage__text.is-warning { color: var(--warning-600); font-weight: 600; }
.usage__text.is-danger { color: var(--danger-600); font-weight: 600; }
.usage__text.is-free { color: var(--text-placeholder); }

/* ---------- 库位库存抽屉 ---------- */
.stock { display: flex; flex-direction: column; gap: 14px; }
.stock__head { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--bg-surface-2); }
.stock__loc { display: flex; flex-direction: column; line-height: 1.5; }
.stock__loc strong { font-size: 14px; color: var(--text-primary); }
.stock__loc span { font-size: 12px; color: var(--text-secondary); }
.stock__stat { display: flex; flex-direction: column; align-items: flex-end; line-height: 1.4; }
.stock__stat span { font-size: 11.5px; color: var(--text-secondary); }
.stock__stat strong { font-size: 20px; font-weight: 700; color: var(--brand-600); font-variant-numeric: tabular-nums; }
.stock__stat em { font-size: 11px; font-style: normal; color: var(--text-placeholder); }
.stock__empty { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 28px 0; }
.stock__empty p { font-size: 13.5px; color: var(--text-regular); }
.stock__empty em { font-size: 12px; font-style: normal; color: var(--text-placeholder); }
.mono { font-family: var(--font-mono); font-size: 12.5px; }
.muted { color: var(--text-placeholder); }
.num { font-family: var(--font-mono); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.num--strong { font-weight: 700; color: var(--brand-600); }
</style>