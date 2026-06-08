<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DatabaseZap } from 'lucide-vue-next'
import { login } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const session = await login(username.value.trim(), password.value)
    auth.setSession(session.token, session.user)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="brand-line">
        <span class="brand-mark"><DatabaseZap :size="22" /></span>
        <span>Voyager Docs</span>
      </div>
      <h1>企业文档 AI 检索平台</h1>
      <p>登录后管理文档、配置百炼模型，并用自然语言查找公司资料。</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" size="large" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submit">登录</el-button>
      </el-form>
    </section>
  </main>
</template>
