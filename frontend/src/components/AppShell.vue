<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArchiveRestore,
  Camera,
  ClipboardList,
  DatabaseZap,
  FileStack,
  ListChecks,
  LogOut,
  MessageSquareText,
  Search,
  Settings,
  Users,
} from 'lucide-vue-next'
import { getAvatarBlob, uploadAvatar } from '../api'
import DocumentLibrary from '../views/DocumentLibrary.vue'
import SearchView from '../views/SearchView.vue'
import ChatView from '../views/ChatView.vue'
import JobsView from '../views/JobsView.vue'
import SettingsView from '../views/SettingsView.vue'
import AccountManagementView from '../views/AccountManagementView.vue'
import RecycleBinView from '../views/RecycleBinView.vue'
import AuditLogView from '../views/AuditLogView.vue'
import { useAuthStore } from '../stores/auth'

type NavKey = 'documents' | 'search' | 'chat' | 'jobs' | 'settings' | 'accounts' | 'audit' | 'recycle'

const auth = useAuthStore()
const active = ref<NavKey>('documents')
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUrl = ref('')
const avatarUploading = ref(false)

const adminNavItems = [
  { key: 'documents', label: '文档库', icon: FileStack },
  { key: 'jobs', label: '任务队列', icon: ListChecks },
  { key: 'settings', label: '系统设置', icon: Settings },
  { key: 'accounts', label: '账号管理', icon: Users },
  { key: 'audit', label: '审计日志', icon: ClipboardList },
  { key: 'recycle', label: '回收站', icon: ArchiveRestore },
] as const

const employeeNavItems = [
  { key: 'documents', label: '文档库', icon: FileStack },
  { key: 'search', label: 'AI 搜索', icon: Search },
  { key: 'chat', label: '对话找文档', icon: MessageSquareText },
] as const

const navItems = computed(() => (auth.user?.role === 'ADMIN' ? adminNavItems : employeeNavItems))
const currentTitle = computed(() => navItems.value.find((item) => item.key === active.value)?.label || '文档库')
const currentSubtitle = computed(() =>
  auth.user?.role === 'ADMIN'
    ? '管理模型配置、账号、审计日志、备份恢复和解析任务。'
    : '上传文档，用 AI 搜索和对话快速找到公司资料。',
)
const initials = computed(() => (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())

watch(
  navItems,
  (items) => {
    if (!items.some((item) => item.key === active.value)) {
      active.value = items[0].key
    }
  },
  { immediate: true },
)

watch(
  () => auth.user?.avatarUrl,
  () => {
    void loadAvatar()
  },
  { immediate: true },
)

onUnmounted(() => {
  revokeAvatarUrl()
})

async function loadAvatar() {
  revokeAvatarUrl()
  if (!auth.user?.avatarUrl) return
  try {
    const blob = await getAvatarBlob(auth.user.id)
    avatarUrl.value = URL.createObjectURL(blob)
  } catch {
    avatarUrl.value = ''
  }
}

async function chooseAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  avatarUploading.value = true
  try {
    const user = await uploadAvatar(file)
    auth.setUser(user)
    await loadAvatar()
    ElMessage.success('头像已更新')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '头像上传失败')
  } finally {
    avatarUploading.value = false
  }
}

function revokeAvatarUrl() {
  if (avatarUrl.value) {
    URL.revokeObjectURL(avatarUrl.value)
    avatarUrl.value = ''
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-mark"><DatabaseZap :size="20" /></span>
        <span>Voyager Docs</span>
      </div>
      <nav class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: active === item.key }"
          @click="active = item.key"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
      <button class="logout-button" @click="auth.logout()">
        <LogOut :size="17" />
        <span>退出登录</span>
      </button>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <h1>{{ currentTitle }}</h1>
          <p>{{ currentSubtitle }}</p>
        </div>
        <div class="user-chip">
          <span class="avatar-preview">
            <img v-if="avatarUrl" :src="avatarUrl" alt="用户头像" />
            <span v-else>{{ initials }}</span>
          </span>
          <span>{{ auth.user?.displayName }}</span>
          <el-button size="small" :loading="avatarUploading" @click="avatarInput?.click()">
            <Camera :size="15" />
            头像
          </el-button>
          <input ref="avatarInput" class="hidden-input" type="file" accept="image/png,image/jpeg,image/webp" @change="chooseAvatar" />
        </div>
      </header>

      <DocumentLibrary v-if="active === 'documents'" />
      <SearchView v-else-if="active === 'search'" />
      <ChatView v-else-if="active === 'chat'" />
      <JobsView v-else-if="active === 'jobs'" />
      <SettingsView v-else-if="active === 'settings'" />
      <AccountManagementView v-else-if="active === 'accounts'" />
      <AuditLogView v-else-if="active === 'audit'" />
      <RecycleBinView v-else />
    </main>
  </div>
</template>
