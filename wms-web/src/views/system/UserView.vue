<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { toastError } from '@/utils/error'
import CrudTable from '@/components/crud/CrudTable.vue'
import PageHeading from '@/components/common/PageHeading.vue'
import { userApi } from '@/api/system'
import { ROLE_OPTIONS, ROLE_TAG, USER_STATUS_OPTIONS, USER_STATUS_TAG } from '@/constants/options'

const columns = [
  { prop: 'username', label: '登录名', width: 150, mono: true },
  { prop: 'realName', label: '姓名', minWidth: 140 },
  { prop: 'role', label: '角色', width: 130, tag: ROLE_TAG },
  { prop: 'status', label: '状态', width: 100, tag: USER_STATUS_TAG },
  { prop: 'createTime', label: '创建时间', width: 180, mono: true, type: 'datetime' },
  { prop: 'updateTime', label: '最近修改', width: 180, mono: true, type: 'datetime' },
]

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '登录名 / 姓名' },
  { prop: 'role', label: '角色', type: 'select', options: ROLE_OPTIONS },
  { prop: 'status', label: '状态', type: 'select', options: USER_STATUS_OPTIONS },
]

const formFields = [
  {
    prop: 'username',
    label: '登录名',
    required: true,
    maxlength: 50,
    // 登录名是账号不是昵称：建完就不给改了，改的是「这个人」，不是「这个号」
    disabledOnEdit: true,
    help: '只能包含字母、数字、下划线、点和横线，创建后不可修改',
  },
  { prop: 'realName', label: '姓名', required: true, maxlength: 50 },
  {
    prop: 'role',
    label: '角色',
    type: 'select',
    required: true,
    options: ROLE_OPTIONS,
    defaultValue: 'OPERATOR',
    help: '管理员=全部权限；主管=主数据+库存调整；作业员=收货/上架/拣货+查询',
  },
  {
    prop: 'password',
    label: '初始密码',
    required: true,
    maxlength: 32,
    // 只在新建时出现：改密码走行操作里的「重置密码」，
    // 顺带避免「编辑时密码框空着提交」把密码改没
    createOnly: true,
    omitIfEmpty: true,
    help: '至少 6 位。密码只存 BCrypt 哈希，系统里谁也查不出原文',
    rules: [{ min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  },
  {
    prop: 'status',
    label: '状态',
    type: 'switch',
    activeValue: 1,
    inactiveValue: 0,
    defaultValue: 1,
    hint: '停用后该账号无法登录（已经登录的 token 到期后失效）',
  },
]

/** 重置密码：管理员不需要知道旧密码 */
async function handleResetPassword(row) {
  let value
  try {
    const result = await ElMessageBox.prompt(
      `给「${row.realName || row.username}」设置一个新密码（至少 6 位）。`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入新密码',
        inputValidator: (val) => (val && val.length >= 6 ? true : '密码至少 6 位'),
      },
    )
    value = result.value
  } catch {
    return
  }

  try {
    await userApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置，请通知本人用新密码登录')
  } catch (error) {
    toastError(error, '重置密码失败')
  }
}

</script>

<template>
  <div>
    <PageHeading
      title="用户管理"
      description="维护登录账号与角色。角色决定能看哪些菜单、能做哪些操作，后端接口同样按角色校验"
    />

    <CrudTable
      ref="tableRef"
      :api="userApi"
      :columns="columns"
      :search-fields="searchFields"
      :form-fields="formFields"
      :action-width="260"
      entity-name="用户"
    >
      <!--
        行操作只额外加一个「重置密码」，编辑和删除继续用 CrudTable 默认的那两个 ——
        自己再写一遍只会把「删除要二次确认 + 删完刷新列表」这些现成逻辑写坏。
        「不能删自己 / 不能禁用自己 / 不能把最后一个管理员降级」由后端拦，
        拦下来会返回明确的提示文案。
      -->
      <template #row-actions-extra="{ row }">
        <el-button link type="primary" @click.stop="handleResetPassword(row)">重置密码</el-button>
      </template>
    </CrudTable>
  </div>
</template>