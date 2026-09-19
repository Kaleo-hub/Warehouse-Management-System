<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import LineItemsEditor from '@/components/form/LineItemsEditor.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { outboundApi } from '@/api/outbound'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { useSkuOptions } from '@/composables/useSkuOptions'
import { toastError } from '@/utils/error'

const router = useRouter()
const formRef = ref(null)
const itemsRef = ref(null)
const submitting = ref(false)

const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()
const { options: skuOptions, load: loadSkus } = useSkuOptions()

const form = reactive({
  warehouseId: null,
  customerName: '',
  receiveAddress: '',
  expectShipTime: null,
  remark: '',
  items: [],
})

const rules = {
  warehouseId: [{ required: true, message: '请选择出库仓库', trigger: 'change' }],
}

const totalQty = computed(() =>
  form.items.reduce((sum, row) => sum + (Number(row.qty) || 0), 0),
)

onMounted(async () => {
  await Promise.all([loadWarehouses(), loadSkus()])
  if (!form.items.length) itemsRef.value?.addRow()
})

async function handleSubmit() {
  if (submitting.value) return

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const itemError = itemsRef.value?.validate()
  if (itemError) {
    ElMessage.warning(itemError)
    return
  }

  submitting.value = true
  try {
    const payload = {
      warehouseId: form.warehouseId,
      customerName: form.customerName,
      receiveAddress: form.receiveAddress,
      expectShipTime: form.expectShipTime,
      remark: form.remark,
      items: form.items.map((row) => ({
        skuId: row.skuId,
        qty: Number(row.qty),
        remark: row.remark || '',
      })),
    }

    const id = await outboundApi.create(payload)
    ElMessage.success('出库单创建成功')
    router.replace(id ? `/outbound/orders/${id}` : '/outbound/orders')
  } catch (error) {
    toastError(error, '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeading title="新建出库单" description="填写出库仓库与需求明细，保存后单据进入「待分配」状态">
      <el-button :icon="ArrowLeft" @click="router.back()">返回</el-button>
    </PageHeading>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="card">
        <header class="card__head"><h2>单据信息</h2></header>

        <div class="grid">
          <el-form-item label="出库仓库" prop="warehouseId">
            <el-select
              v-model="form.warehouseId"
              placeholder="请选择出库仓库"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="option in warehouseOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="客户名称" prop="customerName">
            <el-input v-model="form.customerName" placeholder="选填" maxlength="128" show-word-limit />
          </el-form-item>

          <el-form-item label="要求发货时间" prop="expectShipTime">
            <el-date-picker
              v-model="form.expectShipTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选填"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="收货地址" prop="receiveAddress">
            <el-input v-model="form.receiveAddress" placeholder="选填" maxlength="255" show-word-limit />
          </el-form-item>

          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" placeholder="选填" maxlength="255" show-word-limit />
          </el-form-item>
        </div>
      </section>

      <section class="card">
        <header class="card__head">
          <h2>出库明细</h2>
          <span class="card__hint">保存后再点「分配库存」，系统会按 FIFO 自动选批次</span>
        </header>

        <LineItemsEditor
          ref="itemsRef"
          v-model="form.items"
          :sku-options="skuOptions"
          qty-label="出库数量"
          qty-prop="qty"
        />
      </section>

      <footer class="actions">
        <div class="actions__summary">
          需求总量 <strong>{{ totalQty }}</strong>
        </div>
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存出库单</el-button>
      </footer>
    </el-form>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card {
  padding: 20px 22px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.card + .card {
  margin-top: 14px;
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

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0 20px;
}

.actions {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 14px 22px;
  background: color-mix(in srgb, var(--bg-surface) 92%, transparent);
  backdrop-filter: blur(10px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.actions__summary {
  margin-right: auto;
  font-size: 13px;
  color: var(--text-secondary);
}

.actions__summary strong {
  font-size: 16px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
</style>