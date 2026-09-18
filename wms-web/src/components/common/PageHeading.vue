<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<template>
  <header class="heading">
    <div class="heading__text">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="heading__extra">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.heading__text h1 {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.2px;
  color: var(--text-primary);
}

.heading__text p {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.heading__extra {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 720px) {
  .heading {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>