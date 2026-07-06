<template>
  <div id="app-container">
    <!-- 顶部导航栏：系统名称 + 副标题 + 汉堡菜单 -->
    <header class="app-header">
      <div class="header-left">
        <h1>个性化学习多智能体系统</h1>
        <span class="header-subtitle">Multi-Agent Personalized Learning</span>
      </div>
      <div class="header-right">
        <UserMenu
          v-if="authStore.isLoggedIn"
          @account-manage="showAccountModal = true"
          @delete-account="showDeleteDialog = true"
          @logout="showLogoutDialog = true"
        />
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="app-main">
      <ChatView />
    </main>

    <!-- Live2D 看板娘 -->
    <Live2DWidget v-if="authStore.isLoggedIn" />

    <!-- 登录/注册模态框 -->
    <AuthModal
      v-model="showAuth"
      @logged-in="onLoggedIn"
    />



    <!-- 账号管理模态框 -->
    <AccountModal
      v-model="showAccountModal"
      @close="showAccountModal = false"
    />

    <!-- 注销账号确认弹窗 -->
    <el-dialog
      v-model="showDeleteDialog"
      :align-center="true"
      :show-close="true"
      :close-on-click-modal="true"
      :close-on-press-escape="false"
      :lock-scroll="true"
      width="420px"
      modal-class="plan-expand-overlay"
      @closed="deletePassword = ''; deleteError = ''"
    >
      <h2 class="acct-title">注销账号</h2>
      <div class="delete-dialog-content">
        <p class="delete-warning">⚠️ 此操作不可撤销，将永久删除以下数据：</p>
        <ul class="delete-list">
          <li>你的用户账户</li>
          <li>所有对话记录</li>
          <li>学习画像数据</li>
        </ul>
        <div class="acct-field">
          <label class="acct-label">请输入密码以确认注销</label>
          <el-input
            v-model="deletePassword"
            type="password"
            size="large"
            show-password
            placeholder="请输入密码"
            autocomplete="new-password"
            @keyup.enter="handleDeleteAccount"
          />
        </div>
      </div>
      <p v-if="deleteError" class="error-msg">{{ deleteError }}</p>
      <div class="acct-footer">
        <el-button size="large" class="cancel-btn" @click="showDeleteDialog = false">取消</el-button>
        <button class="submit-btn delete-submit-btn" :disabled="deleting" @click="handleDeleteAccount">
          {{ deleting ? '注销中...' : '确认注销' }}
        </button>
      </div>
    </el-dialog>

    <!-- 退出登录确认弹窗 -->
    <el-dialog
      v-model="showLogoutDialog"
      :align-center="true"
      :show-close="true"
      :close-on-click-modal="true"
      :lock-scroll="true"
      width="420px"
      modal-class="plan-expand-overlay"
    >
      <h2 class="acct-title">退出登录</h2>
      <p style="text-align:center; color:#7A6A60; font-size:14px; margin:0;">确定要退出当前账号吗？</p>
      <div class="acct-footer">
        <el-button size="large" class="cancel-btn" @click="showLogoutDialog = false">取消</el-button>
        <button class="submit-btn" @click="handleLogout">确认退出</button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from './stores/authStore'
import { deleteAccountApi } from './services/api'
import type { AuthUser } from './services/api'
import ChatView from './views/ChatView.vue'
import Live2DWidget from './components/Live2DWidget.vue'
import AuthModal from './components/AuthModal.vue'
import UserMenu from './components/UserMenu.vue'
import AccountModal from './components/AccountModal.vue'

const authStore = useAuthStore()
const showAccountModal = ref(false)
const showAuth = ref(!authStore.isLoggedIn)

// 退出登录确认
const showLogoutDialog = ref(false)

function handleLogout() {
  showLogoutDialog.value = false
  authStore.logout()
  window.location.reload()
}

// 注销账号
const showDeleteDialog = ref(false)
const deletePassword = ref('')
const deleteError = ref('')
const deleting = ref(false)

async function handleDeleteAccount() {
  if (!deletePassword.value.trim()) {
    deleteError.value = '请输入密码'
    return
  }
  const userId = authStore.user?.id
  if (!userId) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteAccountApi(userId, deletePassword.value)
    showDeleteDialog.value = false
    authStore.logout()
    window.location.reload()
  } catch (err: unknown) {
    deleteError.value = err instanceof Error ? err.message : '注销失败'
  } finally {
    deleting.value = false
  }
}

function onLoggedIn(user: AuthUser) {
  authStore.setUser(user)
  showAuth.value = false
}
</script>

<style>
/* ===== 浅色主题变量 ===== */
:root {
  --bg-primary: #F8F5F1;
  --bg-secondary: rgba(248, 245, 241, 0.45);
  --bg-card: rgba(255, 255, 255, 0.55);
  --bg-input: rgba(255, 255, 255, 0.55);
  --bg-hover: var(--accent-hover);
  --bg-plan: rgba(255, 255, 255, 0.4);
  --text-primary: #3D4255;
  --text-secondary: #7A6A60;
  --text-muted: #8A7565;
  --text-faint: #A09080;
  --text-placeholder: #B8AFA5;
  --border-light: rgba(255, 255, 255, 0.35);
  --border-solid: #E0DCD6;
  --shadow-card: 0 8px 32px rgba(0, 0, 0, 0.06);
  --shadow-bubble: 0 4px 20px rgba(0, 0, 0, 0.06);
  --ai-bubble-bg: rgba(255, 255, 255, 0.55);
  --ai-bubble-border: rgba(255, 255, 255, 0.4);
  --code-bg: #282c34;
  --code-text: #abb2bf;
  --scrollbar-thumb: #D5CFC8;
  --danger: #E74C3C;
  --success: #67c23a;
  --accent: #D4916F;
  --accent-dark: #B87858;
  --accent-glow: rgba(212, 145, 111, 0.35);
  --accent-hover: rgba(212, 145, 111, 0.1);
  --live2d-text: rgba(212, 145, 111, 0.06);
}

/* ===== 深色主题变量（灰底微暖调） ===== */
[data-theme="dark"] {
  --bg-primary: #352e28;
  --bg-secondary: rgba(53, 46, 40, 0.6);
  --bg-card: rgba(65, 55, 48, 0.5);
  --bg-input: rgba(65, 55, 48, 0.6);
  --bg-hover: var(--accent-hover);
  --bg-plan: rgba(65, 55, 48, 0.4);
  --text-primary: #ece6e0;
  --text-secondary: #b8aea5;
  --text-muted: #988e85;
  --text-faint: #78706a;
  --text-placeholder: #5a5450;
  --border-light: rgba(255, 255, 255, 0.08);
  --border-solid: rgba(255, 255, 255, 0.12);
  --shadow-card: 0 8px 32px rgba(0, 0, 0, 0.3);
  --shadow-bubble: 0 4px 20px rgba(0, 0, 0, 0.3);
  --ai-bubble-bg: rgba(58, 52, 48, 0.6);
  --ai-bubble-border: rgba(255, 255, 255, 0.08);
  --code-bg: #201c1a;
  --code-text: #d0c8c0;
  --scrollbar-thumb: #4a4440;
  --danger: #ff6b6b;
  --success: #67c23a;
  --accent: #d4916f;
  --accent-dark: #b87858;
  --accent-glow: rgba(212, 145, 111, 0.3);
  --accent-hover: rgba(212, 145, 111, 0.12);
  --live2d-text: rgba(212, 145, 111, 0.025);
}

/* ===== 全局样式重置 ===== */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Mi Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Apple Color Emoji', 'Segoe UI Emoji', sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
  transition: background 0.3s ease, color 0.3s ease;
}

/* 移除 Chrome 自动填充的蓝色底纹 */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus,
input:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 30px var(--bg-primary) inset !important;
  box-shadow: 0 0 0 30px var(--bg-primary) inset !important;
  -webkit-text-fill-color: var(--text-primary) !important;
  transition: background-color 5000s ease-in-out 0s;
}

/* ===== 全屏布局 ===== */
#app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: transparent;
}

/* ===== 顶部标题栏 —— 毛玻璃 ===== */
.app-header {
  position: relative;
  z-index: 100;
  background: var(--accent);
  backdrop-filter: blur(24px) saturate(1.4);
  -webkit-backdrop-filter: blur(24px) saturate(1.4);
  color: white;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
}

.app-header h1 {
  font-size: 20px;
  font-weight: 600;
}

.header-subtitle {
  font-size: 13px;
  opacity: 0.8;
}

/* 主区域填满剩余高度 */
.app-main {
  flex: 1;
  overflow: hidden;
}


/* ===== 注销弹窗样式（与账号管理模态框统一） ===== */
.acct-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.delete-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.delete-warning {
  color: var(--danger);
  font-weight: 600;
  font-size: 14px;
}

.delete-list {
  margin: 0 0 4px 20px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.acct-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.acct-label {
  font-size: 13px;
  color: var(--text-faint);
}

.error-msg {
  color: var(--danger);
  font-size: 13px;
  text-align: center;
  margin-top: 12px;
}

.acct-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

.cancel-btn {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-solid) !important;
  color: var(--text-muted) !important;
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
  background: var(--accent);
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

.delete-submit-btn {
  background: var(--danger);
}

</style>
