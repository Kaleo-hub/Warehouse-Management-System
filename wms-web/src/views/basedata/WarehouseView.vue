<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toastError } from '@/utils/error'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { locationApi, warehouseApi, zoneApi } from '@/api/basedata'
import { fetchAllOptions } from '@/utils/loadAllOptions'
import { STATUS_OPTIONS, STATUS_TAG, ZONE_TYPE_TAG } from '@/constants/options'

const router = useRouter()

const columns = [
  { prop: 'code', label: '仓库编码', width: 140, mono: true },
  { prop: 'name', label: '仓库名称', minWidth: 160 },
  { prop: 'address', label: '仓库地址', minWidth: 220 },
  { prop: 'status', label: '状态', width: 100, tag: STATUS_TAG },
  { prop: 'remark', label: '备注', minWidth: 140 },
  { prop: 'createTime', label: '创建时间', width: 180, mono: true, type: 'datetime' },
]

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码 / 名称 / 地址' },
  { prop: 'status', label: '状态', type: 'select', options: STATUS_OPTIONS },
]

const formFields = [
  {
    prop: 'code',
    label: '仓库编码',
    required: true,
    maxlength: 32,
    help: '全局唯一，保存后不建议修改',
    rules: [{ max: 32, message: '编码最长 32 个字符', trigger: 'blur' }],
  },
  { prop: 'name', label: '仓库名称', required: true, maxlength: 64 },
  { prop: 'address', label: '仓库地址', maxlength: 255 },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: STATUS_OPTIONS,
    defaultValue: 'ENABLED',
  },
  { prop: 'remark', label: '备注', type: 'textarea', maxlength: 255 },
]

/* ---------------- 库区下钻：这个仓库下面有哪些库区 ---------------- */

/*
 * 层级关系是「仓库 → 库区 → 库位 → 商品库存」，
 * 所以每一层都应该能直接往下看一眼，而不是让人自己去下一个页面重新筛。
 * 这里点「库区」弹出抽屉，再点某一行的「看库位」跳到库位管理并带上筛选条件。
 */
const zoneVisible = ref(false)
const zoneLoading = ref(false)
const currentWarehouse = ref(null)
const zones = ref([])
const locations = ref([])

/** 每个库区下有几个库位，用来在抽屉里直接看出哪块是空的 */
const locationCount = computed(() => {
  const map = {}
  locations.value.forEach((item) => {
    map[item.zoneId] = (map[item.zoneId] || 0) + 1
  })
  return map
})

/**
 * 库区和库位一次并发拉回来。
 *
 * 库区列表是抽屉自己要显示的；库位只是用来数「每个库区有几个库位」——
 * 如果改成「先拉库区、再逐个库区查库位数」，就是典型的 N+1 请求。
 * 两个请求并发发出去，一次往返搞定。
 */
async function openZones(row) {
  currentWarehouse.value = row
  zones.value = []
  locations.value = []
  zoneVisible.value = true
  zoneLoading.value = true
  try {
    const [zoneList, locationList] = await Promise.all([
      fetchAllOptions((params) => zoneApi.page({ ...params, warehouseId: row.id })),
      fetchAllOptions((params) => locationApi.page({ ...params, warehouseId: row.id })),
    ])
    zones.value = zoneList
    locations.value = locationList
  } catch (error) {
    toastError(error, '库区列表加载失败')
  } finally {
    zoneLoading.value = false
  }
}

/** 跳到库位管理，并把这个库区作为预置筛选条件 */
function goZoneLocations(zone) {
  zoneVisible.value = false
  router.push({ path: '/basedata/locations', query: { zoneId: zone.id } })
}
</script>

<template>
  <div>
    <PageHeading
      title="仓库管理"
      description="维护仓库主数据。点「库区」可以展开看这个仓库下面的库区，再往下是库位和商品"
    />

    <CrudTable
      :api="warehouseApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      :action-width="200"
      entity-name="仓库"
    >
      <template #row-actions-extra="{ row }">
        <el-button link type="primary" @click.stop="openZones(row)">库区</el-button>
      </template>
    </CrudTable>

    <!-- 仓库下的库区 -->
    <el-drawer
      v-model="zoneVisible"
      :title="`库区 · ${currentWarehouse?.name || ''}`"
      size="660px"
    >
      <div v-loading="zoneLoading" class="zones">
        <div class="zones__head">
          <div class="zones__wh">
            <strong>{{ currentWarehouse?.name }}</strong>
            <span>{{ currentWarehouse?.code }} · {{ currentWarehouse?.address || '未填地址' }}</span>
          </div>
          <div class="zones__stat">
            <span>库区 / 库位</span>
            <strong>{{ zones.length }}<em> / {{ locations.length }}</em></strong>
          </div>
        </div>

        <el-table :data="zones" size="small" border>
          <el-table-column prop="code" label="库区编码" width="100" />
          <el-table-column prop="name" label="库区名称" min-width="140" />
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <el-tag :type="ZONE_TYPE_TAG[row.zoneType]?.type || 'info'" size="small" disable-transitions>
                {{ ZONE_TYPE_TAG[row.zoneType]?.text || row.zoneType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="库位数" width="80" align="right">
            <template #default="{ row }">
              <span class="num">{{ locationCount[row.id] || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="STATUS_TAG[row.status]?.type || 'info'" size="small" disable-transitions>
                {{ STATUS_TAG[row.status]?.text || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="goZoneLocations(row)">看库位</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <div class="zones__empty">
              <p>这个仓库还没有库区</p>
              <em>去「库区管理」新建一个，库位要挂在库区下面</em>
            </div>
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.zones {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.zones__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  background: var(--bg-surface-2);
}

.zones__wh {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.zones__wh strong {
  font-size: 14px;
  color: var(--text-primary);
}

.zones__wh span {
  font-size: 12px;
  color: var(--text-secondary);
}

.zones__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.4;
}

.zones__stat span {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.zones__stat strong {
  font-size: 20px;
  font-weight: 700;
  color: var(--brand-600);
  font-variant-numeric: tabular-nums;
}

.zones__stat em {
  font-size: 13px;
  font-style: normal;
  font-weight: 600;
  color: var(--text-placeholder);
}

.zones__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 0;
}

.zones__empty p {
  font-size: 13.5px;
  color: var(--text-regular);
}

.zones__empty em {
  font-size: 12px;
  font-style: normal;
  color: var(--text-placeholder);
}

.num {
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}
</style>