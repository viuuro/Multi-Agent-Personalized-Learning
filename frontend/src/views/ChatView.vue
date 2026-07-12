<template>
  <div ref="chatViewRef" class="chat-view" :class="{ 'sidebar-closed': !sidebarOpen }">
    <!-- ==================== 左侧面板 ==================== -->
    <div class="left-panel">
      <div class="panel-card">
        <!-- 卡片头部：标题+操作（左） / 标签切换（右） -->
        <div class="card-header">
          <div class="profile-heading">
            <template v-if="activeTab === 'plan' && planHasData">
              <span class="card-title">4周学习计划</span>
            </template>
          </div>
          <div class="card-tabs">
            <div class="tab-slider" :class="{ right: activeTab === 'plan' }">
              <span class="tab-option" @click="activeTab = 'profile'">学习画像</span>
              <span class="tab-option" @click="activeTab = 'plan'">学习计划</span>
              <div class="tab-slider-knob"></div>
            </div>
          </div>
        </div>

        <!-- 学习画像内容 -->
        <aside v-show="sidebarOpen" class="workspace-sidebar">
        <div class="tab-content profile-content">
          <div class="radar-layout">
            <div class="radar-left">
              <RadarChart />
            </div>
            <div class="radar-right">
              <div class="summary-row">
                <span class="label">风格</span>
                <span class="value">{{
                  profileStore.profile.cognitiveStyle === 'visual' ? '视觉型' :
                  profileStore.profile.cognitiveStyle === 'verbal' ? '语言型' : '动手实践型'
                }}</span>
              </div>
              <div class="summary-row">
                <span class="label">目标</span>
                <span class="value">{{ profileStore.profile.shortTermGoal || '待设定' }}</span>
              </div>
              <div class="summary-row">
                <span class="label">薄弱</span>
                <span class="value">{{ profileStore.profile.weaknessPoints.join('、') || '待评估' }}</span>
              </div>
              <div class="summary-row">
                <span class="label">兴趣</span>
                <span class="value">{{ profileStore.profile.interestAreas.join('、') || '待评估' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 学习计划内容 -->
        <div class="tab-content plan-tab-content">
          <div v-if="!planHasData" class="plan-empty-state">
            <button class="submit-file-btn" @click="handleGeneratePlan" :disabled="planLoading">
              <el-icon :size="14"><Loading v-if="planLoading" /><Plus v-else /></el-icon>
              <span>{{ planLoading ? '生成中...' : '生成学习计划' }}</span>
            </button>
          </div>

          <PlanCard ref="planCardRef" />

          <div v-if="planHasData" class="plan-header-btns">
            <button class="expand-plan-btn" @click="showEditModal = true">
              <el-icon><Edit /></el-icon>
            </button>
            <button class="expand-plan-btn" @click="showExpandModal = true">
              <el-icon><FullScreen /></el-icon>
            </button>
          </div>

          <!-- 评分结果显示 -->
          <div v-if="evaluationResult" class="evaluation-result">
            <div class="eval-header">
              <span class="eval-score">{{ evaluationResult.score }} 分</span>
              <button class="eval-close" @click="evaluationResult = null">&times;</button>
            </div>
            <p class="eval-analysis">{{ evaluationResult.analysis }}</p>
            <p class="eval-suggestion">{{ evaluationResult.suggestion }}</p>
          </div>
        </div>
        </aside>
      </div>
    </div>

    <div
      v-if="planHasData && sidebarOpen"
      class="submission-dock"
      :style="{ top: submissionCenterY ? `${submissionCenterY}px` : '72%' }"
    >
      <button class="submit-file-btn" @click="handleSubmissionUpload" :disabled="submitting">
        <el-icon :size="14"><Upload /></el-icon>
        <span>{{ submitting ? 'AI评分中...' : '提交学习成果' }}</span>
      </button>
    </div>

    <!-- ==================== 右侧面板 ==================== -->
    <button
      class="sidebar-toggle"
      :class="{ collapsed: !sidebarOpen }"
      type="button"
      :aria-expanded="sidebarOpen"
      aria-label="切换学习计划侧边栏"
      @click="sidebarOpen = !sidebarOpen"
    >
      <svg viewBox="0 0 20 20" aria-hidden="true">
        <path d="m8 5 5 5-5 5" />
      </svg>
    </button>

    <div class="chat-panel">
      <div class="panel-card chat-card">
        <div class="message-list" ref="messageListRef">
          <MessageBubble
            v-for="msg in chatStore.messages"
            :key="msg.id"
            :role="msg.role"
            :content="msg.content"
            :image-url="msg.imageUrl"
          />

          <MessageBubble
            v-if="chatStore.isStreaming && chatStore.streamingContent"
            role="assistant"
            :content="chatStore.streamingContent"
            :isStreaming="true"
          />

          <div v-if="chatStore.isStreaming && !chatStore.streamingContent" class="loading-indicator">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>

        <div class="input-area">
          <!-- 暂存图片/文件预览 -->
          <transition name="preview-slide">
            <div v-if="stagedImage || stagedFile" class="staged-preview">
              <!-- 图片预览 -->
              <div v-if="stagedImage" class="staged-preview-item">
                <img :src="stagedImage.previewUrl" alt="preview" />
                <button class="staged-remove" @click="clearStagedImage">
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M9 3L3 9M3 3l6 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
              <!-- 文件预览 -->
              <div v-if="stagedFile" class="staged-file-item">
                <div class="file-icon">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#D4916F" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                </div>
                <div class="file-info">
                  <span class="file-name">{{ stagedFile.name }}</span>
                  <span class="file-size">{{ stagedFile.size }}</span>
                </div>
                <button class="staged-remove" @click="clearStagedFile">
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M9 3L3 9M3 3l6 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </div>
          </transition>
          <div ref="capsuleBarRef" class="capsule-bar">
            <!-- + 按钮（左下角，圆形，与发送按钮风格一致） -->
            <button class="plus-btn" @click.stop="showPlusMenu = !showPlusMenu">
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M9 3v12M3 9h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
            <textarea
              ref="inputRef"
              v-model="inputText"
              class="capsule-input"
              :placeholder="stagedImage ? '输入关于图片的问题... (Enter 发送)' : stagedFile ? '输入关于文件的问题... (Enter 发送)' : '输入你的学习情况或问题... (Enter 发送 / Shift+Enter 换行)'"
              rows="1"
              @keydown.enter="handleEnter"
              @input="autoResize"
              :disabled="chatStore.isStreaming"
            />
            <button
              :disabled="(!inputText.trim() && !stagedImage && !stagedFile) || chatStore.isStreaming"
              @click="handleSend"
              class="capsule-send"
            >发送</button>
          </div>
          <!-- + 按钮下拉菜单（与汉堡菜单样式一致） -->
          <transition name="plus-menu-drop">
            <div v-if="showPlusMenu" class="plus-menu" @click.stop>
              <div class="plus-menu-item" @click="handleImageUpload">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#7A6A60" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <circle cx="8.5" cy="8.5" r="1.5"/>
                  <polyline points="21 15 16 10 5 21"/>
                </svg>
                <span>图片</span>
              </div>
              <div class="plus-menu-item" @click="handleFileUpload">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#7A6A60" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
                <span>文件</span>
              </div>
            </div>
          </transition>
          <!-- 隐藏的文件选择器 -->
          <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="onImageSelected" />
          <input ref="fileInputRef" type="file" style="display:none" @change="onFileSelected" />
        </div>
      </div>
    </div>

    <!-- ==================== 展开模态框 ==================== -->
    <el-dialog
      v-model="showExpandModal"
      title="学习计划"
      width="680px"
      align-center
      :lock-scroll="true"
      modal-class="plan-expand-overlay"
    >
      <div class="expand-plan-content">
        <PlanCard :plan-data="planCardRef?.plan" variant="expand" />
      </div>
    </el-dialog>

    <!-- ==================== 编辑模态框 ==================== -->
    <el-dialog
      v-model="showEditModal"
      title="编辑学习计划"
      width="680px"
      align-center
      :lock-scroll="true"
      modal-class="plan-expand-overlay"
      @closed="editPlanCardRef?.cancelEdit()"
    >
      <div class="expand-plan-content">
        <PlanCard v-if="showEditModal" ref="editPlanCardRef" :plan-data="planCardRef?.plan" variant="expand" :initial-editing="true" @edit-done="showEditModal = false" />
      </div>
      <template #footer>
        <div class="edit-modal-footer">
          <el-button type="primary" class="save-btn" size="small" @click="editPlanCardRef?.saveEdit()">保存</el-button>
          <el-button size="small" @click="editPlanCardRef?.cancelEdit()">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { FullScreen, Edit, Loading, Picture, Document, Upload } from '@element-plus/icons-vue'
import { useChatStore } from '../stores/chatStore'
import { useProfileStore } from '../stores/profileStore'
import { useAuthStore } from '../stores/authStore'
import { sendMessage } from '../services/sse'
import { fetchProfile, parseFileApi, fetchConversationsApi, fetchSavedPlanApi } from '../services/api'
import RadarChart from '../components/RadarChart.vue'
import PlanCard from '../components/PlanCard.vue'
import MessageBubble from '../components/MessageBubble.vue'

const chatStore = useChatStore()
const profileStore = useProfileStore()
const authStore = useAuthStore()

/** 记录上次加载画像的用户 ID，用于检测账号切换 */
let lastLoadedUserId: number | null = null

const inputText = ref('')
const inputRef = ref<HTMLTextAreaElement>()
const chatViewRef = ref<HTMLDivElement>()
const capsuleBarRef = ref<HTMLDivElement>()
const submissionCenterY = ref(0)
const messageListRef = ref<HTMLDivElement>()
const planCardRef = ref<InstanceType<typeof PlanCard>>()
const planLoading = ref(false)
const showExpandModal = ref(false)
const showEditModal = ref(false)
const editPlanCardRef = ref<InstanceType<typeof PlanCard>>()
const activeTab = ref<'profile' | 'plan'>('profile')
const planHasData = ref(false)
const sidebarOpen = ref(true)
const showPlusMenu = ref(false)
const imageInputRef = ref<HTMLInputElement>()
const fileInputRef = ref<HTMLInputElement>()

// 暂存待发送的图片
const stagedImage = ref<{ previewUrl: string; base64: string } | null>(null)

// 暂存待发送的文件
const stagedFile = ref<{ file: File; name: string; size: string } | null>(null)

// ===== 文件提交 & AI 评分 =====
const submitting = ref(false)
const evaluationResult = ref<{ score: number; analysis: string; suggestion: string } | null>(null)

function getGreeting(): string {
  const now = new Date()
  const hour = now.getHours()
  const minute = now.getMinutes().toString().padStart(2, '0')
  const timeStr = `${hour}:${minute}`

  let period: string
  if (hour < 6) period = '凌晨'
  else if (hour < 9) period = '早上'
  else if (hour < 12) period = '上午'
  else if (hour < 14) period = '中午'
  else if (hour < 19) period = '下午'
  else period = '晚上'

  const cares = [
    '今天感觉怎么样？有什么想学习的吗？',
    '记得保持好心情，学习效率会更高哦~',
    '准备好开启一段新的学习旅程了吗？',
    '累了就歇一歇，学习是马拉松不是短跑呀~',
  ]
  const care = cares[Math.floor(Math.random() * cares.length)]

  const username = authStore.user?.username || '同学'
  return `${period}好呀，${username}~ 现在是 ${timeStr}，${care}`
}

function handleEnter(e: KeyboardEvent) {
  if (e.shiftKey) return
  e.preventDefault()
  handleSend()
}

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}

async function handleSend() {
  const text = inputText.value.trim()
  const hasImage = !!stagedImage.value
  const hasFile = !!stagedFile.value
  if ((!text && !hasImage && !hasFile) || chatStore.isStreaming) return

  // 如果有文件，先解析文件内容
  if (hasFile) {
    chatStore.startStreaming()
    try {
      const result = await parseFileApi(stagedFile.value!.file)
      const fileContent = result.text || '（文件内容为空）'
      const msgContent = text || `请分析这个文件：${stagedFile.value!.name}`
      const fullMessage = `${msgContent}\n\n---\n📄 文件：${stagedFile.value!.name}\n\n${fileContent}\n---`

      chatStore.addMessage({
        id: Date.now().toString(),
        role: 'user',
        content: msgContent,
        timestamp: Date.now(),
      })

      inputText.value = ''
      clearStagedFile()
      scrollToBottom()
      sendMessage(fullMessage, undefined, authStore.user?.id)
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : '文件解析失败'
      chatStore.appendStreamContent(`\n\n[错误: ${errorMsg}]`)
      chatStore.finishStreaming()
    }
    return
  }

  // 图片或纯文本发送
  const msgContent = text || '请分析这张图片'
  const imageUrl = stagedImage.value?.previewUrl
  const imageData = stagedImage.value?.base64

  chatStore.addMessage({
    id: Date.now().toString(),
    role: 'user',
    content: msgContent,
    timestamp: Date.now(),
    ...(hasImage && { imageUrl, imageData }),
  })

  inputText.value = ''
  stagedImage.value = null
  scrollToBottom()
  sendMessage(msgContent, imageData, authStore.user?.id)
}

async function handleGeneratePlan() {
  planLoading.value = true
  try {
    await planCardRef.value?.generatePlan()
    planHasData.value = true
  } finally {
    planLoading.value = false
  }
}

function handleImageUpload() {
  showPlusMenu.value = false
  imageInputRef.value?.click()
}

function handleFileUpload() {
  showPlusMenu.value = false
  fileInputRef.value?.click()
}

function onImageSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) { alert('图片大小不能超过 5MB'); return }
  clearStagedImage()
  const reader = new FileReader()
  reader.onload = (event) => {
    const base64Data = event.target?.result as string
    stagedImage.value = { previewUrl: URL.createObjectURL(file), base64: base64Data }
  }
  reader.readAsDataURL(file)
  ;(e.target as HTMLInputElement).value = ''
}

function clearStagedImage() {
  if (stagedImage.value) {
    URL.revokeObjectURL(stagedImage.value.previewUrl)
    stagedImage.value = null
  }
}

function onFileSelected(e: Event) {
  const mode = (e.target as HTMLInputElement).getAttribute('data-mode')
  if (mode === 'submit') {
    handleSubmissionFileSelected(e)
    ;(e.target as HTMLInputElement).setAttribute('data-mode', '')
    return
  }

  // 原有的聊天文件上传逻辑
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { alert('文件大小不能超过 10MB'); return }
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!['pdf', 'docx', 'txt'].includes(ext)) { alert('仅支持 PDF、Word (.docx)、TXT 文件'); return }
  clearStagedFile()
  stagedFile.value = { file, name: file.name, size: formatFileSize(file.size) }
  ;(e.target as HTMLInputElement).value = ''
}

function clearStagedFile() {
  stagedFile.value = null
}

// ===== AI 评分文件提交 =====

async function handleSubmissionUpload() {
  fileInputRef.value?.setAttribute('data-mode', 'submit')
  fileInputRef.value?.click()
}

async function handleSubmissionFileSelected(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { alert('文件大小不能超过 10MB'); return }

  submitting.value = true
  evaluationResult.value = null

  try {
    // 1. 解析文件内容
    const result = await parseFileApi(file)
    const fileContent = result.text || '（文件内容为空）'
    const userId = authStore.user?.id
    if (!userId) { alert('请先登录'); return }

    // 2. 调用 AI 提交评分接口
    const submissionRes = await fetch('/api/submissions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': String(userId) },
      body: JSON.stringify({ taskId: 1, content: fileContent }),
    })
    const subResult = await submissionRes.json()
    if (subResult.code !== 200) throw new Error(subResult.message || '提交失败')
    const submissionId = subResult.data.submissionId

    // 3. 轮询获取 AI 评分结果
    let retries = 10
    while (retries > 0) {
      await new Promise(r => setTimeout(r, 2000))
      const evalRes = await fetch(`/api/submissions/${submissionId}`, { headers: { 'X-User-Id': String(userId) } })
      const evalData = await evalRes.json()
      if (evalData.code === 200 && evalData.data?.status === 'EVALUATED' && evalData.data?.evaluation) {
        evaluationResult.value = {
          score: evalData.data.evaluation.score,
          analysis: evalData.data.evaluation.analysis,
          suggestion: evalData.data.evaluation.suggestion,
        }
        break
      }
      retries--
    }
    if (!evaluationResult.value) {
      evaluationResult.value = { score: 0, analysis: 'AI 评分超时，请稍后重试', suggestion: 'AI Mock 模式返回固定评分，或检查 API Key 配置' }
    }
  } catch (err: unknown) {
    evaluationResult.value = { score: 0, analysis: '提交失败: ' + (err instanceof Error ? err.message : '未知错误'), suggestion: '请检查文件格式并重试' }
  } finally {
    submitting.value = false
    ;(e.target as HTMLInputElement).value = ''
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(
  () => [chatStore.messages.length, chatStore.streamingContent],
  () => scrollToBottom()
)

// 登录后流式输出问候语
function streamGreeting(text: string) {
  chatStore.startStreaming()
  const chars = [...text]
  let i = 0
  const interval = setInterval(() => {
    if (i < chars.length) {
      chatStore.appendStreamContent(chars[i])
      i++
    } else {
      clearInterval(interval)
      chatStore.finishStreaming()
    }
  }, 35)
}

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      const userId = authStore.user?.id
      if (userId && lastLoadedUserId !== null && lastLoadedUserId !== userId) {
        chatStore.clearMessages()
        profileStore.resetProfile()
        planHasData.value = false
      }
      setTimeout(async () => {
        try { const profile = await fetchProfile(userId!); profileStore.setProfile(profile); lastLoadedUserId = userId! } catch {}
        try { const history = await fetchConversationsApi(userId!); if (history.length > 0) { chatStore.clearMessages(); for (const msg of history) { chatStore.addMessage({ id: msg.id.toString(), role: msg.role as 'user' | 'assistant', content: msg.content, timestamp: new Date(msg.timestamp).getTime() }) } } } catch {}
        try { const savedPlan = await fetchSavedPlanApi(userId!); if (savedPlan && savedPlan.weeks && savedPlan.weeks.length > 0) { planHasData.value = true; nextTick(() => { planCardRef.value?.setPlan(savedPlan) }) } } catch {}
        if (chatStore.messages.length === 0) streamGreeting(getGreeting())
      }, 400)
    } else {
      chatStore.clearMessages()
      profileStore.resetProfile()
      planHasData.value = false
      lastLoadedUserId = null
    }
  },
  { immediate: true }
)

let scrollTimer: ReturnType<typeof setTimeout> | null = null
let alignmentObserver: ResizeObserver | null = null
let alignmentFrame: number | null = null

function syncSubmissionCenter() {
  if (alignmentFrame !== null) cancelAnimationFrame(alignmentFrame)
  alignmentFrame = requestAnimationFrame(() => {
    const container = chatViewRef.value
    const composer = capsuleBarRef.value
    if (!container || !composer) return

    const containerRect = container.getBoundingClientRect()
    const composerRect = composer.getBoundingClientRect()
    submissionCenterY.value = composerRect.top + composerRect.height / 2 - containerRect.top
  })
}

function handleMsgScroll() {
  const el = messageListRef.value
  if (!el) return
  el.classList.add('scrolling')
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => el.classList.remove('scrolling'), 600)
}

function closePlusMenu() {
  showPlusMenu.value = false
}

onMounted(() => {
  messageListRef.value?.addEventListener('scroll', handleMsgScroll)
  document.addEventListener('click', closePlusMenu)
  alignmentObserver = new ResizeObserver(syncSubmissionCenter)
  if (chatViewRef.value) alignmentObserver.observe(chatViewRef.value)
  if (capsuleBarRef.value) alignmentObserver.observe(capsuleBarRef.value)
  window.addEventListener('resize', syncSubmissionCenter)
  nextTick(syncSubmissionCenter)
})

onUnmounted(() => {
  messageListRef.value?.removeEventListener('scroll', handleMsgScroll)
  document.removeEventListener('click', closePlusMenu)
  window.removeEventListener('resize', syncSubmissionCenter)
  alignmentObserver?.disconnect()
  if (alignmentFrame !== null) cancelAnimationFrame(alignmentFrame)
  if (scrollTimer) clearTimeout(scrollTimer)
})

watch(sidebarOpen, () => nextTick(syncSubmissionCenter))
</script>

<style scoped>
.chat-view { display: flex; height: 100%; gap: 0; }
.left-panel { width: 35%; min-width: 280px; display: flex; flex-direction: column; background: transparent; overflow-y: auto; padding: 12px; }
.left-panel::-webkit-scrollbar { width: 4px; }
.left-panel::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
.panel-card { position: relative; background: transparent; padding: 12px; flex: 1; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.panel-card::before { content: ''; position: absolute; inset: 0; background: transparent; backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-radius: 20px; z-index: -1; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 0; flex-shrink: 0; }
.card-header-left { display: flex; align-items: center; gap: 8px; }
.card-title { display: inline-block; position: relative; top: 24px; font-size: 20px; font-weight: 700; color: var(--text-secondary); white-space: nowrap; margin-left: 10px; }
.edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-solid) !important;
  border-radius: 10px !important;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--accent) !important;
  font-size: 12px !important;
  cursor: pointer;
  transition: background 0.2s;
}
.edit-btn:hover, .edit-btn:focus, .edit-btn:active {
  background: var(--bg-hover) !important;
  color: var(--accent) !important;
}
.edit-btn .el-icon { margin-right: 2px; }
.expand-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-solid) !important;
  border-radius: 10px !important;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--accent) !important;
  font-size: 12px !important;
  cursor: pointer;
  transition: background 0.2s;
}
.expand-btn:hover, .expand-btn:focus, .expand-btn:active {
  background: var(--bg-hover) !important;
  color: var(--accent) !important;
}
.expand-btn .el-icon { margin-right: 2px; }
.card-tabs { margin-left: auto; }
.tab-slider { display: flex; position: relative; background: var(--bg-input); border: 1px solid var(--border-solid); border-radius: 10px; overflow: hidden; }
.tab-option { position: relative; z-index: 1; padding: 4px 14px; font-size: 12px; color: var(--text-muted); cursor: pointer; transition: color 0.25s; user-select: none; }
.tab-slider-knob { position: absolute; top: 0; left: 0; width: 50%; height: 100%; background: var(--accent); border-radius: 9px; transition: transform 0.25s ease; }
.tab-slider.right .tab-slider-knob { transform: translateX(100%); }
.tab-slider.right .tab-option:first-child { color: var(--text-muted); }
.tab-option:first-child { color: white; }
.tab-slider.right .tab-option:nth-child(2) { color: white; }
.tab-content { flex: 1; overflow-y: auto; }
.plan-tab-content { padding-top: 32px; }
.plan-footer { display: flex; justify-content: space-between; align-items: center; padding: 8px 0 0; flex-shrink: 0; gap: 8px; }
.plan-footer-actions { display: flex; gap: 4px; }
.tab-content::-webkit-scrollbar { width: 3px; }
.tab-content::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }

/* 提交按钮 */
.submit-file-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--accent);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.2s;
}
.submit-file-btn:hover:not(:disabled),
.submit-file-btn:focus:not(:disabled),
.submit-file-btn:active:not(:disabled) {
  border-color: var(--border-solid);
  background: var(--bg-hover);
  color: var(--accent);
}
.submit-file-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 评分结果显示 */
.evaluation-result {
  margin-top: 12px;
  padding: 16px;
  border-radius: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-solid);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  animation: evalFadeIn 0.3s ease;
}
.eval-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.eval-score { font-size: 22px; font-weight: 700; color: var(--accent); }
.eval-close { border: none; background: none; font-size: 20px; color: var(--text-faint); cursor: pointer; }
.eval-analysis { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin-bottom: 8px; }
.eval-suggestion { font-size: 13px; color: var(--accent); line-height: 1.6; background: var(--accent-hover); padding: 10px 14px; border-radius: 10px; margin: 0; }

@keyframes evalFadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

.radar-layout { display: flex; align-items: center; gap: 12px; }
.radar-left { flex-shrink: 0; width: 55%; }
.radar-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; padding-left: 16px; margin-top: -54px; }
.summary-row { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.summary-row .label { color: var(--accent); width: 32px; flex-shrink: 0; }
.summary-row .value { color: var(--text-muted); flex: 1; word-break: break-all; white-space: normal; line-height: 1.4; }
.plan-empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 40px 20px; }
.plan-empty-state p { font-size: 13px; color: var(--text-placeholder); }
.plan-loading-text { display: flex; align-items: center; gap: 8px; color: var(--text-faint); font-size: 13px; }
.plan-empty-state .el-button--primary { background: var(--accent); border: none; border-radius: 12px; }
.right-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; background: transparent; padding: 12px 12px 12px 0; margin-left: 16px; }
.chat-card { position: relative; background: transparent; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.chat-card::before { content: ''; position: absolute; inset: 0; background: transparent; backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-radius: 20px; z-index: -1; box-shadow: none; }
.message-list { position: relative; z-index: 1; flex: 1; overflow-y: auto; padding: 0px 10px; }
.message-list::-webkit-scrollbar { width: 5px; }
.message-list::-webkit-scrollbar-thumb { background: transparent; border-radius: 3px; }
.message-list.scrolling::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); }
.loading-indicator { display: flex; gap: 4px; padding: 12px 0; }
.dot { width: 6px; height: 6px; background: var(--text-placeholder); border-radius: 50%; animation: bounce 1.2s infinite; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); } 30% { transform: translateY(-6px); } }
.input-area { position: relative; z-index: 5; padding: 12px 16px 0; background: transparent; flex-shrink: 0; margin-bottom: 16px; }
.capsule-bar { display: flex; align-items: flex-end; background: var(--bg-input); backdrop-filter: blur(20px) saturate(1.3); -webkit-backdrop-filter: blur(20px) saturate(1.3); border: 1px solid var(--border-solid); border-radius: 16px; padding: 4px 4px 4px 4px; box-shadow: var(--shadow-card); }
.plus-btn { flex-shrink: 0; width: 36px; height: 36px; border: none; border-radius: 50%; background: transparent; color: var(--text-primary); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background 0.2s; margin-right: 8px; }
.plus-btn:hover { background: transparent; border: 1px solid var(--border-solid); }
.staged-preview { display: flex; padding: 0 0 8px 0; gap: 8px; }
.staged-preview-item { position: relative; width: 72px; height: 72px; border-radius: 12px; overflow: hidden; border: 2px solid var(--border-solid); background: var(--bg-card); }
.staged-preview-item img { width: 100%; height: 100%; object-fit: cover; }
.staged-remove { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border: none; border-radius: 50%; background: rgba(0,0,0,0.55); color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; padding: 0; transition: background 0.2s; }
.staged-remove:hover { background: rgba(0,0,0,0.75); }
.staged-file-item { position: relative; display: flex; align-items: center; gap: 8px; padding: 8px 32px 8px 10px; border-radius: 12px; border: 2px solid var(--border-solid); background: var(--bg-card); max-width: 240px; }
.file-icon { flex-shrink: 0; }
.file-info { display: flex; flex-direction: column; min-width: 0; }
.file-name { font-size: 13px; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { font-size: 11px; color: var(--text-faint); }
.staged-file-item .staged-remove { top: 50%; right: 6px; transform: translateY(-50%); }
.preview-slide-enter-active, .preview-slide-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.preview-slide-enter-from, .preview-slide-leave-to { opacity: 0; transform: translateY(6px); }
.plus-menu { position: absolute; bottom: calc(100% + 8px); left: 16px; min-width: 140px; background: var(--bg-primary); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-radius: 14px; box-shadow: var(--shadow-card); overflow: hidden; z-index: 1100; border: 1px solid var(--border-solid); }
.plus-menu-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; font-size: 13px; color: var(--text-secondary); cursor: pointer; transition: background 0.2s; }
.plus-menu-item:hover { background: var(--accent-hover); }
.plus-menu-drop-enter-active, .plus-menu-drop-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.plus-menu-drop-enter-from, .plus-menu-drop-leave-to { opacity: 0; transform: translateY(6px); }
.capsule-input { flex: 1; border: none; outline: none; background: transparent; font-size: 14px; color: var(--text-primary); padding: 8px 0; resize: none; overflow-y: auto; max-height: 120px; font-family: inherit; line-height: 1.5; }
.capsule-input::-webkit-scrollbar { width: 3px; }
.capsule-input::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
.capsule-input::placeholder { color: var(--text-placeholder); }
.capsule-send { flex-shrink: 0; padding: 8px 20px; margin-right: 1px; border: none; border-radius: 12px; background: var(--accent); color: #fff; font-size: 14px; cursor: pointer; transition: opacity 0.2s; }
.capsule-send:disabled { opacity: 0.5; cursor: not-allowed; }
.capsule-send:not(:disabled):hover { opacity: 0.85; }
.expand-plan-content { max-height: 65vh; overflow-y: auto; }
.expand-plan-content::-webkit-scrollbar { width: 4px; }
.expand-plan-content::-webkit-scrollbar-thumb { background: var(--scrollbar-thumb); border-radius: 2px; }
/* Three-column workspace: compact profile / conversation / action sidebar */
.chat-view {
  display: grid;
  grid-template-columns: minmax(250px, 21vw) minmax(0, 1fr) minmax(280px, 304px);
  grid-template-rows: minmax(0, 1fr);
  gap: 0;
  transition: grid-template-columns 240ms ease;
}
.chat-view.sidebar-closed {
  grid-template-columns: minmax(250px, 21vw) minmax(0, 1fr) 0;
}
.left-panel,
.left-panel .panel-card { display: contents; }
.left-panel .card-header,
.left-panel .profile-content {
  grid-column: 1;
  min-width: 0;
}
.left-panel .card-header {
  grid-row: 1;
  align-self: start;
  display: block;
  padding: 22px 18px 0;
  margin: 0;
}
.left-panel .profile-heading { display: none; }
.left-panel .card-header-left,
.left-panel .card-tabs { display: none; }
.left-panel .profile-content {
  grid-row: 1;
  align-self: start;
  margin-top: 8px;
  padding: 0 12px;
  overflow: visible;
}
.left-panel .radar-layout {
  display: block;
  border: none;
  border-radius: 18px;
  background: transparent;
  box-shadow: none;
  padding: 6px 6px 11px;
}
.left-panel .radar-left { width: 100%; }
.left-panel .radar-right {
  margin: -18px 5px 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.left-panel .summary-row {
  min-width: 0;
  display: block;
  padding: 7px 8px;
  border-radius: 9px;
  background: transparent;
}
.left-panel .summary-row .label {
  display: block;
  width: auto;
  margin-bottom: 2px;
  font-size: 10px;
}
.left-panel .summary-row .value {
  display: block;
  font-size: 11px;
}
.chat-panel {
  grid-column: 2;
  display: flex;
  min-width: 0;
  min-height: 0;
  padding: 12px 16px;
}
.chat-panel .panel-card { width: 100%; }
.left-panel .plan-tab-content {
  grid-column: 3;
  grid-row: 1;
  position: relative;
  width: 304px;
  min-width: 304px;
  padding: 58px 16px 16px;
  overflow-y: auto;
  background: var(--bg-secondary);
  border-left: none;
  box-shadow: none;
  transition: opacity 240ms ease, transform 240ms ease, width 240ms ease, min-width 240ms ease, padding 240ms ease;
}
.chat-view.sidebar-closed .left-panel .plan-tab-content {
  opacity: 0;
  transform: translateX(20px);
  width: 0;
  min-width: 0;
  padding: 0;
  border-left: none;
  overflow: hidden;
}
.left-panel .plan-tab-content::before {
  content: '计划与成果';
  position: absolute;
  top: 25px;
  left: 21.5%;
  transform: translateX(-50%);
  color: var(--text-secondary);
  font-size: 16px;
  font-weight: 700;
}
.left-panel .plan-tab-content::after {
  content: '';
  display: none;
}
.plan-header-actions {
  display: flex;
  gap: 2px;
}
.plan-header-btns {
  position: absolute;
  top: 22px;
  right: 16px;
  z-index: 10;
  display: flex;
  gap: 4px;
}
.expand-plan-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-solid);
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--accent);
  cursor: pointer;
  transition: background 0.2s;
}
.expand-plan-btn:hover {
  background: var(--bg-hover);
}
.left-panel .plan-empty-state { padding: 70px 12px 28px; }
.left-panel .plan-footer { flex-wrap: wrap; }
.left-panel .submit-file-btn {
  width: 100%;
  justify-content: center;
  padding: 9px 12px;
  border-color: var(--border-solid);
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 10px;
}
.sidebar-toggle {
  grid-column: 3;
  grid-row: 1;
  justify-self: start;
  align-self: start;
  z-index: 20;
  width: 32px;
  height: 32px;
  margin: 22px 0 0 -16px;
  border: 1px solid var(--border-solid);
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--accent);
  cursor: pointer;
  transition: background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.sidebar-toggle:hover { background: var(--bg-hover); }
.sidebar-toggle svg { width: 15px; height: 15px; fill: none; stroke: currentColor; stroke-width: 1.8; }
.chat-view.sidebar-closed .sidebar-toggle { transform: rotate(180deg); }
.chat-view.sidebar-closed .plan-tab-content {
  opacity: 0;
  transform: translateX(20px);
  pointer-events: none;
  overflow: hidden;
  min-width: 0;
  width: 0;
  padding: 0;
  border: none;
}

/* Radar and planning now share the collapsible right sidebar. */
.chat-view {
  position: relative;
  grid-template-columns: minmax(250px, 21vw) minmax(0, 1fr) 304px;
}
.chat-view.sidebar-closed {
  grid-template-columns: minmax(250px, 21vw) minmax(0, 1fr) 0;
}
.left-panel .card-header { display: none; }
.workspace-sidebar {
  grid-column: 3;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  width: 304px;
  min-width: 304px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--bg-secondary);
  transition: opacity 240ms ease, transform 240ms ease;
}
.left-panel .profile-content {
  flex: 0 0 auto;
  margin: 0;
  padding: 8px 12px 4px;
  overflow: visible;
  border-bottom: none;
}
.left-panel .profile-content::before {
  content: '';
  display: none;
}
.left-panel .radar-layout {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
  padding: 0 4px 2px;
}
.left-panel .radar-left {
  width: 100%;
  min-height: 320px;
  flex: 0 0 320px;
}
.left-panel .radar-right {
  width: calc(100% - 8px);
  margin: 0 auto;
  padding: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 8px;
}
.left-panel .summary-row {
  min-height: 0;
  display: flex;
  align-items: flex-start;
  gap: 5px;
  padding: 5px 7px;
  text-align: left;
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: inset 0 0 0 1px var(--border-solid);
}
.left-panel .summary-row:nth-child(n + 3) {
  grid-column: 1 / -1;
}
.left-panel .summary-row .label {
  flex: 0 0 auto;
  width: auto;
  margin: 1px 0 0;
  font-size: 11px;
  line-height: 1.35;
  color: var(--text-faint);
}
.left-panel .summary-row .value {
  min-width: 0;
  flex: 1;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  overflow-wrap: anywhere;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.35;
  color: var(--text-secondary);
}
.left-panel .plan-tab-content {
  flex: 1 1 auto;
  min-height: 0;
  width: auto;
  min-width: 0;
  padding: 58px 16px 16px;
  overflow-y: auto;
  background: transparent;
}
.chat-panel {
  grid-column: 2;
  padding-right: 20px;
}
.sidebar-toggle {
  grid-column: 3;
}
.submission-dock {
  position: absolute;
  right: 16px;
  z-index: 80;
  width: 272px;
  transform: translateY(-50%);
}
.submission-dock .submit-file-btn {
  width: 100%;
  min-height: 36px;
  justify-content: center;
  padding: 9px 12px;
  border-color: var(--border-solid);
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: none;
}
.chat-view.sidebar-closed .workspace-sidebar { display: none !important; }

@media (max-width: 980px) {
  .chat-view { grid-template-columns: minmax(210px, 27vw) minmax(0, 1fr) 280px; }
  .chat-view.sidebar-closed { grid-template-columns: minmax(210px, 27vw) minmax(0, 1fr) 0; }
  .workspace-sidebar { width: 280px; min-width: 280px; }
  .submission-dock { width: 248px; }
}
@media (max-width: 760px) {
  .chat-view, .chat-view.sidebar-closed { display: flex; flex-direction: column; overflow-y: auto; }
  .left-panel, .left-panel .panel-card { display: contents; }
  .workspace-sidebar { order: 2; width: 100%; min-width: 0; height: auto; overflow: visible; }
  .chat-panel { order: 1; flex: 1; min-height: 520px; padding: 0 12px 12px; }
  .left-panel .profile-content { padding: 16px 12px 12px; }
  .left-panel .plan-tab-content { width: 100%; min-height: 360px; border-left: none; }
  .sidebar-toggle { display: none; }
  .submission-dock {
    position: static;
    order: 3;
    width: calc(100% - 24px);
    margin: 0 12px 12px;
    transform: none;
  }
}
</style>

<style>
.plan-expand-overlay { backdrop-filter: blur(8px) !important; -webkit-backdrop-filter: blur(8px) !important; background-color: transparent !important; }
.el-input__wrapper { border-radius: 14px !important; background-color: var(--bg-input) !important; box-shadow: 0 0 0 1px var(--border-solid) inset !important; transition: background-color 0.2s, box-shadow 0.2s; }
.el-input__wrapper:hover { box-shadow: 0 0 0 1px var(--accent) inset !important; }
.el-input__wrapper.is-focus { box-shadow: 0 0 0 1px var(--accent) inset !important; }
.el-input__inner { color: var(--text-primary) !important; }
.el-input__inner::placeholder { color: var(--text-placeholder) !important; }
.el-input__suffix .el-icon { color: var(--text-faint) !important; }
.el-dialog { background: var(--bg-primary) !important; backdrop-filter: blur(20px) !important; -webkit-backdrop-filter: blur(20px) !important; border-radius: 20px !important; transform: translateX(160px); }
.el-dialog__header, .el-dialog__body { background: transparent !important; }
.el-dialog__title { color: var(--text-secondary) !important; padding-left: 6px; }
.el-dialog__headerbtn .el-icon { color: var(--text-muted) !important; }
.el-dialog__headerbtn:hover .el-icon { color: var(--text-muted) !important; background: transparent !important; }
.edit-modal-footer { display: flex; justify-content: flex-end; gap: 8px; }
.save-btn.el-button--primary { background: var(--accent) !important; border-color: var(--accent) !important; }
.save-btn.el-button--primary:hover { background: var(--accent-dark) !important; border-color: var(--accent-dark) !important; }
.edit-modal-footer .el-button { border-radius: 8px !important; }
.edit-modal-footer .el-button:last-child { margin-right: 4px; background: var(--bg-input) !important; border-color: var(--border-solid) !important; color: var(--text-muted) !important; }
.edit-modal-footer .el-button:last-child:hover { border-color: var(--accent) !important; color: var(--accent) !important; background: transparent !important; }
</style>
