/**
 * 分页循环拉取「全部」选项。
 *
 * 【为什么不能直接写 size: 200】
 * 后端 PageQuery 对 size 有 @Max(100) 约束，传 200 会被直接拒绝：
 *   {"code":10002,"data":{"size":"size 最大 100"},"message":"参数校验失败"}
 * 而且就算放开上限，一次性拉几百条也不是好做法。
 *
 * 所以这里按 100 一页循环拉，最多 maxPages 页，避免数据量失控时把页面拖死。
 * 真到几百上千条的时候，下拉应该改成远程搜索（el-select 的 remote + remote-method），
 * 而不是继续加大 pageSize。
 *
 * @param {(params: {page: number, size: number}) => Promise<any>} fetchPage
 * @param {{pageSize?: number, maxPages?: number}} [options]
 * @returns {Promise<Array>}
 */
export async function fetchAllOptions(fetchPage, { pageSize = 100, maxPages = 5 } = {}) {
  const all = []
  let total = 0

  for (let page = 1; page <= maxPages; page++) {
    const data = await fetchPage({ page, size: pageSize })
    // 兼容 records / list 两种分页字段命名
    const list = data?.records ?? data?.list ?? []
    all.push(...list)
    total = Number(data?.total ?? all.length)

    // 已经拿全，或者这一页没满，都可以停了
    if (all.length >= total || list.length < pageSize) break
  }

  if (all.length < total) {
    console.warn(
      `[options] 选项共 ${total} 条，本次只加载了 ${all.length} 条。` +
        '数据量到这个规模时，下拉应改成远程搜索而不是继续加大 pageSize。',
    )
  }

  return all
}