<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { stocktakingOrderApi } from '@/api/inventory'
import { STOCKTAKING_ORDER_STATUS } from '@/constants/inventory'
import { useIdempotentKey } from '@/composables/useIdempotentKey'
import { formatDateTime } from '@/utils/format'
import { toastError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const order = ref(null)
const loading = ref(false)
const acting = ref(false)
const saveKey = useIdempotentKey()
const postKey = useIdempotentKey()

const statusMeta = computed(() => STOCKTAKING_ORDER_STATUS[order.value?.status] || { text: '-', type: 'info' })
const canEdit = computed(() => order.value?.status === 'COUNTING')
const allCounted = computed(() => (order.value?.items || []).every((row) => row.actualQty !== null && row.actualQty !== undefined))
const filledCount = computed(() => (order.value?.items || []).filter((row) => row.actualQty !== null && row.actualQty !== undefined).length)

function diffOf(row) {
  if (row.actualQty === null || row.actualQty === undefined) return null
  return Number(row.actualQty) - Number(row.systemQty || 0)
}

async function load() {
  loading.value = true
  try {
    order.value = await stocktakingOrderApi.detail(id)
  } catch (error) {
    toastError(error, '盘点单加载失败')
  } finally {
    loading.value = false
  }
}

async function persistCounts() {
  const lines = (order.value?.items || [])
    .filter((row) => row.actualQty !== null && row.actualQty !== undefined)
    .map((row) => ({ id: row.id, actualQty: Number(row.actualQty), remark: row.remark || '' }))
  if (!lines.length) throw new Error('EMPTY_COUNTS')
  await stocktakingOrderApi.saveCounts(id, { items: lines }, saveKey.value)
  saveKey.reset()
}

async function saveCounts() {
  if (acting.value) return
  if (!filledCount.value) {
    ElMessage.warning('请至少录入一行实盘数量')
    return
  }
  acting.value = true
  try {
    await persistCounts()
    ElMessage.success('盘点结果已保存')
    await load()
  } catch (error) {
    if (error.message !== 'EMPTY_COUNTS') toastError(error, '保存失败')
  } finally {
    acting.value = false
  }
}

async function postOrder() {
  if (!allCounted.value) {
    ElMessage.warning('所有明细都必须录入实盘数量后才能过账')
    return
  }
  const invalid = (order.value.items || []).find(
    (row) => Number(row.actualQty) < Number(row.lockedQty || 0) + Number(row.frozenQty || 0),
  )
  if (invalid) {
    ElMessage.warning(`${invalid.skuCode} / ${invalid.batchNo || '无批次'} 的实盘数量低于锁定 + 冻结`)
    return
  }
  try {
    await ElMessageBox.confirm('过账会按实盘差异修改库存并写入盘盈/盘亏流水，确认执行？', '确认盘点过账', {
      type: 'warning', confirmButtonText: '确认过账', cancelButtonText: '再检查一下',
    })
  } catch { return }
  if (acting.value) return
  acting.value = true
  try {
    await persistCounts()
    await stocktakingOrderApi.post(id, postKey.value)
    postKey.reset()
    ElMessage.success('盘点过账完成')
    await load()
  } catch (error) {
    toastError(error, '过账失败')
  } finally {
    acting.value = false
  }
}

async function cancelOrder() {
  try {
    await ElMessageBox.confirm('取消后本盘点单不能继续录入或过账，确认取消？', '取消盘点单', {
      type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '返回',
    })
  } catch { return }
  try {
    await stocktakingOrderApi.cancel(id)
    ElMessage.success('盘点单已取消')
    await load()
  } catch (error) {
    toastError(error, '取消失败')
  }
}

function diffClass(value) {
  if (value > 0) return 'diff diff--gain'
  if (value < 0) return 'diff diff--loss'
  return 'diff'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeading :title="order?.orderNo || '盘点单详情'" :description="order ? `${order.locationCode} · ${order.locationName}` : ''">
      <el-button :icon="ArrowLeft" @click="router.push('/inventory/stocktaking')">返回列表</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button v-if="canEdit" type="danger" plain @click="cancelOrder">取消单据</el-button>
      <el-button v-if="canEdit" :loading="acting" @click="saveCounts">保存录入</el-button>
      <el-button v-if="canEdit" type="primary" :disabled="!allCounted" :loading="acting" @click="postOrder">过账</el-button>
    </PageHeading>

    <template v-if="order">
      <section class="statusbar">
        <el-tag :type="statusMeta.type" effect="dark" round size="large">{{ statusMeta.text }}</el-tag>
        <span v-if="canEdit">已录入 {{ filledCount }}/{{ order.totalItems }} 行，全部录入后才能过账</span>
        <span v-else-if="order.status === 'COMPLETED'">已过账：盘盈 {{ order.gainQty || 0 }}，盘亏 {{ order.lossQty || 0 }}，过账时间 {{ formatDateTime(order.postTime) }}</span>
      </section>

      <section class="card">
        <header class="card__head"><h2>单据信息</h2></header>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="盘点仓库">{{ order.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="盘点库位">{{ order.locationCode }} · {{ order.locationName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(order.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="明细总数">{{ order.totalItems }}</el-descriptions-item>
          <el-descriptions-item label="差异行">{{ order.diffItems || 0 }}</el-descriptions-item>
          <el-descriptions-item label="盘盈 / 盘亏">{{ order.gainQty || 0 }} / {{ order.lossQty || 0 }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ order.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="card">
        <header class="card__head"><h2>盘点明细</h2><span class="hint">盘盈 = 正数，盘亏 = 负数；实盘数量不能低于锁定 + 冻结</span></header>
        <el-table :data="order.items || []" border>
          <el-table-column type="index" label="#" width="52" align="center" />
          <el-table-column prop="skuCode" label="SKU 编码" width="130" />
          <el-table-column prop="skuName" label="商品名称" min-width="150" />
          <el-table-column label="批次" width="140"><template #default="{ row }"><span class="mono">{{ row.batchNo || '无批次' }}</span></template></el-table-column>
          <el-table-column prop="systemQty" label="系统数量" width="100" align="right" />
          <el-table-column prop="lockedQty" label="锁定" width="80" align="right" />
          <el-table-column prop="frozenQty" label="冻结" width="80" align="right" />
          <el-table-column label="实盘数量" width="160" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.actualQty" :min="Number(row.lockedQty || 0) + Number(row.frozenQty || 0)" :disabled="!canEdit" controls-position="right" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="差异" width="100" align="right">
            <template #default="{ row }"><span :class="diffClass(diffOf(row))">{{ diffOf(row) == null ? '-' : (diffOf(row) > 0 ? `+${diffOf(row)}` : diffOf(row)) }}</span></template>
          </el-table-column>
          <el-table-column label="备注" min-width="140"><template #default="{ row }"><el-input v-model="row.remark" :disabled="!canEdit" size="small" /></template></el-table-column>
        </el-table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px; }
.statusbar { display: flex; align-items: center; gap: 14px; padding: 16px 20px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-secondary); }
.card { padding: 20px 22px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-xs); }
.card__head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; padding-bottom: 14px; margin-bottom: 16px; border-bottom: 1px solid var(--divider); }
.card__head h2 { font-size: 15px; color: var(--text-primary); }
.hint { font-size: 12px; color: var(--text-placeholder); }
.mono { font-family: var(--font-mono); font-size: 12.5px; }
.diff { font-family: var(--font-mono); font-weight: 650; color: var(--text-secondary); }
.diff--gain { color: var(--success-600); }
.diff--loss { color: var(--danger-600); }
</style>