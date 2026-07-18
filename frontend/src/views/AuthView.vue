<template>
  <main class="auth-page">
    <a class="auth-brand" href="#" aria-label="玛丽学习助手首页" @click.prevent>
      <span class="brand-mark"><UiIcon name="sparkles" /></span>
      <span>MARIE</span>
    </a>

    <section class="auth-content" aria-labelledby="auth-title">
      <div class="auth-mode-switch" role="tablist" aria-label="登录或注册">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          :class="{ active: mode === 'login' }"
          @click="switchToLogin"
        >登录</button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          :class="{ active: mode === 'register' }"
          @click="switchToRegister"
        >注册</button>
      </div>

      <header class="auth-heading">
        <h1 id="auth-title">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h1>
        <p>{{ mode === 'login' ? '继续你的学习旅程' : '开始建立专属于你的学习空间' }}</p>
      </header>

      <form class="auth-form" @submit.prevent="mode === 'login' ? handleLogin() : handleRegister()">
        <label class="auth-field">
          <span>用户名</span>
          <el-input
            v-model="username"
            placeholder="请输入用户名"
            size="large"
            clearable
            autocomplete="username"
          />
        </label>

        <label class="auth-field">
          <span>密码</span>
          <el-input
            v-model="password"
            type="password"
            :placeholder="mode === 'login' ? '请输入密码' : '至少 8 个字符'"
            size="large"
            show-password
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          />
        </label>

        <label v-if="mode === 'register'" class="auth-field">
          <span>确认密码</span>
          <el-input
            v-model="confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
            show-password
            autocomplete="new-password"
          />
        </label>

        <p v-if="errorMsg" class="auth-error" role="alert">{{ errorMsg }}</p>

        <button class="auth-submit" type="submit" :disabled="loading">
          <span>{{ loading ? (mode === 'login' ? '登录中...' : '注册中...') : (mode === 'login' ? '登录' : '创建账号') }}</span>
          <UiIcon v-if="!loading" name="send" />
        </button>
      </form>

      <p class="auth-switch-copy">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <button type="button" @click="mode === 'login' ? switchToRegister() : switchToLogin()">
          {{ mode === 'login' ? '立即注册' : '返回登录' }}
        </button>
      </p>
    </section>

    <p class="auth-note">对话、学习画像与计划将安全保存在你的账号中</p>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { loginApi, registerApi } from '../services/api'
import type { AuthUser } from '../services/api'
import UiIcon from '../components/UiIcon.vue'

const emit = defineEmits<{
  (e: 'logged-in', user: AuthUser): void
}>()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMsg = ref('')
const loading = ref(false)

function switchToRegister() {
  mode.value = 'register'
  errorMsg.value = ''
  confirmPassword.value = ''
}

function switchToLogin() {
  mode.value = 'login'
  errorMsg.value = ''
  confirmPassword.value = ''
}

async function handleLogin() {
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    emit('logged-in', await loginApi(username.value.trim(), password.value))
  } catch (error: unknown) {
    errorMsg.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  if (password.value.length < 8) {
    errorMsg.value = '密码至少需要 8 个字符'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMsg.value = '两次密码不一致'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    emit('logged-in', await registerApi(username.value.trim(), password.value))
  } catch (error: unknown) {
    errorMsg.value = error instanceof Error ? error.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  position: relative;
  display: grid;
  min-height: 100dvh;
  grid-template-rows: auto 1fr auto;
  padding: 34px clamp(24px, 5vw, 72px) 26px;
  overflow: hidden;
  color: var(--text-primary);
  background:
    radial-gradient(circle at 50% 42%, var(--accent-hover), transparent 34%),
    var(--bg-primary);
}

.auth-brand {
  display: inline-flex;
  width: max-content;
  align-items: center;
  gap: 10px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .16em;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--accent) 32%, transparent);
  border-radius: 10px;
  color: var(--accent);
}

.brand-mark .ui-icon { width: 15px; height: 15px; }

.auth-content {
  align-self: center;
  width: min(100%, 360px);
  margin: 28px auto 38px;
}

.auth-mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 38px;
  border-bottom: 1px solid var(--border-solid);
}

.auth-mode-switch button {
  position: relative;
  height: 40px;
  border: 0;
  color: var(--text-faint);
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.auth-mode-switch button::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 1px;
  background: transparent;
}

.auth-mode-switch button.active {
  color: var(--text-primary);
  font-weight: 600;
}

.auth-mode-switch button.active::after { background: var(--accent); }

.auth-heading { margin-bottom: 30px; text-align: center; }
.auth-heading h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(25px, 3vw, 30px);
  font-weight: 650;
  letter-spacing: -.04em;
}
.auth-heading p {
  margin: 10px 0 0;
  color: var(--text-faint);
  font-size: 13px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 17px;
}

.auth-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.auth-field > span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.auth-field :deep(.el-input__wrapper) {
  min-height: 46px;
  padding-inline: 14px;
  border: 1px solid var(--border-solid);
  border-radius: 11px !important;
  background: var(--bg-input) !important;
  box-shadow: none !important;
  transition: border-color .18s ease, background .18s ease;
}

.auth-field :deep(.el-input__wrapper:hover),
.auth-field :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--accent) 68%, var(--border-solid));
}

.auth-error {
  margin: -2px 0 0;
  color: var(--danger);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.auth-submit {
  display: flex;
  width: 100%;
  height: 46px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin-top: 3px;
  border: 0;
  border-radius: 11px;
  color: #fff;
  background: var(--accent);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  transition: background .18s ease, opacity .18s ease;
}

.auth-submit .ui-icon { width: 14px; height: 14px; }
.auth-submit:hover:not(:disabled) { background: var(--accent-dark); }
.auth-submit:disabled { opacity: .55; cursor: not-allowed; }

.auth-switch-copy {
  margin: 20px 0 0;
  color: var(--text-faint);
  font-size: 12px;
  text-align: center;
}

.auth-switch-copy button {
  padding: 0 2px;
  border: 0;
  color: var(--accent-dark);
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
}

.auth-note {
  justify-self: center;
  margin: 0;
  color: var(--text-placeholder);
  font-size: 11px;
  text-align: center;
}

@media (max-width: 520px) {
  .auth-page { padding: 24px 20px 20px; }
  .auth-content { margin-block: 22px 28px; }
  .auth-mode-switch { margin-bottom: 30px; }
}

@media (max-height: 650px) {
  .auth-page { overflow-y: auto; }
  .auth-content { align-self: start; margin-block: 28px; }
  .auth-mode-switch { margin-bottom: 24px; }
  .auth-heading { margin-bottom: 22px; }
}
</style>
