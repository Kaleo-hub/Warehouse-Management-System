<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getLastTraceId } from '@/api/request'
import { locationApi, productApi, warehouseApi } from '@/api/basedata'
import { roleLabel } from '@/utils/format'

const auth = useAuthStore()
const traceId = ref(getLastTraceId())

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
})

const stats = ref([
  { key: 'warehouse', label: '仓库总数', value: null, unit: '个', hint: '基础数据' },
  { key: 'product', label: '商品 SKU', value: null, unit: '个', hint: '基础数据' },
  { key: 'location', label: '库位总数', value: null, unit: '个', hint: '基础数据' },
  { key: 'task', label: '待处理任务', value: null, unit: '个', hint: '待后端接入' },
])

const tokenPreview = computed(() => {
  const token = auth.token || ''
  if (!token) return '—'
  return token.length > 34 ? `${token.slice(0, 18)}…${token.slice(-10)}` : token
})

const weekScope = [
  { text: '登录对接真实接口 + token 持久化 + 路由守卫', done: true },
  { text: '应用骨架：侧边栏 / 顶栏 / 面包屑 / 亮暗主题', done: true },
  { text: '统一请求层：Result 拆包、错误码、traceId、401 自动跳转', done: true },
  { text: '通用 CRUD 组件：查询 / 表格 / 分页 / 抽屉表单 / 删除确认', done: true },
  { text: '基础数据四模块：仓库 / 库区 / 库位 / 商品', done: true },
  { text: '入库、出库、拣货业务页', done: false },
]

onMounted(async () => {
  // 拿不到就保持 --，不弹错误提示：这两周后端还没提供基础数据接口
  const load = async (key, api) => {
    try {
      const data = await api.page({ page: 1, size: 1 })
      const target = stats.value.find((item) => item.key === key)
      if (target) target.value = Number(data?.total ?? 0)
    } catch {
      /* 后端未就绪，保持占位 */
    }
  }

  await Promise.all([
    load('warehouse', warehouseApi),
    load('product', productApi),
    load('location', locationApi),
  ])
})
</script>

<template>
  <div class="dash">
    <!-- 欢迎条 -->
    <section class="hero">
      <div class="hero__glow" aria-hidden="true"></div>
      <div class="hero__text">
        <p class="hero__date">{{ today }}</p>
        <h1 class="hero__title">
          {{ greeting }}，<span>{{ auth.displayName }}</span>
        </h1>
        <p class="hero__sub">
          当前身份
          <el-tag :type="auth.roleTag" size="small" effect="dark" round>
            {{ roleLabel(auth.user?.role) }}
          </el-tag>
          · 库存闭环流程尚未接入，先把基础数据打牢。
        </p>
      </div>

      <div class="hero__badge">
        <span class="hero__badge-dot"></span>
        <div>
          <strong>后端已连接</strong>
          <em>JWT 鉴权生效中</em>
        </div>
      </div>
    </section>

    <!-- 指标卡 -->
    <section class="stats">
      <article v-for="item in stats" :key="item.key" class="stat">
        <header class="stat__head">
          <span class="stat__label">{{ item.label }}</span>
          <span class="stat__hint">{{ item.hint }}</span>
        </header>
        <p class="stat__value">
          <strong>{{ item.value === null ? '--' : item.value.toLocaleString() }}</strong>
          <em>{{ item.unit }}</em>
        </p>
      </article>
    </section>

    <div class="dash__grid">
      <!-- 登录态 -->
      <section class="card">
        <header class="card__head">
          <h2>当前登录态</h2>
          <span class="card__tag">由 /api/v1/auth/login 返回</span>
        </header>

        <dl class="kv">
          <div class="kv__row">
            <dt>用户 ID</dt>
            <dd class="u-mono">{{ auth.user?.id ?? '—' }}</dd>
          </div>
          <div class="kv__row">
            <dt>登录账号</dt>
            <dd>{{ auth.user?.username ?? '—' }}</dd>
          </div>
          <div class="kv__row">
            <dt>真实姓名</dt>
            <dd>{{ auth.user?.realName ?? '—' }}</dd>
          </div>
          <div class="kv__row">
            <dt>角色</dt>
            <dd>{{ roleLabel(auth.user?.role) }}</dd>
          </div>
          <div class="kv__row">
            <dt>Token</dt>
            <dd class="u-mono kv__token">{{ tokenPreview }}</dd>
          </div>
          <div class="kv__row">
            <dt>最近 traceId</dt>
            <dd class="u-mono kv__token">{{ traceId || '—' }}</dd>
          </div>
        </dl>
      </section>

      <!-- 本周范围 -->
      <section class="card">
        <header class="card__head">
          <h2>本周前端范围</h2>
          <span class="card__tag">对应后端第 2 周</span>
        </header>

        <ul class="scope">
          <li v-for="item in weekScope" :key="item.text" :class="{ 'is-done': item.done }">
            <span class="scope__mark">
              <svg v-if="item.done" viewBox="0 0 16 16" width="10" height="10">
                <path
                  d="m4 8.4 2.6 2.6L12 5.6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span>{{ item.text }}</span>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------- 欢迎条 ---------- */
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  overflow: hidden;
  color: #ffffff;
  border-radius: var(--radius-lg);
  background: linear-gradient(115deg, #16224a 0%, #1e3a8a 45%, #0e6ba8 100%);
  box-shadow: var(--shadow-lg);
}

.hero__glow {
  position: absolute;
  top: -120px;
  right: -60px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.45) 0%, transparent 70%);
}

.hero__text {
  position: relative;
  z-index: 1;
}

.hero__date {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.62);
}

.hero__title {
  margin: 6px 0 8px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.hero__title span {
  background: linear-gradient(100deg, #a5c4ff, #7dd3fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero__sub {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.76);
}

.hero__badge {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 16px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
}

.hero__badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 0 5px rgba(74, 222, 128, 0.2);
}

.hero__badge strong {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
}

.hero__badge em {
  font-size: 11px;
  font-style: normal;
  color: rgba(255, 255, 255, 0.6);
}

/* ---------- 指标卡 ---------- */
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.stat {
  padding: 18px 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
  transition: transform var(--duration-base) var(--ease-out),
    box-shadow var(--duration-base) var(--ease-out);
}

.stat:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stat__label {
  font-size: 13px;
  color: var(--text-secondary);
}

.stat__hint {
  padding: 1px 7px;
  font-size: 10.5px;
  color: var(--text-placeholder);
  border: 1px solid var(--border-color);
  border-radius: 999px;
}

.stat__value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 10px;
}

.stat__value strong {
  font-size: 27px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.stat__value em {
  font-size: 12px;
  font-style: normal;
  color: var(--text-placeholder);
}

/* ---------- 卡片 ---------- */
.dash__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 14px;
}

.card {
  padding: 20px 22px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--divider);
}

.card__head h2 {
  font-size: 15px;
  font-weight: 650;
  color: var(--text-primary);
}

.card__tag {
  font-size: 11px;
  color: var(--text-placeholder);
}

/* 键值列表 */
.kv {
  margin: 0;
}

.kv__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--divider);
}

.kv__row:last-child {
  border-bottom: none;
}

.kv__row dt {
  font-size: 13px;
  color: var(--text-secondary);
}

.kv__row dd {
  margin: 0;
  font-size: 13px;
  color: var(--text-primary);
}

.kv__token {
  max-width: 62%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-regular);
}

/* 范围清单 */
.scope {
  padding-top: 6px;
}

.scope li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.scope li.is-done {
  color: var(--text-regular);
}

.scope__mark {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  color: #ffffff;
  border: 1px solid var(--border-strong);
  border-radius: 50%;
  background: transparent;
}

.scope li.is-done .scope__mark {
  border-color: transparent;
  background: linear-gradient(135deg, var(--brand-500), var(--accent-500));
}

@media (max-width: 720px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>