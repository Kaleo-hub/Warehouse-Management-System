import { ref } from 'vue'
import { warehouseApi } from '@/api/basedata'

/**
 * 仓库下拉选项。多个页面都要用，做一层模块级缓存，避免每次进页面都请求一遍。
 */
const cachedOptions = ref([])
const loaded = ref(false)

export function useWarehouseOptions() {
  const options = ref(cachedOptions.value)
  const loading = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) {
      options.value = cachedOptions.value
      return options.value
    }

    loading.value = true
    try {
      const data = await warehouseApi.page({ page: 1, size: 100 })
      const list = data?.records ?? data?.list ?? []
      cachedOptions.value = list.map((item) => ({
        label: `${item.name}（${item.code}）`,
        value: item.id,
      }))
      loaded.value = true
      options.value = cachedOptions.value
    } catch {
      options.value = []
    } finally {
      loading.value = false
    }

    return options.value
  }

  function labelOf(id) {
    return cachedOptions.value.find((item) => item.value === id)?.label || `#${id}`
  }

  return { options, loading, load, labelOf }
}