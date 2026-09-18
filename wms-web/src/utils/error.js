import { ElMessage } from 'element-plus'

/**
 * 统一的接口错误提示。
 *
 * 有些错误请求层已经处理过了 —— 典型的是会话失效（401）：
 * main.js 的订阅者会弹「登录状态已失效」并跳登录页，
 * 这时候页面层不该再弹一遍后端的原文，否则用户会看到两条提示。
 *
 * 请求层会在这种错误上打 error.silent = true，
 * 页面统一走这个函数就不用每处都写 if 判断了。
 */
export function toastError(error, fallback = '操作失败') {
  if (error?.silent) return
  ElMessage.error(error?.message || fallback)
}