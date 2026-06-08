<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DatabaseBackup, RefreshCw, RotateCcw, Save, ShieldCheck, Trash2 } from 'lucide-vue-next'
import {
  createBackupNow,
  deleteBackup,
  getAiSetting,
  getBackupSettings,
  getMaintenanceStatus,
  listBackups,
  restoreBackup,
  testAiSetting,
  updateAiSetting,
  updateBackupSettings,
} from '../api'
import type { AiSetting, BackupRun, BackupSettings, EmbeddingInvocationMode, MaintenanceStatus } from '../types'
import { formatDate, formatSize } from '../format'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const backupSaving = ref(false)
const backupRunning = ref(false)
const restoringId = ref('')
const deletingId = ref('')

const form = reactive({
  apiKey: '',
  region: 'cn-beijing',
  chatModel: 'qwen3.6-flash',
  textEmbeddingModel: 'text-embedding-v4',
  textEmbeddingDimension: 1024,
  multimodalEmbeddingModel: 'qwen3-vl-embedding',
  multimodalEmbeddingDimension: 2560,
  rerankModel: 'gte-rerank-v2',
  multimodalRerankModel: 'qwen3-vl-rerank',
  embeddingInvocationMode: 'REALTIME' as EmbeddingInvocationMode,
})

const backupForm = reactive({
  enabled: false,
  dailyTime: '02:00',
})

const current = ref<AiSetting | null>(null)
const backupSettings = ref<BackupSettings | null>(null)
const backups = ref<BackupRun[]>([])
const backupTotalBytes = ref(0)
const maintenance = ref<MaintenanceStatus | null>(null)

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadAiSettings(), loadBackupArea()])
  } finally {
    loading.value = false
  }
}

async function loadAiSettings() {
  try {
    current.value = await getAiSetting()
    Object.assign(form, {
      apiKey: '',
      region: current.value.region,
      chatModel: current.value.chatModel,
      textEmbeddingModel: current.value.textEmbeddingModel,
      textEmbeddingDimension: current.value.textEmbeddingDimension,
      multimodalEmbeddingModel: current.value.multimodalEmbeddingModel,
      multimodalEmbeddingDimension: current.value.multimodalEmbeddingDimension,
      rerankModel: current.value.rerankModel,
      multimodalRerankModel: current.value.multimodalRerankModel,
      embeddingInvocationMode: current.value.embeddingInvocationMode || 'REALTIME',
    })
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载 AI 设置失败')
  }
}

async function loadBackupArea() {
  try {
    const [settings, list, status] = await Promise.all([getBackupSettings(), listBackups(), getMaintenanceStatus()])
    backupSettings.value = settings
    backupForm.enabled = settings.enabled
    backupForm.dailyTime = settings.dailyTime
    backups.value = list.items
    backupTotalBytes.value = list.totalBytes
    maintenance.value = status
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载备份信息失败')
  }
}

async function saveAiSettings() {
  saving.value = true
  try {
    current.value = await updateAiSetting({ ...form })
    form.apiKey = ''
    ElMessage.success('AI 设置已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存 AI 设置失败')
  } finally {
    saving.value = false
  }
}

async function testAiConfig() {
  testing.value = true
  try {
    const response = await testAiSetting()
    response.ok ? ElMessage.success(response.message) : ElMessage.warning(response.message)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '测试配置失败')
  } finally {
    testing.value = false
  }
}

async function saveBackupSettings() {
  backupSaving.value = true
  try {
    backupSettings.value = await updateBackupSettings({ ...backupForm })
    ElMessage.success('备份策略已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存备份策略失败')
  } finally {
    backupSaving.value = false
  }
}

async function runBackupNow() {
  backupRunning.value = true
  try {
    await createBackupNow()
    await loadBackupArea()
    ElMessage.success('全量备份已完成')
  } catch (error: any) {
    await loadBackupArea()
    ElMessage.error(error.response?.data?.message || '立即备份失败')
  } finally {
    backupRunning.value = false
  }
}

async function restore(row: BackupRun) {
  const prompt = await ElMessageBox.prompt(
    `恢复到备份“${row.backupFilename || row.id}”会短暂进入维护模式，并将当前数据回滚到该备份。请输入当前管理员密码确认。`,
    '恢复备份',
    {
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPlaceholder: '当前管理员密码',
      type: 'warning',
    },
  )
  restoringId.value = row.id
  try {
    await restoreBackup(row.id, String(prompt.value || ''))
    await loadBackupArea()
    ElMessage.success('恢复已完成，文档已重新进入解析队列')
  } catch (error: any) {
    await loadBackupArea()
    ElMessage.error(error.response?.data?.message || '恢复失败')
  } finally {
    restoringId.value = ''
  }
}

async function removeBackup(row: BackupRun) {
  await ElMessageBox.confirm(`确认删除备份“${row.backupFilename || row.id}”？该操作不会影响当前业务数据。`, '删除备份', {
    type: 'warning',
  })
  deletingId.value = row.id
  try {
    await deleteBackup(row.id)
    await loadBackupArea()
    ElMessage.success('备份已删除')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除备份失败')
  } finally {
    deletingId.value = ''
  }
}

function backupStatusType(status: string) {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    SUCCEEDED: 'success',
    RESTORED: 'success',
    RUNNING: 'warning',
    RESTORING: 'warning',
    FAILED: 'danger',
  }
  return types[status] || 'info'
}

function backupStatusLabel(status: string) {
  const labels: Record<string, string> = {
    RUNNING: '备份中',
    SUCCEEDED: '成功',
    FAILED: '失败',
    RESTORING: '恢复中',
    RESTORED: '已恢复',
  }
  return labels[status] || status
}

function triggerLabel(trigger: string) {
  const labels: Record<string, string> = {
    MANUAL: '手动',
    SCHEDULED: '自动',
    PRE_RESTORE: '恢复前安全备份',
  }
  return labels[trigger] || trigger
}
</script>

<template>
  <section class="settings-grid" v-loading="loading">
    <section class="panel settings-panel">
      <div class="panel-header">
        <div>
          <h2>百炼模型配置</h2>
          <span v-if="current">更新于 {{ formatDate(current.updatedAt) }}</span>
        </div>
      </div>
      <el-form label-position="top" class="settings-form">
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="留空表示保留当前 Key" />
          <small>当前状态：{{ current?.apiKeyConfigured ? current.maskedApiKey : '未配置' }}</small>
        </el-form-item>
        <el-form-item label="调用模式">
          <el-select v-model="form.embeddingInvocationMode">
            <el-option label="实时调用" value="REALTIME" />
            <el-option label="Batch 异步调用" value="BATCH" />
          </el-select>
          <small>Batch 仅用于文本向量离线索引；聊天、重排序和图片向量仍使用实时调用。</small>
        </el-form-item>
        <el-form-item label="地域">
          <el-select v-model="form.region">
            <el-option label="北京 / 中国内地" value="cn-beijing" />
            <el-option label="新加坡 / 国际" value="intl" />
          </el-select>
        </el-form-item>
        <el-form-item label="聊天模型">
          <el-input v-model="form.chatModel" />
        </el-form-item>
        <div class="form-pair">
          <el-form-item label="文本向量模型">
            <el-input v-model="form.textEmbeddingModel" />
          </el-form-item>
          <el-form-item label="文本向量维度">
            <el-input-number v-model="form.textEmbeddingDimension" :min="1" :step="256" />
          </el-form-item>
        </div>
        <div class="form-pair">
          <el-form-item label="多模态向量模型">
            <el-input v-model="form.multimodalEmbeddingModel" />
          </el-form-item>
          <el-form-item label="多模态向量维度">
            <el-input-number v-model="form.multimodalEmbeddingDimension" :min="1" :step="256" />
          </el-form-item>
        </div>
        <div class="form-pair">
          <el-form-item label="文本重排序模型">
            <el-input v-model="form.rerankModel" />
          </el-form-item>
          <el-form-item label="多模态重排序模型">
            <el-input v-model="form.multimodalRerankModel" />
          </el-form-item>
        </div>
        <div class="settings-actions">
          <el-button :loading="testing" @click="testAiConfig">
            <ShieldCheck :size="17" />
            测试配置
          </el-button>
          <el-button type="primary" :loading="saving" @click="saveAiSettings">
            <Save :size="17" />
            保存
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="panel settings-panel">
      <div class="panel-header">
        <div>
          <h2>全量备份与恢复</h2>
          <span>共 {{ backups.length }} 份备份，占用 {{ formatSize(backupTotalBytes) }}</span>
        </div>
        <el-tag v-if="maintenance?.enabled" type="warning">维护中：{{ maintenance.reason }}</el-tag>
      </div>

      <el-form label-position="top" class="settings-form">
        <div class="form-pair">
          <el-form-item label="每日自动备份">
            <el-switch v-model="backupForm.enabled" active-text="启用" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="备份时间">
            <el-input v-model="backupForm.dailyTime" placeholder="02:00" />
          </el-form-item>
        </div>
        <div class="settings-actions">
          <el-button :loading="backupSaving" @click="saveBackupSettings">
            <Save :size="17" />
            保存策略
          </el-button>
          <el-button type="primary" :loading="backupRunning" @click="runBackupNow">
            <DatabaseBackup :size="17" />
            现在备份
          </el-button>
          <el-button @click="loadBackupArea">
            <RefreshCw :size="17" />
            刷新
          </el-button>
        </div>
      </el-form>

      <div class="backup-table-wrap">
        <el-table :data="backups" height="420" class="dense-table backup-table">
          <el-table-column label="备份文件" min-width="270" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="file-cell">
                <strong>{{ row.backupFilename || row.id }}</strong>
                <span>{{ triggerLabel(row.triggerType) }} · {{ row.objectCount }} 个对象 · {{ formatSize(row.fileSize) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="backupStatusType(row.status)">{{ backupStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="校验值" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sha256 || '-' }}</template>
          </el-table-column>
          <el-table-column label="完成时间" width="120">
            <template #default="{ row }">{{ row.completedAt ? formatDate(row.completedAt) : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :disabled="row.status !== 'SUCCEEDED'" :loading="restoringId === row.id" @click="restore(row)">
                <RotateCcw :size="14" />
                恢复
              </el-button>
              <el-button size="small" type="danger" :loading="deletingId === row.id" @click="removeBackup(row)">
                <Trash2 :size="14" />
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-if="!backups.length" description="暂无备份" :image-size="90" />
    </section>

    <aside class="panel model-note">
      <h2>默认策略</h2>
      <p>聊天默认使用 qwen3.6-flash 控制成本；文本向量使用 text-embedding-v4；页面截图、图片和扫描件使用 qwen3-vl-embedding。</p>
      <p>每次备份都是 PostgreSQL 与 MinIO 的全量备份，历史备份默认全部保留；恢复后 OpenSearch 会重新构建索引。</p>
    </aside>
  </section>
</template>
