<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MessageSquarePlus, SendHorizonal, Trash2 } from 'lucide-vue-next'
import { createChatSession, deleteChatSession, listChatMessages, listChatSessions, sendChatMessage } from '../api'
import type { ChatMessage, ChatSession } from '../types'
import { formatDate } from '../format'

type ChatViewMessage = ChatMessage & {
  pending?: boolean
  failed?: boolean
}

const sessions = ref<ChatSession[]>([])
const activeSession = ref<ChatSession | null>(null)
const messages = ref<ChatViewMessage[]>([])
const input = ref('')
const loading = ref(false)
const sending = ref(false)
const deletingSessionId = ref('')
const scrollRef = ref<HTMLElement | null>(null)

onMounted(init)

async function init() {
  loading.value = true
  try {
    sessions.value = await listChatSessions()
    if (!sessions.value.length) {
      const session = await createChatSession()
      sessions.value = [session]
    }
    await selectSession(sessions.value[0])
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载会话失败')
  } finally {
    loading.value = false
  }
}

async function newSession() {
  const session = await createChatSession()
  sessions.value.unshift(session)
  await selectSession(session)
}

async function selectSession(session: ChatSession) {
  activeSession.value = session
  messages.value = await listChatMessages(session.id)
  await scrollBottom()
}

async function removeSession(session: ChatSession) {
  await ElMessageBox.confirm(`确认删除会话“${session.title}”？`, '删除会话', { type: 'warning' })
  deletingSessionId.value = session.id
  try {
    await deleteChatSession(session.id)
    sessions.value = sessions.value.filter((item) => item.id !== session.id)
    if (activeSession.value?.id === session.id) {
      activeSession.value = null
      messages.value = []
      if (!sessions.value.length) {
        const next = await createChatSession()
        sessions.value = [next]
      }
      await selectSession(sessions.value[0])
    }
    ElMessage.success('会话已删除')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除会话失败')
  } finally {
    deletingSessionId.value = ''
  }
}

async function send() {
  if (!activeSession.value || !input.value.trim() || sending.value) return
  const content = input.value.trim()
  const temporaryUserMessage: ChatViewMessage = {
    id: `local-user-${Date.now()}`,
    role: 'USER',
    content,
    citations: [],
    createdAt: new Date().toISOString(),
    pending: true,
  }
  const waitingMessage: ChatViewMessage = {
    id: `local-assistant-${Date.now()}`,
    role: 'ASSISTANT',
    content: '正在查找相关文档...',
    citations: [],
    createdAt: new Date().toISOString(),
    pending: true,
  }
  input.value = ''
  messages.value.push(temporaryUserMessage, waitingMessage)
  await scrollBottom()
  sending.value = true
  try {
    const response = await sendChatMessage(activeSession.value.id, content)
    messages.value = messages.value.map((message) => {
      if (message.id === temporaryUserMessage.id) return response.userMessage
      if (message.id === waitingMessage.id) return response.assistantMessage
      return message
    })
    await scrollBottom()
  } catch (error: any) {
    messages.value = messages.value
      .filter((message) => message.id !== waitingMessage.id)
      .map((message) =>
        message.id === temporaryUserMessage.id ? { ...message, pending: false, failed: true } : message,
      )
    ElMessage.error(error.response?.data?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

async function scrollBottom() {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}
</script>

<template>
  <section class="chat-layout" v-loading="loading">
    <aside class="panel session-panel">
      <div class="panel-header">
        <h2>会话</h2>
        <el-button size="small" title="新建会话" aria-label="新建会话" @click="newSession">
          <MessageSquarePlus :size="15" />
        </el-button>
      </div>
      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-row session-row-shell"
        :class="{ active: activeSession?.id === session.id }"
      >
        <button class="session-main" @click="selectSession(session)">
          <strong>{{ session.title }}</strong>
          <span>{{ formatDate(session.updatedAt) }}</span>
        </button>
        <el-button
          text
          size="small"
          type="danger"
          title="删除会话"
          :loading="deletingSessionId === session.id"
          @click="removeSession(session)"
        >
          <Trash2 :size="14" />
        </el-button>
      </div>
    </aside>

    <section class="panel conversation-panel">
      <div class="message-stream" ref="scrollRef">
        <article
          v-for="message in messages"
          :key="message.id"
          class="message"
          :class="[message.role.toLowerCase(), { pending: message.pending, failed: message.failed }]"
        >
          <div class="message-bubble">
            <p>{{ message.content }}</p>
            <span v-if="message.failed" class="message-status error">发送失败</span>
            <div v-if="message.citations?.length" class="citation-list">
              <div v-for="citation in message.citations" :key="`${citation.documentId}-${citation.pageNumber}`" class="citation">
                <strong>{{ citation.title }}</strong>
                <span v-if="citation.pageNumber">第 {{ citation.pageNumber }} 页</span>
                <p>{{ citation.snippet }}</p>
              </div>
            </div>
          </div>
        </article>
        <el-empty v-if="!messages.length" description="暂无消息" />
      </div>
      <form class="chat-composer" @submit.prevent="send">
        <textarea v-model="input" placeholder="输入你想查找的文档内容或问题" />
        <el-button type="primary" :loading="sending" native-type="submit">
          <SendHorizonal :size="17" />
          发送
        </el-button>
      </form>
    </section>
  </section>
</template>
