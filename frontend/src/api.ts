import axios from 'axios'
import type {
  AiSetting,
  AuditEventPage,
  BackupList,
  BackupRun,
  BackupSettings,
  ChatMessage,
  ChatSession,
  DocumentFolder,
  DocumentItem,
  JobItem,
  ManagedUser,
  MaintenanceStatus,
  SearchResponse,
  User,
  WorkerItem,
} from './types'

export const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('voyager-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('voyager-token')
      localStorage.removeItem('voyager-user')
    }
    return Promise.reject(error)
  },
)

export async function login(username: string, password: string) {
  const { data } = await api.post<{ token: string; user: User }>('/auth/login', { username, password })
  return data
}

export async function me() {
  const { data } = await api.get<User>('/auth/me')
  return data
}

export async function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.put<User>('/account/avatar', form)
  return data
}

export async function getAvatarBlob(userId: number) {
  const { data } = await api.get<Blob>(`/account/avatar/${userId}`, { responseType: 'blob' })
  return data
}

export async function listDocuments() {
  const { data } = await api.get<DocumentItem[]>('/documents')
  return data
}

export async function listFolders() {
  const { data } = await api.get<DocumentFolder[]>('/folders')
  return data
}

export async function createFolder(payload: { name: string; parentId?: string | null }) {
  const { data } = await api.post<DocumentFolder>('/folders', payload)
  return data
}

export async function updateFolder(id: string, payload: { name: string }) {
  const { data } = await api.put<DocumentFolder>(`/folders/${id}`, payload)
  return data
}

export async function deleteFolder(id: string) {
  await api.delete(`/folders/${id}`)
}

export async function uploadDocument(file: File, title: string, folderId?: string | null) {
  const form = new FormData()
  form.append('file', file)
  if (title.trim()) form.append('title', title.trim())
  if (folderId) form.append('folderId', folderId)
  const { data } = await api.post<DocumentItem>('/documents', form)
  return data
}

export async function reindexDocument(id: string) {
  const { data } = await api.post(`/documents/${id}/reindex`)
  return data
}

export async function deleteDocument(id: string) {
  await api.delete(`/documents/${id}`)
}

export async function downloadDocument(id: string) {
  const response = await api.get<Blob>(`/documents/${id}/download`, { responseType: 'blob' })
  return {
    blob: response.data,
    filename: filenameFromDisposition(response.headers['content-disposition']),
  }
}

export async function previewDocument(id: string) {
  const { data } = await api.get<Blob>(`/documents/${id}/preview`, { responseType: 'blob' })
  return data
}

export async function moveDocument(id: string, folderId: string | null) {
  const { data } = await api.patch<DocumentItem>(`/documents/${id}/folder`, { folderId })
  return data
}

export async function listRecycleBinDocuments() {
  const { data } = await api.get<DocumentItem[]>('/admin/recycle-bin/documents')
  return data
}

export async function restoreRecycleBinDocument(id: string) {
  const { data } = await api.post(`/admin/recycle-bin/documents/${id}/restore`)
  return data
}

export async function permanentlyDeleteRecycleBinDocument(id: string) {
  await api.delete(`/admin/recycle-bin/documents/${id}`)
}

export async function titleSearch(query: string) {
  const { data } = await api.get<DocumentItem[]>('/search/title', { params: { q: query } })
  return data
}

export async function aiSearch(query: string, limit = 8) {
  const { data } = await api.post<SearchResponse>('/search/ai', { query, limit })
  return data
}

export async function listJobs() {
  const { data } = await api.get<JobItem[]>('/jobs')
  return data
}

export async function listWorkers() {
  const { data } = await api.get<WorkerItem[]>('/jobs/workers')
  return data
}

export async function retryJob(id: string) {
  const { data } = await api.post<JobItem>(`/jobs/${id}/retry`)
  return data
}

export async function getAiSetting() {
  const { data } = await api.get<AiSetting>('/admin/ai-settings')
  return data
}

export async function updateAiSetting(payload: Partial<AiSetting> & { apiKey?: string }) {
  const { data } = await api.put<AiSetting>('/admin/ai-settings', payload)
  return data
}

export async function testAiSetting() {
  const { data } = await api.post<{ ok: boolean; message: string }>('/admin/ai-settings/test')
  return data
}

export async function getBackupSettings() {
  const { data } = await api.get<BackupSettings>('/admin/backups/settings')
  return data
}

export async function updateBackupSettings(payload: { enabled: boolean; dailyTime: string }) {
  const { data } = await api.put<BackupSettings>('/admin/backups/settings', payload)
  return data
}

export async function listBackups() {
  const { data } = await api.get<BackupList>('/admin/backups')
  return data
}

export async function createBackupNow() {
  const { data } = await api.post<BackupRun>('/admin/backups')
  return data
}

export async function restoreBackup(id: string, currentPassword: string) {
  const { data } = await api.post<BackupRun>(`/admin/backups/${id}/restore`, { currentPassword })
  return data
}

export async function deleteBackup(id: string) {
  await api.delete(`/admin/backups/${id}`)
}

export async function getMaintenanceStatus() {
  const { data } = await api.get<MaintenanceStatus>('/admin/system/maintenance')
  return data
}

export async function listAuditEvents(params: {
  actor?: string
  action?: string
  success?: boolean | null
  page?: number
  size?: number
}) {
  const { data } = await api.get<AuditEventPage>('/admin/audit-events', { params })
  return data
}

export async function updateAdminProfile(payload: {
  username: string
  displayName: string
  currentPassword?: string
  newPassword?: string
}) {
  const { data } = await api.put<{ token: string; user: User }>('/admin/profile', payload)
  return data
}

export async function listManagedUsers() {
  const { data } = await api.get<ManagedUser[]>('/admin/users')
  return data
}

export async function createManagedUser(payload: { username: string; displayName: string; password: string }) {
  const { data } = await api.post<ManagedUser>('/admin/users', payload)
  return data
}

export async function updateManagedUser(
  id: number,
  payload: { username: string; displayName: string; password?: string; enabled: boolean },
) {
  const { data } = await api.put<ManagedUser>(`/admin/users/${id}`, payload)
  return data
}

export async function setManagedUserEnabled(id: number, enabled: boolean) {
  const { data } = await api.patch<ManagedUser>(`/admin/users/${id}/enabled`, { enabled })
  return data
}

export async function deleteManagedUser(id: number) {
  await api.delete(`/admin/users/${id}`)
}

export async function listChatSessions() {
  const { data } = await api.get<ChatSession[]>('/chat/sessions')
  return data
}

export async function createChatSession(title = '新的文档对话') {
  const { data } = await api.post<ChatSession>('/chat/sessions', { title })
  return data
}

export async function listChatMessages(sessionId: string) {
  const { data } = await api.get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`)
  return data
}

export async function sendChatMessage(sessionId: string, content: string) {
  const { data } = await api.post<{ userMessage: ChatMessage; assistantMessage: ChatMessage }>(
    `/chat/sessions/${sessionId}/messages`,
    { content },
  )
  return data
}

export async function deleteChatSession(sessionId: string) {
  await api.delete(`/chat/sessions/${sessionId}`)
}

function filenameFromDisposition(disposition: string | undefined) {
  if (!disposition) return ''
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      return utf8Match[1]
    }
  }
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1] || ''
}
