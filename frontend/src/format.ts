export function formatDate(value: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function statusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: '待解析',
    PROCESSING: '解析中',
    BATCHING: '批处理中',
    READY: '可检索',
    FAILED: '失败',
    DELETED: '已删除',
    BATCH_SUBMITTED: 'Batch 已提交',
    SUCCEEDED: '成功',
  }
  return labels[status] || status
}

export function statusType(status: string) {
  const types: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    READY: 'success',
    SUCCEEDED: 'success',
    PROCESSING: 'warning',
    BATCHING: 'warning',
    BATCH_SUBMITTED: 'warning',
    PENDING: 'info',
    FAILED: 'danger',
    DELETED: 'info',
  }
  return types[status] || 'info'
}
