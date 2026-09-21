<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toastError } from '@/utils/error'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { locationApi, zoneApi } from '@/api/basedata'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import {
  LOCATION_TYPE_TAG,
  STATUS_OPTIONS,
  STATUS_TAG,
  ZONE_TYPE_OPTIONS,
  ZONE_TYPE_TAG,
} from '@/constants/options'

const router = useRouter()
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

/* ---------------- 库位下钻：这个库区下面有哪些库位 ---------------- */

const locationVisible = ref(false)
const locationLoading = ref(false)
const currentZone = ref(null)
const locations = ref([])

async function openLocations(row) {
  currentZone.value = row
  locations.value = []
  locationVisible.value = true
  locationLoading.value = true
  try {
    locations.value = await fetchAllOptions((params) =>
      locationApi.page({ ...params, zoneId: row.id }),
    )
  } catch (error) {
    toastError(error, '库位列表加载失败')
  } finally {
    locationLoading.value = false
  }
}

/** 容量合计 / 已用合计：让人一眼看出这个库区还剩多少位置 */
const capacityTotal = computed(() =>
  locations.value.reduce((sum, item) => sum + (Number(item.capacity) || 0), 0),
)
const capacityUsed = computed(() =>
  locations.value.reduce((sum, item) => sum + (Number(item.usedCapacity) || 0), 0),
)

function usagePercent(row) {
  const cap = Number(row.capacity) || 0
  if (cap <= 0) return 0
  return Math.min(100, Math.round(((Number(row.usedCapacity) || 0) / cap) * 100))
}

/** capacity=0 是不限容量的临时位，不参与百分比，也不该标红 */
function usageText(row) {
  if (Number(row.capacity) <= 0) return '不限'
  return usagePercent(row) + '%'
}

function usageClass(row) {
  if (Number(row.capacity) <= 0) return 'is-free'
  const p = usagePercent(row)
  if (p >= 90) return 'is-danger'
  if (p >= 70) return 'is-warning'
  return ''
}

/** 再往下一层：跳回库位管理，并直接把这个库位的商品库存抽屉打开 */
function goLocationStock(location) {
  locationVisible.value = false
  router.push({ path: '/basedata/locations', query: { stockLocationId: location.id } })
}
</script>

<template>
  <div>
    <PageHeading
      title="库区管理"
      description="库区是仓库内部的功能分区，决定库位用途与拣货策略。点「库位」看这个库区下面有哪些库位"
    />

    <CrudTable
      :api="zoneApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      :action-width="200"
      entity-name="库区"
    >
      <template #col-warehouseName="{ row }">
        <span>{{ row.warehouseName || labelOf(row.warehouseId) }}</span>
      </template>

      <template #row-actions-extra="{ row }">
        <el-button link type="primary" @click.stop="openLocations(row)">库位</el-button>
      </template>
    </CrudTable>

    <!-- 库区下的库位 -->
    <el-drawer
      v-model="locationVisible"
      :title="`库位 · ${currentZone?.name || ''}`"
      size="760px"
    >
      <div v-loading="locationLoading" class="locs">
        <div class="locs__head">
          <div class="locs__zone">
            <strong>{{ currentZone?.warehouseName || labelOf(currentZone?.warehouseId) }}</strong>
            <span>{{ currentZone?.name }} · {{ currentZone?.code }}</span>
          </div>
          <div class="locs__stat">
            <span>库位数</span>
            <strong>{{ locations.length }}</strong>
          </div>
          <div class="locs__stat">
            <span>容量 / 已用</span>
            <strong>
              {{ capacityTotal || '—' }}<em v-if="capacityTotal"> / {{ capacityUsed }}</em>
            </strong>
          </div>
        </div>

        <el-table :data="locations" size="small" border>
          <el-table-column prop="code" label="库位编码" width="140" />
          <el-table-column prop="name" label="库位名称" min-width="140" />
          <el-table-column label="类型" width="92">
            <template #default="{ row }">
              <el-tag :type="LOCATION_TYPE_TAG[row.locationType]?.type || 'info'" size="small" disable-transitions>
                {{ LOCATION_TYPE_TAG[row.locationType]?.text || row.locationType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="容量" width="80" align="right">
            <template #default="{ row }">
              <span class="num">{{ row.capacity || '不限' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已用" width="80" align="right">
            <template #default="{ row }"><span class="num">{{ row.usedCapacity }}</span></template>
          </el-table-column>
          <el-table-column label="占用率" width="150">
            <template #default="{ row }">
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
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="STATUS_TAG[row.status]?.type || 'info'" size="small" disable-transitions>
                {{ STATUS_TAG[row.status]?.text || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="86" align="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="!row.usedCapacity"
                @click="goLocationStock(row)"
              >
                商品
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="locs__empty">
              <p>这个库区还没有库位</p>
              <em>去「库位管理」新建，库位要挂在库区下面</em>
            </div>
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.locs {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.locs__head {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.locs__zone {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  line-height: 1.5;
}

.locs__zone strong {
  font-size: 14px;
  color: var(--text-primary);
}

.locs__zone span {
  font-size: 12px;
  color: var(--text-secondary);
}

.locs__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.4;
}

.locs__stat span {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.locs__stat strong {
  font-size: 18px;
  font-weight: 700;
  color: var(--brand-600);
  font-variant-numeric: tabular-nums;
}

.locs__stat em {
  font-size: 13px;
  font-style: normal;
  font-weight: 600;
  color: var(--text-placeholder);
}

.locs__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 0;
}

.locs__empty p {
  font-size: 13.5px;
  color: var(--text-regular);
}

.locs__empty em {
  font-size: 12px;
  font-style: normal;
  color: var(--text-placeholder);
}

/* ---------- 占用率 ---------- */
.usage {
  display: flex;
  align-items: center;
  gap: 8px;
}

.usage__track {
  flex: 1 1 auto;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--bg-surface-3);
}

.usage__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-500), var(--accent-500));
  transition: width var(--duration-slow) var(--ease-out);
}

.usage__fill.is-warning {
  background: linear-gradient(90deg, #f59e0b, #f97316);
}

.usage__fill.is-danger {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

.usage__fill.is-free {
  width: 0 !important;
}

.usage__text {
  flex: 0 0 auto;
  min-width: 34px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-secondary);
}

.usage__text.is-warning {
  color: var(--warning-600);
  font-weight: 600;
}

.usage__text.is-danger {
  color: var(--danger-600);
  font-weight: 600;
}

.usage__text.is-free {
  color: var(--text-placeholder);
}

.num {
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}
</style>