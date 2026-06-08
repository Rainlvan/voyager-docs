<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Save, Trash2 } from 'lucide-vue-next'
import {
  createManagedUser,
  deleteManagedUser,
  listManagedUsers,
  setManagedUserEnabled,
  updateAdminProfile,
  updateManagedUser,
} from '../api'
import type { ManagedUser } from '../types'
import { formatDate } from '../format'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const savingProfile = ref(false)
const users = ref<ManagedUser[]>([])
const dialogVisible = ref(false)
const editingUser = ref<ManagedUser | null>(null)
const savingUser = ref(false)

const profile = reactive({
  username: '',
  displayName: '',
  currentPassword: '',
  newPassword: '',
})

const userForm = reactive({
  username: '',
  displayName: '',
  password: '',
  enabled: true,
})

const dialogTitle = computed(() => (editingUser.value ? '编辑员工账号' : '新增员工账号'))

onMounted(() => {
  resetProfile()
  void refresh()
})

async function refresh() {
  loading.value = true
  try {
    users.value = await listManagedUsers()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载账号失败')
  } finally {
    loading.value = false
  }
}

function resetProfile() {
  profile.username = auth.user?.username || ''
  profile.displayName = auth.user?.displayName || ''
  profile.currentPassword = ''
  profile.newPassword = ''
}

async function saveProfile() {
  savingProfile.value = true
  try {
    const session = await updateAdminProfile({
      username: profile.username,
      displayName: profile.displayName,
      currentPassword: profile.currentPassword || undefined,
      newPassword: profile.newPassword || undefined,
    })
    auth.setSession(session.token, session.user)
    resetProfile()
    ElMessage.success('管理员资料已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存管理员资料失败')
  } finally {
    savingProfile.value = false
  }
}

function openCreateDialog() {
  editingUser.value = null
  Object.assign(userForm, { username: '', displayName: '', password: '', enabled: true })
  dialogVisible.value = true
}

function openEditDialog(row: ManagedUser) {
  editingUser.value = row
  Object.assign(userForm, {
    username: row.username,
    displayName: row.displayName,
    password: '',
    enabled: row.enabled,
  })
  dialogVisible.value = true
}

async function saveUser() {
  savingUser.value = true
  try {
    if (editingUser.value) {
      await updateManagedUser(editingUser.value.id, {
        username: userForm.username,
        displayName: userForm.displayName,
        password: userForm.password || undefined,
        enabled: userForm.enabled,
      })
      ElMessage.success('员工账号已更新')
    } else {
      await createManagedUser({
        username: userForm.username,
        displayName: userForm.displayName,
        password: userForm.password,
      })
      ElMessage.success('员工账号已创建')
    }
    dialogVisible.value = false
    await refresh()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存员工账号失败')
  } finally {
    savingUser.value = false
  }
}

async function toggleEnabled(row: ManagedUser, enabled: boolean) {
  try {
    await setManagedUserEnabled(row.id, enabled)
    row.enabled = enabled
    ElMessage.success(enabled ? '账号已启用' : '账号已禁用')
  } catch (error: any) {
    row.enabled = !enabled
    ElMessage.error(error.response?.data?.message || '更新账号状态失败')
  }
}

async function removeUser(row: ManagedUser) {
  await ElMessageBox.confirm(
    `确认删除员工“${row.displayName}”？该员工未删除的文档会进入管理员回收站。`,
    '删除员工账号',
    { type: 'warning' },
  )
  try {
    await deleteManagedUser(row.id)
    await refresh()
    ElMessage.success('员工已删除，文档已移入回收站')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除员工失败')
  }
}
</script>

<template>
  <section class="account-page">
    <section class="panel">
      <div class="panel-header">
        <h2>管理员资料</h2>
        <span>修改用户名后会自动刷新登录令牌</span>
      </div>
      <el-form label-position="top" class="settings-form">
        <div class="form-pair">
          <el-form-item label="用户名">
            <el-input v-model="profile.username" autocomplete="username" />
          </el-form-item>
          <el-form-item label="显示名">
            <el-input v-model="profile.displayName" />
          </el-form-item>
        </div>
        <div class="form-pair">
          <el-form-item label="当前密码">
            <el-input v-model="profile.currentPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="profile.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
        </div>
        <div class="settings-actions">
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">
            <Save :size="17" />
            保存资料
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>普通员工账号</h2>
        <el-button type="primary" @click="openCreateDialog">
          <Plus :size="16" />
          新增员工
        </el-button>
      </div>
      <el-table :data="users" v-loading="loading" height="520" class="dense-table">
        <el-table-column prop="username" label="用户名" min-width="160" />
        <el-table-column prop="displayName" label="显示名" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.deleted ? 'info' : row.enabled ? 'success' : 'warning'">
              {{ row.deleted ? '已删除' : row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="row.deleted"
              @change="(value: string | number | boolean) => toggleEnabled(row, Boolean(value))"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" :disabled="row.deleted" @click="openEditDialog(row)">编辑</el-button>
            <el-button text size="small" type="danger" :disabled="row.deleted" @click="removeUser(row)">
              <Trash2 :size="14" />
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item :label="editingUser ? '重置密码（留空不修改）' : '初始密码'">
          <el-input v-model="userForm.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item v-if="editingUser" label="账号状态">
          <el-switch v-model="userForm.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingUser" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
