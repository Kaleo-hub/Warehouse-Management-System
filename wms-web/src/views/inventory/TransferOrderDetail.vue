<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { transferOrderApi } from '@/api/inventory'
import { TRANSFER_ORDER_STATUS } from '@/constants/inventory'
import { useIdempotentKey } from '@/composables/useIdempotentKey'
import { formatDateTime } from '@/utils/format'
import { toastError } from '@/utils/error'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const order = ref(null)
const loading = ref(false)
const acting = ref(false)
const completeKey = useIdempotentKey()
const statusMeta = computed(() => TRANSFER_ORDER_STATUS[order.value?.status] || { text: '-', type: 'info' })
const canComplete = computed(() => order.value?.status === 'CREATED')

async function load() {
  loading.value = true
  try { order.value = await transferOrderApi.detail(id) }
  catch (error) { toastError(error, '移库单加载失败') }
  finally { loading.value = false }
}

async function completeOrder() {
  try { await ElMessageBox.confirm('确认后会在一个事务里执行全部转出/转入，任一行失败整单回滚。', '执行移库', { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' }) }
  catch { return }
  if (acting.value) return
  acting.value = true
  try { await transferOrderApi.complete(id, completeKey.value); completeKey.reset(); ElMessage.success('移库完成'); await load() }
  catch (error) { toastError(error, '移库失败') }
  finally { acting.value = false }
}

async function cancelOrder() {
  try { await ElMessageBox.confirm('取消后不能执行该移库单，确认取消？', '取消移库单', { type: 'warning' }) }
  catch { return }
  try { await transferOrderApi.cancel(id); ElMessage.success('移库单已取消'); await load() }
  catch (error) { toastError(error, '取消失败') }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeading :title="order?.orderNo || '移库单详情'" :description="order ? `${order.fromLocationCode} → ${order.toLocationCode}` : ''">
      <el-button :icon="ArrowLeft" @click="router.push('/inventory/transfer')">返回列表</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button v-if="canComplete" type="danger" plain @click="cancelOrder">取消单据</el-button>
      <el-button v-if="canComplete" type="primary" :loading="acting" @click="completeOrder">执行移库</el-button>
    </PageHeading>

    <template v-if="order">
      <section class="statusbar">
        <el-tag :type="statusMeta.type" effect="dark" round size="large">{{ statusMeta.text }}</el-tag>
        <span v-if="order.status === 'COMPLETED'">执行时间 {{ formatDateTime(order.transferTime) }}</span>
        <span v-else-if="order.status === 'CANCELLED'">该移库单已取消</span>
        <span v-else>确认执行前库存不会发生变化</span>
      </section>
      <section class="card">
        <header class="card__head"><h2>移库信息</h2></header>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="仓库">{{ order.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="移出库位">{{ order.fromLocationCode }}</el-descriptions-item>
          <el-descriptions-item label="移入库位">{{ order.toLocationCode }}</el-descriptions-item>
          <el-descriptions-item label="总数量">{{ order.totalQty }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(order.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ formatDateTime(order.transferTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ order.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>
      <section class="card">
        <header class="card__head"><h2>移库明细</h2></header>
        <el-table :data="order.items || []" border>
          <el-table-column type="index" label="#" width="52" align="center" />
          <el-table-column prop="skuCode" label="SKU 编码" width="140" />
          <el-table-column prop="skuName" label="商品名称" min-width="180" />
          <el-table-column label="批次" width="160"><template #default="{ row }"><span class="mono">{{ row.batchNo || '无批次' }}</span></template></el-table-column>
          <el-table-column prop="qty" label="移库数量" width="110" align="right" />
          <el-table-column prop="remark" label="备注" min-width="160" />
        </el-table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px; }
.statusbar { display: flex; align-items: center; gap: 14px; padding: 16px 20px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); color: var(--text-secondary); }
.card { padding: 20px 22px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-xs); }
.card__head { padding-bottom: 14px; margin-bottom: 16px; border-bottom: 1px solid var(--divider); }
.card__head h2 { font-size: 15px; color: var(--text-primary); }
.mono { font-family: var(--font-mono); font-size: 12.5px; }
</style>