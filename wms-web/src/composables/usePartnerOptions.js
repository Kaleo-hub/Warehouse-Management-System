import { ref } from 'vue'
import { partnerApi } from '@/api/basedata'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { toastError } from '@/utils/error'

/**
 * 往来单位下拉选项（按类型分别缓存）。
 *
 * 【为什么只取 ENABLED】停用的单位不该还能开新单 —— 和商品下拉同一个原则。
 * 历史单据不受影响：它存的是名称快照，和这里没关系。
 *
 * 【为什么要做模块级缓存】供应商/客户会在多个建单页用到，
 * 每次进页面都拉一遍纯属浪费；但缓存又必须按类型分开
 * （供应商和客户是同一张表的两类数据，混在一起下拉里就会出现客户）。
 */
const cache = { SUPPLIER: ref([]), CUSTOMER: ref([]) }
const loaded = { SUPPLIER: false, CUSTOMER: false }

export function usePartnerOptions(type) {
  const options = ref(cache[type]?.value ?? [])
  const loading = ref(false)

  async function load(force = false) {
    if (!cache[type]) {
      console.warn('[partnerOptions] 未知的往来单位类型', type)
      return []
    }
    if (loaded[type] && !force) {
      options.value = cache[type].value
      return options.value
    }

    loading.value = true
    try {
      // 分页拉全量：后端 size 上限是 100，写死大 size 会直接 10002（踩过）
      const list = await fetchAllOptions((params) =>
        partnerApi.page({ ...params, partnerType: type, status: 'ENABLED' }),
      )
      cache[type].value = list.map((item) => ({
        label: `${item.name}（${item.code}）`,
        value: item.id,
        name: item.name,
        code: item.code,
      }))
      loaded[type] = true
      options.value = cache[type].value
    } catch (error) {
      console.error('[partnerOptions] 加载往来单位失败', error)
      toastError(error, '往来单位加载失败')
      options.value = []
    } finally {
      loading.value = false
    }

    return options.value
  }

  /** 选中项的名称（提交时不再传文本，但界面上偶尔要展示） */
  function nameOf(id) {
    return cache[type]?.value.find((item) => item.value === id)?.name || ''
  }

  return { options, loading, load, nameOf }
}