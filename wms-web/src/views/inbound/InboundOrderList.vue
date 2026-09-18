<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toastError } from '@/utils/error'
import { Plus } from '@element-plus/icons-vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inboundApi } from '@/api/inbound'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import {
  CANCELLABLE_STATUS,
  INBOUND_STATUS,
  INBOUND_STATUS_OPTIONS,
  RECEIVABLE_STATUS,
} from '@/constants/inbound'

const router = useRouter()
const tableRef = ref(null)
const { options: warehouseOptions, load: loadWarehouses, labelOf } = useWarehouseOptions()

onMounted(loadWarehouses)

const columns = [
  { prop: 'orderNo', label: '入库单号', width: 175, mono: true },
  { prop: 'warehouseName', label: '入库仓库', minWidth: 170 },
  { prop: 'supplierName', label: '供应商', minWidth: 150 },
  { prop: 'status', label: '状态', width: 100, tag: INBOUND_STATUS },
  { prop: 'totalReceivedQty', label: '收货进度', width: 140 },
  { prop: 'totalExpectQty', label: '预计总量', width: 100, align: 'right' },
  { prop: 'expectArriveTime', label: '预计到货', width: 175, mono: true, type: 'datetime' },
  { prop: 'createTime', label: '创建时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '单号 / 供应商' },
  { prop: 'warehouseId', label: '入库仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'status', label: '单据状态', type: 'select', options: INBOUND_STATUS_OPTIONS },
  {
    prop: 'createRange',
    label: '创建时间',
    type: 'daterange',
    beginProp: 'beginTime',
    endProp: 'endTime',
  },
])

/** 收货进度百分比，纯展示 */
function progressPercent(row) {
  const expect = Number(row.totalExpectQty) || 0
  const received = Number(row.totalReceivedQty) || 0
  if (!expect) return 0
  return Math.min(100, Math.round((received / expect) * 100))
}

function canReceive(row) {
  return RECEIVABLE_STATUS.includes(row.status)
}

function canCancel(row) {
  return CANCELLABLE_STATUS.includes(row.status)
}

function goCreate() {
  router.push('/inbound/orders/create')
}

function goDetail(row) {
  router.push(`/inbound/orders/${row.id}`)
}

function goReceive(row) {
  router.push({ path: `/inbound/orders/${row.id}`, query: { action: 'receive' } })
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定要取消入库单「${row.orderNo}」吗？取消后不能再收货。`,
      '取消入库单',
      { type: 'warning', confirmButtonText: '取消单据', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }

  try {
    await inboundApi.cancel(row.id)
    ElMessage.success('入库单已取消')
    tableRef.value?.reload()
  } catch (error) {
    toastError(error, '取消失败')
  }
}
</script>

<template>
  <div>
    <PageHeading title="入库单" description="创建入库单并登记收货，收货完成后生成上架任务">
      <el-button type="primary" :icon="Plus" @click="goCreate">新建入库单</el-button>
    </PageHeading>

    <CrudTable
      ref="tableRef"
      :api="inboundApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="190"
      entity-name="入库单"
      @row-click="goDetail"
    >
      <template #col-warehouseName="{ row }">
        <span>{{ row.warehouseName || labelOf(row.warehouseId) }}</span>
      </template>

      <template #col-totalReceivedQty="{ row }">
        <div class="progress">
          <div class="progress__bar">
            <span class="progress__fill" :style="{ width: progressPercent(row) + '%' }"></span>
          </div>
          <span class="progress__text">
            {{ row.totalReceivedQty ?? 0 }}/{{ row.totalExpectQty ?? 0 }}
          </span>
        </div>
      </template>

      <template #row-actions="{ row }">
        <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
        <el-button v-if="canReceive(row)" link type="primary" @click.stop="goReceive(row)">
          收货
        </el-button>
        <el-button v-if="canCancel(row)" link type="danger" @click.stop="handleCancel(row)">
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

.progress__text {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-secondary);
}
</style>