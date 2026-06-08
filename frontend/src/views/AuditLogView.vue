<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Search } from 'lucide-vue-next'
import { listAuditEvents } from '../api'
import type { AuditEvent } from '../types'
import { formatDate } from '../format'

const loading = ref(false)
const events = ref<AuditEvent[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  actor: '',
  action: '',
  success: '' as '' | 'true' | 'false',
})

const actionOptions = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
  'LOGIN_RATE_LIMITED',
  'DOCUMENT_UPLOAD',
  'DOCUMENT_DOWNLOAD',
  'DOCUMENT_DELETE',
  'DOCUMENT_REINDEX',
  'USER_CREATE',
  'USER_UPDATE',
  'USER_ENABLE',
  'USER_DISABLE',
  'USER_DELETE',
  'AI_SETTINGS_UPDATE',
  'AI_SETTINGS_TEST',
  'BACKUP_CREATE',
  'BACKUP_DELETE',
  'BACKUP_RESTORE',
  'RECYCLE_RESTORE',
  'RECYCLE_PERMANENT_DELETE',
]

onMounted(load)

async function load() {
  loading.value = true
  try {
    const result = await listAuditEvents({
      actor: filters.actor || undefined,
      action: filters.action || undefined,
      success: filters.success === '' ? null : filters.success === 'true',
      page: page.value - 1,
      size: size.value,
    })
    events.value = result.items
    total.value = result.total
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载审计日志失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function successType(success: boolean) {
  return success ? 'success' : 'danger'
}

function successLabel(success: boolean) {
  return success ? '成功' : '失败'
}
</script>

<template>
  <section class="panel table-panel">
    <div class="panel-header">
      <div>
        <h2>审计日志</h2>
        <span>记录登录、账号、文档、AI 配置、备份恢复等关键操作。</span>
      </div>
      <el-button @click="load">
        <RefreshCw :size="16" />
        刷新
      </el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="filters.actor" clearable placeholder="操作者" @keyup.enter="search" />
      <el-select v-model="filters.action" clearable filterable placeholder="动作">
        <el-option v-for="action in actionOptions" :key="action" :label="action" :value="action" />
      </el-select>
      <el-select v-model="filters.success" clearable placeholder="结果">
        <el-option label="成功" value="true" />
        <el-option label="失败" value="false" />
      </el-select>
      <el-button type="primary" @click="search">
        <Search :size="16" />
        查询
      </el-button>
    </div>

    <el-table :data="events" v-loading="loading" height="560" class="dense-table">
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="actorUsername" label="操作者" min-width="130" show-overflow-tooltip />
      <el-table-column prop="actorRole" label="角色" width="90" />
      <el-table-column prop="action" label="动作" min-width="190" show-overflow-tooltip />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="successType(row.success)">{{ successLabel(row.success) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceType" label="资源" width="110" />
      <el-table-column prop="resourceId" label="资源 ID" min-width="180" show-overflow-tooltip />
      <el-table-column prop="ipAddress" label="IP" width="130" show-overflow-tooltip />
      <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
      <el-table-column prop="userAgent" label="User-Agent" min-width="220" show-overflow-tooltip />
    </el-table>

    <div class="table-footer">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>
  </section>
</template>
