import { createPinia } from 'pinia'

/** 提到模块里 export，路由守卫（在组件外）才能拿到同一个 pinia 实例 */
export const pinia = createPinia()

export default pinia