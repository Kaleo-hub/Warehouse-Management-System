import { ref } from 'vue'
import { productApi } from '@/api/basedata'

/**
 * 商品（SKU）下拉选项，模块级缓存，多个单据页共用。
 * 只取启用状态的商品 —— 停用的 SKU 不应该还能开新单。
 */
const cachedOptions = ref([])
const loaded = ref(false)

export function useSkuOptions() {
  const options = ref(cachedOptions.value)
  const loading = ref(false)

  async function load(force = false) {
    if (loaded.value && !force) {
      options.value = cachedOptions.value
      return options.value
    }

    loading.value = true
    try {
      const data = await productApi.page({ page: 1, size: 200, status: 'ENABLED' })
      const list = data?.records ?? data?.list ?? []
      cachedOptions.value = list.map((item) => ({
        label: `${item.skuCode} ${item.name}`,
        value: item.id,
        code: item.skuCode,
        name: item.name,
        spec: item.spec,
        unit: item.unit,
        shelfLifeDays: item.shelfLifeDays,
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

  function findById(id) {
    return cachedOptions.value.find((item) => item.value === id) || null
  }

  return { options, loading, load, findById }
}