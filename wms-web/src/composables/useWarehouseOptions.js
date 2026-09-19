import { ref } from 'vue'
import { warehouseApi } from '@/api/basedata'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { toastError } from '@/utils/error'

/**
 * 仓库下拉选项。多个页面都要用，做一层模块级缓存，避免每次进页面都请求一遍。
 *
 * 同样走 fetchAllOptions 分页拉取：后端 size 上限是 100，
 * 直接写死一个大的 size 一旦超过上限就会整个下拉都空掉。
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
      const list = await fetchAllOptions((params) => warehouseApi.page(params))
      cachedOptions.value = list.map((item) => ({
        label: `${item.name}（${item.code}）`,
        value: item.id,
      }))
      loaded.value = true
      options.value = cachedOptions.value
    } catch (error) {
      // 不要静默吞：加载失败要让控制台和用户都能看到，否则只会看到"下拉是空的"
      console.error('[warehouseOptions] 加载仓库列表失败', error)
      toastError(error, '仓库列表加载失败')
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