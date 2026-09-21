import { ref } from 'vue'

/**
 * 只允许「最后一次请求」写入页面状态。
 *
 * 典型场景：用户快速切换仓库或连续搜索，请求 A 先发、请求 B 后发。
 * 如果 A 因为网络慢最后才返回，旧结果就会覆盖新结果。
 *
 * 用法：
 *   const { loading, begin, isLatest, finish } = useLatestRequest()
 *   const token = begin()
 *   try {
 *     const data = await api(params)
 *     if (!isLatest(token)) return
 *     rows.value = data
 *   } finally {
 *     finish(token)
 *   }
 */
export function useLatestRequest() {
  const loading = ref(false)
  let sequence = 0

  function begin() {
    const token = ++sequence
    loading.value = true
    return token
  }

  function isLatest(token) {
    return token === sequence
  }

  function finish(token) {
    if (isLatest(token)) loading.value = false
  }

  return { loading, begin, isLatest, finish }
}
