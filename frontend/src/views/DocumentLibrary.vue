<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChevronDown,
  ChevronRight,
  Download,
  Eye,
  File,
  FileImage,
  FileSpreadsheet,
  FileText,
  FileType,
  FileUp,
  Folder,
  FolderOpen,
  MoveRight,
  Pencil,
  Plus,
  RefreshCw,
  RotateCw,
  Trash2,
  UploadCloud,
} from 'lucide-vue-next'
import {
  createFolder,
  deleteDocument,
  deleteFolder,
  downloadDocument,
  listDocuments,
  listFolders,
  moveDocument,
  previewDocument,
  reindexDocument,
  updateFolder,
  uploadDocument,
} from '../api'
import type { DocumentFolder, DocumentItem } from '../types'
import { formatDate, formatSize, statusLabel, statusType } from '../format'
import { useAuthStore } from '../stores/auth'

type FolderSelection = 'ALL' | 'UNFILED' | string
type StatusFilter = 'ALL' | 'READY' | 'PROCESSING'

interface FolderRow extends DocumentFolder {
  depth: number
}

type FileManagerEntry =
  | { entryType: 'FOLDER'; id: string; folder: DocumentFolder }
  | { entryType: 'DOCUMENT'; id: string; document: DocumentItem }

const auth = useAuthStore()
const documents = ref<DocumentItem[]>([])
const folders = ref<DocumentFolder[]>([])
const loading = ref(false)
const uploading = ref(false)
const downloadingId = ref('')
const selectedFiles = ref<File[]>([])
const title = ref('')
const uploadCurrent = ref(0)
const uploadTotal = ref(0)
const dragDepth = ref(0)
const collapsedFolderIds = ref<Set<string>>(new Set())
const selectedFolderId = ref<FolderSelection>('ALL')
const selectedUploadFolderId = ref('')
const statusFilter = ref<StatusFilter>('ALL')
const detailsOpen = ref(false)
const selectedDocument = ref<DocumentItem | null>(null)
const previewUrl = ref('')
const previewLoading = ref(false)
const moveDialogOpen = ref(false)
const moving = ref(false)
const moveTarget = ref<DocumentItem | null>(null)
const moveTargetFolderId = ref('')
const selectedEntries = ref<FileManagerEntry[]>([])
const batchMoveDialogOpen = ref(false)
const batchMoveTargetFolderId = ref('')
const batchWorking = ref(false)
let pollTimer: ReturnType<typeof window.setInterval> | undefined

const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const isDragging = computed(() => dragDepth.value > 0)

const selectedFileLabel = computed(() => {
  if (!selectedFiles.value.length) return '选择文档'
  if (selectedFiles.value.length === 1) return selectedFiles.value[0].name
  return `已选择 ${selectedFiles.value.length} 个文档`
})

const titlePlaceholder = computed(() =>
  selectedFiles.value.length > 1 ? '批量上传时自动使用文件名' : '文档标题（可选）',
)

const uploadButtonText = computed(() => {
  if (uploading.value && uploadTotal.value) return `上传中 ${uploadCurrent.value}/${uploadTotal.value}`
  if (selectedFiles.value.length > 1) return `上传 ${selectedFiles.value.length} 个`
  return '上传'
})

const folderRows = computed<FolderRow[]>(() => {
  const children = new Map<string | null, DocumentFolder[]>()
  for (const folder of folders.value) {
    const key = folder.parentId || null
    children.set(key, [...(children.get(key) || []), folder])
  }
  for (const group of children.values()) {
    group.sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
  }

  const rows: FolderRow[] = []
  const visit = (parentId: string | null, depth: number) => {
    for (const folder of children.get(parentId) || []) {
      rows.push({ ...folder, depth })
      visit(folder.id, depth + 1)
    }
  }
  visit(null, 0)
  return rows
})

const visibleFolderRows = computed<FolderRow[]>(() => {
  const rows: FolderRow[] = []
  const hiddenAncestors = new Set<string>()
  for (const folder of folderRows.value) {
    if (folder.parentId && hiddenAncestors.has(folder.parentId)) {
      hiddenAncestors.add(folder.id)
      continue
    }
    rows.push(folder)
    if (collapsedFolderIds.value.has(folder.id)) {
      hiddenAncestors.add(folder.id)
    }
  }
  return rows
})

const uploadFolderOptions = computed(() => [
  { id: '', label: '未归档' },
  ...folderRows.value.map((folder) => ({
    id: folder.id,
    label: `${'　'.repeat(folder.depth)}${folder.name}`,
  })),
])

const folderFilteredDocuments = computed(() => {
  const filtered = documents.value.filter((document) => {
    if (selectedFolderId.value === 'ALL') return !document.folderId
    if (selectedFolderId.value === 'UNFILED') return !document.folderId
    return document.folderId === selectedFolderId.value
  })
  return [...filtered].sort((left, right) => Number(new Date(right.updatedAt)) - Number(new Date(left.updatedAt)))
})

const displayedDocuments = computed(() => {
  if (statusFilter.value === 'READY') {
    return folderFilteredDocuments.value.filter((item) => item.status === 'READY')
  }
  if (statusFilter.value === 'PROCESSING') {
    return folderFilteredDocuments.value.filter((item) => ['PENDING', 'PROCESSING', 'BATCHING'].includes(item.status))
  }
  return folderFilteredDocuments.value
})

const visibleChildFolders = computed(() => {
  if (selectedFolderId.value === 'UNFILED') return []
  const parentId = selectedFolderId.value === 'ALL' ? null : selectedFolderId.value
  return folders.value
    .filter((folder) => (folder.parentId || null) === parentId)
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
})

const rootEntryCount = computed(() => {
  const rootFolders = folders.value.filter((folder) => !folder.parentId).length
  const rootDocuments = documents.value.filter((document) => !document.folderId).length
  return rootFolders + rootDocuments
})

const metrics = computed(() => ({
  total: visibleChildFolders.value.length + folderFilteredDocuments.value.length,
  ready: folderFilteredDocuments.value.filter((item) => item.status === 'READY').length,
  processing: folderFilteredDocuments.value.filter((item) => ['PENDING', 'PROCESSING', 'BATCHING'].includes(item.status))
    .length,
}))

const displayedEntries = computed<FileManagerEntry[]>(() => [
  ...visibleChildFolders.value.map((folder) => ({
    entryType: 'FOLDER' as const,
    id: `folder-${folder.id}`,
    folder,
  })),
  ...displayedDocuments.value.map((document) => ({
    entryType: 'DOCUMENT' as const,
    id: `document-${document.id}`,
    document,
  })),
])

const selectedEntryIds = computed(() => new Set(selectedEntries.value.map((entry) => entry.id)))

const selectedDocuments = computed(() =>
  selectedEntries.value
    .filter((entry) => entry.entryType === 'DOCUMENT')
    .map((entry) => entry.document),
)

const selectedFolders = computed(() =>
  selectedEntries.value
    .filter((entry) => entry.entryType === 'FOLDER')
    .map((entry) => entry.folder),
)

const selectionSummary = computed(() => {
  const total = selectedEntries.value.length
  if (!total) return '未选择项目'
  const folderCount = selectedFolders.value.length
  const documentCount = selectedDocuments.value.length
  return `已选择 ${total} 项：${folderCount} 个文件夹，${documentCount} 个文档`
})

const canBatchDownload = computed(() => selectedDocuments.value.length > 0)

const canBatchMove = computed(() =>
  selectedDocuments.value.length > 0 && selectedDocuments.value.every((document) => document.canDelete),
)

const canBatchReindex = computed(() => selectedDocuments.value.some((document) => document.canReindex))

const canBatchDelete = computed(() =>
  selectedEntries.value.length > 0
    && selectedDocuments.value.every((document) => document.canDelete)
    && (selectedFolders.value.length === 0 || isAdmin.value),
)

const currentFolderName = computed(() => {
  if (selectedFolderId.value === 'ALL') return '全部文档'
  if (selectedFolderId.value === 'UNFILED') return '未归档'
  return folders.value.find((folder) => folder.id === selectedFolderId.value)?.name || '文件夹'
})

const currentFolderHint = computed(() => {
  if (selectedFolderId.value === 'ALL') return '查看根目录中的文件夹和未归档文档'
  if (selectedFolderId.value === 'UNFILED') return '尚未放入任何文件夹的文档'
  return '当前共享文件夹内的文档'
})

const selectedRealFolder = computed(() =>
  typeof selectedFolderId.value === 'string'
    && selectedFolderId.value !== 'ALL'
    && selectedFolderId.value !== 'UNFILED'
    ? folders.value.find((folder) => folder.id === selectedFolderId.value) || null
    : null,
)

const mobileFolderValue = computed({
  get: () => selectedFolderId.value,
  set: (value: string) => {
    selectedFolderId.value = value
  },
})

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
  revokePreview()
})

watch(selectedFolderId, (value) => {
  if (value === 'UNFILED') {
    selectedUploadFolderId.value = ''
  } else if (value !== 'ALL') {
    selectedUploadFolderId.value = value
  }
})

watch(documents, () => {
  if (!selectedDocument.value) return
  const fresh = documents.value.find((document) => document.id === selectedDocument.value?.id)
  if (fresh) selectedDocument.value = fresh
})

watch([documents, folders, selectedFolderId, statusFilter], () => {
  const entriesById = new Map(displayedEntries.value.map((entry) => [entry.id, entry]))
  selectedEntries.value = selectedEntries.value
    .map((entry) => entriesById.get(entry.id))
    .filter((entry): entry is FileManagerEntry => Boolean(entry))
})

async function refresh(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const [documentItems, folderItems] = await Promise.all([listDocuments(), listFolders()])
    documents.value = documentItems
    folders.value = folderItems
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载文档库失败')
  } finally {
    if (showLoading) loading.value = false
  }
}

function handleVisibilityChange() {
  if (!document.hidden) void refresh(false)
}

function chooseFiles(event: Event) {
  const input = event.target as HTMLInputElement
  addFiles(input.files)
  input.value = ''
}

function addFiles(fileList: FileList | File[] | null | undefined) {
  const incoming = Array.from(fileList || [])
  if (!incoming.length) return
  const known = new Set(selectedFiles.value.map(fileKey))
  const unique = incoming.filter((file) => {
    const key = fileKey(file)
    if (known.has(key)) return false
    known.add(key)
    return true
  })
  if (!unique.length) {
    ElMessage.info('这些文件已经在待上传列表中')
    return
  }
  selectedFiles.value = [...selectedFiles.value, ...unique]
  syncTitleWithSelection()
}

function removeSelectedFile(index: number) {
  selectedFiles.value = selectedFiles.value.filter((_, currentIndex) => currentIndex !== index)
  syncTitleWithSelection()
}

function clearSelectedFiles() {
  selectedFiles.value = []
  title.value = ''
}

function syncTitleWithSelection() {
  if (selectedFiles.value.length === 1) {
    title.value = stripExtension(selectedFiles.value[0].name)
  } else {
    title.value = ''
  }
}

async function upload() {
  if (!selectedFiles.value.length) {
    ElMessage.warning('请选择文件')
    return
  }
  const filesToUpload = [...selectedFiles.value]
  const failedFiles: File[] = []
  let successCount = 0
  uploading.value = true
  uploadTotal.value = filesToUpload.length
  uploadCurrent.value = 0
  try {
    for (const [index, file] of filesToUpload.entries()) {
      uploadCurrent.value = index + 1
      const documentTitle = filesToUpload.length === 1 ? title.value : stripExtension(file.name)
      try {
        await uploadDocument(file, documentTitle, selectedUploadFolderId.value || null)
        successCount += 1
        await refresh(false)
      } catch (error: any) {
        failedFiles.push(file)
        ElMessage.error(`${file.name} 上传失败：${error.response?.data?.message || '请稍后重试'}`)
      }
    }
    selectedFiles.value = failedFiles
    syncTitleWithSelection()
    await refresh(false)
    if (successCount && failedFiles.length) {
      ElMessage.warning(`已上传 ${successCount} 个，${failedFiles.length} 个失败`)
    } else if (successCount) {
      ElMessage.success('上传成功，已进入解析队列')
    } else {
      ElMessage.error('上传失败，请检查文件后重试')
    }
  } finally {
    uploading.value = false
    uploadCurrent.value = 0
    uploadTotal.value = 0
  }
}

function handleDragEnter(event: DragEvent) {
  event.preventDefault()
  dragDepth.value += 1
}

function handleDragOver(event: DragEvent) {
  event.preventDefault()
}

function handleDragLeave(event: DragEvent) {
  event.preventDefault()
  dragDepth.value = Math.max(0, dragDepth.value - 1)
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  dragDepth.value = 0
  addFiles(event.dataTransfer?.files)
}

async function createNewFolder(parentId = selectedRealFolder.value?.id || null) {
  if (!isAdmin.value) return
  try {
    const { value } = await ElMessageBox.prompt('输入文件夹名称', '新建文件夹', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPattern: /^.{1,120}$/,
      inputErrorMessage: '文件夹名称不能为空，且不能超过 120 个字符',
    })
    const folder = await createFolder({ name: value.trim(), parentId })
    await refresh(false)
    selectedFolderId.value = folder.id
    ElMessage.success('文件夹已创建')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '创建文件夹失败')
    }
  }
}

async function renameSelectedFolder() {
  if (!isAdmin.value || !selectedRealFolder.value) return
  const folder = selectedRealFolder.value
  try {
    const { value } = await ElMessageBox.prompt('输入新的文件夹名称', '重命名文件夹', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: folder.name,
      inputPattern: /^.{1,120}$/,
      inputErrorMessage: '文件夹名称不能为空，且不能超过 120 个字符',
    })
    await updateFolder(folder.id, { name: value.trim() })
    await refresh(false)
    ElMessage.success('文件夹已重命名')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '重命名失败')
    }
  }
}

async function removeSelectedFolder() {
  if (!isAdmin.value || !selectedRealFolder.value) return
  const folder = selectedRealFolder.value
  try {
    await ElMessageBox.confirm(`确认删除文件夹“${folder.name}”及其中所有子文件夹和文档？`, '删除文件夹', { type: 'warning' })
    await deleteFolder(folder.id)
    selectedFolderId.value = 'ALL'
    await refresh(false)
    ElMessage.success('文件夹已删除')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '删除文件夹失败')
    }
  }
}

async function removeFolder(folder: DocumentFolder) {
  if (!isAdmin.value) return
  try {
    await ElMessageBox.confirm(`确认删除文件夹“${folder.name}”及其中所有子文件夹和文档？`, '删除文件夹', { type: 'warning' })
    await deleteFolder(folder.id)
    selectedEntries.value = selectedEntries.value.filter((entry) => entry.id !== `folder-${folder.id}`)
    await refresh(false)
    ElMessage.success('文件夹已删除')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '删除文件夹失败')
    }
  }
}

async function download(row: DocumentItem) {
  downloadingId.value = row.id
  try {
    const result = await downloadDocument(row.id)
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = result.filename || row.originalFilename || row.title
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '下载失败')
  } finally {
    downloadingId.value = ''
  }
}

async function reindex(row: DocumentItem) {
  if (!row.canReindex) return
  await reindexDocument(row.id)
  await refresh(false)
  ElMessage.success('已重新加入解析队列')
}

async function remove(row: DocumentItem) {
  if (!row.canDelete) return
  try {
    await ElMessageBox.confirm(`确认删除“${row.title}”？`, '删除文档', { type: 'warning' })
    await deleteDocument(row.id)
    await refresh(false)
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

function openMoveDialog(row: DocumentItem) {
  if (!row.canDelete) return
  moveTarget.value = row
  moveTargetFolderId.value = row.folderId || ''
  moveDialogOpen.value = true
}

function handleSelectionChange(rows: FileManagerEntry[]) {
  selectedEntries.value = rows
}

function toggleMobileSelection(row: FileManagerEntry, checked: boolean) {
  const withoutRow = selectedEntries.value.filter((entry) => entry.id !== row.id)
  selectedEntries.value = checked ? [...withoutRow, row] : withoutRow
}

function toggleMobileSelectionFromCheckbox(row: FileManagerEntry, checked: string | number | boolean) {
  toggleMobileSelection(row, Boolean(checked))
}

async function batchDownload() {
  if (!canBatchDownload.value) return
  batchWorking.value = true
  try {
    for (const document of selectedDocuments.value) {
      await download(document)
    }
  } finally {
    batchWorking.value = false
  }
}

function openBatchMoveDialog() {
  if (!canBatchMove.value) return
  batchMoveTargetFolderId.value = selectedDocuments.value[0]?.folderId || ''
  batchMoveDialogOpen.value = true
}

async function confirmBatchMove() {
  if (!canBatchMove.value) return
  batchWorking.value = true
  try {
    for (const document of selectedDocuments.value) {
      await moveDocument(document.id, batchMoveTargetFolderId.value || null)
    }
    batchMoveDialogOpen.value = false
    selectedEntries.value = []
    await refresh(false)
    ElMessage.success('已移动选中文档')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '批量移动失败')
  } finally {
    batchWorking.value = false
  }
}

async function batchReindex() {
  if (!canBatchReindex.value) return
  batchWorking.value = true
  try {
    for (const document of selectedDocuments.value.filter((item) => item.canReindex)) {
      await reindexDocument(document.id)
    }
    await refresh(false)
    ElMessage.success('已将选中文档加入解析队列')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '批量重建失败')
  } finally {
    batchWorking.value = false
  }
}

async function batchDelete() {
  if (!canBatchDelete.value) return
  const folderCount = selectedFolders.value.length
  const documentCount = selectedDocuments.value.length
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${folderCount} 个文件夹和 ${documentCount} 个文档？文件夹会连同子文件夹和其中的文档一起删除。`,
      '批量删除',
      { type: 'warning' },
    )
    batchWorking.value = true
    for (const document of selectedDocuments.value) {
      await deleteDocument(document.id)
    }
    for (const folder of selectedFolders.value) {
      await deleteFolder(folder.id)
    }
    selectedEntries.value = []
    await refresh(false)
    ElMessage.success('已删除选中项目')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '批量删除失败')
    }
  } finally {
    batchWorking.value = false
  }
}

function createFolderInCurrentLocation() {
  void createNewFolder(selectedRealFolder.value?.id || null)
}

async function confirmMove() {
  if (!moveTarget.value) return
  moving.value = true
  try {
    await moveDocument(moveTarget.value.id, moveTargetFolderId.value || null)
    moveDialogOpen.value = false
    await refresh(false)
    ElMessage.success('文档已移动')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '移动失败')
  } finally {
    moving.value = false
  }
}

async function openDetails(row: DocumentItem) {
  selectedDocument.value = row
  detailsOpen.value = true
  revokePreview()
  if (!row.previewable) return
  previewLoading.value = true
  try {
    const blob = await previewDocument(row.id)
    previewUrl.value = URL.createObjectURL(blob)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function selectFolder(id: FolderSelection) {
  selectedFolderId.value = id
}

function folderHasChildren(folderId: string) {
  return folders.value.some((folder) => folder.parentId === folderId)
}

function isFolderCollapsed(folderId: string) {
  return collapsedFolderIds.value.has(folderId)
}

function toggleFolderCollapse(folderId: string) {
  const next = new Set(collapsedFolderIds.value)
  if (next.has(folderId)) {
    next.delete(folderId)
  } else {
    next.add(folderId)
  }
  collapsedFolderIds.value = next
}

function openEntry(row: FileManagerEntry, _column?: unknown, event?: MouseEvent) {
  const target = event?.target as HTMLElement | null
  if (target?.closest('.el-checkbox, .el-button, button, a, input')) {
    return
  }
  if (row.entryType === 'FOLDER') {
    selectFolder(row.folder.id)
    return
  }
  void openDetails(row.document)
}

function entryTitle(row: FileManagerEntry) {
  return row.entryType === 'FOLDER' ? row.folder.name : row.document.title
}

function entrySubtitle(row: FileManagerEntry) {
  if (row.entryType === 'FOLDER') {
    const childCount = folders.value.filter((folder) => folder.parentId === row.folder.id).length
    const parts = [`${row.folder.documentCount} 个文档`]
    if (childCount) parts.push(`${childCount} 个子文件夹`)
    return parts.join(' · ')
  }
  return `${row.document.originalFilename} · ${formatSize(row.document.fileSize)} · ${row.document.uploadedBy} · ${formatDate(row.document.updatedAt)}`
}

function entryFolderLabel(row: FileManagerEntry) {
  if (row.entryType === 'FOLDER') return '当前目录'
  return row.document.folderName || '未归档'
}

function entryTypeLabel(row: FileManagerEntry) {
  return row.entryType === 'FOLDER' ? '文件夹' : fileTypeLabel(row.document)
}

function entryIcon(row: FileManagerEntry) {
  return row.entryType === 'FOLDER' ? Folder : fileIcon(row.document)
}

function folderCount(folderId: string | null) {
  return documents.value.filter((document) => document.folderId === folderId).length
}

function fileKey(file: File) {
  return `${file.name}-${file.size}-${file.lastModified}`
}

function stripExtension(filename: string) {
  return filename.replace(/\.[^.]+$/, '')
}

function fileIcon(row: DocumentItem) {
  const contentType = (row.contentType || '').toLowerCase()
  const filename = row.originalFilename.toLowerCase()
  if (contentType.startsWith('image/')) return FileImage
  if (contentType.includes('pdf') || filename.endsWith('.pdf')) return FileText
  if (filename.endsWith('.xlsx') || filename.endsWith('.xls') || filename.endsWith('.csv')) return FileSpreadsheet
  if (filename.endsWith('.ppt') || filename.endsWith('.pptx')) return FileType
  if (filename.endsWith('.doc') || filename.endsWith('.docx') || filename.endsWith('.txt')) return FileText
  return File
}

function fileTypeLabel(row: DocumentItem) {
  const contentType = (row.contentType || '').toLowerCase()
  const filename = row.originalFilename.toLowerCase()
  if (contentType.startsWith('image/')) return '图片'
  if (contentType.includes('pdf') || filename.endsWith('.pdf')) return 'PDF'
  if (filename.endsWith('.xlsx') || filename.endsWith('.xls') || filename.endsWith('.csv')) return '表格'
  if (filename.endsWith('.ppt') || filename.endsWith('.pptx')) return '演示文稿'
  if (filename.endsWith('.doc') || filename.endsWith('.docx')) return 'Word'
  if (filename.endsWith('.txt')) return '文本'
  return row.contentType || '未知类型'
}
</script>

<template>
  <section class="document-page file-manager-page">
    <div class="file-manager-layout">
      <aside class="folder-panel">
        <div class="folder-panel-header">
          <div>
            <h2>目录</h2>
            <span>公司共享文件夹</span>
          </div>
          <el-button
            v-if="isAdmin"
            circle
            size="small"
            title="新建文件夹"
            aria-label="新建文件夹"
            @click="createNewFolder(null)"
          >
            <Plus :size="15" />
          </el-button>
        </div>

        <div class="folder-nav">
          <button class="folder-row" :class="{ active: selectedFolderId === 'ALL' }" @click="selectFolder('ALL')">
            <span class="folder-toggle-spacer" />
            <FolderOpen :size="17" />
            <span>全部文档</span>
            <strong>{{ rootEntryCount }}</strong>
          </button>
          <button
            v-for="folder in visibleFolderRows"
            :key="folder.id"
            class="folder-row"
            :class="{ active: selectedFolderId === folder.id }"
            :style="{ paddingLeft: `${12 + (folder.depth + 1) * 18}px` }"
            @click="selectFolder(folder.id)"
          >
            <span
              v-if="folderHasChildren(folder.id)"
              class="folder-toggle"
              :title="isFolderCollapsed(folder.id) ? '展开子文件夹' : '收起子文件夹'"
              @click.stop="toggleFolderCollapse(folder.id)"
            >
              <component :is="isFolderCollapsed(folder.id) ? ChevronRight : ChevronDown" :size="14" />
            </span>
            <span v-else class="folder-toggle-spacer" />
            <Folder :size="17" />
            <span>{{ folder.name }}</span>
            <strong>{{ folderCount(folder.id) }}</strong>
          </button>
          <button
            class="folder-row"
            :class="{ active: selectedFolderId === 'UNFILED' }"
            :style="{ paddingLeft: '30px' }"
            @click="selectFolder('UNFILED')"
          >
            <span class="folder-toggle-spacer" />
            <Folder :size="17" />
            <span>未归档</span>
            <strong>{{ folderCount(null) }}</strong>
          </button>
        </div>
      </aside>

      <main class="file-manager-main">
        <div class="mobile-folder-select">
          <el-select v-model="mobileFolderValue" placeholder="选择目录">
            <el-option label="全部文档" value="ALL" />
            <el-option label="未归档" value="UNFILED" />
            <el-option
              v-for="folder in folderRows"
              :key="folder.id"
              :label="`${'　'.repeat(folder.depth)}${folder.name}`"
              :value="folder.id"
            />
          </el-select>
        </div>

        <section class="upload-strip">
          <label
            class="file-picker"
            :class="{ dragging: isDragging }"
            @dragenter="handleDragEnter"
            @dragover="handleDragOver"
            @dragleave="handleDragLeave"
            @drop="handleDrop"
          >
            <FileUp :size="18" />
            <span>{{ selectedFileLabel }}</span>
            <input type="file" multiple @change="chooseFiles" />
          </label>
          <el-input
            v-model="title"
            :disabled="selectedFiles.length > 1 || uploading"
            :placeholder="titlePlaceholder"
          />
          <el-select v-model="selectedUploadFolderId" clearable placeholder="上传到">
            <el-option
              v-for="option in uploadFolderOptions"
              :key="option.id || 'unfiled'"
              :label="option.label"
              :value="option.id"
            />
          </el-select>
          <el-button type="primary" :loading="uploading" @click="upload">
            <UploadCloud :size="17" />
            {{ uploadButtonText }}
          </el-button>
          <el-button title="刷新文档列表" aria-label="刷新文档列表" @click="refresh()">
            <RefreshCw :size="16" />
          </el-button>
        </section>

        <section v-if="selectedFiles.length" class="pending-upload-list compact-pending-list">
          <div class="pending-upload-header">
            <strong>待上传文档</strong>
            <el-button text size="small" :disabled="uploading" @click="clearSelectedFiles">清空</el-button>
          </div>
          <div v-for="(file, index) in selectedFiles" :key="fileKey(file)" class="pending-upload-item">
            <div>
              <strong>{{ file.name }}</strong>
              <span>{{ formatSize(file.size) }}</span>
            </div>
            <el-button text size="small" type="danger" :disabled="uploading" title="移除文件" @click="removeSelectedFile(index)">
              <Trash2 :size="14" />
            </el-button>
          </div>
        </section>

        <section class="file-command-bar">
          <div class="command-left">
            <el-button v-if="isAdmin" @click="createFolderInCurrentLocation">
              <Plus :size="15" />
              新建文件夹
            </el-button>
            <el-button :disabled="!canBatchDownload || batchWorking" @click="batchDownload">
              <Download :size="15" />
              批量下载
            </el-button>
            <el-button :disabled="!canBatchMove || batchWorking" @click="openBatchMoveDialog">
              <MoveRight :size="15" />
              批量移动
            </el-button>
            <el-button :disabled="!canBatchReindex || batchWorking" @click="batchReindex">
              <RotateCw :size="15" />
              批量重建
            </el-button>
            <el-button type="danger" plain :disabled="!canBatchDelete || batchWorking" @click="batchDelete">
              <Trash2 :size="15" />
              批量删除
            </el-button>
          </div>
          <span>{{ selectionSummary }}</span>
        </section>

        <section class="panel file-list-panel">
          <div class="file-list-toolbar">
            <div>
              <h2>文件列表</h2>
              <span>{{ currentFolderName }} · {{ currentFolderHint }}</span>
            </div>
            <div class="status-filter-chips" aria-label="文档状态筛选">
              <button :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">
                全部 {{ metrics.total }}
              </button>
              <button :class="{ active: statusFilter === 'READY' }" @click="statusFilter = 'READY'">
                可检索 {{ metrics.ready }}
              </button>
              <button :class="{ active: statusFilter === 'PROCESSING' }" @click="statusFilter = 'PROCESSING'">
                处理中 {{ metrics.processing }}
              </button>
            </div>
          </div>

          <el-table
            :data="displayedEntries"
            v-loading="loading"
            row-key="id"
            height="calc(100vh - 340px)"
            class="dense-table document-table file-table"
            highlight-current-row
            @selection-change="handleSelectionChange"
            @row-click="openEntry"
            @row-dblclick="openEntry"
          >
            <el-table-column type="selection" width="42" reserve-selection />
            <el-table-column label="名称" min-width="240">
              <template #default="{ row }">
                <div class="file-name-cell" :class="{ 'folder-entry-cell': row.entryType === 'FOLDER' }">
                  <span class="file-type-icon">
                    <component :is="entryIcon(row)" :size="18" />
                  </span>
                  <div class="file-cell">
                    <strong :title="entryTitle(row)">{{ entryTitle(row) }}</strong>
                    <span :title="entrySubtitle(row)">{{ entrySubtitle(row) }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="目录" width="86">
              <template #default="{ row }">{{ entryFolderLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="72">
              <template #default="{ row }">{{ entryTypeLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <el-tag v-if="row.entryType === 'DOCUMENT'" :type="statusType(row.document.status)" round>
                  {{ statusLabel(row.document.status) }}
                </el-tag>
                <span v-else class="muted-action">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="146" class-name="operation-column">
              <template #default="{ row }">
                <el-button
                  v-if="row.entryType === 'FOLDER'"
                  text
                  size="small"
                  title="打开文件夹"
                  aria-label="打开文件夹"
                  @click.stop="selectFolder(row.folder.id)"
                >
                  <FolderOpen :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'FOLDER' && isAdmin"
                  text
                  size="small"
                  type="danger"
                  title="删除文件夹及其内容"
                  aria-label="删除文件夹及其内容"
                  @click.stop="removeFolder(row.folder)"
                >
                  <Trash2 :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.previewable"
                  text
                  size="small"
                  title="预览"
                  aria-label="预览"
                  @click.stop="openDetails(row.document)"
                >
                  <Eye :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT'"
                  text
                  size="small"
                  :loading="downloadingId === row.document.id"
                  title="下载原文档"
                  aria-label="下载原文档"
                  @click.stop="download(row.document)"
                >
                  <Download :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.canDelete"
                  text
                  size="small"
                  title="移动"
                  @click.stop="openMoveDialog(row.document)"
                >
                  <MoveRight :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.canReindex"
                  text
                  size="small"
                  title="重建索引"
                  @click.stop="reindex(row.document)"
                >
                  <RotateCw :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.canDelete"
                  text
                  size="small"
                  type="danger"
                  title="删除文档"
                  @click.stop="remove(row.document)"
                >
                  <Trash2 :size="14" />
                </el-button>
                <span
                  v-if="row.entryType === 'DOCUMENT' && !row.document.canReindex && !row.document.canDelete"
                  class="muted-action"
                >
                  只读
                </span>
              </template>
            </el-table-column>
          </el-table>

          <div class="document-mobile-list" v-loading="loading">
            <article
              v-for="row in displayedEntries"
              :key="row.id"
              class="document-card-row"
              :class="{ 'folder-card-row': row.entryType === 'FOLDER' }"
              @click="openEntry(row)"
            >
              <div class="document-card-main">
                <div class="mobile-file-title">
                  <el-checkbox
                    :model-value="selectedEntryIds.has(row.id)"
                    @click.stop
                    @change="toggleMobileSelectionFromCheckbox(row, $event)"
                  />
                  <component :is="entryIcon(row)" :size="17" />
                  <strong>{{ entryTitle(row) }}</strong>
                </div>
                <span>{{ entrySubtitle(row) }}</span>
              </div>
              <dl class="document-card-meta">
                <dt>目录</dt>
                <dd>{{ entryFolderLabel(row) }}</dd>
                <dt>状态</dt>
                <dd>
                  <el-tag v-if="row.entryType === 'DOCUMENT'" :type="statusType(row.document.status)" round>
                    {{ statusLabel(row.document.status) }}
                  </el-tag>
                  <span v-else>文件夹</span>
                </dd>
                <dt>上传人</dt>
                <dd>{{ row.entryType === 'DOCUMENT' ? row.document.uploadedBy : '-' }}</dd>
                <dt>更新</dt>
                <dd>{{ row.entryType === 'DOCUMENT' ? formatDate(row.document.updatedAt) : '-' }}</dd>
              </dl>
              <div class="document-card-actions">
                <el-button v-if="row.entryType === 'FOLDER'" text size="small" @click.stop="selectFolder(row.folder.id)">
                  <FolderOpen :size="14" />
                  打开
                </el-button>
                <el-button
                  v-if="row.entryType === 'FOLDER' && isAdmin"
                  text
                  size="small"
                  type="danger"
                  @click.stop="removeFolder(row.folder)"
                >
                  <Trash2 :size="14" />
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT'"
                  text
                  size="small"
                  :loading="downloadingId === row.document.id"
                  @click.stop="download(row.document)"
                >
                  <Download :size="14" />
                  下载
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.canDelete"
                  text
                  size="small"
                  @click.stop="openMoveDialog(row.document)"
                >
                  <MoveRight :size="14" />
                  移动
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.canReindex"
                  text
                  size="small"
                  @click.stop="reindex(row.document)"
                >
                  重建
                </el-button>
                <el-button
                  v-if="row.entryType === 'DOCUMENT' && row.document.canDelete"
                  text
                  size="small"
                  type="danger"
                  title="删除文档"
                  @click.stop="remove(row.document)"
                >
                  <Trash2 :size="14" />
                </el-button>
              </div>
            </article>
            <el-empty v-if="!displayedEntries.length && !loading" description="暂无内容" :image-size="90" />
          </div>
        </section>
      </main>
    </div>

    <el-drawer v-model="detailsOpen" size="420px" class="document-detail-drawer" @closed="revokePreview">
      <template #header>
        <div class="drawer-title">
          <strong>{{ selectedDocument?.title || '文档详情' }}</strong>
          <span>{{ selectedDocument?.folderName || '未归档' }}</span>
        </div>
      </template>

      <div v-if="selectedDocument" class="document-detail">
        <div v-if="selectedDocument.previewable" v-loading="previewLoading" class="image-preview-box">
          <img v-if="previewUrl" :src="previewUrl" :alt="selectedDocument.title" />
          <span v-else>正在加载预览</span>
        </div>
        <div v-else class="file-preview-placeholder">
          <component :is="fileIcon(selectedDocument)" :size="34" />
          <span>此类型暂不支持在线预览，可下载查看原文档。</span>
        </div>

        <dl class="detail-meta">
          <dt>文件名</dt>
          <dd>{{ selectedDocument.originalFilename }}</dd>
          <dt>类型</dt>
          <dd>{{ fileTypeLabel(selectedDocument) }}</dd>
          <dt>大小</dt>
          <dd>{{ formatSize(selectedDocument.fileSize) }}</dd>
          <dt>状态</dt>
          <dd><el-tag :type="statusType(selectedDocument.status)" round>{{ statusLabel(selectedDocument.status) }}</el-tag></dd>
          <dt>上传人</dt>
          <dd>{{ selectedDocument.uploadedBy }}</dd>
          <dt>更新时间</dt>
          <dd>{{ formatDate(selectedDocument.updatedAt) }}</dd>
        </dl>

        <div class="drawer-actions">
          <el-button :loading="downloadingId === selectedDocument.id" @click="download(selectedDocument)">
            <Download :size="15" />
            下载
          </el-button>
          <el-button v-if="selectedDocument.canDelete" @click="openMoveDialog(selectedDocument)">
            <MoveRight :size="15" />
            移动
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="moveDialogOpen" title="移动文档" width="420px">
      <el-select v-model="moveTargetFolderId" class="full-width" clearable placeholder="选择目标文件夹">
        <el-option
          v-for="option in uploadFolderOptions"
          :key="option.id || 'unfiled'"
          :label="option.label"
          :value="option.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="moving" @click="confirmMove">确认移动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchMoveDialogOpen" title="批量移动文档" width="420px">
      <el-select v-model="batchMoveTargetFolderId" class="full-width" clearable placeholder="选择目标文件夹">
        <el-option
          v-for="option in uploadFolderOptions"
          :key="option.id || 'unfiled'"
          :label="option.label"
          :value="option.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="batchMoveDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="batchWorking" @click="confirmBatchMove">确认移动</el-button>
      </template>
    </el-dialog>
  </section>
</template>
