<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { toastError } from '@/utils/error'
import { ArrowLeft } from '@element-plus/icons-vue'
import LineItemsEditor from '@/components/form/LineItemsEditor.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inboundApi } from '@/api/inbound'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { useSkuOptions } from '@/composables/useSkuOptions'

const router = useRouter()
const formRef = ref(null)
const itemsRef = ref(null)
const submitting = ref(false)

const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()
const { options: skuOptions, load: loadSkus } = useSkuOptions()

const form = reactive({
  warehouseId: null,
  supplierName: '',
  expectArriveTime: null,
  remark: '',
  items: [],
})

const rules = {
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }],
}

const totalQty = computed(() =>
  form.items.reduce((sum, row) => sum + (Number(row.expectQty) || 0), 0),
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
    // 只提交后端需要的字段，别把前端的展示字段（label 之类）传过去
    const payload = {
      warehouseId: form.warehouseId,
      supplierName: form.supplierName,
      expectArriveTime: form.expectArriveTime,
      remark: form.remark,
      items: form.items.map((row) => ({
        skuId: row.skuId,
        expectQty: Number(row.expectQty),
        remark: row.remark || '',
      })),
    }

    const id = await inboundApi.create(payload)
    ElMessage.success('入库单创建成功')
    router.replace(id ? `/inbound/orders/${id}` : '/inbound/orders')
  } catch (error) {
    toastError(error, '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeading title="新建入库单" description="填写入库仓库与明细，保存后单据进入「待收货」状态">
      <el-button :icon="ArrowLeft" @click="router.back()">返回</el-button>
    </PageHeading>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <section class="card">
        <header class="card__head">
          <h2>单据信息</h2>
        </header>

        <div class="grid">
          <el-form-item label="入库仓库" prop="warehouseId">
            <el-select
              v-model="form.warehouseId"
              placeholder="请选择入库仓库"
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

          <el-form-item label="供应商" prop="supplierName">
            <el-input
              v-model="form.supplierName"
              placeholder="选填，例如 某某供应链有限公司"
              maxlength="128"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="预计到货时间" prop="expectArriveTime">
            <el-date-picker
              v-model="form.expectArriveTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选填"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="form.remark"
              placeholder="选填"
              maxlength="255"
              show-word-limit
            />
          </el-form-item>
        </div>
      </section>

      <section class="card">
        <header class="card__head">
          <h2>入库明细</h2>
          <span class="card__hint">批次号和生产日期在收货时登记</span>
        </header>

        <LineItemsEditor
          ref="itemsRef"
          v-model="form.items"
          :sku-options="skuOptions"
          qty-label="预计数量"
          qty-prop="expectQty"
        />
      </section>

      <footer class="actions">
        <div class="actions__summary">
          预计总量 <strong>{{ totalQty }}</strong>
        </div>
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存入库单
        </el-button>
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