<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { BrainCircuit, Search } from 'lucide-vue-next'
import { aiSearch, titleSearch } from '../api'
import type { DocumentItem, SearchHit } from '../types'
import { formatDate, statusLabel, statusType } from '../format'

const props = withDefaults(defineProps<{ seedQuery?: string }>(), {
  seedQuery: '',
})

const query = ref(props.seedQuery)
const loading = ref(false)
const aiHits = ref<SearchHit[]>([])
const titleHits = ref<DocumentItem[]>([])
const mode = ref('')

watch(
  () => props.seedQuery,
  (value) => {
    if (value) {
      query.value = value
      void run()
    }
  },
)

async function run() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    const [semantic, titleOnly] = await Promise.all([aiSearch(query.value.trim(), 10), titleSearch(query.value.trim())])
    aiHits.value = semantic.hits
    titleHits.value = titleOnly
    mode.value = semantic.mode
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '搜索失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="search-page">
    <div class="panel search-command">
      <div class="command-icon"><BrainCircuit :size="24" /></div>
      <div class="command-main">
        <textarea v-model="query" placeholder="用自然语言描述你要找的文档、表格内容、PPT 图示或扫描件文字" />
        <div class="command-footer">
          <span>AI 搜索会合并全文、文本向量和视觉向量结果</span>
          <el-button type="primary" :loading="loading" @click="run">
            <Search :size="17" />
            搜索
          </el-button>
        </div>
      </div>
    </div>

    <div class="search-results">
      <section class="panel">
        <div class="panel-header">
          <h2>AI 命中</h2>
          <span>{{ mode || '等待搜索' }}</span>
        </div>
        <div class="result-stack" v-loading="loading">
          <article v-for="hit in aiHits" :key="`${hit.documentId}-${hit.pageNumber}-${hit.score}`" class="result-row">
            <div class="result-title">
              <strong>{{ hit.title }}</strong>
              <el-tag :type="statusType(hit.status)" size="small">{{ statusLabel(hit.status) }}</el-tag>
            </div>
            <p>{{ hit.reason || '语义向量命中' }}</p>
            <footer>
              <span>{{ hit.originalFilename }}</span>
              <span v-if="hit.pageNumber">第 {{ hit.pageNumber }} 页</span>
              <span>score {{ hit.score.toFixed(2) }}</span>
            </footer>
          </article>
          <el-empty v-if="!aiHits.length && !loading" description="暂无 AI 结果" />
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <h2>传统标题搜索</h2>
          <span>仅匹配文档标题</span>
        </div>
        <el-table :data="titleHits" height="420" class="dense-table">
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="130">
            <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>
