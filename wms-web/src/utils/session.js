/**
 * 会话失效事件。
 *
 * 【为什么要多这一层】
 * axios 的拦截器发现 401 时，需要做两件事：
 *   1. 清掉内存里的登录态（Pinia）
 *   2. 跳回登录页（router）
 * 但请求层不应该 import store 和 router —— 那会形成环：
 *   request.js → stores/auth → api/auth → request.js
 * 所以请求层只负责「广播一个事件」，由应用入口（main.js）决定怎么处理。
 * 这也是更好的分层：基础设施层不依赖业务层。
 */
const listeners = new Set()

/** 注册会话失效处理器，返回取消注册的函数 */
export function onSessionExpired(handler) {
  listeners.add(handler)
  return () => listeners.delete(handler)
}

/** 广播会话失效。可以安全地重复调用，由订阅方自己决定要不要去重 */
export function emitSessionExpired() {
  listeners.forEach((handler) => {
    try {
      handler()
    } catch (error) {
      // 一个处理器出错不能影响其它的
      console.error('[session] 会话失效处理器执行失败', error)
    }
  })
}