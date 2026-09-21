<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inventoryApi } from '@/api/inventory'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { useAppStore } from '@/stores/app'
import { ZONE_TYPE_TAG } from '@/constants/options'
import { toastError } from '@/utils/error'
import { useLatestRequest } from '@/composables/useLatestRequest'

/**
 * 库存汇总：把逐条明细按维度合并。
 *
 * 明细视图回答「A 库位放了什么」，汇总视图回答「我这个仓一共多少货」——
 * 后者才是管理员每天要看的东西。
 */
const app = useAppStore()
const { loading, begin, isLatest, finish } = useLatestRequest()
const rows = ref([])
const dimension = ref('WAREHOUSE')
const warehouseId = ref(app.currentWarehouseId)

const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()

const DIMENSIONS = [
  { label: '按仓库', value: 'WAREHOUSE' },
  { label: '按库区', value: 'ZONE' },
  { label: '按 SKU', value: 'SKU' },
]

/** 表格第一列的表头随维度变化 */
const nameLabel = computed(() => ({
  WAREHOUSE: '仓库',
  ZONE: '库区',
  SKU: '商品',
}[dimension.value]))

const isSku = computed(() => dimension.value === 'SKU')
const isZone = computed(() => dimension.value === 'ZONE')

const totals = computed(() => rows.value.reduce(
  (acc, r) => ({
    qty: acc.qty + Number(r.qty || 0),
    availableQty: acc.availableQty + Number(r.availableQty || 0),
    lockedQty: acc.lockedQty + Number(r.lockedQty || 0),
    frozenQty: acc.frozenQty + Number(r.frozenQty || 0),
  }),
  { qty: 0, availableQty: 0, lockedQty: 0, frozenQty: 0 },
))

const maxQty = computed(() => Math.max(1, ...rows.value.map((r) => Number(r.qty) || 0)))

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN') }
function percent(v) { return Math.round(((Number(v) || 0) / maxQty.value) * 100) }

async function load() {
  const token = begin()
  try {
    const data = await inventoryApi.groupedSummary({
      dimension: dimension.value,
      warehouseId: warehouseId.value || undefined,
    })
    if (!isLatest(token)) return
    rows.value = data
  } catch (error) {
    if (!isLatest(token)) return
    toastError(error, '加载汇总失败')
    rows.value = []
  } finally {
    finish(token)
  }
}

watch(() => app.currentWarehouseId, (id) => { warehouseId.value = id })

watch([dimension, warehouseId], load)
onMounted(async () => { await loadWarehouses(); await load() })
</script>

<template>
  <div>
    <PageHeading
      title="库存汇总"
      description="按仓库 / 库区 / 商品三种维度看库存分布，回答「我这个仓一共多少货」"
    >
      <el-radio-group v-model="dimension" size="default">
        <el-radio-button v-for="d in DIMENSIONS" :key="d.value" :value="d.value">
          {{ d.label }}
        </el-radio-button>
      </el-radio-group>
      <el-select
        v-model="warehouseId"
        placeholder="全部仓库"
        clearable
        style="width: 160px"
      >
        <el-option
          v-for="o in warehouseOptions"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </PageHeading>

    <!-- 合计条 -->
    <section class="totals">
      <div class="total">
        <span class="total__label">在库合计</span>
        <strong class="total__value">{{ fmt(totals.qty) }}</strong>
      </div>
      <div class="total">
        <span class="total__label">可用合计</span>
        <strong class="total__value total__value--brand">{{ fmt(totals.availableQty) }}</strong>
      </div>
      <div class="total">
        <span class="total__label">锁定 / 冻结</span>
        <strong class="total__value">{{ fmt(totals.lockedQty) }} / {{ fmt(totals.frozenQty) }}</strong>
      </div>
      <div class="total">
        <span class="total__label">分组数</span>
        <strong class="total__value">{{ rows.length }}</strong>
      </div>
    </section>

    <section class="card">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column :label="nameLabel" min-width="220">
          <template #default="{ row }">
            <div class="name">
              <span class="name__main">{{ row.groupName || '-' }}</span>
              <span class="name__code">{{ row.groupCode }}</span>
              <el-tag
                v-if="isZone && row.groupType"
                :type="ZONE_TYPE_TAG[row.groupType]?.type || 'info'"
                size="small"
                disable-transitions
              >
                {{ ZONE_TYPE_TAG[row.groupType]?.text || row.groupType }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column v-if="isZone" label="所属仓库" min-width="150">
          <template #default="{ row }">{{ row.parentName || '-' }}</template>
        </el-table-column>

        <el-table-column label="在库" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ fmt(row.qty) }}</span></template>
        </el-table-column>
        <el-table-column label="锁定" width="90" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ 'is-zero': !row.lockedQty }">{{ fmt(row.lockedQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="冻结" width="90" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ 'is-zero': !row.frozenQty }">{{ fmt(row.frozenQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用" width="110" align="right">
          <template #default="{ row }"><span class="num num--strong">{{ fmt(row.availableQty) }}</span></template>
        </el-table-column>

        <el-table-column v-if="!isSku" label="SKU 数" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.skuCount) }}</template>
        </el-table-column>
        <el-table-column label="库位数" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.locationCount) }}</template>
        </el-table-column>

        <el-table-column label="占比" min-width="160">
          <template #default="{ row }">
            <div class="ratio">
              <div class="ratio__track">
                <span class="ratio__fill" :style="{ width: percent(row.qty) + '%' }"></span>
              </div>
              <span class="ratio__text">{{ percent(row.qty) }}%</span>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <p>还没有库存数据</p>
            <em>走一遍「入库 → 收货 → 上架」，这里就会有数据了</em>
          </div>
        </template>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.totals {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
  padding: 16px 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}
.total { display: flex; flex-direction: column; gap: 4px; }
.total__label { font-size: 12px; color: var(--text-secondary); }
.total__value { font-size: 22px; font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.total__value--brand { color: var(--brand-600); }

.card { padding: 4px 0; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-xs); overflow: hidden; }

.name { display: flex; align-items: center; gap: 8px; }
.name__main { font-size: 13.5px; color: var(--text-primary); }
.name__code { font-family: var(--font-mono); font-size: 11.5px; color: var(--text-placeholder); }

.num { font-family: var(--font-mono); font-size: 13px; font-variant-numeric: tabular-nums; color: var(--text-regular); }
.num--strong { font-weight: 700; color: var(--brand-600); }
.is-zero { color: var(--text-placeholder); }

.ratio { display: flex; align-items: center; gap: 8px; }
.ratio__track { flex: 1 1 auto; height: 6px; overflow: hidden; border-radius: 999px; background: var(--bg-surface-3); }
.ratio__fill { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-500), var(--accent-500)); transition: width var(--duration-slow) var(--ease-out); }
.ratio__text { flex: 0 0 auto; font-family: var(--font-mono); font-size: 11.5px; color: var(--text-secondary); }

.empty { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 30px 0; }
.empty p { font-size: 13.5px; color: var(--text-regular); }
.empty em { font-size: 12px; font-style: normal; color: var(--text-placeholder); }
</style>
