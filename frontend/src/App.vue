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
        />
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="app-main">
      <ChatView />
    </main>

    <!-- 全局动态光流背景 -->
    <div class="bg-aurora">
      <div class="aurora-orb orb-1"></div>
      <div class="aurora-orb orb-2"></div>
      <div class="aurora-orb orb-3"></div>
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
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from './stores/authStore'
import type { AuthUser } from './services/api'
import ChatView from './views/ChatView.vue'
import Live2DWidget from './components/Live2DWidget.vue'
import AuthModal from './components/AuthModal.vue'
import UserMenu from './components/UserMenu.vue'
import AccountModal from './components/AccountModal.vue'

const authStore = useAuthStore()
const showAccountModal = ref(false)
const showAuth = ref(!authStore.isLoggedIn)

function onLoggedIn(user: AuthUser) {
  authStore.setUser(user)
  showAuth.value = false
}
</script>

<style>
/* ===== 全局样式重置 ===== */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Apple Color Emoji', 'Segoe UI Emoji', sans-serif;
  background: #F8F5F1;
  color: #3D4255;
}

/* 移除 Chrome 自动填充的蓝色底纹 */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus,
input:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 30px white inset !important;
  box-shadow: 0 0 0 30px white inset !important;
  -webkit-text-fill-color: #3D4255 !important;
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
  background: linear-gradient(135deg, rgba(212, 145, 111, 0.85), rgba(184, 120, 88, 0.85));
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

/* ===== 全局光球背景 ===== */
.bg-aurora {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.aurora-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
}

.orb-1 {
  right: 0;
  bottom: 0;
  width: 2048px;
  height: 2048px;
  transform: translate(50%, 50%);
  background: radial-gradient(circle at 40% 40%,
    rgba(245, 235, 220, 0.5) 0%,
    rgba(240, 225, 205, 0.35) 25%,
    rgba(235, 218, 195, 0.25) 50%,
    rgba(245, 235, 220, 0.12) 70%,
    transparent 90%
  );
  animation: orb-drift-1 20s ease-in-out infinite alternate;
}

@keyframes orb-drift-1 {
  0%   { transform: translate(50%, 50%) scale(1); }
  100% { transform: translate(45%, 55%) scale(1.03); }
}

/* 冷色调光晕（蓝紫）—— 为毛玻璃提供色彩折射 */
.orb-2 {
  left: -10%;
  top: -15%;
  width: 1600px;
  height: 1600px;
  background: radial-gradient(circle at 60% 60%,
    rgba(180, 200, 255, 0.25) 0%,
    rgba(160, 180, 240, 0.15) 30%,
    rgba(140, 160, 220, 0.08) 60%,
    transparent 85%
  );
  animation: orb-drift-2 25s ease-in-out infinite alternate;
}

/* 暖粉色调光晕 —— 增加层次 */
.orb-3 {
  left: 30%;
  bottom: -20%;
  width: 1400px;
  height: 1400px;
  background: radial-gradient(circle at 50% 50%,
    rgba(255, 200, 180, 0.2) 0%,
    rgba(240, 180, 160, 0.12) 30%,
    rgba(220, 160, 140, 0.06) 60%,
    transparent 85%
  );
  animation: orb-drift-3 30s ease-in-out infinite alternate;
}

@keyframes orb-drift-2 {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(60px, 40px) scale(1.05); }
}

@keyframes orb-drift-3 {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-40px, -30px) scale(1.08); }
}

</style>
