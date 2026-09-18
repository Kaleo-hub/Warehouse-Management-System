<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="nf">
    <div class="nf__card">
      <p class="nf__code">404</p>
      <h1>页面走丢了</h1>
      <p class="nf__desc">你访问的地址不存在，或者已经被移动到别处。</p>
      <el-button type="primary" @click="router.replace('/')">返回工作台</el-button>
    </div>
  </div>
</template>

<style scoped>
.nf {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--bg-app);
}

.nf__card {
  max-width: 380px;
  padding: 40px 36px;
  text-align: center;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.nf__code {
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -2px;
  background: linear-gradient(120deg, var(--brand-500), var(--accent-500));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.nf__card h1 {
  margin: 14px 0 8px;
  font-size: 19px;
  font-weight: 650;
  color: var(--text-primary);
}

.nf__desc {
  margin-bottom: 22px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>