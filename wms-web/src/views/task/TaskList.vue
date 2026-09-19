<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { stockTaskApi } from '@/api/task'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { toastError } from '@/utils/error'
import { TASK_STATUS, TASK_STATUS_OPTIONS, TASK_TYPE } from '@/constants/inbound'

/**
 * 作业任务列表，上架（PUTAWAY）和拣货（PICK）共用。
 *
 * 【为什么按类型区分文案】
 * 上架是「暂存位 → 存储位，完成后库存增加」；
 * 拣货是「存储位 → 暂存位，完成后库存不变（发货才核销）」。
 * 两者的路径方向、按钮文案、成功提示都不一样，混用会让作业员看错。
 */
const props = defineProps({
  /** PUTAWAY / PICK */
  taskType: { type: String, required: true },
})

const tableRef = ref(null)
const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()

const completeVisible = ref(false)
const completing = ref(false)
const currentTask = ref(null)
const completeForm = reactive({ actualQty: 0, remark: '' })

const isPick = computed(() => props.taskType === 'PICK')

const pageTitle = computed(() => (isPick.value ? '拣货任务' : '上架任务'))
const pageDesc = computed(() =>
  isPick.value
    ? '按分配明细生成，从库位拣出货物。拣货不动库存，发货才核销'
    : '收货完成后生成任务，执行上架的瞬间写入库存与流水',
)
const routeLabel = computed(() => (isPick.value ? '拣货路径' : '上架路径'))
const completeAction = computed(() => (isPick.value ? '完成拣货' : '完成上架'))
const completeTitle = computed(() => (isPick.value ? '完成拣货' : '完成上架'))
const qtyLabel = computed(() => (isPick.value ? '实际拣货数量' : '实际上架数量'))
const successText = computed(() =>
  isPick.value ? '拣货完成，等待发货核销库存' : '上架完成，库存已增加',
)

/** 按任务类型过滤，避免上架页混进拣货任务 */
const api = {
  page: (params) => stockTaskApi.page({ ...params, taskType: props.taskType }),
}

onMounted(loadWarehouses)

// 路径列标题按任务类型变化：上架是「暂存位 → 存储位」，拣货是「存储位 → 暂存位」
const columns = computed(() => [
  { prop: 'taskNo', label: '任务号', width: 190, mono: true },
  { prop: 'status', label: '状态', width: 100, tag: TASK_STATUS },
  { prop: 'skuCode', label: 'SKU 编码', width: 130, mono: true },
  { prop: 'skuName', label: '商品名称', minWidth: 160 },
  { prop: 'fromLocationCode', label: routeLabel.value, minWidth: 210 },
  { prop: 'planQty', label: '计划数量', width: 100, align: 'right' },
  { prop: 'actualQty', label: '实际数量', width: 100, align: 'right' },
  { prop: 'batchNo', label: '批次号', width: 120 },
  { prop: 'assigneeName', label: '执行人', width: 100 },
  { prop: 'createTime', label: '创建时间', width: 175, mono: true, type: 'datetime' },
])

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '任务号 / SKU' },
  { prop: 'warehouseId', label: '仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'status', label: '任务状态', type: 'select', options: TASK_STATUS_OPTIONS },
])

const canAssign = (row) => row.status === 'CREATED'
const canStart = (row) => row.status === 'ASSIGNED'
const canComplete = (row) => row.status === 'PROCESSING'

async function handleAssign(row) {
  try {
    await stockTaskApi.assign(row.id)
    ElMessage.success('任务已领取')
    tableRef.value?.reload()
  } catch (error) {
    // 并发下被别人抢走返回 70003
    toastError(error, '领取失败')
  }
}

async function handleStart(row) {
  try {
    await stockTaskApi.start(row.id)
    ElMessage.success('任务已开始')
    tableRef.value?.reload()
  } catch (error) {
    toastError(error, '操作失败')
  }
}

function openComplete(row) {
  currentTask.value = row
  completeForm.actualQty = row.planQty ?? 0
  completeForm.remark = ''
  completeVisible.value = true
}

async function submitComplete() {
  if (completing.value) return
  const qty = Number(completeForm.actualQty)
  if (qty < 0) {
    ElMessage.warning('实际数量不能为负')
    return
  }

  completing.value = true
  try {
    await stockTaskApi.complete(currentTask.value.id, {
      actualQty: qty,
      remark: completeForm.remark,
    })
    ElMessage.success(successText.value)
    completeVisible.value = false
    tableRef.value?.reload()
  } catch (error) {
    toastError(error, '操作失败')
  } finally {
    completing.value = false
  }
}
</script>

<template>
  <div>
    <PageHeading :title="pageTitle" :description="pageDesc" />

    <CrudTable
      ref="tableRef"
      :api="api"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="160"
      :entity-name="pageTitle"
    >
      <template #col-fromLocationCode="{ row }">
        <span class="route">
          <span class="mono">{{ row.fromLocationCode || '—' }}</span>
          <span class="route__arrow">→</span>
          <span class="mono route__target">{{ row.toLocationCode || '—' }}</span>
        </span>
      </template>

      <template #col-planQty="{ row }">
        <span class="num">{{ row.planQty ?? 0 }}</span>
      </template>

      <template #col-actualQty="{ row }">
        <span class="num" :class="{ 'is-zero': !row.actualQty }">{{ row.actualQty ?? 0 }}</span>
      </template>

      <template #row-actions="{ row }">
        <el-button v-if="canAssign(row)" link type="primary" @click.stop="handleAssign(row)">
          领取
        </el-button>
        <el-button v-if="canStart(row)" link type="primary" @click.stop="handleStart(row)">
          开始
        </el-button>
        <el-button v-if="canComplete(row)" link type="primary" @click.stop="openComplete(row)">
          {{ completeAction }}
        </el-button>
        <span v-if="!canAssign(row) && !canStart(row) && !canComplete(row)" class="actions-none">—</span>
      </template>
    </CrudTable>

    <el-dialog v-model="completeVisible" :title="completeTitle" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="商品">
          <span>{{ currentTask?.skuCode }} {{ currentTask?.skuName }}</span>
        </el-form-item>
        <el-form-item :label="routeLabel">
          <span class="mono">
            {{ currentTask?.fromLocationCode || '—' }} → {{ currentTask?.toLocationCode || '—' }}
          </span>
        </el-form-item>
        <el-form-item label="批次号">
          <span class="mono">{{ currentTask?.batchNo || '无批次' }}</span>
        </el-form-item>
        <el-form-item :label="qtyLabel">
          <el-input-number
            v-model="completeForm.actualQty"
            :min="0"
            :max="currentTask?.planQty ?? 0"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="completeForm.remark" placeholder="选填" maxlength="255" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="completing" @click="submitComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.route {
  display: flex;
  align-items: center;
  gap: 7px;
}

.route__arrow {
  color: var(--text-placeholder);
}

.route__target {
  color: var(--brand-600);
}

.num {
  font-family: var(--font-mono);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.is-zero {
  color: var(--text-placeholder);
}

.actions-none {
  color: var(--text-placeholder);
}
</style>