export interface User {
  id: number
  username: string
  displayName: string
  role: 'ADMIN' | 'USER'
  enabled: boolean
  deleted: boolean
  avatarUrl: string | null
}

export interface ManagedUser extends User {
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'BATCHING' | 'READY' | 'FAILED' | 'DELETED'

export interface DocumentItem {
  id: string
  title: string
  originalFilename: string
  contentType: string | null
  fileSize: number
  status: DocumentStatus
  uploadedById: number
  uploadedBy: string
  uploadedByUsername: string
  uploadedByEnabled: boolean
  inRecycleBin: boolean
  canDelete: boolean
  canReindex: boolean
  folderId: string | null
  folderName: string | null
  previewable: boolean
  createdAt: string
  updatedAt: string
}

export interface DocumentFolder {
  id: string
  name: string
  parentId: string | null
  documentCount: number
}

export interface JobItem {
  id: string
  documentId: string
  documentTitle: string
  status: string
  attemptCount: number
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkerItem {
  workerId: string
  status: string
  currentJobId: string | null
  message: string | null
  startedAt: string
  lastSeenAt: string
  online: boolean
}

export type EmbeddingInvocationMode = 'REALTIME' | 'BATCH'

export interface AiSetting {
  id: number
  provider: string
  region: string
  apiKeyConfigured: boolean
  maskedApiKey: string
  chatModel: string
  textEmbeddingModel: string
  textEmbeddingDimension: number
  multimodalEmbeddingModel: string
  multimodalEmbeddingDimension: number
  rerankModel: string
  multimodalRerankModel: string
  embeddingInvocationMode: EmbeddingInvocationMode
  updatedAt: string
}

export interface SearchHit {
  documentId: string
  title: string
  originalFilename: string
  status: string
  reason: string
  pageNumber: number | null
  score: number
}

export interface SearchResponse {
  query: string
  hits: SearchHit[]
  mode: string
}

export interface ChatSession {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface Citation {
  documentId: string
  title: string
  pageNumber: number | null
  snippet: string
}

export interface ChatMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: Citation[]
  createdAt: string
}

export interface AuditEvent {
  id: string
  actorUserId: number | null
  actorUsername: string | null
  actorRole: string | null
  ipAddress: string | null
  userAgent: string | null
  action: string
  resourceType: string | null
  resourceId: string | null
  success: boolean
  summary: string | null
  createdAt: string
}

export interface AuditEventPage {
  items: AuditEvent[]
  total: number
  page: number
  size: number
}

export interface BackupSettings {
  id: number
  enabled: boolean
  dailyTime: string
  updatedAt: string | null
}

export interface BackupRun {
  id: string
  status: string
  triggerType: string
  startedById: number | null
  startedBy: string | null
  backupFilename: string | null
  fileSize: number
  sha256: string | null
  objectCount: number
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
  createdAt: string
}

export interface BackupList {
  items: BackupRun[]
  totalCount: number
  totalBytes: number
}

export interface MaintenanceStatus {
  enabled: boolean
  reason: string | null
  startedAt: string | null
  startedById: number | null
  startedBy: string | null
}
