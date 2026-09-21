<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { toastError } from '@/utils/error'
import { triggerDownload } from '@/utils/format'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { inventoryApi } from '@/api/inventory'
import { useWarehouseOptions } from '@/composables/useWarehouseOptions'
import { useIdempotentKey } from '@/composables/useIdempotentKey'

const router = useRouter()
const tableRef = ref(null)
const { options: warehouseOptions, load: loadWarehouses } = useWarehouseOptions()

onMounted(loadWarehouses)

const columns = [
  { prop: 'warehouseName', label: '仓库', minWidth: 150 },
  { prop: 'locationCode', label: '库位', width: 140, mono: true },
  { prop: 'skuCode', label: 'SKU 编码', width: 140, mono: true },
  { prop: 'skuName', label: '商品名称', minWidth: 180 },
  { prop: 'batchNo', label: '批次号', width: 130 },
  { prop: 'qty', label: '在库', width: 90, align: 'right' },
  { prop: 'lockedQty', label: '锁定', width: 90, align: 'right' },
  { prop: 'frozenQty', label: '冻结', width: 90, align: 'right' },
  { prop: 'availableQty', label: '可用', width: 100, align: 'right' },
  { prop: 'inboundTime', label: '入库时间', width: 175, mono: true, type: 'datetime' },
]

const searchFields = computed(() => [
  { prop: 'keyword', label: '关键字', placeholder: 'SKU / 商品名 / 库位' },
  { prop: 'warehouseId', label: '仓库', type: 'select', options: warehouseOptions.value },
  { prop: 'batchNo', label: '批次号', placeholder: '精确匹配' },
])

/* ------------------------------ 冻结 / 解冻 ------------------------------ */

/*
 * 冻结和解冻是「对某一条库存行」的操作，所以入口就放在行上，不用另外开页面。
 * 数量要用户填，而且必须限制上限：
 *   冻结最多能冻 = 可用数量（冻完不能把可用扣成负数）
 *   解冻最多能解 = 当前冻结量（不能解出负数）
 * 上限在这里只是帮用户少犯错，真正的校验在服务端（applyChange 的不变量校验），
 * 前端拦不住的并发场景由后端兜底。
 */
const freezeVisible = ref(false)
const freezeMode = ref('freeze')
const freezeRow = ref(null)
const freezeQty = ref(1)
const freezing = ref(false)
/** 幂等键：每次打开冻结/解冻弹窗算一次动作，成功后换新的 */
const idempotentKey = useIdempotentKey()

const freezeTitle = computed(() => (freezeMode.value === 'freeze' ? '冻结库存' : '解冻库存'))
const freezeLimit = computed(() =>
  freezeMode.value === 'freeze'
    ? Number(freezeRow.value?.availableQty ?? 0)
    : Number(freezeRow.value?.frozenQty ?? 0),
)

function openFreeze(row, mode) {
  freezeMode.value = mode
  freezeRow.value = row
  freezeQty.value = Math.min(1, freezeLimit.value || 1)
  freezeVisible.value = true
}

async function submitFreeze() {
  if (freezing.value) return
  const qty = Number(freezeQty.value)
  if (!qty || qty < 1) {
    ElMessage.warning('数量必须大于 0')
    return
  }
  if (qty > freezeLimit.value) {
    ElMessage.warning(freezeMode.value === 'freeze' ? '不能超过可用数量' : '不能超过当前冻结量')
    return
  }

  freezing.value = true
  try {
    if (freezeMode.value === 'freeze') {
      await inventoryApi.freeze(freezeRow.value.id, qty, idempotentKey.value)
      ElMessage.success(`已冻结 ${qty} 件，这批货不能再被分配或移库`)
    } else {
      await inventoryApi.unfreeze(freezeRow.value.id, qty, idempotentKey.value)
      ElMessage.success(`已解冻 ${qty} 件，可用数量已恢复`)
    }
    idempotentKey.reset()
    freezeVisible.value = false
    tableRef.value?.reload()
  } catch (error) {
    toastError(error, freezeMode.value === 'freeze' ? '冻结失败' : '解冻失败')
  } finally {
    freezing.value = false
  }
}

/* ------------------------------ 跳转到移库 / 盘点 ------------------------------ */

/** 带上 inventoryId 进移库页，源库存直接锁定，不用让用户再选一遍 */
function goTransfer(row) {
  router.push({ path: '/inventory/transfer/quick', query: { inventoryId: row.id } })
}

/* ---------------- 导出 ---------------- */

/**
 * 导出当前筛选结果。
 * params 由 CrudTable 的工具栏插槽给出来 —— 和列表查询用的是同一份条件，
 * 不会出现「筛了 A 导出 B」。分页参数要丢掉（导的是全部匹配行，不是当前页）。
 */
const exporting = ref(false)

async function handleExport(params) {
  exporting.value = true
  try {
    const { page, size, ...filters } = params
    const blob = await inventoryApi.exportInventory(filters)
    triggerDownload(blob, `库存_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功，单次最多 5000 行')
  } catch (error) {
    toastError(error, '导出失败')
  } finally {
    exporting.value = false
  }
}

function goStocktake(row) {
  router.push({ path: '/inventory/stocktaking/quick', query: { inventoryId: row.id } })
}
</script>

<template>
  <div>
    <PageHeading
      title="库存查询"
      description="可用 = 在库 − 锁定 − 冻结。库存只能由业务动作改变，这里没有「直接改数」的按钮"
    />

    <CrudTable
      ref="tableRef"
      :api="inventoryApi"
      :columns="columns"
      :search-fields="searchFields"
      :can-create="false"
      :can-update="false"
      :can-delete="false"
      :action-width="240"
      entity-name="库存"
    >
      <template #toolbar="{ params }">
        <el-button :icon="Download" :loading="exporting" @click="handleExport(params)">
          导出 Excel
        </el-button>
      </template>

      <template #col-batchNo="{ row }">
        <span :class="row.batchNo ? 'mono' : 'muted'">{{ row.batchNo || '无批次' }}</span>
      </template>

      <template #col-qty="{ row }">
        <span class="num">{{ row.qty ?? 0 }}</span>
      </template>
      <template #col-lockedQty="{ row }">
        <span class="num" :class="{ 'is-zero': !row.lockedQty }">{{ row.lockedQty ?? 0 }}</span>
      </template>
      <template #col-frozenQty="{ row }">
        <span class="num" :class="{ 'is-zero': !row.frozenQty }">{{ row.frozenQty ?? 0 }}</span>
      </template>
      <template #col-availableQty="{ row }">
        <span class="num num--strong">{{ row.availableQty ?? 0 }}</span>
      </template>

      <template #row-actions="{ row }">
        <el-button
          link
          type="primary"
          :disabled="!row.availableQty"
          @click.stop="openFreeze(row, 'freeze')"
        >
          冻结
        </el-button>
        <el-button
          link
          type="primary"
          :disabled="!row.frozenQty"
          @click.stop="openFreeze(row, 'unfreeze')"
        >
          解冻
        </el-button>
        <el-button link type="primary" :disabled="!row.availableQty" @click.stop="goTransfer(row)">
          移库
        </el-button>
        <el-button link type="primary" @click.stop="goStocktake(row)">盘点</el-button>
      </template>
    </CrudTable>

    <!-- 冻结 / 解冻 -->
    <el-dialog v-model="freezeVisible" :title="freezeTitle" width="460px">
      <div v-if="freezeRow" class="freeze">
        <div class="freeze__row">
          <span class="freeze__loc">{{ freezeRow.locationCode }}</span>
          <span class="freeze__sku">{{ freezeRow.skuCode }} {{ freezeRow.skuName }}</span>
          <span class="freeze__batch">{{ freezeRow.batchNo || '无批次' }}</span>
        </div>

        <div class="freeze__stats">
          <div><em>在库</em><strong>{{ freezeRow.qty ?? 0 }}</strong></div>
          <div><em>锁定</em><strong>{{ freezeRow.lockedQty ?? 0 }}</strong></div>
          <div><em>冻结</em><strong>{{ freezeRow.frozenQty ?? 0 }}</strong></div>
          <div><em>可用</em><strong class="is-brand">{{ freezeRow.availableQty ?? 0 }}</strong></div>
        </div>

        <el-form label-position="top">
          <el-form-item :label="freezeMode === 'freeze' ? '冻结数量' : '解冻数量'">
            <el-input-number
              v-model="freezeQty"
              :min="1"
              :max="freezeLimit || 1"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>

        <p class="freeze__hint">
          {{
            freezeMode === 'freeze'
              ? `最多可冻 ${freezeLimit} 件（可用数量）。冻结后货还在库里，但不能被出库分配、也不能移库。`
              : `最多可解冻 ${freezeLimit} 件（当前冻结量）。解冻后这部分数量重新变成可用。`
          }}
        </p>
      </div>

      <template #footer>
        <el-button @click="freezeVisible = false">取消</el-button>
        <el-button type="primary" :loading="freezing" @click="submitFreeze">
          {{ freezeMode === 'freeze' ? '确认冻结' : '确认解冻' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mono {
  font-family: var(--font-mono);
  font-size: 12.5px;
}

.muted {
  color: var(--text-placeholder);
}

.num {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-regular);
  font-variant-numeric: tabular-nums;
}

.num--strong {
  font-weight: 700;
  color: var(--brand-600);
}

.is-zero {
  color: var(--text-placeholder);
}

/* ---------- 冻结弹窗 ---------- */
.freeze {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.freeze__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.freeze__loc {
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand-600);
}

.freeze__sku {
  font-size: 13px;
  color: var(--text-primary);
}

.freeze__batch {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-secondary);
}

.freeze__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.freeze__stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.freeze__stats em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-secondary);
}

.freeze__stats strong {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.freeze__stats .is-brand {
  color: var(--brand-600);
}

.freeze__hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}
</style>