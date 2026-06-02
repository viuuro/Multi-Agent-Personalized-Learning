<template>
  <div class="user-menu-container">
    <!-- 菜单按钮 -->
    <button class="hamburger-btn" @click.stop="showMenu = !showMenu">
      <svg width="20" height="16" viewBox="0 0 20 16" fill="none">
        <rect x="0" y="0" width="20" height="2" rx="1" fill="white"/>
        <rect x="0" y="7" width="20" height="2" rx="1" fill="white"/>
        <rect x="0" y="14" width="20" height="2" rx="1" fill="white"/>
      </svg>
    </button>

    <!-- 下拉菜单 -->
    <transition name="menu-drop">
      <div v-if="showMenu" class="menu-dropdown" @click.stop>
        <div class="menu-item" @click="handleAccountManage">
          <el-icon :size="16"><User /></el-icon>
          <span>账号管理</span>
        </div>
        <div class="menu-item logout-item" @click="handleLogout">
          <el-icon :size="16"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </div>
        <div class="menu-item delete-item" @click="showDeleteDialog = true">
          <el-icon :size="16"><Delete /></el-icon>
          <span>注销账号</span>
        </div>
      </div>
    </transition>

    <!-- 注销确认弹窗 -->
    <el-dialog
      v-model="showDeleteDialog"
      :align-center="true"
      :show-close="true"
      :close-on-click-modal="true"
      :close-on-press-escape="false"
      :lock-scroll="true"
      width="420px"
      modal-class="plan-expand-overlay"
      @closed="deletePassword = ''"
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { User, SwitchButton, Delete } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/authStore'
import { deleteAccountApi } from '../services/api'

const emit = defineEmits<{
  (e: 'account-manage'): void
}>()

const authStore = useAuthStore()
const showMenu = ref(false)
const showDeleteDialog = ref(false)
const deletePassword = ref('')
const deleteError = ref('')
const deleting = ref(false)

let docCleanup: (() => void) | null = null

onMounted(() => {
  const handler = () => { showMenu.value = false }
  document.addEventListener('click', handler)
  docCleanup = () => document.removeEventListener('click', handler)
})

onUnmounted(() => {
  if (docCleanup) docCleanup()
})

function handleAccountManage() {
  showMenu.value = false
  emit('account-manage')
}

function handleLogout() {
  showMenu.value = false
  authStore.logout()
  window.location.reload()
}

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
</script>

<style scoped>
.user-menu-container {
  position: relative;
  display: flex;
  align-items: center;
}

.hamburger-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  transition: background 0.2s;
}

.hamburger-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.menu-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  min-width: 160px;
  background: rgba(248, 245, 241, 0.95);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 14px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  z-index: 1100;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: #7A6A60;
  cursor: pointer;
  transition: background 0.15s;
}

.menu-item:hover {
  background: rgba(212, 145, 111, 0.1);
}

.logout-item {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  color: #B87858;
}

.delete-item {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  color: #E74C3C;
}

.delete-item:hover {
  background: rgba(231, 76, 60, 0.1) !important;
}

/* 注销弹窗内容 —— 复用 AccountModal 的排版风格 */
.delete-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.delete-warning {
  color: #E74C3C;
  font-weight: 600;
  font-size: 14px;
}

.delete-list {
  margin: 0 0 4px 20px;
  color: #7A6A60;
  font-size: 13px;
  line-height: 1.8;
}

/* 复用 AccountModal 的字段布局 */
.acct-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: #7A6A60;
  margin-bottom: 24px;
}

.acct-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.acct-label {
  font-size: 13px;
  color: #A09080;
}

.error-msg {
  color: #e57373;
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

/* 注销按钮使用红色渐变 */
.delete-submit-btn {
  background: linear-gradient(135deg, #E74C3C, #C0392B);
}

/* 下拉过渡 */
.menu-drop-enter-active,
.menu-drop-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.menu-drop-enter-from,
.menu-drop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
