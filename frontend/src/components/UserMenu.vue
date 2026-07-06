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
        <!-- 主题切换 -->
        <div class="menu-item theme-item" @click="themeStore.toggleMode">
          <el-icon :size="16">
            <Sunny v-if="themeStore.mode === 'light'" />
            <Moon v-else-if="themeStore.mode === 'dark'" />
            <Monitor v-else />
          </el-icon>
          <span>{{ themeModeLabel }}</span>
        </div>
        <div class="menu-item" @click="handleAccountManage">
          <el-icon :size="16"><User /></el-icon>
          <span>账号管理</span>
        </div>
        <div class="menu-item logout-item" @click="handleLogout">
          <el-icon :size="16"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </div>
        <div class="menu-item delete-item" @click="handleDeleteAccount">
          <el-icon :size="16"><Delete /></el-icon>
          <span>注销账号</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { User, SwitchButton, Delete, Moon, Sunny, Monitor } from '@element-plus/icons-vue'
import { useThemeStore } from '../stores/themeStore'

const emit = defineEmits<{
  (e: 'account-manage'): void
  (e: 'delete-account'): void
  (e: 'logout'): void
}>()

const themeStore = useThemeStore()

const themeModeLabel = computed(() => {
  const labels = { auto: '跟随系统', light: '浅色模式', dark: '深色模式' }
  return labels[themeStore.mode]
})

const showMenu = ref(false)

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
  emit('logout')
}

function handleDeleteAccount() {
  showMenu.value = false
  emit('delete-account')
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
  background: var(--bg-primary);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  overflow: hidden;
  z-index: 1100;
  border: 1px solid var(--border-solid);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s;
}

.menu-item:hover {
  background: var(--bg-hover);
}

.logout-item {
  color: var(--accent-dark);
}

.delete-item {
  color: var(--danger);
}

.delete-item:hover {
  background: rgba(231, 76, 60, 0.1);
}

/* 菜单动画 */
.menu-drop-enter-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.menu-drop-leave-active {
  transition: opacity 0.1s ease, transform 0.1s ease;
}
.menu-drop-enter-from,
.menu-drop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
