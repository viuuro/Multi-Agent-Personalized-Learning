<template>
  <!--
    消息气泡组件
    用户消息：靠右，紫色渐变背景，白色文字
    AI 消息：  靠左，白色背景，黑色文字，支持 Markdown 渲染
    流式输出时：在 AI 消息后显示闪烁打字光标
  -->
  <div :class="['message-bubble', role]">
    <!-- 头像区域 -->
    <div class="message-avatar">
      <el-avatar v-if="role === 'user'" :size="42" :src="authStore.user?.avatar || undefined" :style="{ background: '#D4916F' }">
        {{ authStore.user?.avatar ? '' : (authStore.user?.username?.charAt(0)?.toUpperCase() || '我') }}
      </el-avatar>
      <el-avatar v-else :size="42" :src="'/models/iromari/preview.png'" />
    </div>

    <!-- 消息内容区域 -->
    <div class="message-body">
      <!-- 图片预览（如果有） -->
      <div v-if="imageUrl" class="message-image">
        <img :src="imageUrl" alt="uploaded image" @click="previewImage" />
      </div>
      <!-- 消息文本（用户消息直接显示，AI 消息渲染 Markdown） -->
      <div class="message-content" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

/**
 * 消息气泡组件
 *
 * 功能：
 *   - 用户消息：纯文本展示，HTML 转义防止 XSS
 *   - AI 消息：Markdown 渲染（标题、列表、代码块、粗体、链接等）
 *   - 流式输出时显示闪烁光标动画
 */

/** 初始化 Markdown 渲染器 */
const md = new MarkdownIt({
  html: false,    // 禁用原始 HTML 标签，防止 XSS 攻击
  linkify: true,  // 自动识别并转换 URL 为链接
  breaks: true,   // 将换行符转换为 <br>
})

const props = defineProps<{
  role: 'user' | 'assistant' | 'system'  // 消息发送者
  content: string                          // 消息文本内容
  isStreaming?: boolean                    // 是否正在流式输出（仅 AI 消息）
  imageUrl?: string                        // 图片 URL（可选）
}>()

/** 预览图片 */
function previewImage() {
  if (props.imageUrl) {
    window.open(props.imageUrl, '_blank')
  }
}

/** 根据消息角色渲染内容：用户消息 HTML 转义，AI 消息 Markdown 渲染 */
const renderedContent = computed(() => {
  if (props.role === 'user') {
    return escapeHtml(props.content)
  }
  return md.render(props.content)
})

/** 转义 HTML 特殊字符，防止 XSS 攻击 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}
</script>

<style scoped>
/* 消息行布局 */
.message-bubble {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  max-width: 85%;
}

/* 用户消息靠右 */
.message-bubble.user {
  flex-direction: row-reverse;
  align-self: flex-end;
  margin-left: auto;
}

/* AI 消息靠左 */
.message-bubble.assistant {
  align-self: flex-start;
}

.message-avatar {
  flex-shrink: 0;
}

.message-body {
  position: relative;
}

/* 消息内容气泡 */
.message-content {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

/* 图片预览 */
.message-image {
  margin-bottom: 8px;
  max-width: 300px;
}

.message-image img {
  width: 100%;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.message-image img:hover {
  opacity: 0.9;
}

/* 用户消息：与 tab-slider-knob 同色 */
.user .message-content {
  background: var(--accent);
  color: white;
  box-shadow: 0 4px 18px var(--accent-glow);
}

/* AI 消息：毛玻璃 */
.assistant .message-content {
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(16px) saturate(1.2);
  -webkit-backdrop-filter: blur(16px) saturate(1.2);
  color: var(--text-primary);
  border: 1px solid var(--border-solid);
  box-shadow: 0 4px 20px var(--shadow-bubble);
}

/* ===== Markdown 内容样式（通过 :deep 穿透 scoped） ===== */

.message-content :deep(p) {
  margin: 0 0 8px;
}
.message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(code) {
  background: var(--bg-hover);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Courier New', monospace;
}
.user .message-content :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.message-content :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
.message-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}
.message-content :deep(li) {
  margin: 2px 0;
}

.message-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
}
.user .message-content :deep(a) {
  color: #F5D0B0;
}

.message-content :deep(strong) {
  font-weight: 600;
}

.message-content :deep(blockquote) {
  border-left: 3px solid var(--border-solid);
  padding-left: 10px;
  color: var(--text-faint);
  margin: 8px 0;
}

</style>
