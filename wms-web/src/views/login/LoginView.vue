<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User, WarningFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getLastTraceId } from '@/api/request'
import { getRememberedUsername, setRememberedUsername } from '@/utils/storage'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref(null)
const passwordRef = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const errorTraceId = ref('')
const shake = ref(false)

const form = reactive({
  username: '',
  password: '',
  remember: false,
})

/** 与后端 LoginDTO 的校验注解保持一致，避免前后端规则打架 */
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在 3 到 50 之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6 到 32 之间', trigger: 'blur' },
  ],
}

const demoAccount = { username: 'admin', password: '123456' }

const features = [
  { title: '实时库存', desc: '账实同步，可用量一目了然' },
  { title: '流程闭环', desc: '入库到发货全链路打通' },
  { title: '作业可追溯', desc: '每一次变动都留流水' },
]

/** 货架矩阵：1 表示有货，静态定义即可，不用随机，避免每次渲染闪动 */
const rackRows = [
  [1, 0, 1, 1, 0, 1, 1, 0],
  [1, 1, 0, 1, 1, 0, 1, 1],
  [0, 1, 1, 0, 1, 1, 0, 1],
  [1, 1, 1, 1, 0, 1, 1, 0],
]

const redirectTarget = computed(() => {
  const target = route.query.redirect
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('/login')) return '/'
  return target
})

onMounted(async () => {
  const remembered = getRememberedUsername()
  if (remembered) {
    form.username = remembered
    form.remember = true
    await nextTick()
    passwordRef.value?.focus?.()
  }
})

function triggerShake() {
  shake.value = false
  window.requestAnimationFrame(() => {
    shake.value = true
    window.setTimeout(() => {
      shake.value = false
    }, 460)
  })
}

function fillDemo() {
  form.username = demoAccount.username
  form.password = demoAccount.password
  errorMessage.value = ''
  errorTraceId.value = ''
}

function handleForgot() {
  ElMessage.info('请联系系统管理员重置密码')
}

async function handleSubmit() {
  if (loading.value) return

  errorMessage.value = ''
  errorTraceId.value = ''

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    setRememberedUsername(form.remember ? form.username.trim() : '')
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    await router.replace(redirectTarget.value)
  } catch (error) {
    errorMessage.value = error?.message || '登录失败，请稍后重试'
    errorTraceId.value = error?.traceId || getLastTraceId()
    triggerShake()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <!-- ============================ 左侧品牌区 ============================ -->
    <section class="brand">
      <div class="brand__grid" aria-hidden="true"></div>
      <div class="brand__glow brand__glow--a" aria-hidden="true"></div>
      <div class="brand__glow brand__glow--b" aria-hidden="true"></div>

      <header class="brand__top">
        <span class="brand__mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" width="26" height="26">
            <defs>
              <linearGradient id="loginLogo" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#8aa4ff" />
                <stop offset="1" stop-color="#38bdf8" />
              </linearGradient>
            </defs>
            <path
              d="M16 4.5 27 10.5v11L16 27.5 5 21.5v-11z"
              fill="none"
              stroke="url(#loginLogo)"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
            <path
              d="M5 10.5 16 16.5l11-6M16 16.5v11"
              fill="none"
              stroke="url(#loginLogo)"
              stroke-width="1.8"
              stroke-linejoin="round"
              opacity=".85"
            />
          </svg>
        </span>
        <span class="brand__wordmark">
          <strong>WMS</strong>
          <em>智能仓储管理系统</em>
        </span>
      </header>

      <div class="brand__body">
        <p class="brand__eyebrow">
          <span class="brand__dot"></span>
          Warehouse Management System
        </p>
        <h1 class="brand__headline">
          让每一件货<br />
          <span>都清清楚楚</span>
        </h1>
        <p class="brand__sub">
          入库 · 上架 · 库存 · 出库 · 拣货，全链路闭环管理，库存变动全程留痕可追溯。
        </p>

        <ul class="brand__features">
          <li v-for="item in features" :key="item.title" class="feature">
            <span class="feature__tick" aria-hidden="true">
              <svg viewBox="0 0 16 16" width="12" height="12">
                <path
                  d="m4 8.4 2.6 2.6L12 5.6"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="feature__text">
              <strong>{{ item.title }}</strong>
              <em>{{ item.desc }}</em>
            </span>
          </li>
        </ul>
      </div>

      <!-- 货架可视化 -->
      <div class="rack" aria-hidden="true">
        <div class="rack__frame">
          <div v-for="(row, rowIndex) in rackRows" :key="rowIndex" class="rack__row">
            <span
              v-for="(filled, colIndex) in row"
              :key="colIndex"
              class="rack__slot"
              :class="[
                filled ? 'is-filled' : 'is-empty',
                filled ? `is-tone-${(rowIndex + colIndex) % 3}` : '',
              ]"
            >
              <i v-if="filled" class="rack__box"></i>
            </span>
          </div>
          <span class="rack__beam"></span>
        </div>

        <div class="chip chip--stock">
          <span class="chip__label">在库 SKU</span>
          <strong class="chip__value">1,284</strong>
        </div>
        <div class="chip chip--task">
          <span class="chip__pulse"></span>
          <span class="chip__label">待处理任务</span>
          <strong class="chip__value">26</strong>
        </div>
      </div>

      <footer class="brand__bottom">
        <span class="brand__status">
          <i class="brand__status-dot"></i>
          服务运行中
        </span>
        <span class="brand__stack">Spring Boot 4 · Vue 3 · MySQL 8</span>
      </footer>
    </section>

    <!-- ============================ 右侧表单区 ============================ -->
    <section class="panel">
      <div class="panel__inner" :class="{ 'is-shaking': shake }">
        <header class="panel__head">
          <h2>欢迎回来</h2>
          <p>请登录以继续使用仓储管理系统</p>
        </header>

        <transition name="alert">
          <div v-if="errorMessage" class="panel__alert" role="alert">
            <el-icon class="panel__alert-icon"><WarningFilled /></el-icon>
            <div class="panel__alert-body">
              <span>{{ errorMessage }}</span>
              <code v-if="errorTraceId">traceId · {{ errorTraceId }}</code>
            </div>
          </div>
        </transition>

        <el-form
          ref="formRef"
          class="panel__form"
          :model="form"
          :rules="rules"
          :show-message="true"
          @submit.prevent="handleSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="用户名"
              size="large"
              clearable
              autocomplete="username"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              ref="passwordRef"
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              placeholder="密码"
              size="large"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <div class="panel__row">
            <el-checkbox v-model="form.remember">记住用户名</el-checkbox>
            <button class="panel__link" type="button" @click="handleForgot">忘记密码？</button>
          </div>

          <el-button
            class="panel__submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="loading"
          >
            {{ loading ? '正在登录…' : '登 录' }}
          </el-button>
        </el-form>

        <div class="panel__demo">
          <span class="panel__demo-label">演示账号</span>
          <button class="panel__demo-btn" type="button" @click="fillDemo">
            <span class="u-mono">admin / 123456</span>
            <em>一键填入</em>
          </button>
        </div>
      </div>

      <p class="panel__foot">
        © 2026 WMS 智能仓储管理系统 · 个人学习项目
      </p>
    </section>
  </div>
</template>

<style scoped>
/* ============================== 布局 ============================== */
.login {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(420px, 0.88fr);
  min-height: 100vh;
  background: var(--bg-surface);
}

/* ============================== 左侧品牌区 ============================== */
.brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 40px 56px 32px;
  overflow: hidden;
  color: #eaf0fb;
  background:
    radial-gradient(120% 90% at 8% 0%, #17284d 0%, transparent 55%),
    radial-gradient(90% 80% at 100% 100%, #0b2b46 0%, transparent 60%),
    linear-gradient(160deg, #0b1220 0%, #0d1524 55%, #0a1120 100%);
}

.brand__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(122, 158, 255, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(122, 158, 255, 0.07) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(120% 80% at 30% 20%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(120% 80% at 30% 20%, #000 0%, transparent 78%);
  pointer-events: none;
}

.brand__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}

.brand__glow--a {
  top: -160px;
  left: -110px;
  width: 460px;
  height: 460px;
  background: rgba(59, 100, 244, 0.32);
}

.brand__glow--b {
  right: -140px;
  bottom: -180px;
  width: 420px;
  height: 420px;
  background: rgba(14, 165, 233, 0.22);
}

.brand__top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(138, 164, 255, 0.28);
  border-radius: 12px;
  background: rgba(138, 164, 255, 0.1);
  backdrop-filter: blur(6px);
}

.brand__wordmark {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand__wordmark strong {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.brand__wordmark em {
  font-size: 12px;
  font-style: normal;
  letter-spacing: 0.08em;
  color: rgba(197, 214, 245, 0.66);
}

.brand__body {
  position: relative;
  z-index: 2;
  max-width: 520px;
  margin: auto 0;
  padding: 42px 0 24px;
}

.brand__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(160, 187, 255, 0.9);
  border: 1px solid rgba(122, 158, 255, 0.24);
  border-radius: 999px;
  background: rgba(122, 158, 255, 0.08);
}

.brand__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 0 4px rgba(56, 189, 248, 0.18);
  animation: wms-pulse 2.4s var(--ease-in-out) infinite;
}

.brand__headline {
  margin: 22px 0 16px;
  font-size: 46px;
  font-weight: 700;
  line-height: 1.18;
  letter-spacing: -0.5px;
  color: #ffffff;
}

.brand__headline span {
  background: linear-gradient(100deg, #8aa4ff 0%, #38bdf8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand__sub {
  max-width: 430px;
  font-size: 14.5px;
  line-height: 1.85;
  color: rgba(186, 202, 232, 0.78);
}

.brand__features {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 34px;
}

.feature {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 148px;
  padding: 12px 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(8px);
  transition: border-color var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out);
}

.feature:hover {
  border-color: rgba(138, 164, 255, 0.4);
  transform: translateY(-2px);
}

.feature__tick {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  margin-top: 2px;
  color: #0b1220;
  border-radius: 50%;
  background: linear-gradient(135deg, #8aa4ff, #38bdf8);
}

.feature__text {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.feature__text strong {
  font-size: 13px;
  font-weight: 600;
  color: #ffffff;
}

.feature__text em {
  font-size: 11.5px;
  font-style: normal;
  color: rgba(186, 202, 232, 0.62);
}

/* ============================== 货架可视化 ============================== */
.rack {
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  width: 100%;
  max-width: 560px;
  height: 210px;
  perspective: 1000px;
}

.rack__frame {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 190px;
  padding: 14px;
  overflow: hidden;
  border: 1px solid rgba(122, 158, 255, 0.18);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(20, 34, 62, 0.72), rgba(11, 18, 32, 0.55));
  transform: rotateX(9deg) rotateY(-7deg);
  transform-origin: center bottom;
  box-shadow: 0 30px 60px -40px rgba(0, 0, 0, 0.9);
}

.rack__row {
  display: flex;
  gap: 8px;
  flex: 1 1 0;
}

.rack__slot {
  position: relative;
  display: grid;
  flex: 1 1 0;
  place-items: center;
  border-radius: 6px;
  border: 1px solid rgba(122, 158, 255, 0.1);
  background: rgba(122, 158, 255, 0.03);
}

.rack__slot.is-filled {
  border-color: rgba(122, 158, 255, 0.26);
  background: rgba(122, 158, 255, 0.08);
}

.rack__box {
  display: block;
  width: 62%;
  height: 56%;
  border-radius: 4px;
  background: linear-gradient(150deg, rgba(138, 164, 255, 0.85), rgba(56, 189, 248, 0.5));
  box-shadow: 0 6px 12px -8px rgba(56, 189, 248, 0.9);
}

.rack__slot.is-tone-1 .rack__box {
  background: linear-gradient(150deg, rgba(125, 211, 252, 0.8), rgba(59, 100, 244, 0.45));
}

.rack__slot.is-tone-2 .rack__box {
  background: linear-gradient(150deg, rgba(160, 180, 255, 0.9), rgba(14, 165, 233, 0.45));
}

.rack__beam {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 42px;
  background: linear-gradient(
    180deg,
    rgba(56, 189, 248, 0) 0%,
    rgba(56, 189, 248, 0.22) 60%,
    rgba(56, 189, 248, 0.7) 100%
  );
  animation: wms-scan 4.6s var(--ease-in-out) infinite;
}

.chip {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 9px 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  background: rgba(15, 24, 42, 0.78);
  backdrop-filter: blur(10px);
  box-shadow: 0 18px 34px -22px rgba(0, 0, 0, 0.95);
  animation: wms-float 6s var(--ease-in-out) infinite;
}

.chip--stock {
  top: 0;
  right: 8px;
}

.chip--task {
  bottom: 8px;
  left: 0;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  animation-delay: -2.4s;
}

.chip__label {
  font-size: 10.5px;
  letter-spacing: 0.06em;
  color: rgba(186, 202, 232, 0.62);
}

.chip__value {
  font-size: 19px;
  font-weight: 700;
  line-height: 1.2;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
}

.chip__pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 0 4px rgba(56, 189, 248, 0.18);
}

/* ============================== 品牌区底部 ============================== */
.brand__bottom {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;
  font-size: 11.5px;
  color: rgba(186, 202, 232, 0.5);
}

.brand__status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.brand__status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.16);
}

.brand__stack {
  letter-spacing: 0.04em;
}

/* ============================== 右侧表单区 ============================== */
.panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 48px 56px;
  background: var(--bg-surface);
}

.panel__inner {
  width: 100%;
  max-width: 384px;
}

.panel__inner.is-shaking {
  animation: wms-shake 0.45s var(--ease-in-out);
}

.panel__head {
  margin-bottom: 26px;
}

.panel__head h2 {
  font-size: 27px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--text-primary);
}

.panel__head p {
  margin-top: 7px;
  font-size: 13.5px;
  color: var(--text-secondary);
}

/* 错误提示 */
.panel__alert {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  padding: 11px 14px;
  border: 1px solid rgba(229, 72, 77, 0.28);
  border-left: 3px solid var(--danger-500);
  border-radius: 10px;
  background: var(--danger-50);
}

.panel__alert-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  font-size: 15px;
  color: var(--danger-500);
}

.panel__alert-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.panel__alert-body span {
  font-size: 13px;
  line-height: 1.55;
  color: var(--danger-600);
}

.panel__alert-body code {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--text-placeholder);
  word-break: break-all;
}

.alert-enter-active {
  transition: opacity var(--duration-base) var(--ease-out),
    transform var(--duration-base) var(--ease-out);
}

.alert-leave-active {
  transition: opacity var(--duration-fast) var(--ease-in-out);
}

.alert-enter-from,
.alert-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 表单 */
.panel__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.panel__form :deep(.el-input__wrapper) {
  padding: 4px 14px;
  border-radius: 10px;
  background: var(--bg-surface-2);
}

.panel__form :deep(.el-input__inner) {
  height: 42px;
  font-size: 14px;
}

.panel__form :deep(.el-input__prefix) {
  margin-right: 4px;
  color: var(--text-placeholder);
}

.panel__form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--danger-500) inset;
}

.panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 20px;
}

.panel__link {
  padding: 0;
  font-size: 13px;
  color: var(--brand-500);
  background: none;
  border: 0;
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-in-out);
}

.panel__link:hover {
  color: var(--brand-400);
}

.panel__submit {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.16em;
  border-radius: 10px;
  background-image: linear-gradient(120deg, var(--brand-500), var(--brand-600) 55%, #1b7fd8);
  border: none;
  box-shadow: var(--shadow-brand);
  transition: transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-base) var(--ease-out), filter var(--duration-fast);
}

.panel__submit:hover:not(.is-loading) {
  transform: translateY(-1px);
  filter: brightness(1.06);
}

.panel__submit:active:not(.is-loading) {
  transform: translateY(0);
}

/* 演示账号 */
.panel__demo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed var(--border-color);
}

.panel__demo-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.panel__demo-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  font-size: 12.5px;
  color: var(--text-regular);
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-surface-2);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.panel__demo-btn:hover {
  color: var(--brand-500);
  border-color: var(--brand-200);
  background: var(--brand-50);
}

.panel__demo-btn em {
  font-style: normal;
  font-size: 11px;
  color: var(--text-placeholder);
}

.panel__foot {
  font-size: 11.5px;
  color: var(--text-placeholder);
}

/* ============================== 响应式 ============================== */
@media (max-width: 1180px) {
  .brand {
    padding: 32px 36px 26px;
  }

  .brand__headline {
    font-size: 38px;
  }

  .rack {
    height: 180px;
  }

  .rack__frame {
    height: 160px;
  }
}

@media (max-width: 960px) {
  .login {
    grid-template-columns: 1fr;
  }

  .brand {
    padding: 28px 28px 32px;
  }

  .brand__body {
    padding: 28px 0 8px;
  }

  .brand__headline {
    font-size: 32px;
  }

  .brand__features,
  .rack,
  .brand__bottom {
    display: none;
  }

  .panel {
    padding: 36px 24px 44px;
  }
}

@media (max-width: 560px) {
  .brand {
    padding: 22px 20px 24px;
  }

  .brand__headline {
    font-size: 27px;
  }

  .brand__sub {
    font-size: 13px;
  }

  .panel {
    padding: 28px 20px 36px;
  }
}
</style>