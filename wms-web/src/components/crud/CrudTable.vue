<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { toastError } from '@/utils/error'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/format'

/**
 * 通用 CRUD 容器：查询栏 + 工具栏 + 表格 + 分页 + 表单抽屉。
 * 各业务页面只提供 columns / searchFields / formFields / api 四个配置，不重复写模板。
 */
const props = defineProps({
  /** { page(params), create(payload), update(id, payload), remove(id) } */
  api: { type: Object, required: true },
  columns: { type: Array, required: true },
  searchFields: { type: Array, default: () => [] },
  formFields: { type: Array, default: () => [] },
  /** 抽屉标题里的业务名，例如「仓库」 */
  entityName: { type: String, default: '记录' },
  /** 新建时的初始表单 */
  defaultForm: { type: Function, default: () => ({}) },
  rowKey: { type: String, default: 'id' },
  canCreate: { type: Boolean, default: true },
  canUpdate: { type: Boolean, default: true },
  canDelete: { type: Boolean, default: true },
  /** 操作列宽度，行操作多的时候调大 */
  actionWidth: { type: Number, default: 140 },
})

const emit = defineEmits(['loaded', 'row-click'])

/* ------------------------------ 查询 ------------------------------ */
function buildQuery() {
  const query = {}
  props.searchFields.forEach((field) => {
    // 日期区间绑一个数组，提交时再拆成 beginTime / endTime
    query[field.prop] = field.type === 'daterange' ? null : field.defaultValue ?? ''
  })
  return query
}

const query = reactive(buildQuery())
const page = reactive({ current: 1, size: 10, total: 0 })
const rows = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    props.searchFields.forEach((field) => {
      const value = query[field.prop]
      if (field.type === 'daterange') {
        if (Array.isArray(value) && value.length === 2) {
          params[field.beginProp || 'beginTime'] = value[0]
          params[field.endProp || 'endTime'] = value[1]
        }
        return
      }
      if (value !== '' && value !== null && value !== undefined) params[field.prop] = value
    })

    const data = await props.api.page(params)

    // 兼容 records / list 两种分页字段命名，后端定稿后可以删掉其中之一
    rows.value = data?.records ?? data?.list ?? []
    page.total = Number(data?.total ?? rows.value.length)
    emit('loaded', data)
  } catch (error) {
    rows.value = []
    page.total = 0
    toastError(error, '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.current = 1
  load()
}

function handleReset() {
  Object.assign(query, buildQuery())
  page.current = 1
  load()
}

function handleSizeChange(size) {
  page.size = size
  page.current = 1
  load()
}

function handlePageChange(current) {
  page.current = current
  load()
}

/* ------------------------------ 表单 ------------------------------ */
const drawerVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({})

const isEdit = computed(() => editingId.value !== null)
const drawerTitle = computed(() => `${isEdit.value ? '编辑' : '新建'}${props.entityName}`)

const rules = computed(() => {
  const result = {}
  props.formFields.forEach((field) => {
    const fieldRules = [...(field.rules || [])]
    if (field.required && !fieldRules.some((rule) => rule.required)) {
      fieldRules.unshift({
        required: true,
        message: field.requiredMessage || `请${field.type === 'select' ? '选择' : '输入'}${field.label}`,
        trigger: field.type === 'select' ? 'change' : 'blur',
      })
    }
    if (fieldRules.length) result[field.prop] = fieldRules
  })
  return result
})

function resetForm() {
  const initial = props.defaultForm?.() || {}
  Object.keys(form).forEach((key) => delete form[key])
  props.formFields.forEach((field) => {
    const value = initial[field.prop]
    form[field.prop] = value !== undefined ? value : field.defaultValue ?? (field.type === 'number' ? undefined : '')
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  drawerVisible.value = true
}

function openEdit(row) {
  editingId.value = row[props.rowKey]
  resetForm()
  props.formFields.forEach((field) => {
    const value = row[field.prop]
    if (value !== undefined) form[field.prop] = value
  })
  drawerVisible.value = true
}

async function handleSubmit() {
  if (submitting.value) return

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = { ...form }
    if (isEdit.value) {
      await props.api.update(editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await props.api.create(payload)
      ElMessage.success('创建成功')
      page.current = 1
    }
    drawerVisible.value = false
    await load()
  } catch (error) {
    toastError(error, '保存失败')
  } finally {
    submitting.value = false
  }
}

/* ------------------------------ 删除 ------------------------------ */
async function handleDelete(row) {
  const label = row.name || row.code || row.skuCode || `#${row[props.rowKey]}`
  try {
    await ElMessageBox.confirm(`确定要删除「${label}」吗？该操作不可撤销。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }

  try {
    await props.api.remove(row[props.rowKey])
    ElMessage.success('删除成功')
    if (rows.value.length === 1 && page.current > 1) page.current -= 1
    await load()
  } catch (error) {
    toastError(error, '删除失败')
  }
}

/**
 * 单元格取值。
 * type: 'datetime' 会把后端返回的 ISO 串（2026-09-16T11:03:24.123）
 * 统一格式化成 yyyy-MM-dd HH:mm:ss，避免时间列被撑爆。
 */
function cellValue(row, column) {
  const value = row[column.prop]
  if (value === null || value === undefined || value === '') return '-'
  if (column.type === 'datetime') return formatDateTime(value)
  return value
}

onMounted(load)

defineExpose({ reload: load, openCreate })
</script>

<template>
  <div class="crud">
    <!-- 查询条件 -->
    <section v-if="searchFields.length" class="crud__query">
      <el-form :model="query" class="crud__query-form" @submit.prevent="handleSearch">
        <div class="crud__query-fields">
          <el-form-item v-for="field in searchFields" :key="field.prop" :label="field.label">
            <el-input
              v-if="!field.type || field.type === 'input'"
              v-model="query[field.prop]"
              :placeholder="field.placeholder || `请输入${field.label}`"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="query[field.prop]"
              :placeholder="field.placeholder || `全部`"
              clearable
              style="width: 180px"
            >
              <el-option
                v-for="option in field.options || []"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-date-picker
              v-else-if="field.type === 'daterange'"
              v-model="query[field.prop]"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 250px"
            />
          </el-form-item>
        </div>

        <div class="crud__query-actions">
          <el-button type="primary" :icon="Search" native-type="submit">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </div>
      </el-form>
    </section>

    <!-- 表格 -->
    <section class="crud__panel">
      <header class="crud__toolbar">
        <div class="crud__toolbar-left">
          <slot name="toolbar-left" />
        </div>
        <div class="crud__toolbar-right">
          <slot name="toolbar" :reload="load" />
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">
            新建{{ entityName }}
          </el-button>
        </div>
      </header>

      <el-table
        v-loading="loading"
        :data="rows"
        :row-key="rowKey"
        class="crud__table"
        stripe
        header-cell-class-name="crud__th"
        @row-click="(row) => emit('row-click', row)"
      >
        <el-table-column
          v-for="column in columns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :align="column.align || 'left'"
          :fixed="column.fixed"
          :sortable="column.sortable"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <slot :name="`col-${column.prop}`" :row="row" :column="column">
              <el-tag
                v-if="column.tag"
                :type="column.tag[row[column.prop]]?.type || 'info'"
                size="small"
                disable-transitions
              >
                {{ column.tag[row[column.prop]]?.text || cellValue(row, column) }}
              </el-tag>
              <span v-else :class="{ 'crud__mono': column.mono }">
                {{ cellValue(row, column) }}
              </span>
            </slot>
          </template>
        </el-table-column>

        <el-table-column
          v-if="canUpdate || canDelete || $slots['row-actions']"
          label="操作"
          :width="actionWidth"
          align="right"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="crud__row-actions">
              <!-- 传了 row-actions 插槽就完全接管行操作，否则用默认的编辑/删除 -->
              <slot name="row-actions" :row="row" :reload="load">
                <el-button
                  v-if="canUpdate"
                  link
                  type="primary"
                  :icon="Edit"
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="canDelete"
                  link
                  type="danger"
                  :icon="Delete"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </slot>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="crud__empty">
            <span class="crud__empty-mark">空</span>
            <p>没有符合条件的数据</p>
            <em>试试调整查询条件，或新建一条{{ entityName }}</em>
          </div>
        </template>
      </el-table>

      <footer class="crud__pager">
        <el-pagination
          :current-page="page.current"
          :page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </footer>
    </section>

    <!-- 表单抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="460px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="crud__form"
      >
        <el-form-item
          v-for="field in formFields"
          :key="field.prop"
          :label="field.label"
          :prop="field.prop"
        >
          <slot :name="`form-${field.prop}`" :form="form" :field="field">
            <el-input
              v-if="!field.type || field.type === 'input'"
              v-model="form[field.prop]"
              :placeholder="field.placeholder || `请输入${field.label}`"
              :maxlength="field.maxlength"
              show-word-limit
              clearable
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="form[field.prop]"
              type="textarea"
              :rows="3"
              :placeholder="field.placeholder || `请输入${field.label}`"
              :maxlength="field.maxlength || 255"
              show-word-limit
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="form[field.prop]"
              :min="field.min ?? 0"
              :max="field.max"
              :step="field.step || 1"
              controls-position="right"
              style="width: 100%"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="form[field.prop]"
              :placeholder="field.placeholder || `请选择${field.label}`"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="option in field.options || []"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <div v-else-if="field.type === 'switch'" class="crud__switch">
              <el-switch
                v-model="form[field.prop]"
                :active-value="field.activeValue"
                :inactive-value="field.inactiveValue"
              />
              <span>{{ field.hint }}</span>
            </div>
          </slot>
          <p v-if="field.help" class="crud__form-help">{{ field.help }}</p>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="crud__drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '确认创建' }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.crud {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------- 查询栏 ---------- */
.crud__query {
  padding: 16px 18px 4px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.crud__query-form {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.crud__query-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}

.crud__query-fields :deep(.el-form-item) {
  margin-bottom: 12px;
  margin-right: 12px;
}

.crud__query-actions {
  display: flex;
  margin-bottom: 12px;
}

/* ---------- 表格面板 ---------- */
.crud__panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.crud__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 60px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--divider);
}

.crud__toolbar-left:empty,
.crud__toolbar-right:empty {
  display: none;
}

.crud__toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.crud__table {
  width: 100%;
}

.crud__mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
  letter-spacing: -0.01em;
  color: var(--text-regular);
}

.crud__row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}

.crud__row-actions :deep(.el-button) {
  margin-left: 0;
}

.crud__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 34px 0;
}

.crud__empty-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: 4px;
  font-size: 15px;
  color: var(--text-placeholder);
  border: 1px dashed var(--border-strong);
  border-radius: 12px;
  background: var(--bg-surface-2);
}

.crud__empty p {
  font-size: 13.5px;
  color: var(--text-regular);
}

.crud__empty em {
  font-size: 12px;
  font-style: normal;
  color: var(--text-placeholder);
}

.crud__pager {
  display: flex;
  justify-content: flex-end;
  padding: 13px 18px;
  border-top: 1px solid var(--divider);
}

/* ---------- 表单 ---------- */
.crud__form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 13px;
}

.crud__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.crud__form-help {
  width: 100%;
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-placeholder);
}

.crud__switch {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text-secondary);
}

.crud__drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>