<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { outboundApi } from '@/api/outbound'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { toastError } from '@/utils/error'
import {
  ALLOCATABLE_STATUS,
  OUTBOUND_STATUS,
  OUTBOUND_STATUS_OPTIONS,
} from '@/constants/outbound'

const router = useRouter()
const tableRef = ref(null)
const { options: warehouseOptions, load: loadWarehouses, labelOf } = useWarehouseOptions()

onMounted(loadWarehouses)

const columns = [
  { prop: 'orderNo', label: '出库单号', width: 175, mono: true },
  { prop: 'warehouseName', label: '出库仓库', minWidth: 160 },
  { prop: 'customerName', label: '客户', minWidth: 140 },
  { prop: 'status', label: '状态', width: 100, tag: OUTBOUND_STATUS },
  { prop: 'allocatedQty', label: '分配进度', width: 140 },
  { prop: 'pickedQty', label: '拣货进度', width: 140 },
  { prop: 'expectShipTime', label: '要求发货', width: 175, mono: true, type: 'datetime' },
  { prop: 'createTime', label: '创建时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '单号 / 客户' },
  { prop: 'warehouseId', label: '出库仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'status', label: '单据状态', type: 'select', options: OUTBOUND_STATUS_OPTIONS },
  {
    prop: 'createRange',
    label: '创建时间',
    type: 'daterange',
    beginProp: 'beginTime',
    endProp: 'endTime',
  },
])

function percent(done, total) {
  const t = Number(total) || 0
  if (!t) return 0
  return Math.min(100, Math.round((Number(done) || 0) / t * 100))
}

function canAllocate(row) {
  return ALLOCATABLE_STATUS.includes(row.status)
}

function goCreate() {
  router.push('/outbound/orders/create')
}

function goDetail(row) {
  router.push(`/outbound/orders/${row.id}`)
}

async function handleAllocate(row) {
  try {
    await ElMessageBox.confirm(
      `将按 FIFO 为「${row.orderNo}」锁定库存，任一明细可用量不足整单会失败。`,
      '分配库存',
      { type: 'info', confirmButtonText: '开始分配', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await outboundApi.allocate(row.id)
    ElMessage.success('库存分配成功')
    tableRef.value?.reload()
  } catch (error) {
    toastError(error, '分配失败')
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确定要取消出库单「${row.orderNo}」吗？`, '取消出库单', {
      type: 'warning',
      confirmButtonText: '取消单据',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }

  try {
    await outboundApi.cancel(row.id)
    ElMessage.success('出库单已取消')
    tableRef.value?.reload()
  } catch (error) {
    toastError(error, '取消失败')
  }
}
</script>

<template>
  <div>
    <PageHeading title="出库单" description="创建出库单 → 分配库存 → 拣货 → 发货，库存分三步核销">
      <el-button type="primary" :icon="Plus" @click="goCreate">新建出库单</el-button>
    </PageHeading>

    <CrudTable
      ref="tableRef"
      :api="outboundApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="190"
      entity-name="出库单"
      @row-click="goDetail"
    >
      <template #col-warehouseName="{ row }">
        <span>{{ row.warehouseName || labelOf(row.warehouseId) }}</span>
      </template>

      <template #col-allocatedQty="{ row }">
        <div class="progress">
          <div class="progress__bar">
            <span
              class="progress__fill"
              :style="{ width: percent(row.allocatedQty, row.totalQty) + '%' }"
            ></span>
          </div>
          <span class="progress__text">{{ row.allocatedQty ?? 0 }}/{{ row.totalQty ?? 0 }}</span>
        </div>
      </template>

      <template #col-pickedQty="{ row }">
        <div class="progress">
          <div class="progress__bar progress__bar--pick">
            <span
              class="progress__fill progress__fill--pick"
              :style="{ width: percent(row.pickedQty, row.totalQty) + '%' }"
            ></span>
          </div>
          <span class="progress__text">{{ row.pickedQty ?? 0 }}/{{ row.totalQty ?? 0 }}</span>
        </div>
      </template>

      <template #row-actions="{ row }">
        <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
        <el-button v-if="canAllocate(row)" link type="primary" @click.stop="handleAllocate(row)">
          分配
        </el-button>
        <el-button v-if="canAllocate(row)" link type="danger" @click.stop="handleCancel(row)">
          取消
        </el-button>
      </template>
    </CrudTable>
  </div>
</template>

<style scoped>
.progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress__bar {
  position: relative;
  flex: 1 1 auto;
  height: 5px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--bg-surface-3);
}

.progress__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-500), var(--accent-500));
  transition: width var(--duration-base) var(--ease-out);
}

.progress__fill--pick {
  background: linear-gradient(90deg, #f59e0b, #f97316);
}

.progress__text {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-secondary);
}
</style>