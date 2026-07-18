<template>
  <div id="app-container">
    <!-- 顶部导航栏：系统名称 + 副标题 + 汉堡菜单 -->
    <header class="app-header">
      <div class="header-left">
        <button
          v-if="authStore.isLoggedIn"
          class="header-tab-btn"
          :class="{ active: activeHeaderTab === 'data' }"
          @click="activeHeaderTab = 'data'"
        >学习数据</button>
        <button
          v-if="authStore.isLoggedIn"
          class="header-tab-btn"
          :class="{ active: activeHeaderTab === 'files' }"
          @click="activeHeaderTab = 'files'"
        >历史文件</button>
      </div>
      <h1 v-if="authStore.isLoggedIn" class="current-conversation-title" :title="chatStore.conversationTitle">
        {{ chatStore.conversationTitle }}
      </h1>
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

    <!-- 学习数据 / 历史文件 面板 -->
    <div v-if="authStore.isLoggedIn" class="overlay-panel">
      <div class="dashboard-switch-slot">
        <!-- 学习数据：贡献图 -->
        <div v-if="activeHeaderTab === 'data'" class="contribution-graph">
          <div class="graph-header">
            <span class="graph-title">学习活跃度</span>
            <span class="graph-subtitle">近 16 周</span>
          </div>
          <div class="graph-grid">
            <div
              v-for="(day, i) in contributionData"
              :key="i"
              class="graph-cell"
              :style="{ opacity: day.level === 0 ? 0.15 : 0.3 + day.level * 0.15 }"
              :title="`${day.date}：${day.conversationCount} 次对话，${day.submissionCount} 次成果提交，活跃度 ${day.score}`"
            ></div>
          </div>
          <div class="graph-legend">
            <span>少</span>
            <div class="legend-cell" style="opacity: 0.15"></div>
            <div class="legend-cell" style="opacity: 0.45"></div>
            <div class="legend-cell" style="opacity: 0.6"></div>
            <div class="legend-cell" style="opacity: 0.75"></div>
            <div class="legend-cell" style="opacity: 0.9"></div>
            <span>多</span>
          </div>
        </div>
        <!-- 历史文件 -->
        <div v-else class="file-history">
          <div class="file-header">
            <span class="file-title">历史文件</span>
            <span class="file-subtitle">最近上传</span>
          </div>
          <div v-if="uploadedFiles.length === 0" class="file-empty">暂无上传记录</div>
          <div v-else class="file-list">
            <div v-for="file in uploadedFiles" :key="file.id" class="file-item">
              <UiIcon name="file" />
              <span class="file-name">{{ file.name }}</span>
              <span class="file-time">{{ file.time }}</span>
            </div>
          </div>
        </div>
      </div>

      <section class="conversation-picker">
        <div class="conversation-picker-header">
          <span class="conversation-picker-title">对话</span>
          <button
            class="new-conversation-btn"
            type="button"
            title="新建对话"
            aria-label="新建对话"
            :disabled="chatStore.isStreaming"
            @click="startNewConversation"
          >
            <UiIcon name="new-chat" />
          </button>
        </div>
        <div v-if="conversationSessions.length" class="conversation-list">
          <button
            v-for="session in conversationSessions"
            :key="session.id"
            class="conversation-item"
            :class="{ active: session.id === chatStore.conversationId }"
            type="button"
            :disabled="chatStore.isStreaming"
            @click="selectConversation(session)"
          >
            <span class="conversation-item-title">{{ session.title }}</span>
            <span class="conversation-item-time">{{ session.timeLabel }}</span>
          </button>
        </div>
        <div v-else class="conversation-empty">暂无历史对话</div>
      </section>
    </div>

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
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from './stores/authStore'
import { useChatStore } from './stores/chatStore'
import type { Message } from './stores/chatStore'
import { deleteAccountApi, fetchConversationsApi, fetchCurrentUserApi, fetchLearningActivityApi, fetchUploadedFilesApi, logoutApi } from './services/api'
import type { AuthUser, ConversationRecord, DailyLearningActivity } from './services/api'
import { fallbackConversationTitle, readConversationTitles } from './services/conversationTitles'
import ChatView from './views/ChatView.vue'
import Live2DWidget from './components/Live2DWidget.vue'
import AuthModal from './components/AuthModal.vue'
import UserMenu from './components/UserMenu.vue'
import AccountModal from './components/AccountModal.vue'
import UiIcon from './components/UiIcon.vue'

const authStore = useAuthStore()
const chatStore = useChatStore()
const showAccountModal = ref(false)
const showAuth = ref(!authStore.isLoggedIn)
const HEADER_TAB_KEY = 'edu-agent-header-tab'
const activeHeaderTab = ref<'data' | 'files'>(
  localStorage.getItem(HEADER_TAB_KEY) === 'files' ? 'files' : 'data'
)
watch(activeHeaderTab, value => localStorage.setItem(HEADER_TAB_KEY, value))

// 贡献图数据（16周 = 112天）
type ContributionDay = DailyLearningActivity & { count: number }
const contributionData = ref<ContributionDay[]>([])
const uploadedFiles = ref<{ id: number; name: string; time: string }[]>([])

async function loadContributionData() {
  const userId = authStore.user?.id
  if (!userId) {
    contributionData.value = []
    return
  }
  try {
    const data = await fetchLearningActivityApi(userId, 112)
    contributionData.value = data.map(day => ({
      ...day,
      date: day.date.slice(5).replace('-', '/'),
      count: day.level,
    }))
  } catch (err) {
    console.warn('学习活跃度加载失败', err)
    contributionData.value = []
  }
}

function handleActivityUpdated() {
  void loadContributionData()
}

async function loadUploadedFiles() {
  const userId = authStore.user?.id
  if (!userId) {
    uploadedFiles.value = []
    return
  }
  try {
    const files = await fetchUploadedFilesApi(userId, 50)
    uploadedFiles.value = files.map(file => ({
      id: file.id,
      name: file.fileName,
      time: `${formatConversationTime(new Date(file.uploadedAt).getTime())} · ${file.purpose === 'SUBMISSION' ? '学习成果' : '对话文件'}`,
    }))
  } catch (err) {
    console.warn('历史文件加载失败', err)
    uploadedFiles.value = []
  }
}

function handleFileHistoryUpdated() {
  void loadUploadedFiles()
}

interface ConversationSession {
  id: string
  title: string
  timeLabel: string
  updatedAt: number
  messages: Message[]
}

const conversationSessions = ref<ConversationSession[]>([])
let hasInitializedConversations = false

function formatConversationTime(timestamp: number) {
  const date = new Date(timestamp)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }
  return `${date.getMonth() + 1}/${date.getDate()}`
}

async function loadConversationSessions() {
  const userId = authStore.user?.id
  if (!userId) {
    conversationSessions.value = []
    return
  }
  try {
    const records = await fetchConversationsApi(userId, 300)
    const grouped = new Map<string, ConversationRecord[]>()
    for (const record of records) {
      if (!record.conversationId) continue
      const group = grouped.get(record.conversationId) || []
      group.push(record)
      grouped.set(record.conversationId, group)
    }
    const savedTitles = readConversationTitles(userId)
    const sessions = Array.from(grouped.entries()).map(([id, items]) => {
      const messages: Message[] = items.map(item => ({
        id: item.id.toString(),
        role: item.role as 'user' | 'assistant',
        content: item.content,
        timestamp: new Date(item.timestamp).getTime(),
        ...(item.attachmentType === 'image' && item.attachmentData
          ? { imageUrl: item.attachmentData, imageData: item.attachmentData }
          : {}),
      }))
      const updatedAt = Math.max(...messages.map(message => message.timestamp))
      const persistedTitle = items
        .find(item => item.conversationTitle?.trim() && item.conversationTitle.trim() !== '新对话')
        ?.conversationTitle?.trim()
      return {
        id,
        title: persistedTitle || savedTitles[id] || fallbackConversationTitle(messages),
        timeLabel: formatConversationTime(updatedAt),
        updatedAt,
        messages,
      }
    }).sort((a, b) => b.updatedAt - a.updatedAt)
    conversationSessions.value = sessions

    if (!hasInitializedConversations) {
      hasInitializedConversations = true
      if (sessions.length > 0 && !chatStore.conversationId) {
        selectConversation(sessions[0])
      } else if (sessions.length === 0 && chatStore.messages.length === 0) {
        startNewConversation()
      }
    }
  } catch (err) {
    console.warn('对话列表加载失败', err)
  }
}

function selectConversation(session: ConversationSession) {
  if (chatStore.isStreaming) return
  chatStore.loadConversation(session.id, session.title, session.messages)
  window.dispatchEvent(new CustomEvent('conversation-selected', {
    detail: { conversationId: session.id },
  }))
}

function startNewConversation() {
  if (chatStore.isStreaming) return
  const conversationId = typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID().replace(/-/g, '').slice(0, 12)
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  window.dispatchEvent(new CustomEvent('new-conversation', {
    detail: { conversationId },
  }))
}

function handleConversationListUpdated() {
  void loadConversationSessions()
}

function handleConversationTitleUpdated(event: Event) {
  const detail = (event as CustomEvent<{ conversationId: string; title: string }>).detail
  if (!detail) return
  const session = conversationSessions.value.find(item => item.id === detail.conversationId)
  if (session) session.title = detail.title
}

onMounted(async () => {
  if (authStore.user) {
    try {
      authStore.setUser(await fetchCurrentUserApi())
      showAuth.value = false
    } catch {
      authStore.logout()
      showAuth.value = true
    }
  }
  void loadContributionData()
  void loadUploadedFiles()
  void loadConversationSessions()
  window.addEventListener('learning-activity-updated', handleActivityUpdated)
  window.addEventListener('file-history-updated', handleFileHistoryUpdated)
  window.addEventListener('conversation-list-updated', handleConversationListUpdated)
  window.addEventListener('conversation-title-updated', handleConversationTitleUpdated)
})

onUnmounted(() => {
  window.removeEventListener('learning-activity-updated', handleActivityUpdated)
  window.removeEventListener('file-history-updated', handleFileHistoryUpdated)
  window.removeEventListener('conversation-list-updated', handleConversationListUpdated)
  window.removeEventListener('conversation-title-updated', handleConversationTitleUpdated)
})

// 退出登录确认
const showLogoutDialog = ref(false)

async function handleLogout() {
  showLogoutDialog.value = false
  try { await logoutApi() } catch { /* local logout still completes */ }
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
  hasInitializedConversations = false
  void loadContributionData()
  void loadUploadedFiles()
  void loadConversationSessions()
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
  --bg-primary: #322c28;
  --bg-secondary: rgba(50, 44, 40, 0.6);
  --bg-card: rgba(60, 52, 46, 0.5);
  --bg-input: rgba(60, 52, 46, 0.6);
  --bg-hover: var(--accent-hover);
  --bg-plan: rgba(60, 52, 46, 0.4);
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

/* ===== 顶部标题栏 ===== */
.app-header {
  position: relative;
  z-index: 100;
  background: transparent;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-tab-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  opacity: 0.72;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 8px;
  transition: color 0.2s;
}

.header-tab-btn:hover {
  color: var(--text-secondary);
  opacity: 1;
}

.header-tab-btn.active {
  color: var(--accent);
  font-weight: 700;
  opacity: 1;
}

.header-right {
  display: flex;
  align-items: center;
}

.current-conversation-title {
  position: absolute;
  left: 50%;
  top: 50%;
  width: min(46vw, 620px);
  transform: translate(-50%, -50%);
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}

.app-header h1 {
  font-size: 18px;
  font-weight: 600;
  color: var(--accent);
}
.app-header .current-conversation-title {
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 600;
}

.header-subtitle {
  font-size: 13px;
  color: var(--text-faint);
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

/* ===== 学习数据 / 历史文件 面板 ===== */
.overlay-panel {
  position: fixed;
  top: 64px;
  bottom: auto;
  left: 16px;
  z-index: 1001;
  width: clamp(226px, calc(21vw - 24px), 320px);
  background: var(--bg-primary);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: none;
  border-radius: 14px;
  padding: 14px;
  box-shadow: none;
}
.dashboard-switch-slot {
  height: 144px;
  overflow: visible;
}

/* 贡献图 */
.graph-header, .file-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.graph-title, .file-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
}
.conversation-picker {
  margin-top: 16px;
  padding-top: 10px;
  border-top: none;
}
.conversation-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
}
.conversation-picker-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
}
.new-conversation-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--border-solid);
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--accent);
  cursor: pointer;
  transition: color 0.2s, background 0.2s, border-color 0.2s;
}
.new-conversation-btn .ui-icon {
  width: 17px;
  height: 17px;
}
.new-conversation-btn:hover:not(:disabled) {
  background: var(--bg-hover);
}
.new-conversation-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: clamp(72px, calc(100vh - 689px), 178px);
  padding-right: 2px;
  overflow-y: auto;
}
.conversation-list::-webkit-scrollbar { width: 3px; }
.conversation-list::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
.conversation-item {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-solid);
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.conversation-item:hover:not(:disabled) { background: var(--bg-hover); }
.conversation-item.active {
  background: var(--bg-hover);
  color: var(--text-secondary);
}
.conversation-item:disabled { cursor: not-allowed; }
.conversation-item-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 500;
}
.conversation-item-time {
  flex: 0 0 auto;
  font-size: 10px;
  color: var(--text-faint);
}
.conversation-empty {
  padding: 12px 8px;
  color: var(--text-placeholder);
  font-size: 11px;
  text-align: center;
}
.graph-subtitle, .file-subtitle {
  font-size: 11px;
  color: var(--text-faint);
}
.graph-grid {
  display: grid;
  grid-template-columns: repeat(16, minmax(8px, 10px));
  justify-content: space-between;
  gap: 2px;
}
.graph-cell {
  aspect-ratio: 1;
  background: var(--accent);
  border-radius: 2px;
}
.graph-legend {
  display: flex;
  align-items: center;
  gap: 3px;
  justify-content: flex-end;
  margin-top: 8px;
  font-size: 10px;
  color: var(--text-faint);
}
.legend-cell {
  width: 10px;
  height: 10px;
  background: var(--accent);
  border-radius: 2px;
}

/* 历史文件 */
.file-empty {
  text-align: center;
  color: var(--text-placeholder);
  font-size: 13px;
  padding: 20px 0;
}
.file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 92px;
  overflow-y: auto;
}
.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}
.file-item:hover {
  background: var(--bg-hover);
}
.file-item .ui-icon { width: 14px; height: 14px; }
.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-time {
  font-size: 11px;
  color: var(--text-faint);
}
</style>
