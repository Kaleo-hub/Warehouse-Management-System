<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inventoryApi } from '@/api/inventory'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { useAppStore } from '@/stores/app'
import { toastError } from '@/utils/error'

/**
 * 库存预警：三类需要人工处理的情况。
 *
 *   临期      —— 快到期了，该优先发出去
 *   冻结      —— 冻着没动，占着可用量
 *   库位超容  —— 数据异常（正常流程不该出现），要人工核对
 */
const app = useAppStore()
const loading = ref(false)
const alerts = ref({ expiring: [], frozen: [], overCapacity: [], expireDays: 30 })
const activeTab = ref('expiring')
const expireDays = ref(app.alertDays)
const warehouseId = ref(app.currentWarehouseId)

const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()

const counts = computed(() => ({
  expiring: alerts.value.expiring?.length || 0,
  frozen: alerts.value.frozen?.length || 0,
  overCapacity: alerts.value.overCapacity?.length || 0,
}))

const total = computed(() => counts.value.expiring + counts.value.frozen + counts.value.overCapacity)

const QUICK_DAYS = [7, 30, 90]

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN') }

/** 剩余天数：已过期（负数）标红，7 天内标橙，其余标蓝 */
function expiryTagType(days) {
  const d = Number(days)
  if (d < 0) return 'danger'
  if (d <= 7) return 'warning'
  return 'primary'
}
function expiryText(days) {
  const d = Number(days)
  if (d < 0) return `已过期 ${Math.abs(d)} 天`
  if (d === 0) return '今天到期'
  return `还剩 ${d} 天`
}

async function load() {
  loading.value = true
  try {
    alerts.value = await inventoryApi.alerts({
      expireDays: expireDays.value,
      warehouseId: warehouseId.value || undefined,
    })
  } catch (error) {
    toastError(error, '加载预警失败')
  } finally {
    loading.value = false
  }
}

watch([() => app.currentWarehouseId, () => app.alertDays], ([warehouse, days]) => {
  warehouseId.value = warehouse
  expireDays.value = days
})

watch([expireDays, warehouseId], load)
onMounted(async () => { await loadWarehouses(); await load() })
</script>

<template>
  <div>
    <PageHeading
      title="库存预警"
      description="临期、长期冻结、库位超容 —— 三类需要人工处理的库存情况"
    >
      <el-select v-model="warehouseId" placeholder="全部仓库" clearable style="width: 160px">
        <el-option v-for="o in warehouseOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
    </PageHeading>

    <!-- 总览条 -->
    <section class="overview" :class="{ 'is-clear': total === 0 }">
      <el-icon class="overview__icon"><WarningFilled /></el-icon>
      <div class="overview__text">
        <strong v-if="total > 0">共 {{ total }} 条需要关注</strong>
        <strong v-else>暂无库存预警</strong>
        <span v-if="total > 0">
          临期 {{ counts.expiring }} · 冻结 {{ counts.frozen }} · 超容 {{ counts.overCapacity }}
        </span>
        <span v-else>临期、冻结、库位超容都正常</span>
      </div>
    </section>

    <section class="card">
      <el-tabs v-model="activeTab">
        <!-- ============ 临期 ============ -->
        <el-tab-pane name="expiring">
          <template #label>
            <span class="tab-label">临期商品<span v-if="counts.expiring" class="tab-badge">{{ counts.expiring }}</span></span>
          </template>

          <div class="toolbar">
            <span class="toolbar__label">临期阈值</span>
            <el-radio-group v-model="expireDays" size="small">
              <el-radio-button v-for="d in QUICK_DAYS" :key="d" :value="d">{{ d }} 天</el-radio-button>
            </el-radio-group>
            <span class="toolbar__hint">
              已过期的也会列出来（剩余天数为负）
            </span>
          </div>

          <el-table v-loading="loading" :data="alerts.expiring || []" stripe>
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <div class="cell-name">
                  <span>{{ row.skuName }}</span>
                  <em class="mono">{{ row.skuCode }}</em>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="批次号" width="150">
              <template #default="{ row }">
                <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库位" width="140">
              <template #default="{ row }"><span class="mono">{{ row.locationCode || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="在库" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ fmt(row.qty) }}</span></template>
            </el-table-column>
            <el-table-column label="生产日期" width="120">
              <template #default="{ row }">{{ row.productionDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="到期日期" width="120">
              <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
            </el-table-column>
            <el-table-column label="剩余" width="130">
              <template #default="{ row }">
                <el-tag :type="expiryTagType(row.daysToExpire)" size="small" disable-transitions>
                  {{ expiryText(row.daysToExpire) }}
                </el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty">
                <p>{{ expireDays }} 天内没有临期商品</p>
                <em>商品要有到期日期才会出现在这里（收货时登记）</em>
              </div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- ============ 冻结 ============ -->
        <el-tab-pane name="frozen">
          <template #label>
            <span class="tab-label">冻结库存<span v-if="counts.frozen" class="tab-badge">{{ counts.frozen }}</span></span>
          </template>

          <div class="toolbar">
            <span class="toolbar__hint">冻结的库存不参与出库分配，长期冻着等于占着可用量</span>
          </div>

          <el-table v-loading="loading" :data="alerts.frozen || []" stripe>
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <div class="cell-name">
                  <span>{{ row.skuName }}</span>
                  <em class="mono">{{ row.skuCode }}</em>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="批次号" width="150">
              <template #default="{ row }">
                <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库位" width="140">
              <template #default="{ row }"><span class="mono">{{ row.locationCode || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="在库" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ fmt(row.qty) }}</span></template>
            </el-table-column>
            <el-table-column label="冻结" width="100" align="right">
              <template #default="{ row }"><span class="num is-frozen">{{ fmt(row.frozenQty) }}</span></template>
            </el-table-column>
            <el-table-column label="可用" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ fmt(row.availableQty) }}</span></template>
            </el-table-column>
            <template #empty>
              <div class="empty"><p>没有冻结中的库存</p></div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- ============ 超容 ============ -->
        <el-tab-pane name="overCapacity">
          <template #label>
            <span class="tab-label">库位超容<span v-if="counts.overCapacity" class="tab-badge">{{ counts.overCapacity }}</span></span>
          </template>

          <div class="toolbar">
            <span class="toolbar__hint">
              正常流程下不该出现（上架时会校验容量），有数据说明哪里绕过了校验，需要人工核对
            </span>
          </div>

          <el-table v-loading="loading" :data="alerts.overCapacity || []" stripe>
            <el-table-column label="库位编码" width="160">
              <template #default="{ row }"><span class="mono">{{ row.locationCode }}</span></template>
            </el-table-column>
            <el-table-column prop="locationName" label="库位名称" min-width="170" />
            <el-table-column label="仓库" min-width="150">
              <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
            </el-table-column>
            <el-table-column label="库区" min-width="140">
              <template #default="{ row }">{{ row.zoneName || '-' }}</template>
            </el-table-column>
            <el-table-column label="容量" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ fmt(row.capacity) }}</span></template>
            </el-table-column>
            <el-table-column label="已用" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ fmt(row.usedCapacity) }}</span></template>
            </el-table-column>
            <el-table-column label="超出" width="100" align="right">
              <template #default="{ row }"><span class="num is-over">+{{ fmt(row.overQty) }}</span></template>
            </el-table-column>
            <template #empty>
              <div class="empty"><p>没有库位超容</p><em>库位容量数据是正常的</em></div>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.overview {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 14px; padding: 14px 20px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(224,135,0,.35);
  background: rgba(224,135,0,.08);
}
.overview.is-clear { border-color: var(--border-color); background: var(--bg-surface); }
.overview__icon { font-size: 20px; color: var(--warning-500); }
.overview.is-clear .overview__icon { color: var(--text-placeholder); }
.overview__text { display: flex; flex-direction: column; line-height: 1.5; }
.overview__text strong { font-size: 14px; color: var(--text-primary); }
.overview__text span { font-size: 12px; color: var(--text-secondary); }

.card { padding: 4px 20px 16px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-xs); }

.tab-label { display: inline-flex; align-items: center; gap: 6px; }
.tab-badge {
  display: inline-block; min-width: 18px; padding: 0 5px;
  font-size: 11px; line-height: 17px; text-align: center;
  color: #fff; background: var(--danger-500); border-radius: 999px;
}

.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.toolbar__label { font-size: 13px; color: var(--text-regular); }
.toolbar__hint { font-size: 12px; color: var(--text-placeholder); }

.cell-name { display: flex; flex-direction: column; line-height: 1.4; }
.cell-name em { font-style: normal; font-size: 11.5px; color: var(--text-placeholder); }
.mono { font-family: var(--font-mono); font-size: 12.5px; }
.muted { color: var(--text-placeholder); }
.num { font-family: var(--font-mono); font-size: 13px; font-variant-numeric: tabular-nums; color: var(--text-regular); }
.is-frozen { font-weight: 700; color: var(--warning-600); }
.is-over { font-weight: 700; color: var(--danger-600); }

.empty { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 30px 0; }
.empty p { font-size: 13.5px; color: var(--text-regular); }
.empty em { font-size: 12px; font-style: normal; color: var(--text-placeholder); }
</style>
