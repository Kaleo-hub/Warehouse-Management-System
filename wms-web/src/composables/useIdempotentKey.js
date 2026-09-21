import { ref } from 'vue'

/**
 * 生成一个幂等键。
 *
 * 【键的生命周期是关键】
 * 必须是「一次用户动作一个键」，而不是「一次请求一个键」：
 *   打开新建页 / 打开弹窗时生成一次 → 双击、失败重试、网络重发都用这同一个键
 *   动作成功之后重新生成 → 下一次动作是新的键，不会被当成重复提交
 * 如果每次请求都重新生成，幂等就形同虚设。
 *
 * crypto.randomUUID 在 https 和 localhost 下都可用；老浏览器退化成随机串。
 */
export function newIdempotentKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return `k-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

/**
 * 用法：
 *   const { key, reset } = useIdempotentKey()
 *   await api.create(payload, key.value)   // 提交时带上
 *   reset()                                // 成功后换一个新键
 */
export function useIdempotentKey() {
  const key = ref(newIdempotentKey())
  function reset() {
    key.value = newIdempotentKey()
  }
  return { key, reset }
}