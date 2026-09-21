import { ref, watch } from 'vue'
import { inventoryApi } from '@/api/inventory'
import { useAppStore } from '@/stores/app'
import { useLatestRequest } from '@/composables/useLatestRequest'

/**
 * 库存预警条数，给顶栏的铃铛用。
 *
 * 三类预警（临期 / 冻结 / 库位超容）加起来 = 「需要有人看一眼的事项数」。
 *
 * 【失败为什么静默】铃铛只是个提示，不是业务功能。
 * 拉不到预警就当成「没有预警」，不要把用户正在干的事打断成一个错误弹窗。
 * 想看详情本来就会点进预警页，那里有正经的错误提示。
 */
export function useInventoryAlertCount() {
  const app = useAppStore()
  const alertTotal = ref(0)
  const { loading, begin, isLatest, finish } = useLatestRequest()

  async function load() {
    const token = begin()
    try {
      const data = await inventoryApi.alerts({
        expireDays: app.alertDays,
        warehouseId: app.currentWarehouseId || undefined,
      })
      if (!isLatest(token)) return
      alertTotal.value =
        (data?.expiring?.length || 0) +
        (data?.frozen?.length || 0) +
        (data?.overCapacity?.length || 0)
    } catch {
      if (!isLatest(token)) return
      alertTotal.value = 0
    } finally {
      finish(token)
    }
  }

  watch([() => app.currentWarehouseId, () => app.alertDays], load)

  return { alertTotal, loading, load }
}
