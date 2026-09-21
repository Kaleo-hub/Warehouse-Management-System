<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { inventoryApi } from '@/api/inventory'
import { roleLabel } from '@/utils/format'
import { TX_TYPE } from '@/constants/inbound'
import { formatDateTime } from '@/utils/format'
import { toastError } from '@/utils/error'
import { useLatestRequest } from '@/composables/useLatestRequest'

const router = useRouter()
const auth = useAuthStore()
const app = useAppStore()

const { loading, begin, isLatest, finish } = useLatestRequest()
const summary = ref(null)
const alerts = ref(null)
const recentTransactions = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
})

/** 五张指标卡：每个都点击可跳转到对应页面 */
const cards = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    {
      key: 'total', label: '在库总量', value: fmt(s.totalQty), unit: '件',
      sub: `锁定 ${fmt(s.lockedQty)} · 冻结 ${fmt(s.frozenQty)}`,
      to: '/inventory/stock',
    },
    {
      key: 'available', label: '可用库存', value: fmt(s.availableQty), unit: '件',
      sub: '可被出库单分配的部分',
      to: '/inventory/stock', accent: true,
    },
    {
      key: 'sku', label: '在库 SKU', value: fmt(s.skuCount), unit: '种',
      sub: `分布在 ${fmt(s.usedLocationCount)} 个库位`,
      to: '/inventory/summary',
    },
    {
      key: 'capacity', label: '库位占用率',
      value: s.capacityUsageRate === null ? '—' : s.capacityUsageRate + '',
      unit: s.capacityUsageRate === null ? '' : '%',
      sub: s.capacityTotal ? `${fmt(s.capacityUsed)} / ${fmt(s.capacityTotal)}` : `共 ${fmt(s.locationCount)} 个库位`,
      to: '/basedata/locations',
      percent: s.capacityUsageRate,
    },
    {
      key: 'pending', label: '待处理事项',
      value: fmt(nz(s.pendingTaskCount) + nz(s.pendingInboundCount) + nz(s.pendingOutboundCount)),
      unit: '项',
      sub: `任务 ${nz(s.pendingTaskCount)} · 入库 ${nz(s.pendingInboundCount)} · 出库 ${nz(s.pendingOutboundCount)}`,
      to: '/inventory/alerts', warn: true,
    },
  ]
})

/** 按仓库分布的条：宽度按最大值归一化，避免小仓库的条看不见 */
const warehouseBars = computed(() => {
  const list = summary.value?.byWarehouse || []
  const max = Math.max(1, ...list.map((it) => Number(it.qty) || 0))
  return list.map((it) => ({
    ...it,
    percent: Math.round(((Number(it.qty) || 0) / max) * 100),
  }))
})

const alertTotal = computed(() => {
  const a = alerts.value
  if (!a) return 0
  return (a.expiring?.length || 0) + (a.frozen?.length || 0) + (a.overCapacity?.length || 0)
})

function nz(v) { return v === null || v === undefined ? 0 : Number(v) }
function fmt(v) { return nz(v).toLocaleString('zh-CN') }

/** 流水变动量：正数绿色带 +，负数红色 */
function changeClass(v) {
  const n = Number(v) || 0
  if (n > 0) return 'is-plus'
  if (n < 0) return 'is-minus'
  return 'is-zero'
}
function signed(v) {
  const n = Number(v) || 0
  return n > 0 ? `+${n}` : String(n)
}
/** 只写数量的流水（分配/冻结）变动量是 0，实际动的是 locked/frozen，这里给出可读的说明 */
function changeText(row) {
  if (Number(row.qtyChange)) return signed(row.qtyChange)
  if (Number(row.lockedChange)) return `锁定 ${signed(row.lockedChange)}`
  if (Number(row.frozenChange)) return `冻结 ${signed(row.frozenChange)}`
  return '0'
}

/*
 * 加载失败要显式说出来。
 *
 * 之前失败只在 catch 里弹一个几秒就消失的提示，页面本身会渲染成
 * 「还没有库存数据」「暂无库存预警」—— 那是在说谎：不是没数据，是根本没拿到数据。
 * 运维/仓库看到"空的"会以为仓库真没货，这比报错危险得多。
 */
const loadError = ref('')

async function load() {
  const token = begin()
  loadError.value = ''
  try {
    const [s, a, tx] = await Promise.all([
      inventoryApi.summary({ warehouseId: app.currentWarehouseId || undefined }),
      inventoryApi.alerts({ expireDays: app.alertDays, warehouseId: app.currentWarehouseId || undefined }),
      inventoryApi.transactions({ page: 1, size: 8, warehouseId: app.currentWarehouseId || undefined }),
    ])
    if (!isLatest(token)) return
    summary.value = s
    alerts.value = a
    recentTransactions.value = tx?.records ?? tx?.list ?? []
  } catch (error) {
    if (!isLatest(token)) return
    loadError.value = error?.message || '加载失败'
    toastError(error, '加载库存看板失败')
  } finally {
    finish(token)
  }
}

function go(to) { if (to) router.push(to) }

watch([() => app.currentWarehouseId, () => app.alertDays], load)

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dash">
    <!-- 欢迎条 -->
    <section class="hero">
      <div class="hero__glow" aria-hidden="true"></div>
      <div class="hero__text">
        <p class="hero__date">{{ today }}</p>
        <h1 class="hero__title">{{ greeting }}，<span>{{ auth.displayName }}</span></h1>
        <p class="hero__sub">
          当前身份
          <el-tag :type="auth.roleTag" size="small" effect="dark" round>
            {{ roleLabel(auth.user?.role) }}
          </el-tag>
          <template v-if="alertTotal > 0">
            · 有 <b class="hero__warn">{{ alertTotal }}</b> 条库存预警需要关注
          </template>
          <template v-else>· 库存状态正常</template>
        </p>
      </div>
      <div class="hero__actions">
        <el-button :icon="Refresh" text @click="load">刷新</el-button>
        <el-button color="#ffffff" :icon="WarningFilled" @click="go('/inventory/alerts')">
          查看预警
        </el-button>
      </div>
    </section>

    <!-- 加载失败：说清楚是"没拿到"，而不是"没有" -->
    <section v-if="loadError" class="load-error">
      <div class="load-error__text">
        <strong>看板数据加载失败</strong>
        <p>{{ loadError }}</p>
      </div>
      <el-button :icon="Refresh" @click="load">重新加载</el-button>
    </section>

    <!-- 指标卡 -->
    <section v-if="!loadError" class="stats">
      <article
        v-for="card in cards"
        :key="card.key"
        class="stat"
        :class="{ 'stat--accent': card.accent, 'stat--warn': card.warn && alertTotal > 0 }"
        @click="go(card.to)"
      >
        <header class="stat__head">
          <span class="stat__label">{{ card.label }}</span>
          <el-icon class="stat__arrow"><ArrowRight /></el-icon>
        </header>
        <p class="stat__value">
          <strong>{{ card.value }}</strong>
          <em>{{ card.unit }}</em>
        </p>
        <div v-if="card.percent !== undefined && card.percent !== null" class="stat__bar">
          <span class="stat__fill" :style="{ width: Math.min(100, card.percent) + '%' }"></span>
        </div>
        <p class="stat__sub">{{ card.sub }}</p>
      </article>
    </section>

    <div v-if="!loadError" class="dash__grid">
      <!-- 按仓库分布 -->
      <section class="card">
        <header class="card__head">
          <h2>按仓库分布</h2>
          <router-link class="card__more" to="/inventory/summary">查看汇总 <el-icon><ArrowRight /></el-icon></router-link>
        </header>

        <ul v-if="warehouseBars.length" class="bars">
          <li v-for="w in warehouseBars" :key="w.groupId" class="bar">
            <div class="bar__top">
              <span class="bar__name">{{ w.groupName }}</span>
              <span class="bar__qty">{{ fmt(w.qty) }} 件</span>
            </div>
            <div class="bar__track">
              <span class="bar__fill" :style="{ width: w.percent + '%' }"></span>
            </div>
            <div class="bar__meta">
              {{ fmt(w.skuCount) }} 个 SKU · 占用 {{ fmt(w.locationCount) }} 个库位 · 可用 {{ fmt(w.availableQty) }}
            </div>
          </li>
        </ul>
        <el-empty v-else description="还没有库存数据" :image-size="60" />
      </section>

      <!-- 待处理 + 预警 -->
      <section class="card">
        <header class="card__head">
          <h2>待处理事项</h2>
        </header>

        <ul class="todo">
          <li class="todo__item" @click="go('/inbound/orders')">
            <span class="todo__label">待收货入库单</span>
            <span class="todo__value">{{ nz(summary?.pendingInboundCount) }}</span>
          </li>
          <li class="todo__item" @click="go('/outbound/orders')">
            <span class="todo__label">待处理出库单</span>
            <span class="todo__value">{{ nz(summary?.pendingOutboundCount) }}</span>
          </li>
          <li class="todo__item" @click="go('/putaway/tasks')">
            <span class="todo__label">待处理作业任务</span>
            <span class="todo__value">{{ nz(summary?.pendingTaskCount) }}</span>
          </li>
        </ul>

        <div class="alert-box" :class="{ 'is-ok': alertTotal === 0 }" @click="go('/inventory/alerts')">
          <template v-if="alertTotal > 0">
            <el-icon class="alert-box__icon"><WarningFilled /></el-icon>
            <div class="alert-box__body">
              <strong>{{ alertTotal }} 条库存预警</strong>
              <span>
                临期 {{ alerts?.expiring?.length || 0 }} ·
                冻结 {{ alerts?.frozen?.length || 0 }} ·
                超容 {{ alerts?.overCapacity?.length || 0 }}
              </span>
            </div>
            <el-icon><ArrowRight /></el-icon>
          </template>
          <template v-else>
            <div class="alert-box__body">
              <strong>暂无库存预警</strong>
              <span>临期、冻结、库位超容都正常</span>
            </div>
          </template>
        </div>
      </section>
    </div>

    <!-- 最近流水 -->
    <section class="card">
      <header class="card__head">
        <h2>最近库存变动</h2>
        <router-link class="card__more" to="/inventory/transactions">全部流水 <el-icon><ArrowRight /></el-icon></router-link>
      </header>

      <el-table :data="recentTransactions" size="small">
        <el-table-column label="时间" width="165">
          <template #default="{ row }">
            <span class="mono">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="TX_TYPE[row.txType]?.type || 'info'" size="small" disable-transitions>
              {{ TX_TYPE[row.txType]?.text || row.txType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="skuName" label="商品" min-width="150" />
        <el-table-column label="库位" width="130">
          <template #default="{ row }"><span class="mono">{{ row.locationCode || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="变动" width="120" align="right">
          <template #default="{ row }">
            <span class="num" :class="changeClass(row.qtyChange || row.lockedChange || row.frozenChange)">
              {{ changeText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="来源单号" min-width="180">
          <template #default="{ row }">
            <span :class="row.sourceNo ? 'mono' : 'muted'">{{ row.sourceNo || '-' }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">还没有库存变动记录</div>
        </template>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.dash { display: flex; flex-direction: column; gap: 14px; }

/* ---------- 加载失败 ---------- */
.load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--danger-300, #fca5a5);
  border-radius: var(--radius-md);
  background: var(--danger-50, #fef2f2);
}

.load-error__text strong {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--danger-600);
}

.load-error__text p {
  font-size: 12.5px;
  color: var(--text-secondary);
}

/* ---------- 欢迎条 ---------- */
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 28px;
  overflow: hidden;
  color: #fff;
  border-radius: var(--radius-lg);
  background: linear-gradient(115deg, #16224a 0%, #1e3a8a 45%, #0e6ba8 100%);
  box-shadow: var(--shadow-lg);
}
.hero__glow {
  position: absolute; top: -120px; right: -60px; width: 320px; height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(56, 189, 248, .45) 0%, transparent 70%);
}
.hero__text { position: relative; z-index: 1; }
.hero__date { font-size: 12px; letter-spacing: .06em; color: rgba(255,255,255,.62); }
.hero__title { margin: 6px 0 8px; font-size: 24px; font-weight: 700; letter-spacing: -.3px; }
.hero__title span {
  background: linear-gradient(100deg, #a5c4ff, #7dd3fc);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.hero__sub { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 13px; color: rgba(255,255,255,.78); }
.hero__warn { color: #fbbf24; font-weight: 700; }
.hero__actions { position: relative; z-index: 1; display: flex; gap: 8px; }

/*
 * 欢迎条是深蓝渐变色块，里面的 text 按钮必须手动给亮色。
 * Element 的 text 按钮默认用 --el-text-color-regular，
 * 浅色模式下那是深灰 #475467，压在 #1E3A8A 上对比度只有 1.5:1 —— 基本看不见。
 * 顺带加一层半透明白底，让人一眼看出这是个按钮，而不是一行文字。
 */
/* 容器已经用 gap 控间距，这里把 Element 给相邻按钮的 margin 归零，免得变成 16px */
.hero__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.hero__actions :deep(.el-button.is-text) {
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.14);
}

.hero__actions :deep(.el-button.is-text:hover),
.hero__actions :deep(.el-button.is-text:focus-visible) {
  color: #fff;
  background: rgba(255, 255, 255, 0.26);
}

/* ---------- 指标卡 ---------- */
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; }
.stat {
  padding: 16px 18px;
  cursor: pointer;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
.stat:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.stat--accent { border-color: var(--brand-200); background: linear-gradient(180deg, var(--brand-50), var(--bg-surface) 60%); }
.stat--warn { border-color: rgba(224,135,0,.4); }
.stat__head { display: flex; align-items: center; justify-content: space-between; }
.stat__label { font-size: 13px; color: var(--text-secondary); }
.stat__arrow { font-size: 12px; color: var(--text-placeholder); }
.stat__value { display: flex; align-items: baseline; gap: 4px; margin-top: 8px; }
.stat__value strong { font-size: 26px; font-weight: 700; letter-spacing: -.5px; color: var(--text-primary); font-variant-numeric: tabular-nums; }
.stat__value em { font-size: 12px; font-style: normal; color: var(--text-placeholder); }
.stat__sub { margin-top: 6px; font-size: 11.5px; color: var(--text-placeholder); }
.stat__bar { height: 4px; margin-top: 10px; overflow: hidden; border-radius: 999px; background: var(--bg-surface-3); }
.stat__fill { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-500), var(--accent-500)); }

/* ---------- 卡片 ---------- */
.dash__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 14px; }
.card { padding: 18px 20px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-xs); }
.card__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 12px; margin-bottom: 12px; border-bottom: 1px solid var(--divider); }
.card__head h2 { font-size: 15px; font-weight: 650; color: var(--text-primary); }
.card__more { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; color: var(--brand-500); }

/* ---------- 仓库分布条 ---------- */
.bars { display: flex; flex-direction: column; gap: 14px; }
.bar__top { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 6px; }
.bar__name { font-size: 13px; color: var(--text-primary); }
.bar__qty { font-family: var(--font-mono); font-size: 13px; font-weight: 600; color: var(--text-primary); }
.bar__track { height: 8px; overflow: hidden; border-radius: 999px; background: var(--bg-surface-3); }
.bar__fill { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--brand-500), var(--accent-500)); transition: width var(--duration-slow) var(--ease-out); }
.bar__meta { margin-top: 5px; font-size: 11.5px; color: var(--text-placeholder); }

/* ---------- 待处理 ---------- */
.todo { display: flex; flex-direction: column; }
.todo__item { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; cursor: pointer; border-radius: var(--radius-sm); transition: background var(--duration-fast); }
.todo__item:hover { background: var(--bg-surface-2); }
.todo__label { font-size: 13px; color: var(--text-regular); }
.todo__value { font-size: 17px; font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }

.alert-box {
  display: flex; align-items: center; gap: 10px;
  margin-top: 12px; padding: 12px 14px;
  cursor: pointer; border-radius: var(--radius-sm);
  border: 1px solid rgba(224,135,0,.35); background: rgba(224,135,0,.08);
}
.alert-box.is-ok { cursor: default; border-color: var(--border-color); background: var(--bg-surface-2); }
.alert-box__icon { font-size: 18px; color: var(--warning-500); }
.alert-box__body { display: flex; flex: 1 1 auto; flex-direction: column; line-height: 1.5; }
.alert-box__body strong { font-size: 13px; color: var(--text-primary); }
.alert-box__body span { font-size: 11.5px; color: var(--text-secondary); }

.mono { font-family: var(--font-mono); font-size: 12.5px; }
.muted { color: var(--text-placeholder); }
.num { font-family: var(--font-mono); font-size: 13px; font-variant-numeric: tabular-nums; }
.is-plus { font-weight: 600; color: var(--success-600); }
.is-minus { font-weight: 600; color: var(--danger-600); }
.is-zero { color: var(--text-placeholder); }
.empty { padding: 26px 0; font-size: 13px; color: var(--text-placeholder); }

@media (max-width: 720px) {
  .hero { flex-direction: column; align-items: flex-start; }
}
</style>
