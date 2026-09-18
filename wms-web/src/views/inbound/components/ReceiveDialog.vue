<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { toastError } from '@/utils/error'
import { inboundApi } from '@/api/inbound'

/**
 * 收货登记。
 * 交互约定：默认把每行的「本次实收」填成剩余待收数量（绝大多数情况是整单全收），
 * 没收到的行手动清零。累计实收不允许超过预计数量（后端错误码 40004）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 入库单详情，必须带 items */
  order: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'success'])

const rows = ref([])
const submitting = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const totalInput = computed(() =>
  rows.value.reduce((sum, row) => sum + (Number(row.inputQty) || 0), 0),
)

const hasInput = computed(() => totalInput.value > 0)

function remainOf(item) {
  return Math.max(0, (Number(item.expectQty) || 0) - (Number(item.receivedQty) || 0))
}

function initRows() {
  rows.value = (props.order?.items || []).map((item) => {
    const remain = remainOf(item)
    return {
      itemId: item.id,
      skuCode: item.skuCode,
      skuName: item.skuName,
      expectQty: Number(item.expectQty) || 0,
      receivedQty: Number(item.receivedQty) || 0,
      remainQty: remain,
      inputQty: remain,
      batchNo: item.batchNo || '',
      productionDate: item.productionDate || null,
      expiryDate: item.expiryDate || null,
    }
  })
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) initRows()
  },
)

function fillAll() {
  rows.value.forEach((row) => {
    row.inputQty = row.remainQty
  })
}

function clearAll() {
  rows.value.forEach((row) => {
    row.inputQty = 0
  })
}

function overReceived(row) {
  return Number(row.receivedQty) + Number(row.inputQty) > Number(row.expectQty)
}

async function handleSubmit() {
  if (submitting.value) return

  const invalid = rows.value.find((row) => overReceived(row))
  if (invalid) {
    ElMessage.warning(`${invalid.skuCode} 的累计实收不能超过预计数量 ${invalid.expectQty}`)
    return
  }

  const items = rows.value
    .filter((row) => Number(row.inputQty) > 0)
    .map((row) => ({
      itemId: row.itemId,
      receivedQty: Number(row.inputQty),
      batchNo: row.batchNo || '',
      productionDate: row.productionDate || null,
      expiryDate: row.expiryDate || null,
    }))

  if (!items.length) {
    ElMessage.warning('请至少填写一行实收数量')
    return
  }

  submitting.value = true
  try {
    await inboundApi.receive(props.order.id, { items })
    ElMessage.success('收货登记成功')
    visible.value = false
    emit('success')
  } catch (error) {
    toastError(error, '收货失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="收货登记"
    width="1000px"
    top="6vh"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="recv">
      <div class="recv__tip">
        <span>
          入库单 <b>{{ order?.orderNo }}</b> · 默认按剩余数量全收，没收到的行请手动改成 0
        </span>
        <div class="recv__tip-actions">
          <el-button link type="primary" @click="fillAll">全部按剩余收货</el-button>
          <el-button link type="primary" @click="clearAll">清空</el-button>
        </div>
      </div>

      <el-table :data="rows" border max-height="420" class="recv__table">
        <el-table-column type="index" label="#" width="50" align="center" />

        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <div class="recv__sku">
              <span class="recv__sku-code">{{ row.skuCode }}</span>
              <span class="recv__sku-name">{{ row.skuName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="预计" prop="expectQty" width="80" align="right" />
        <el-table-column label="已收" prop="receivedQty" width="80" align="right" />

        <el-table-column label="本次实收" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.inputQty"
              :min="0"
              :max="row.remainQty"
              controls-position="right"
              style="width: 100%"
              :class="{ 'is-over': overReceived(row) }"
            />
          </template>
        </el-table-column>

        <el-table-column label="批次号" width="150">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="无批次留空" />
          </template>
        </el-table-column>

        <el-table-column label="生产日期" width="150">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选填"
              style="width: 100%"
            />
          </template>
        </el-table-column>

        <el-table-column label="到期日期" width="150">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选填"
              style="width: 100%"
            />
          </template>
        </el-table-column>
      </el-table>

      <p class="recv__note">
        收货只是登记数量，<b>不会增加库存</b>。库存要等上架完成后才会写入。
      </p>
    </div>

    <template #footer>
      <div class="recv__footer">
        <span class="recv__total">
          本次实收合计 <strong>{{ totalInput }}</strong>
        </span>
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!hasInput"
          @click="handleSubmit"
        >
          提交收货
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.recv {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recv__tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--text-regular);
  border: 1px solid var(--brand-200);
  border-radius: var(--radius-sm);
  background: var(--brand-50);
}

.recv__tip-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.recv__table :deep(.is-over .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--danger-500) inset;
}

.recv__sku {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.recv__sku-code {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
}

.recv__sku-name {
  font-size: 13px;
  color: var(--text-primary);
}

.recv__note {
  font-size: 12px;
  color: var(--text-secondary);
}

.recv__note b {
  color: var(--brand-600);
}

.recv__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.recv__total {
  margin-right: auto;
  font-size: 13px;
  color: var(--text-secondary);
}

.recv__total strong {
  font-size: 17px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
</style>