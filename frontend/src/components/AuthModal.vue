<template>
  <el-dialog
    v-model="visible"
    :align-center="true"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :lock-scroll="true"
    width="420px"
    modal-class="plan-expand-overlay"
  >
    <!-- ===== 登录模式 ===== -->
    <template v-if="mode === 'login'">
      <h2 class="auth-title">登录</h2>
      <div class="auth-form">
        <el-input
          v-model="username"
          placeholder="用户名"
          size="large"
          clearable
        />
        <el-input
          v-model="password"
          type="password"
          placeholder="密码"
          size="large"
          show-password
          @keydown.enter="handleLogin"
        />
        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
      </div>
      <div class="auth-footer">
        <el-button class="alt-btn" size="large" @click="switchToRegister">注册</el-button>
        <button class="submit-btn" :disabled="loading" @click="handleLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>
    </template>

    <!-- ===== 注册模式 ===== -->
    <template v-else>
      <h2 class="auth-title">注册</h2>
      <div class="auth-form">
        <el-input
          v-model="username"
          placeholder="用户名"
          size="large"
          clearable
        />
        <el-input
          v-model="password"
          type="password"
          placeholder="密码"
          size="large"
          show-password
        />
        <el-input
          v-model="confirmPassword"
          type="password"
          placeholder="确认密码"
          size="large"
          show-password
          @keydown.enter="handleRegister"
        />
        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
      </div>
      <div class="auth-footer">
        <el-button class="alt-btn" size="large" @click="switchToLogin">返回</el-button>
        <button class="submit-btn" :disabled="loading" @click="handleRegister">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { loginApi, registerApi } from '../services/api'
import type { AuthUser } from '../services/api'

const emit = defineEmits<{
  (e: 'logged-in', user: AuthUser): void
}>()

const visible = defineModel<boolean>({ required: true })

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMsg = ref('')
const loading = ref(false)

function switchToRegister() {
  mode.value = 'register'
  errorMsg.value = ''
}

function switchToLogin() {
  mode.value = 'login'
  errorMsg.value = ''
}

async function handleLogin() {
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const user = await loginApi(username.value.trim(), password.value)
    visible.value = false
    emit('logged-in', user)
  } catch (e: any) {
    errorMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMsg.value = '两次密码不一致'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const user = await registerApi(username.value.trim(), password.value)
    visible.value = false
    emit('logged-in', user)
  } catch (e: any) {
    errorMsg.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
:deep(.el-dialog__header) {
  display: none;
}

.auth-title {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: #7A6A60;
  margin-bottom: 28px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.error-msg {
  color: #e57373;
  font-size: 13px;
  text-align: center;
}

.auth-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
}

.alt-btn {
  background: #fff !important;
  border: 1px solid #D8D0C8 !important;
  color: #8A7565 !important;
  border-radius: 12px !important;
  padding: 8px 24px !important;
  font-size: 14px !important;
  height: 40px !important;
}

.submit-btn {
  padding: 8px 24px;
  height: 40px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #D4916F, #B87858);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.submit-btn:not(:disabled):hover {
  opacity: 0.85;
}
</style>
