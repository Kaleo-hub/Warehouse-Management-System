<script setup>
import { computed, ref } from 'vue'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { auditApi } from '@/api/system'

/*
 * 审计日志是【只读】的：写入口只有一个（后端 AuditLogAspect 自动记），
 * 界面只负责查。所以这页没有新建/编辑/删除。
 *
 * 最实用的字段是 traceId：一次请求在后端日志、审计日志里用的是同一个 traceId，
 * 拿着它来搜，能把「这一次调用到底发生了什么」完整串起来。
 */
const detailVisible = ref(false)
const current = ref(null)

const columns = [
  { prop: 'createTime', label: '时间', width: 180, mono: true },
  { prop: 'userName', label: '操作人', width: 120 },
  { prop: 'module', label: '模块', width: 110 },
  { prop: 'action', label: '动作', minWidth: 160, mono: true },
  { prop: 'targetId', label: '对象', width: 170, mono: true },
  { prop: 'result', label: '结果', width: 90 },
  { prop: 'costMs', label: '耗时', width: 90, align: 'right' },
  { prop: 'ip', label: '来源 IP', width: 130, mono: true },
  { prop: 'traceId', label: 'traceId', width: 200, mono: true },
]

const MODULE_OPTIONS = [
  { label: '入库', value: 'inbound' },
  { label: '出库', value: 'outbound' },
  { label: '库存', value: 'inventory' },
  { label: '基础数据', value: 'basedata' },
  { label: '系统', value: 'system' },
  { label: '认证', value: 'auth' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: '操作人 / 动作 / 对象 id' },
  { prop: 'module', label: '模块', type: 'select', options: MODULE_OPTIONS },
  { prop: 'result', label: '结果', type: 'select', options: [{ label: '成功', value: 1 }, { label: '失败', value: 0 }] },
  { prop: 'traceId', label: 'traceId', placeholder: '精确匹配，排查单次请求' },
])

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

/** 请求参数里的 JSON 展开显示；空的就提示一句 */
const detailText = computed(() => {
  const raw = current.value?.detail
  if (!raw) return '（这条没有记录请求参数）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})
</script>

<template>
  <div>
    <PageHeading
      title="操作日志"
      description="所有写接口自动留痕：谁、什么时候、对哪个对象做了什么、成没成。失败的操作也会记录"
    />

    <CrudTable
      :api="auditApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="120"
      entity-name="日志"
    >
      <template #col-userName="{ row }">
        <span :class="{ muted: !row.userName }">{{ row.userName || '（未登录）' }}</span>
      </template>

      <template #col-action="{ row }">
        <span class="mono">{{ row.action }}</span>
      </template>

      <template #col-targetId="{ row }">
        <span class="mono">{{ row.targetType }}:{{ row.targetId || '-' }}</span>
      </template>

      <template #col-result="{ row }">
        <el-tag :type="row.result === 1 ? 'success' : 'danger'" size="small" disable-transitions>
          {{ row.result === 1 ? '成功' : '失败' }}
        </el-tag>
      </template>

      <template #col-costMs="{ row }">
        <span class="mono">{{ row.costMs }} ms</span>
      </template>

      <template #col-traceId="{ row }">
        <span class="mono muted">{{ row.traceId }}</span>
      </template>

      <template #row-actions="{ row }">
        <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
      </template>
    </CrudTable>

    <el-drawer v-model="detailVisible" title="审计详情" size="620px">
      <div v-if="current" class="detail">
        <div class="detail__grid">
          <div><em>时间</em><span>{{ current.createTime }}</span></div>
          <div><em>操作人</em><span>{{ current.userName || '（未登录）' }}</span></div>
          <div><em>模块 / 动作</em><span>{{ current.module }} · {{ current.action }}</span></div>
          <div><em>对象</em><span>{{ current.targetType }}:{{ current.targetId || '-' }}</span></div>
          <div><em>结果</em><span>{{ current.result === 1 ? '成功' : '失败' }}</span></div>
          <div><em>耗时</em><span>{{ current.costMs }} ms</span></div>
          <div><em>来源 IP</em><span>{{ current.ip }}</span></div>
          <div><em>traceId</em><span class="mono">{{ current.traceId }}</span></div>
        </div>

        <el-alert
          v-if="current.result !== 1 && current.errorMsg"
          type="error"
          :closable="false"
          show-icon
          :title="current.errorMsg"
        />

        <div>
          <h4>请求参数</h4>
          <pre class="detail__json">{{ detailText }}</pre>
          <p class="detail__hint">密码类字段在写入审计前已脱敏为 ***</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.detail__grid div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.detail__grid em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.detail__grid span {
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-all;
}

.detail h4 {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail__json {
  max-height: 320px;
  padding: 12px 14px;
  overflow: auto;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-regular);
  background: var(--bg-surface-2);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.detail__hint {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--text-placeholder);
}

.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.muted {
  color: var(--text-placeholder);
}
</style>