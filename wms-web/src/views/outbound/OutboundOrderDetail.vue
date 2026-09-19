<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { outboundApi } from '@/api/outbound'
import { formatDateTime } from '@/utils/format'
import { toastError } from '@/utils/error'
import {
  ALLOCATABLE_STATUS,
  ALLOCATE_STATUS,
  OUTBOUND_STATUS,
  PICKABLE_STATUS,
  RELEASABLE_STATUS,
  SHIPPABLE_STATUS,
} from '@/constants/outbound'

const route = useRoute()
const router = useRouter()

const orderId = route.params.id
const loading = ref(false)
const order = ref(null)
const acting = ref(false)

const statusMeta = computed(() => OUTBOUND_STATUS[order.value?.status] || { text: '-', type: 'info' })

const canAllocate = computed(() => ALLOCATABLE_STATUS.includes(order.value?.status))
const canRelease = computed(() => RELEASABLE_STATUS.includes(order.value?.status))
const canPick = computed(() => PICKABLE_STATUS.includes(order.value?.status))
const canShip = computed(() => SHIPPABLE_STATUS.includes(order.value?.status))
const canCancel = computed(() => ALLOCATABLE_STATUS.includes(order.value?.status))

/** 进度条：0 建单 → 1 已分配 → 2 拣货中 → 3 已拣完 → 4 已发货 */
const stepActive = computed(() => {
  const map = {
    CREATED: 0,
    ALLOCATED: 1,
    PICKING: 2,
    PICKED: 3,
    SHIPPED: 4,
    CLOSED: 4,
  }
  return map[order.value?.status] ?? 0
})

async function load() {
  loading.value = true
  try {
    order.value = await outboundApi.detail(orderId)
  } catch (error) {
    toastError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 统一的动作执行：确认 → 调接口 → 刷新。
 * 出库的动作比较多（分配/释放/生成拣货任务/发货/取消），收敛成一个函数避免重复。
 */
async function runAction({ confirmTitle, confirmText, confirmButton, api, successText }) {
  try {
    await ElMessageBox.confirm(confirmText, confirmTitle, {
      type: 'warning',
      confirmButtonText: confirmButton,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  if (acting.value) return
  acting.value = true
  try {
    await api()
    ElMessage.success(successText)
    await load()
  } catch (error) {
    toastError(error, '操作失败')
  } finally {
    acting.value = false
  }
}

function handleAllocate() {
  runAction({
    confirmTitle: '分配库存',
    confirmText: '将按 FIFO 锁定库存（入库早、生产日期早的批次优先）。任一明细可用量不足会整单失败。',
    confirmButton: '开始分配',
    api: () => outboundApi.allocate(orderId),
    successText: '库存分配成功',
  })
}

function handleRelease() {
  runAction({
    confirmTitle: '释放分配',
    confirmText: '将把已锁定的库存还回可用量，单据回到「待分配」。只有还没开始拣货才能释放。',
    confirmButton: '释放',
    api: () => outboundApi.release(orderId),
    successText: '已释放分配',
  })
}

function handleGeneratePickTasks() {
  runAction({
    confirmTitle: '生成拣货任务',
    confirmText: '每条分配明细会生成一条拣货任务，单据进入「拣货中」。',
    confirmButton: '生成',
    api: () => outboundApi.generatePickTasks(orderId),
    successText: '拣货任务已生成',
  })
}

function handleShip() {
  runAction({
    confirmTitle: '确认发货',
    confirmText: '发货会核销库存（在库和锁定一起扣减），这一步不可撤销。',
    confirmButton: '确认发货',
    api: () => outboundApi.ship(orderId),
    successText: '发货完成，库存已核销',
  })
}

function handleCancel() {
  runAction({
    confirmTitle: '取消出库单',
    confirmText: `确定要取消出库单「${order.value?.orderNo}」吗？取消后不能再分配。`,
    confirmButton: '取消单据',
    api: () => outboundApi.cancel(orderId),
    successText: '出库单已取消',
  })
}

function goPickTasks() {
  router.push('/picking/tasks')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeading :title="order?.orderNo || '出库单详情'" :description="order?.customerName || ''">
      <el-button :icon="ArrowLeft" @click="router.push('/outbound/orders')">返回列表</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>

      <el-button v-if="canCancel" type="danger" plain @click="handleCancel">取消单据</el-button>
      <el-button v-if="canRelease" @click="handleRelease">释放分配</el-button>
      <el-button v-if="canPick" type="warning" plain @click="handleGeneratePickTasks">
        生成拣货任务
      </el-button>
      <el-button v-if="canAllocate" type="primary" @click="handleAllocate">分配库存</el-button>
      <el-button v-if="order?.status === 'PICKING'" type="primary" plain @click="goPickTasks">
        去拣货
      </el-button>
      <el-button v-if="canShip" type="success" @click="handleShip">确认发货</el-button>
    </PageHeading>

    <template v-if="order">
      <!-- 状态条 -->
      <section class="statusbar">
        <div class="statusbar__left">
          <el-tag :type="statusMeta.type" effect="dark" round size="large">
            {{ statusMeta.text }}
          </el-tag>
          <span v-if="order.status === 'CANCELLED'" class="statusbar__hint statusbar__hint--danger">
            该单据已取消
          </span>
          <span v-else-if="order.status === 'PICKING'" class="statusbar__hint">
            货已从货架拣出，账面暂不变化 —— 发货时才核销库存
          </span>
        </div>

        <el-steps
          v-if="order.status !== 'CANCELLED'"
          class="statusbar__steps"
          :active="stepActive"
          align-center
          finish-status="success"
        >
          <el-step title="创建出库单" />
          <el-step title="分配库存" />
          <el-step title="拣货" />
          <el-step title="发货核销" />
        </el-steps>
      </section>

      <!-- 单据信息 -->
      <section class="card">
        <header class="card__head"><h2>单据信息</h2></header>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="出库仓库">
            {{ order.warehouseName || `#${order.warehouseId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ order.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="要求发货时间">
            {{ formatDateTime(order.expectShipTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="需求总量">{{ order.totalQty ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="已分配">{{ order.allocatedQty ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="已拣货">{{ order.pickedQty ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="分配时间">
            {{ formatDateTime(order.allocateTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(order.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ order.receiveAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ order.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <!-- 出库明细 -->
      <section class="card">
        <header class="card__head">
          <h2>出库明细</h2>
          <span class="card__hint">共 {{ order.items?.length ?? 0 }} 行</span>
        </header>

        <el-table :data="order.items || []" border>
          <el-table-column type="index" label="#" width="52" align="center" />
          <el-table-column prop="skuCode" label="SKU 编码" width="140" />
          <el-table-column prop="skuName" label="商品名称" min-width="180" />
          <el-table-column label="需求数量" width="110" align="right">
            <template #default="{ row }">{{ row.qty ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="已分配" width="110" align="right">
            <template #default="{ row }">
              <span :class="{ 'is-zero': !row.allocatedQty }">{{ row.allocatedQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已拣货" width="110" align="right">
            <template #default="{ row }">
              <span :class="{ 'is-zero': !row.pickedQty }">{{ row.pickedQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140">
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
      </section>

      <!-- 分配明细 -->
      <section class="card">
        <header class="card__head">
          <h2>库存分配明细</h2>
          <span class="card__hint">
            按 FIFO 自动拆分批次：入库早的批次优先，不够才拆到下一批
          </span>
        </header>

        <el-table :data="order.allocates || []" border>
          <el-table-column prop="skuCode" label="SKU 编码" width="140" />
          <el-table-column prop="skuName" label="商品名称" min-width="160" />
          <el-table-column prop="locationCode" label="库位" width="140">
            <template #default="{ row }">
              <span class="mono">{{ row.locationCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次号" width="150">
            <template #default="{ row }">
              <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分配数量" width="110" align="right">
            <template #default="{ row }">
              <span class="num">{{ row.allocatedQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已拣货" width="110" align="right">
            <template #default="{ row }">
              <span class="num" :class="{ 'is-zero': !row.pickedQty }">{{ row.pickedQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="ALLOCATE_STATUS[row.status]?.type || 'info'" size="small" disable-transitions>
                {{ ALLOCATE_STATUS[row.status]?.text || row.status }}
              </el-tag>
            </template>
          </el-table-column>

          <template #empty>
            <div class="empty">
              <p>还没有分配库存</p>
              <em>点击右上角「分配库存」，系统会按 FIFO 自动选批次</em>
            </div>
          </template>
        </el-table>
      </section>
    </template>
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

.statusbar__hint {
  font-size: 13px;
  color: var(--text-secondary);
}

.statusbar__hint--danger {
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

.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.muted {
  color: var(--text-placeholder);
}

.num {
  font-family: var(--font-mono);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.is-zero {
  color: var(--text-placeholder);
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 26px 0;
}

.empty p {
  font-size: 13.5px;
  color: var(--text-regular);
}

.empty em {
  font-size: 12px;
  font-style: normal;
  color: var(--text-placeholder);
}
</style>