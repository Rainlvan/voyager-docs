<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshCw, RotateCcw, Trash2 } from 'lucide-vue-next'
import {
  listRecycleBinDocuments,
  permanentlyDeleteRecycleBinDocument,
  restoreRecycleBinDocument,
} from '../api'
import type { DocumentItem } from '../types'
import { formatDate, formatSize, statusLabel, statusType } from '../format'

const documents = ref<DocumentItem[]>([])
const loading = ref(false)
const workingId = ref('')

onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    documents.value = await listRecycleBinDocuments()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载回收站失败')
  } finally {
    loading.value = false
  }
}

async function restore(row: DocumentItem) {
  workingId.value = row.id
  try {
    await restoreRecycleBinDocument(row.id)
    await refresh()
    ElMessage.success('文档已恢复并重新进入解析队列')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '恢复失败')
  } finally {
    workingId.value = ''
  }
}

async function permanentlyDelete(row: DocumentItem) {
  await ElMessageBox.confirm(`确认永久删除“${row.title}”？该操作不可恢复。`, '永久删除文档', {
    type: 'warning',
  })
  workingId.value = row.id
  try {
    await permanentlyDeleteRecycleBinDocument(row.id)
    await refresh()
    ElMessage.success('文档已永久删除')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '永久删除失败')
  } finally {
    workingId.value = ''
  }
}
</script>

<template>
  <section class="panel table-panel">
    <div class="panel-header">
      <div>
        <h2>员工文档回收站</h2>
        <span>删除员工账号后，该员工未删除的文档会进入这里。</span>
      </div>
      <el-button @click="refresh">
        <RefreshCw :size="16" />
        刷新
      </el-button>
    </div>
    <el-table :data="documents" v-loading="loading" height="580" class="dense-table">
      <el-table-column label="文档名" min-width="260">
        <template #default="{ row }">
          <div class="file-cell">
            <strong>{{ row.title }}</strong>
            <span>{{ row.originalFilename }} · {{ formatSize(row.fileSize) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="uploadedBy" label="原上传人" width="150" />
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :loading="workingId === row.id" @click="restore(row)">
            <RotateCcw :size="14" />
            恢复
          </el-button>
          <el-button size="small" type="danger" :loading="workingId === row.id" @click="permanentlyDelete(row)">
            <Trash2 :size="14" />
            永久删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!documents.length && !loading" description="回收站暂无文档" :image-size="90" />
  </section>
</template>
