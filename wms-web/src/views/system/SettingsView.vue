<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Setting } from '@element-plus/icons-vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { roleLabel } from '@/utils/format'

const app = useAppStore()
const auth = useAuthStore()
const { options: warehouseOptions, load: loadWarehouseOptions } = useWarehouseOptions()
const saving = ref(false)
const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
const form = reactive({
  warehouseId: app.currentWarehouseId,
  alertDays: app.alertDays,
  pageSize: app.pageSize,
  theme: app.theme,
})

const ALERT_DAY_OPTIONS = [
  { label: '7 天', value: 7 },
  { label: '15 天', value: 15 },
  { label: '30 天', value: 30 },
  { label: '60 天', value: 60 },
  { label: '90 天', value: 90 },
]

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100].map((value) => ({ label: `${value} 条`, value }))

watch([() => app.currentWarehouseId, () => app.theme], ([warehouseId, theme]) => {
  if (warehouseId) form.warehouseId = warehouseId
  form.theme = theme
})

onMounted(async () => {
  await loadWarehouseOptions()
  if (!form.warehouseId && warehouseOptions.value.length) {
    form.warehouseId = warehouseOptions.value[0].value
  }
})

function save() {
  if (saving.value) return
  saving.value = true
  try {
    app.setCurrentWarehouse(form.warehouseId)
    app.updatePreferences({ alertDays: form.alertDays, pageSize: form.pageSize })
    app.theme = form.theme
    app.applyTheme()
    ElMessage.success('设置已保存，当前页和后续业务页会按新范围加载')
  } finally {
    saving.value = false
  }
}

function reset() {
  app.resetPreferences()
  form.alertDays = app.alertDays
  form.pageSize = app.pageSize
  form.theme = 'light'
  app.theme = 'light'
  app.applyTheme()
  ElMessage.success('已恢复默认界面偏好')
}
</script>

<template>
  <div class="page">
    <PageHeading title="系统设置" description="配置当前仓库和本浏览器的界面偏好，不修改任何业务数据">
      <el-button :icon="Refresh" @click="reset">恢复默认</el-button>
      <el-button type="primary" :icon="Setting" :loading="saving" @click="save">保存设置</el-button>
    </PageHeading>

    <section class="card">
      <header class="card__head"><h2>工作范围</h2></header>
      <el-form label-width="140px" class="settings-form">
        <el-form-item label="当前仓库">
          <el-select v-model="form.warehouseId" filterable placeholder="选择当前仓库" style="width: 320px">
            <el-option v-for="item in warehouseOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <p class="hint">顶栏切换和这里保持一致；业务列表、看板、新建单会默认使用该仓库。</p>
        </el-form-item>
        <el-form-item label="临期预警阈值">
          <el-select v-model="form.alertDays" style="width: 180px">
            <el-option v-for="item in ALERT_DAY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <p class="hint">工作台和预警页按这个天数判断「即将过期」。</p>
        </el-form-item>
        <el-form-item label="列表默认页大小">
          <el-select v-model="form.pageSize" style="width: 180px">
            <el-option v-for="item in PAGE_SIZE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <p class="hint">通用列表首次加载优先使用这个数量，仍可在分页器单独切换。</p>
        </el-form-item>
      </el-form>
    </section>

    <section class="card">
      <header class="card__head"><h2>界面外观</h2></header>
      <el-radio-group v-model="form.theme" size="large">
        <el-radio-button value="light">浅色模式</el-radio-button>
        <el-radio-button value="dark">深色模式</el-radio-button>
      </el-radio-group>
      <p class="hint">主题保存在当前浏览器，登录其他账号不会继承。</p>
    </section>

    <section class="card">
      <header class="card__head"><h2>当前会话</h2></header>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="登录账号">{{ auth.user?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleLabel(auth.user?.role) }}</el-descriptions-item>
        <el-descriptions-item label="接口前缀">{{ apiBase }}</el-descriptions-item>
        <el-descriptions-item label="设置存储">当前浏览器 localStorage</el-descriptions-item>
      </el-descriptions>
    </section>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px; }
.card { padding: 20px 22px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-xs); }
.card__head { padding-bottom: 14px; margin-bottom: 18px; border-bottom: 1px solid var(--divider); }
.card__head h2 { font-size: 15px; color: var(--text-primary); }
.settings-form { max-width: 720px; }
.hint { margin-top: 7px; font-size: 12px; line-height: 1.5; color: var(--text-placeholder); }
</style>
