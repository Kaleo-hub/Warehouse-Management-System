<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { locationApi } from '@/api/basedata'
import { stocktakingOrderApi } from '@/api/inventory'
import { useIdempotentKey } from '@/composables/useIdempotentKey'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { toastError } from '@/utils/error'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const idempotentKey = useIdempotentKey()
const locations = ref([])
const form = reactive({ locationId: null, remark: '' })

const rules = {
  locationId: [{ required: true, message: '请选择盘点库位', trigger: 'change' }],
}

const selectedLocation = computed(() => locations.value.find((item) => item.id === form.locationId))

onMounted(async () => {
  try {
    locations.value = (await fetchAllOptions(locationApi.page)).filter((item) => item.status === 'ENABLED')
  } catch (error) {
    toastError(error, '库位加载失败')
  }
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitting.value) return
  submitting.value = true
  try {
    const id = await stocktakingOrderApi.create({ ...form }, idempotentKey.value)
    idempotentKey.reset()
    ElMessage.success('盘点单已创建，库存快照已生成')
    router.replace(`/inventory/stocktaking/${id}`)
  } catch (error) {
    toastError(error, '创建盘点单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <PageHeading title="新建盘点单" description="建单时冻结一份库存数量快照，后续实盘差异以这份快照为基准">
      <el-button :icon="ArrowLeft" @click="router.push('/inventory/stocktaking')">返回列表</el-button>
    </PageHeading>

    <section class="card form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="盘点库位" prop="locationId">
          <el-select v-model="form.locationId" filterable placeholder="选择要盘点的库位" style="width: 100%">
            <el-option
              v-for="item in locations"
              :key="item.id"
              :label="`${item.code} · ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>

        <el-alert
          v-if="selectedLocation"
          type="info"
          :closable="false"
          show-icon
          :title="`将快照 ${selectedLocation.code} 当前全部库存行；建单后库存仍可正常作业，过账时会校验快照是否过期。`"
        />

        <div class="actions">
          <el-button @click="router.push('/inventory/stocktaking')">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">创建盘点单</el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.card {
  padding: 24px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}
.form-card { max-width: 720px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 22px; }
.actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>