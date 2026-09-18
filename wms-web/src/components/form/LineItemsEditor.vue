<script setup>
import { computed } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

/**
 * 可增删行的单据明细编辑器（主子表里的「子表」）。
 *
 * 入库单在用，后面出库单、盘点单直接复用。
 * v-model 绑一个数组，每一行就是一条明细对象；行内编辑直接改对象属性。
 */
const props = defineProps({
  modelValue: { type: Array, required: true },
  /** 商品下拉选项，至少要有 label / value */
  skuOptions: { type: Array, default: () => [] },
  /** 是否显示批次相关列（创建时不需要，收货登记时才需要） */
  showBatch: { type: Boolean, default: false },
  /** 是否显示「已收 / 已上架」等只读列 */
  readonlyColumns: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  /** 数量列的标题，出库时改成「出库数量」 */
  qtyLabel: { type: String, default: '预计数量' },
  qtyProp: { type: String, default: 'expectQty' },
})

const emit = defineEmits(['update:modelValue'])

const rows = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const totalQty = computed(() =>
  rows.value.reduce((sum, row) => sum + (Number(row[props.qtyProp]) || 0), 0),
)

function addRow() {
  const row = {}
  props.readonlyColumns.forEach((column) => {
    row[column.prop] = 0
  })
  row[props.qtyProp] = 1
  if (props.showBatch) {
    row.batchNo = ''
    row.productionDate = null
    row.expiryDate = null
  }
  row.remark = ''
  rows.value = [...rows.value, row]
}

function removeRow(index) {
  const next = [...rows.value]
  next.splice(index, 1)
  rows.value = next
}

/**
 * 表单提交前调用。返回第一条错误信息，没有错误返回 null。
 * 具体业务校验（比如实收不能超预计）放在各自的页面里，这里只管「明细本身合不合法」。
 */
function validate() {
  if (!rows.value.length) return '请至少添加一条明细'

  for (let i = 0; i < rows.value.length; i++) {
    const row = rows.value[i]
    const no = i + 1
    if (!row.skuId) return `第 ${no} 行：请选择商品`
    const qty = Number(row[props.qtyProp])
    if (!qty || qty <= 0) return `第 ${no} 行：${props.qtyLabel}必须大于 0`
  }
  return null
}

defineExpose({ validate, addRow, totalQty })
</script>

<template>
  <div class="items">
    <el-table :data="rows" class="items__table" border header-cell-class-name="items__th">
      <el-table-column type="index" label="#" width="52" align="center" />

      <el-table-column label="商品" min-width="260">
        <template #default="{ row }">
          <el-select
            v-model="row.skuId"
            placeholder="搜索 SKU 编码或名称"
            filterable
            :disabled="disabled"
            style="width: 100%"
          >
            <el-option
              v-for="option in skuOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            >
              <span class="items__sku-code">{{ option.code }}</span>
              <span class="items__sku-name">{{ option.name }}</span>
              <span v-if="option.spec" class="items__sku-spec">{{ option.spec }}</span>
            </el-option>
          </el-select>
        </template>
      </el-table-column>

      <el-table-column :label="qtyLabel" width="150">
        <template #default="{ row }">
          <el-input-number
            v-model="row[qtyProp]"
            :min="0"
            :max="99999999"
            controls-position="right"
            :disabled="disabled"
            style="width: 100%"
          />
        </template>
      </el-table-column>

      <template v-if="showBatch">
        <el-table-column label="批次号" width="160">
          <template #default="{ row }">
            <el-input
              v-model="row.batchNo"
              placeholder="无批次可留空"
              :disabled="disabled"
            />
          </template>
        </el-table-column>

        <el-table-column label="生产日期" width="160">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              :disabled="disabled"
              style="width: 100%"
            />
          </template>
        </el-table-column>

        <el-table-column label="到期日期" width="160">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              :disabled="disabled"
              style="width: 100%"
            />
          </template>
        </el-table-column>
      </template>

      <el-table-column
        v-for="column in readonlyColumns"
        :key="column.prop"
        :label="column.label"
        :width="column.width || 110"
        align="right"
      >
        <template #default="{ row }">
          <span class="items__readonly">{{ row[column.prop] ?? 0 }}</span>
        </template>
      </el-table-column>

      <el-table-column label="备注" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.remark" placeholder="选填" :disabled="disabled" />
        </template>
      </el-table-column>

      <el-table-column v-if="!disabled" label="操作" width="70" align="center" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" :icon="Delete" @click="removeRow($index)" />
        </template>
      </el-table-column>

      <template #empty>
        <div class="items__empty">还没有明细，点下面的「添加一行」开始</div>
      </template>
    </el-table>

    <div class="items__foot">
      <el-button v-if="!disabled" :icon="Plus" @click="addRow">添加一行</el-button>
      <div class="items__total">
        <span>共 {{ rows.length }} 行</span>
        <span class="items__total-sep"></span>
        <span>{{ qtyLabel }}合计</span>
        <strong>{{ totalQty }}</strong>
      </div>
    </div>
  </div>
</template>

<style scoped>
.items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.items__table {
  width: 100%;
}

.items__table :deep(.items__th) {
  font-size: 12px;
}

.items__table :deep(.el-input-number .el-input__wrapper) {
  padding-left: 8px;
  padding-right: 30px;
}

.items__sku-code {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
}

.items__sku-name {
  margin-left: 10px;
}

.items__sku-spec {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-placeholder);
}

.items__readonly {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-regular);
}

.items__empty {
  padding: 26px 0;
  font-size: 13px;
  color: var(--text-placeholder);
}

.items__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.items__total {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.items__total-sep {
  width: 1px;
  height: 14px;
  background: var(--border-color);
}

.items__total strong {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
</style>