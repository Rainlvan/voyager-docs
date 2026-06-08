<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Activity, RefreshCw, RotateCcw } from 'lucide-vue-next'
import { listJobs, listWorkers, retryJob } from '../api'
import type { JobItem, WorkerItem } from '../types'
import { formatDate, statusLabel, statusType } from '../format'

const jobs = ref<JobItem[]>([])
const workers = ref<WorkerItem[]>([])
const loading = ref(false)
const retryingJobId = ref('')
let pollTimer: ReturnType<typeof window.setInterval> | undefined

onMounted(() => {
  void refresh()
  pollTimer = window.setInterval(() => {
    if (!document.hidden) void refresh(false)
  }, 3000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

async function refresh(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const [nextJobs, nextWorkers] = await Promise.all([listJobs(), listWorkers()])
    jobs.value = nextJobs
    workers.value = nextWorkers
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载任务失败')
  } finally {
    if (showLoading) loading.value = false
  }
}

function handleVisibilityChange() {
  if (!document.hidden) void refresh(false)
}

async function retry(row: JobItem) {
  retryingJobId.value = row.id
  try {
    await retryJob(row.id)
    await refresh(false)
    ElMessage.success('任务已重新加入队列')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '重试失败')
  } finally {
    retryingJobId.value = ''
  }
}
</script>

<template>
  <section class="jobs-page">
    <section class="panel">
      <div class="panel-header">
        <h2>Worker 状态</h2>
        <el-button @click="refresh()">
          <RefreshCw :size="16" />
          刷新
        </el-button>
      </div>
      <el-table :data="workers" v-loading="loading" class="dense-table">
        <el-table-column label="Worker" min-width="180">
          <template #default="{ row }">
            <div class="worker-cell">
              <Activity :size="17" :class="{ online: row.online }" />
              <strong>{{ row.workerId }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="在线" width="100">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'info'">{{ row.online ? '在线' : '离线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="message" label="最近消息" min-width="280" show-overflow-tooltip />
        <el-table-column label="最后心跳" width="150">
          <template #default="{ row }">{{ formatDate(row.lastSeenAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!workers.length && !loading" description="Worker 启动后会在这里显示心跳" :image-size="90" />
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2>解析任务</h2>
        <span>失败任务可直接重新加入队列</span>
      </div>
      <el-table :data="jobs" v-loading="loading" height="520" class="dense-table">
        <el-table-column prop="documentTitle" label="文档" min-width="260" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="attemptCount" label="尝试" width="90" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="260" show-overflow-tooltip />
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              size="small"
              :loading="retryingJobId === row.id"
              @click="retry(row)"
            >
              <RotateCcw :size="14" />
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>
