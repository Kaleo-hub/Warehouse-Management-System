<script setup>
import { onMounted, ref, watch } from 'vue'
import { inventoryApi } from '@/api/inventory'
import { toastError } from '@/utils/error'

/**
 * 库存行选择器：选「哪个库位的哪个批次」，移库和盘点都用它。
 *
 * 【为什么不一次把所有库存拉下来当下拉选项】
 * 之前踩过这个坑：下拉写死 size: 200，后端 PageQuery 有 @Max(100)，整个下拉直接空掉。
 * 库存行是会随着批次增长的（一个 SKU 一个库位一个批次就是一行），
 * 全量塞进下拉迟早会失控。所以这里只拉一页（50 条）并给一个关键字输入框，
 * 让人自己缩小范围 —— 真到几百上千行时，再把 input 换成远程搜索即可。
 *
 * scope=available：只列「可用数量 > 0」的行（移库要能真的挪得动）
 * scope=qty      ：只列「在库数量 > 0」的行（盘点允许把货盘成 0，但没货的行不该拿来盘）
 */
const props = defineProps({
  modelValue: { type: Object, default: null },
  scope: { type: String, default: 'qty' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const options = ref([])
const loading = ref(false)
const keyword = ref('')

function toOption(row) {
  return {
    value: row.id,
    row,
    label: `${row.locationCode} · ${row.skuCode} · ${row.batchNo || '无批次'}`,
    meta: `在库 ${row.qty} / 可用 ${row.availableQty}`,
  }
}

async function search() {
  loading.value = true
  try {
    const data = await inventoryApi.page({
      page: 1,
      size: 50,
      keyword: keyword.value.trim() || undefined,
    })

    const list = (data?.records ?? []).filter((row) =>
      props.scope === 'available' ? Number(row.availableQty) > 0 : Number(row.qty) > 0,
    )

    options.value = list.map(toOption)

    // 深链进来的行可能不在这一页里（比如被关键字过滤掉了），补进去免得下拉显示成空白
    if (props.modelValue && !options.value.some((item) => item.value === props.modelValue.id)) {
      options.value = [toOption(props.modelValue), ...options.value]
    }
  } catch (error) {
    toastError(error, '库存列表加载失败')
    options.value = []
  } finally {
    loading.value = false
  }
}

onMounted(search)

// 外部把选中的行换掉（比如从别的页面深链过来）时，保证它在下拉里可见
watch(
  () => props.modelValue?.id,
  (id) => {
    if (id && !options.value.some((item) => item.value === id) && props.modelValue) {
      options.value = [toOption(props.modelValue), ...options.value]
    }
  },
)

function handleChange(id) {
  emit('update:modelValue', options.value.find((item) => item.value === id)?.row ?? null)
}
</script>

<template>
  <div class="picker">
    <div class="picker__search">
      <el-input
        v-model="keyword"
        placeholder="按 SKU / 商品名 / 库位缩小范围"
        clearable
        :disabled="disabled"
        @keyup.enter="search"
      />
      <el-button :disabled="disabled" @click="search">查询</el-button>
    </div>

    <el-select
      :model-value="modelValue?.id"
      class="picker__select"
      placeholder="请选择库存行（库位 + 商品 + 批次）"
      filterable
      clearable
      :disabled="disabled"
      :loading="loading"
      @change="handleChange"
    >
      <el-option v-for="item in options" :key="item.value" :label="item.label" :value="item.value">
        <div class="picker__option">
          <span>{{ item.label }}</span>
          <em>{{ item.meta }}</em>
        </div>
      </el-option>
      <template #empty>
        <p class="picker__empty">没有匹配的库存行（只列在库数量大于 0 的记录）</p>
      </template>
    </el-select>
  </div>
</template>

<style scoped>
/*
 * el-form-item__content 本身是 flex 容器，子项默认按内容宽度排，
 * 不给 width: 100% 的话这个选择器只有 248px 宽，
 * 而同一张表单里的输入框是满宽的 —— 看起来就是「这一行没对齐」。
 */
.picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.picker__search {
  display: flex;
  gap: 8px;
}

/* 关键字框吃掉剩余宽度，查询按钮保持自身宽度 */
.picker__search :deep(.el-input) {
  flex: 1 1 auto;
  min-width: 0;
}

.picker__select {
  width: 100%;
}

.picker__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.picker__option em {
  font-size: 11.5px;
  font-style: normal;
  color: var(--text-placeholder);
}

.picker__empty {
  padding: 14px 0;
  font-size: 12.5px;
  text-align: center;
  color: var(--text-placeholder);
}
</style>