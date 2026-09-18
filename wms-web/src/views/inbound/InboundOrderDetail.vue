<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toastError } from '@/utils/error'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import ReceiveDialog from './components/ReceiveDialog.vue'
import { inboundApi } from '@/api/inbound'
import { formatDateTime } from '@/utils/format'
import {
  CANCELLABLE_STATUS,
  INBOUND_STATUS,
  PUTAWAY_GENERATABLE_STATUS,
  RECEIVABLE_STATUS,
} from '@/constants/inbound'

const route = useRoute()
const router = useRouter()

const orderId = route.params.id
const loading = ref(false)
const order = ref(null)
const receiveVisible = ref(false)

const statusMeta = computed(() => INBOUND_STATUS[order.value?.status] || { text: '-', type: 'info' })

const canReceive = computed(() => RECEIVABLE_STATUS.includes(order.value?.status))
const canCancel = computed(() => CANCELLABLE_STATUS.includes(order.value?.status))
const canGeneratePutaway = computed(() =>
  PUTAWAY_GENERATABLE_STATUS.includes(order.value?.status),
)

/** 进度条：0 创建 → 1 收货中 → 2 已收货 → 3 已上架 */
const stepActive = computed(() => {
  const map = { CREATED: 0, RECEIVING: 1, RECEIVED: 2, PUTAWAY_DONE: 3, CLOSED: 3 }
  return map[order.value?.status] ?? 0
})

async function load() {
  loading.value = true
  try {
    order.value = await inboundApi.detail(orderId)
  } catch (error) {
    toastError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(
      `确定要取消入库单「${order.value.orderNo}」吗？取消后不能再收货。`,
      '取消入库单',
      { type: 'warning', confirmButtonText: '取消单据', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  try {
    await inboundApi.cancel(orderId)
    ElMessage.success('入库单已取消')
    await load()
  } catch (error) {
    toastError(error, '取消失败')
  }
}

async function handleGeneratePutaway() {
  try {
    await ElMessageBox.confirm(
      '将按已收货数量生成上架任务，任务会进入任务池等待作业员领取。',
      '生成上架任务',
      { type: 'info', confirmButtonText: '生成', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await inboundApi.generatePutawayTasks(orderId)
    ElMessage.success('上架任务已生成')
    await load()
  } catch (error) {
    toastError(error, '生成失败')
  }
}

onMounted(async () => {
  await load()
  // 从列表页点「收货」进来时直接弹窗
  if (route.query.action === 'receive' && canReceive.value) receiveVisible.value = true
})
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeading :title="order?.orderNo || '入库单详情'" :description="order?.supplierName || ''">
      <el-button :icon="ArrowLeft" @click="router.push('/inbound/orders')">返回列表</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <el-button v-if="canCancel" type="danger" plain @click="handleCancel">取消单据</el-button>
      <el-button v-if="canGeneratePutaway" type="primary" plain @click="handleGeneratePutaway">
        生成上架任务
      </el-button>
      <el-button v-if="canReceive" type="primary" @click="receiveVisible = true">
        收货登记
      </el-button>
    </PageHeading>

    <template v-if="order">
      <!-- 状态条 -->
      <section class="statusbar">
        <div class="statusbar__left">
          <el-tag :type="statusMeta.type" effect="dark" round size="large">
            {{ statusMeta.text }}
          </el-tag>
          <span v-if="order.status === 'CANCELLED'" class="statusbar__cancelled">
            该单据已取消，不能再收货或上架
          </span>
        </div>

        <el-steps
          v-if="order.status !== 'CANCELLED'"
          class="statusbar__steps"
          :active="stepActive"
          align-center
          finish-status="success"
        >
          <el-step title="创建入库单" />
          <el-step title="收货登记" />
          <el-step title="生成上架任务" />
          <el-step title="上架完成" />
        </el-steps>
      </section>

      <!-- 单据信息 -->
      <section class="card">
        <header class="card__head"><h2>单据信息</h2></header>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="入库仓库">
            {{ order.warehouseName || `#${order.warehouseId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="供应商">{{ order.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="预计到货时间">
            {{ formatDateTime(order.expectArriveTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="预计总量">
            {{ order.totalExpectQty ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="实收总量">
            {{ order.totalReceivedQty ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(order.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">
            {{ order.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <!-- 明细 -->
      <section class="card">
        <header class="card__head">
          <h2>入库明细</h2>
          <span class="card__hint">共 {{ order.items?.length ?? 0 }} 行</span>
        </header>

        <el-table :data="order.items || []" border class="detail-table">
          <el-table-column type="index" label="#" width="52" align="center" />
          <el-table-column prop="skuCode" label="SKU 编码" width="140" />
          <el-table-column prop="skuName" label="商品名称" min-width="180" />
          <el-table-column label="预计数量" width="100" align="right">
            <template #default="{ row }">{{ row.expectQty ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="实收数量" width="100" align="right">
            <template #default="{ row }">
              <span :class="{ 'is-zero': !row.receivedQty }">{{ row.receivedQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已上架" width="100" align="right">
            <template #default="{ row }">
              <span :class="{ 'is-zero': !row.putawayQty }">{{ row.putawayQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次号" width="150">
            <template #default="{ row }">
              <span class="mono">{{ row.batchNo || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="生产日期" width="120">
            <template #default="{ row }">{{ row.productionDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="到期日期" width="120">
            <template #default="{ row }">{{ row.expiryDate || '-' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140">
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <ReceiveDialog v-model="receiveVisible" :order="order" @success="load" />
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 320px;
}

.statusbar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 22px 8px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.statusbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.statusbar__cancelled {
  font-size: 13px;
  color: var(--danger-600);
}

.statusbar__steps {
  padding-bottom: 10px;
}

.card {
  padding: 20px 22px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--divider);
}

.card__head h2 {
  font-size: 15px;
  font-weight: 650;
  color: var(--text-primary);
}

.card__hint {
  font-size: 12px;
  color: var(--text-placeholder);
}

.detail-table :deep(td) {
  font-size: 13px;
}

.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.is-zero {
  color: var(--text-placeholder);
}
</style>