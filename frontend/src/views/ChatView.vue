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
          <div class="plan-section-header">
            <span class="plan-section-title">计划与成果</span>
            <div class="plan-section-actions">
              <div v-if="planHasData" class="plan-header-btns">
                <button class="expand-plan-btn" aria-label="编辑学习计划" @click="showEditModal = true">
                  <UiIcon name="edit" />
                </button>
                <button class="expand-plan-btn" aria-label="展开学习计划" @click="showExpandModal = true">
                  <UiIcon name="expand" />
                </button>
              </div>
              <button class="expand-plan-btn generate-plan-btn" @click="handleGeneratePlan" :disabled="planLoading">
              <span>{{ planLoading ? '生成中...' : (planHasData ? '重新生成' : '生成学习计划') }}</span>
              </button>
            </div>
          </div>

          <PlanCard ref="planCardRef" />

          <!-- 评分结果显示 -->
          <div v-if="evaluationResult" class="evaluation-result">
            <div class="eval-header">
              <div>
                <span class="eval-agent">成长档案</span>
                <div class="eval-score-line">
                  <span class="eval-score">{{ evaluationResult.score }} 分</span>
                  <span v-if="evaluationResult.scoreDelta != null" class="eval-delta" :class="{ down: evaluationResult.scoreDelta < 0 }">
                    {{ evaluationResult.scoreDelta >= 0 ? '+' : '' }}{{ evaluationResult.scoreDelta }}
                  </span>
                  <span v-else class="eval-baseline">首次基线</span>
                </div>
              </div>
              <button class="eval-close" aria-label="关闭评分结果" @click="evaluationResult = null">
                <UiIcon name="close" />
              </button>
            </div>
            <div v-if="evaluationResult.status === 'EVALUATED'" class="eval-dimensions">
              <div v-for="dimension in evaluationDimensions" :key="dimension.key" class="eval-dimension">
                <span>{{ dimension.label }}</span>
                <div class="eval-progress"><i :style="{ width: `${dimension.value}%` }"></i></div>
                <b>{{ dimension.value }}</b>
              </div>
            </div>
            <p class="eval-analysis">{{ evaluationResult.analysis }}</p>
            <div v-if="evaluationResult.masteredPoints.length" class="eval-section">
              <strong>这次真正掌握了</strong>
              <ul><li v-for="item in evaluationResult.masteredPoints" :key="item">{{ item }}</li></ul>
            </div>
            <div v-if="evaluationResult.progressEvidence.length" class="eval-section">
              <strong>{{ evaluationResult.scoreDelta == null ? '成长起点' : '看得见的进步' }}</strong>
              <ul><li v-for="item in evaluationResult.progressEvidence" :key="item">{{ item }}</li></ul>
            </div>
            <div v-if="evaluationResult.behaviorLinks.length" class="eval-section eval-behavior">
              <strong>与你过去的学习联动</strong>
              <ul><li v-for="item in evaluationResult.behaviorLinks" :key="item">{{ item }}</li></ul>
            </div>
            <div v-if="evaluationResult.blessingText" class="eval-blessing">
              <span>“{{ evaluationResult.blessingText }}”</span>
              <button type="button" :disabled="growthVoiceLoading" @click="playGrowthBlessing">
                {{ growthVoiceLoading ? '生成语音中…' : '▶ 播放祝福' }}
              </button>
            </div>
            <p class="eval-suggestion"><b>下一步：</b>{{ evaluationResult.nextChallenge || evaluationResult.suggestion }}</p>
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
        <UiIcon name="upload" />
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
      <UiIcon name="chevron-right" />
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
            :voice-enabled="voiceStore.voiceEnabled"
            :speech-state="speechStateFor(msg.id)"
            @speak="speakMessage(msg)"
            @pause="pauseSpeech"
            @resume="resumeSpeech"
            @stop="stopSpeech"
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
                <button class="staged-remove" aria-label="移除图片" @click="clearStagedImage">
                  <UiIcon name="close" />
                </button>
              </div>
              <!-- 文件预览 -->
              <div v-if="stagedFile" class="staged-file-item">
                <div class="file-icon">
                  <UiIcon name="file" />
                </div>
                <div class="file-info">
                  <span class="file-name">{{ stagedFile.name }}</span>
                  <span class="file-size">{{ stagedFile.size }}</span>
                </div>
                <button class="staged-remove" aria-label="移除文件" @click="clearStagedFile">
                  <UiIcon name="close" />
                </button>
              </div>
            </div>
          </transition>
          <div class="artifact-mode-bar" aria-label="选择智能体输出资源类型">
            <button
              v-for="mode in artifactModes"
              :key="mode.value"
              type="button"
              :class="{ active: outputMode === mode.value }"
              :disabled="chatStore.isStreaming"
              @click="setOutputMode(mode.value)"
            >{{ mode.label }}</button>
          </div>
          <div ref="capsuleBarRef" class="capsule-bar">
            <!-- + 按钮 -->
            <button class="plus-btn" type="button" aria-label="添加图片或文件" :disabled="outputMode !== 'TEXT'" @click.stop="showPlusMenu = !showPlusMenu">
              <UiIcon name="plus" />
            </button>
            <textarea
              ref="inputRef"
              v-model="inputText"
              class="capsule-input"
              :placeholder="outputMode === 'IMAGE' ? '描述要生成的知识图、流程图或学习图片...' : stagedImage ? '输入关于图片的问题... (Enter 发送)' : stagedFile ? '输入关于文件的问题... (Enter 发送)' : '输入你的学习情况或问题... (Enter 发送 / Shift+Enter 换行)'"
              rows="1"
              @keydown.enter="handleEnter"
              @input="autoResize"
              :disabled="chatStore.isStreaming"
            />
            <button
              :disabled="(!inputText.trim() && !stagedImage && !stagedFile) || chatStore.isStreaming"
              @click="handleSend"
              class="capsule-send"
              type="button"
              aria-label="发送"
            >
              <UiIcon name="send" />
            </button>
          </div>
          <!-- + 按钮下拉菜单（与汉堡菜单样式一致） -->
          <transition name="plus-menu-drop">
            <div v-if="showPlusMenu" class="plus-menu" @click.stop>
              <div class="plus-menu-item" @click="handleImageUpload">
                <UiIcon name="image" />
                <span>图片</span>
              </div>
              <div class="plus-menu-item" @click="handleFileUpload">
                <UiIcon name="file" />
                <span>文件</span>
              </div>
            </div>
          </transition>
          <!-- 隐藏的文件选择器 -->
          <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="onImageSelected" />
          <input ref="fileInputRef" type="file" accept=".pdf,.docx,.txt" style="display:none" @change="onFileSelected" />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showSubmissionTaskDialog"
      title="选择这次要提交的任务"
      width="560px"
      align-center
      :lock-scroll="true"
      modal-class="plan-expand-overlay"
    >
      <p class="submission-task-tip">选择具体任务后，才能把这次成果和同一任务的上一版准确比较。</p>
      <div class="submission-task-list">
        <button
          v-for="task in submissionTaskOptions"
          :key="task.key"
          type="button"
          class="submission-task-option"
          :class="{ selected: selectedSubmissionTaskKey === task.key }"
          @click="selectedSubmissionTaskKey = task.key"
        >
          <span>第 {{ task.weekNumber }} 周 · {{ task.topic }}</span>
          <strong>{{ task.title }}</strong>
        </button>
      </div>
      <div class="acct-footer submission-task-footer">
        <el-button size="large" class="cancel-btn" @click="showSubmissionTaskDialog = false">取消</el-button>
        <button class="submit-btn" :disabled="!selectedSubmissionTask" @click="confirmSubmissionTask">
          选择成果文件
        </button>
      </div>
    </el-dialog>

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
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '../stores/chatStore'
import type { Message } from '../stores/chatStore'
import { useProfileStore } from '../stores/profileStore'
import { useAuthStore } from '../stores/authStore'
import { useVoiceStore } from '../stores/voiceStore'
import { sendMessage } from '../services/sse'
import {
  fetchProfile,
  parseFileApi,
  fetchSavedPlanApi,
  fetchVoiceAudio,
  fetchWelcomeVoice,
  generateConversationTitleApi,
  submitLearningResultApi,
  fetchSubmissionApi,
  fetchConversationSubmissionsApi,
  generateImageArtifactApi,
} from '../services/api'
import type { LearningPlan, SubmissionDetail } from '../services/api'
import { fallbackConversationTitle, readConversationTitles, saveConversationTitle } from '../services/conversationTitles'
import RadarChart from '../components/RadarChart.vue'
import PlanCard from '../components/PlanCard.vue'
import UiIcon from '../components/UiIcon.vue'
import MessageBubble from '../components/MessageBubble.vue'

const chatStore = useChatStore()
const profileStore = useProfileStore()
const authStore = useAuthStore()
const voiceStore = useVoiceStore()

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
const SIDEBAR_OPEN_KEY = 'edu-agent-sidebar-open'
const sidebarOpen = ref(localStorage.getItem(SIDEBAR_OPEN_KEY) !== 'false')
const showPlusMenu = ref(false)
type ArtifactOutputMode = 'TEXT' | 'IMAGE'
const outputMode = ref<ArtifactOutputMode>('TEXT')
const artifactModes: Array<{ value: ArtifactOutputMode; label: string }> = [
  { value: 'TEXT', label: '问答' },
  { value: 'IMAGE', label: '图片' },
]
const imageInputRef = ref<HTMLInputElement>()
const fileInputRef = ref<HTMLInputElement>()

// 暂存待发送的图片
const stagedImage = ref<{ previewUrl: string; base64: string; name: string } | null>(null)

// 暂存待发送的文件
const stagedFile = ref<{ file: File; name: string; size: string } | null>(null)

// ===== 文件提交 & AI 评分 =====
const submitting = ref(false)
let submissionRunId = 0

function cancelSubmissionPolling() {
  submissionRunId++
  submitting.value = false
}
interface GrowthResult {
  status: SubmissionDetail['status']
  score: number
  scoreDelta: number | null
  analysis: string
  suggestion: string
  masteredPoints: string[]
  progressEvidence: string[]
  behaviorLinks: string[]
  dimensions: Record<string, number>
  nextChallenge: string
  blessingText: string
}

interface SubmissionTaskOption {
  key: string
  weekNumber: number
  taskIndex: number
  topic: string
  title: string
}

const evaluationResult = ref<GrowthResult | null>(null)
const showSubmissionTaskDialog = ref(false)
const selectedSubmissionTaskKey = ref('')
const growthVoiceLoading = ref(false)
let growthAudio: HTMLAudioElement | null = null
let growthAudioUrl = ''

const submissionTaskOptions = computed<SubmissionTaskOption[]>(() => {
  const plan = planCardRef.value?.plan as LearningPlan | null | undefined
  return (plan?.weeks || []).flatMap(week => (week.tasks || []).map((title, taskIndex) => ({
    key: `${week.weekNumber}-${taskIndex}`,
    weekNumber: week.weekNumber,
    taskIndex,
    topic: week.topic,
    title,
  })))
})

const selectedSubmissionTask = computed(() =>
  submissionTaskOptions.value.find(task => task.key === selectedSubmissionTaskKey.value) || null)

const evaluationDimensions = computed(() => {
  const labels: Record<string, string> = {
    completion: '完成度', accuracy: '准确性', depth: '理解深度',
    practice: '实践性', expression: '表达',
  }
  return Object.entries(labels).map(([key, label]) => ({
    key,
    label,
    value: Math.max(0, Math.min(100, Number(evaluationResult.value?.dimensions[key] || 0))),
  }))
})

function parseJsonArray(value?: string): string[] {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : []
  } catch {
    return []
  }
}

function parseJsonObject(value?: string): Record<string, number> {
  if (!value) return {}
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(Object.entries(parsed).map(([key, score]) => [key, Number(score) || 0]))
  } catch {
    return {}
  }
}

function transientGrowthResult(analysis: string, suggestion: string): GrowthResult {
  return {
    status: 'ERROR', score: 0, scoreDelta: null, analysis, suggestion,
    masteredPoints: [], progressEvidence: [], behaviorLinks: [], dimensions: {},
    nextChallenge: suggestion, blessingText: '',
  }
}

function applySubmissionResult(detail?: SubmissionDetail) {
  clearGrowthVoice()
  if (!detail) {
    evaluationResult.value = null
    return
  }
  if (detail.status === 'EVALUATED' && detail.evaluation) {
    const evaluation = detail.evaluation
    evaluationResult.value = {
      status: detail.status,
      score: evaluation.score,
      scoreDelta: evaluation.scoreDelta ?? null,
      analysis: evaluation.analysis,
      suggestion: evaluation.suggestion,
      masteredPoints: parseJsonArray(evaluation.masteredPointsJson),
      progressEvidence: parseJsonArray(evaluation.progressEvidenceJson),
      behaviorLinks: parseJsonArray(evaluation.behaviorLinksJson),
      dimensions: parseJsonObject(evaluation.dimensionsJson),
      nextChallenge: evaluation.nextChallenge || '',
      blessingText: evaluation.blessingText || '',
    }
    return
  }
  if (detail.status === 'ERROR') {
    evaluationResult.value = transientGrowthResult(
      detail.errorMessage || 'AI 评分失败', '请检查学习成果内容后重新提交。')
    return
  }
  evaluationResult.value = transientGrowthResult(
    '正在整理这次成长档案，结果会自动保存。', '稍后重新打开当前对话即可查看。')
  evaluationResult.value.status = detail.status
}

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

  if (outputMode.value !== 'TEXT') {
    if (!text) {
      ElMessage.warning('请先描述希望生成的资源内容')
      return
    }
    if (hasImage || hasFile) {
      ElMessage.warning('图片生成暂不支持同时上传附件，请先移除附件')
      return
    }
    await generateImageArtifact(text)
    return
  }

  // 如果有文件，先解析文件内容
  if (hasFile) {
    chatStore.startStreaming()
    try {
      const userId = authStore.user?.id
      const conversationId = chatStore.conversationId
      const result = await parseFileApi(stagedFile.value!.file, {
        userId,
        conversationId,
        purpose: 'CHAT',
      })
      window.dispatchEvent(new Event('file-history-updated'))
      if (!result.text?.trim()) throw new Error('文件中没有可提取的文本')
      const fileContent = result.text
      const msgContent = text || `请分析这个文件：${stagedFile.value!.name}`
      const displayContent = `${msgContent}\n\n📄 文件：${stagedFile.value!.name}`
      const fullMessage = `${msgContent}\n\n---\n📄 文件：${stagedFile.value!.name}\n\n${fileContent}\n---`

      chatStore.addMessage({
        id: Date.now().toString(),
        role: 'user',
        content: displayContent,
        timestamp: Date.now(),
      })

      const attachmentName = stagedFile.value!.name
      inputText.value = ''
      clearStagedFile()
      scrollToBottom()
      sendMessage(fullMessage, undefined, userId, {
        displayMessage: displayContent,
        attachmentName,
        attachmentType: 'file',
      })
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : '文件解析失败'
      chatStore.appendStreamContent(`\n\n[错误: ${errorMsg}]`)
      chatStore.finishStreaming()
    }
    return
  }

  // 图片或纯文本发送
  const msgContent = text || '请分析这张图片'
  const imageData = stagedImage.value?.base64
  const imageUrl = imageData
  const imageName = stagedImage.value?.name

  chatStore.addMessage({
    id: Date.now().toString(),
    role: 'user',
    content: msgContent,
    timestamp: Date.now(),
    ...(hasImage && { imageUrl, imageData }),
  })

  inputText.value = ''
  clearStagedImage()
  scrollToBottom()
  sendMessage(msgContent, imageData, authStore.user?.id, {
    displayMessage: msgContent,
    attachmentName: imageName,
    attachmentType: hasImage ? 'image' : undefined,
  })
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
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  if (file.size > 5 * 1024 * 1024) { alert('图片大小不能超过 5MB'); return }
  clearStagedImage()
  const reader = new FileReader()
  reader.onload = (event) => {
    const base64Data = event.target?.result as string
    stagedImage.value = { previewUrl: URL.createObjectURL(file), base64: base64Data, name: file.name }
  }
  reader.readAsDataURL(file)
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
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  if (file.size > 10 * 1024 * 1024) { alert('文件大小不能超过 10MB'); return }
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!['pdf', 'docx', 'txt'].includes(ext)) { alert('仅支持 PDF、Word (.docx)、TXT 文件'); return }
  clearStagedFile()
  stagedFile.value = { file, name: file.name, size: formatFileSize(file.size) }
}

function clearStagedFile() {
  stagedFile.value = null
}

// ===== AI 评分文件提交 =====

async function handleSubmissionUpload() {
  if (!submissionTaskOptions.value.length) {
    alert('当前学习计划里还没有可提交的具体任务')
    return
  }
  if (!selectedSubmissionTask.value) {
    selectedSubmissionTaskKey.value = submissionTaskOptions.value[0].key
  }
  showSubmissionTaskDialog.value = true
}

function confirmSubmissionTask() {
  if (!selectedSubmissionTask.value) return
  showSubmissionTaskDialog.value = false
  fileInputRef.value?.setAttribute('data-mode', 'submit')
  fileInputRef.value?.click()
}

async function handleSubmissionFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  if (file.size > 10 * 1024 * 1024) { alert('文件大小不能超过 10MB'); return }

  const runId = ++submissionRunId
  submitting.value = true
  evaluationResult.value = null

  try {
    // 1. 解析文件内容
    const conversationId = chatStore.conversationId
    if (!conversationId) throw new Error('当前对话尚未初始化')
    const result = await parseFileApi(file, {
      userId: authStore.user?.id,
      conversationId,
      purpose: 'SUBMISSION',
    })
    window.dispatchEvent(new Event('file-history-updated'))
    if (!result.text?.trim()) throw new Error('文件中没有可提取的文本')
    const fileContent = result.text
    const userId = authStore.user?.id
    if (!userId) { alert('请先登录'); return }
    const target = selectedSubmissionTask.value
    if (!target) throw new Error('请选择要提交的具体学习任务')

    // 2. 调用 AI 提交评分接口
    const submissionId = await submitLearningResultApi(
      userId, conversationId, file, fileContent, {
        weekNumber: target.weekNumber,
        taskIndex: target.taskIndex,
      })
    window.dispatchEvent(new Event('learning-activity-updated'))

    // 3. 轮询获取 AI 评分结果
    let retries = 70
    while (retries > 0) {
      await new Promise(r => setTimeout(r, 2000))
      if (runId !== submissionRunId || chatStore.conversationId !== conversationId) return
      const detail = await fetchSubmissionApi(userId, submissionId)
      if (runId !== submissionRunId || chatStore.conversationId !== conversationId) return
      if (detail.status === 'EVALUATED' && detail.evaluation) {
        applySubmissionResult(detail)
        window.dispatchEvent(new Event('learning-activity-updated'))
        break
      }
      if (detail.status === 'ERROR') {
        applySubmissionResult(detail)
        break
      }
      retries--
    }
    if (!evaluationResult.value) {
      evaluationResult.value = transientGrowthResult(
        'AI 评分超时，请稍后重试', '请稍后重新打开当前对话，或检查智能服务配置。')
    }
  } catch (err: unknown) {
    evaluationResult.value = transientGrowthResult(
      '提交失败: ' + (err instanceof Error ? err.message : '未知错误'), '请检查文件格式并重试。')
  } finally {
    if (runId === submissionRunId) submitting.value = false
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
  if (greetingTimer) clearInterval(greetingTimer)
  chatStore.startStreaming()
  const chars = [...text]
  let i = 0
  greetingTimer = setInterval(() => {
    if (i < chars.length) {
      chatStore.appendStreamContent(chars[i])
      i++
    } else {
      if (greetingTimer) clearInterval(greetingTimer)
      greetingTimer = null
      chatStore.finishStreaming()
    }
  }, 35)
}

function setOutputMode(mode: ArtifactOutputMode) {
  outputMode.value = mode
  showPlusMenu.value = false
  if (mode !== 'TEXT') {
    clearStagedImage()
    clearStagedFile()
  }
  void nextTick(() => inputRef.value?.focus())
}

async function generateImageArtifact(prompt: string) {
  chatStore.addMessage({
    id: Date.now().toString(), role: 'user', content: `【生成图片】${prompt}`, timestamp: Date.now(),
  })
  inputText.value = ''
  chatStore.startStreaming()
  scrollToBottom()
  try {
    const result = await generateImageArtifactApi(prompt, chatStore.conversationId || undefined)
    chatStore.finishStreaming()
    chatStore.addMessage({
      id: `${Date.now()}-image`, role: 'assistant',
      content: '学习图片已生成。',
      imageUrl: result.dataUrl, timestamp: Date.now(),
    })
  } catch (error) {
    chatStore.appendStreamContent(`\n\n[资源生成失败：${error instanceof Error ? error.message : '服务暂时不可用'}]`)
    chatStore.finishStreaming()
  } finally {
    await nextTick()
    autoResize()
    scrollToBottom()
  }
}

let greetingTimer: ReturnType<typeof setInterval> | null = null

type SpeechState = 'idle' | 'loading' | 'playing' | 'paused' | 'error'

const activeSpeechId = ref('')
const activeSpeechState = ref<SpeechState>('idle')
let speechAbortController: AbortController | null = null
let speechAudio: HTMLAudioElement | null = null
let speechAudioUrl = ''
let speechRunId = 0
let settleCurrentAudio: ((completed: boolean) => void) | null = null
let resumeAfterInteraction: (() => void) | null = null
let suppressNextAutoRead = false

function speechStateFor(messageId: string): SpeechState {
  return activeSpeechId.value === messageId ? activeSpeechState.value : 'idle'
}

function normalizeSpeechText(markdown: string): string {
  return markdown
    .replace(/```[\s\S]*?```/g, '。代码块已省略。')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+[.)]\s+/gm, '')
    .replace(/[>*_~|]/g, '')
    .replace(/https?:\/\/\S+/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function splitSpeechText(text: string, maxLength = 1200): string[] {
  if (text.length <= maxLength) return text ? [text] : []
  const sentences = text.split(/(?<=[。！？!?；;\n])/)
  const chunks: string[] = []
  let current = ''
  for (const sentence of sentences) {
    if (current && current.length + sentence.length > maxLength) {
      chunks.push(current.trim())
      current = ''
    }
    if (sentence.length > maxLength) {
      for (let offset = 0; offset < sentence.length; offset += maxLength) {
        if (current) {
          chunks.push(current.trim())
          current = ''
        }
        chunks.push(sentence.slice(offset, offset + maxLength).trim())
      }
    } else {
      current += sentence
    }
  }
  if (current.trim()) chunks.push(current.trim())
  return chunks.filter(Boolean)
}

function releaseSpeechAudio() {
  if (resumeAfterInteraction) {
    document.removeEventListener('pointerdown', resumeAfterInteraction)
    resumeAfterInteraction = null
  }
  speechAudio?.pause()
  speechAudio = null
  if (speechAudioUrl) {
    URL.revokeObjectURL(speechAudioUrl)
    speechAudioUrl = ''
  }
}

function stopSpeech() {
  speechRunId++
  speechAbortController?.abort()
  speechAbortController = null
  const settle = settleCurrentAudio
  settleCurrentAudio = null
  releaseSpeechAudio()
  settle?.(false)
  activeSpeechId.value = ''
  activeSpeechState.value = 'idle'
}

function pauseSpeech() {
  if (!speechAudio || activeSpeechState.value !== 'playing') return
  speechAudio.pause()
  activeSpeechState.value = 'paused'
}

async function resumeSpeech() {
  if (!speechAudio || activeSpeechState.value !== 'paused') return
  if (resumeAfterInteraction) {
    document.removeEventListener('pointerdown', resumeAfterInteraction)
    resumeAfterInteraction = null
  }
  try {
    await speechAudio.play()
    activeSpeechState.value = 'playing'
  } catch {
    activeSpeechState.value = 'paused'
  }
}

function playAudioBlob(blob: Blob, runId: number): Promise<boolean> {
  return new Promise(resolve => {
    if (runId !== speechRunId) return resolve(false)
    releaseSpeechAudio()
    speechAudioUrl = URL.createObjectURL(blob)
    const audio = new Audio(speechAudioUrl)
    speechAudio = audio
    let settled = false
    const finish = (completed: boolean) => {
      if (settled) return
      settled = true
      settleCurrentAudio = null
      releaseSpeechAudio()
      resolve(completed)
    }
    settleCurrentAudio = finish
    audio.addEventListener('ended', () => finish(true), { once: true })
    audio.addEventListener('error', () => finish(false), { once: true })
    activeSpeechState.value = 'playing'
    void audio.play().catch(() => {
      activeSpeechState.value = 'paused'
      const resume = () => {
        resumeAfterInteraction = null
        void resumeSpeech()
      }
      resumeAfterInteraction = resume
      document.addEventListener('pointerdown', resume, { once: true })
    })
  })
}

async function speakText(speechId: string, content: string) {
  const user = authStore.user
  const text = normalizeSpeechText(content)
  if (!voiceStore.voiceEnabled || !user || !text) return

  stopSpeech()
  const runId = speechRunId
  activeSpeechId.value = speechId
  activeSpeechState.value = 'loading'
  speechAbortController = new AbortController()

  try {
    for (const chunk of splitSpeechText(text)) {
      if (runId !== speechRunId || !voiceStore.voiceEnabled) return
      activeSpeechState.value = 'loading'
      const blob = await fetchVoiceAudio(
        user.id, user.username, chunk, '', speechAbortController.signal)
      if (!blob.size || runId !== speechRunId) return
      const completed = await playAudioBlob(blob, runId)
      if (!completed || runId !== speechRunId) return
    }
    if (runId === speechRunId) {
      activeSpeechId.value = ''
      activeSpeechState.value = 'idle'
      speechAbortController = null
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return
    console.info('语音暂不可用，文字内容不受影响。', error)
    ElMessage.warning(error instanceof Error ? error.message : '语音暂不可用，请稍后重试')
    if (runId === speechRunId) {
      activeSpeechState.value = 'error'
      speechAbortController = null
    }
  }
}

function speakMessage(message: Message) {
  if (message.role !== 'assistant') return
  void speakText(message.id, message.content)
}

function clearGrowthVoice() {
  growthAudio?.pause()
  growthAudio = null
  growthVoiceLoading.value = false
  if (growthAudioUrl) {
    URL.revokeObjectURL(growthAudioUrl)
    growthAudioUrl = ''
  }
}

async function playGrowthBlessing() {
  const text = evaluationResult.value?.blessingText
  if (!text || growthVoiceLoading.value) return
  if (growthAudio) {
    growthAudio.currentTime = 0
    await growthAudio.play().catch(() => undefined)
    return
  }
  growthVoiceLoading.value = true
  try {
    const style = '角色是一名温柔含蓄、长期陪伴用户学习的少女。声音轻柔平缓、连贯自然，带着发自内心的关怀和一点羞怯，咬字清晰，富有细腻感情顿挫，但不要活泼跳跃、夸张撒娇或带宗教仪式腔。'
    const blob = await fetchWelcomeVoice(authStore.user?.username || '', text, style)
    if (!blob.size) return
    growthAudioUrl = URL.createObjectURL(blob)
    growthAudio = new Audio(growthAudioUrl)
    await growthAudio.play()
  } catch (error) {
    console.warn('成长祝福语音生成失败:', error)
  } finally {
    growthVoiceLoading.value = false
  }
}

function startWelcomeGreeting() {
  if (chatStore.messages.length || chatStore.isStreaming) return
  const greeting = getGreeting()
  suppressNextAutoRead = true
  streamGreeting(greeting)
  if (voiceStore.autoReadEnabled && voiceStore.voiceEnabled) {
    void speakText(`welcome-${Date.now()}`, greeting)
  }
}

watch(
  () => chatStore.isStreaming,
  (streaming, previous) => {
    if (!previous || streaming) return
    const latest = chatStore.messages[chatStore.messages.length - 1]
    if (suppressNextAutoRead) {
      suppressNextAutoRead = false
      return
    }
    if (latest?.role === 'assistant' && voiceStore.autoReadEnabled && voiceStore.voiceEnabled) {
      speakMessage(latest)
    }
  },
)

watch(
  () => voiceStore.voiceEnabled,
  enabled => { if (!enabled) stopSpeech() },
)

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
      lastLoadedUserId = userId || null
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
    // 以输入框底部固定高度的发送按钮为锚点。文本域增加行数时只向上扩展，
    // 不再使用整个胶囊框的动态中心，避免右侧提交按钮跟着上移。
    const composerAnchor = composer.querySelector<HTMLElement>('.capsule-send') || composer
    const anchorRect = composerAnchor.getBoundingClientRect()
    submissionCenterY.value = anchorRect.top + anchorRect.height / 2 - containerRect.top
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

const titledMessageCounts = new Map<string, number>()
let titleAnalysisTimer: ReturnType<typeof setTimeout> | null = null

function buildConversationTitleContext() {
  return chatStore.messages
    .filter(message => message.role === 'user' || message.role === 'assistant')
    .slice(-30)
    .map(message => `${message.role === 'user' ? '用户' : '智能体'}：${message.content}`)
    .join('\n')
    .slice(-10000)
}

async function analyzeConversationTitle(conversationId: string, messageCount: number) {
  const context = buildConversationTitleContext()
  if (!context || !chatStore.messages.some(message => message.role === 'user')) return
  const userId = authStore.user?.id
  if (!userId) return
  let title = fallbackConversationTitle(chatStore.messages)
  try {
    title = await generateConversationTitleApi(userId, conversationId, context)
  } catch (err) {
    console.warn('会话标题分析失败，已使用本地简化标题', err)
  }
  saveConversationTitle(userId, conversationId, title)
  titledMessageCounts.set(conversationId, messageCount)
  if (chatStore.conversationId === conversationId) {
    chatStore.setConversationTitle(title)
  }
  window.dispatchEvent(new CustomEvent('conversation-title-updated', {
    detail: { conversationId, title },
  }))
}

watch(
  () => [chatStore.conversationId, chatStore.messages.length, chatStore.isStreaming] as const,
  ([conversationId, messageCount, streaming]) => {
    if (!conversationId || streaming || messageCount < 2) return
    const previousCount = titledMessageCounts.get(conversationId)
    if (previousCount === undefined) {
      const userId = authStore.user?.id
      const savedTitle = userId ? readConversationTitles(userId)[conversationId] : ''
      if (savedTitle) {
        titledMessageCounts.set(conversationId, messageCount)
        chatStore.setConversationTitle(savedTitle)
        return
      }
    } else if (messageCount - previousCount < 6) {
      return
    }

    if (titleAnalysisTimer) clearTimeout(titleAnalysisTimer)
    titleAnalysisTimer = setTimeout(() => {
      void analyzeConversationTitle(conversationId, messageCount)
    }, 900)
  },
  { immediate: true }
)

let workspaceLoadVersion = 0

async function loadConversationWorkspace(conversationId: string) {
  const userId = authStore.user?.id
  if (!userId || !conversationId) return
  const version = ++workspaceLoadVersion
  profileStore.resetProfile()
  planHasData.value = false
  planCardRef.value?.setPlan(null)
  clearGrowthVoice()
  evaluationResult.value = null

  try {
    const profile = await fetchProfile(userId, conversationId)
    if (version === workspaceLoadVersion && chatStore.conversationId === conversationId) {
      profileStore.setProfile(profile)
    }
  } catch (err) {
    console.warn('对话画像加载失败', err)
  }

  try {
    const savedPlan = await fetchSavedPlanApi(userId, conversationId)
    if (version !== workspaceLoadVersion || chatStore.conversationId !== conversationId) return
    if (savedPlan?.weeks?.length) {
      planHasData.value = true
      nextTick(() => planCardRef.value?.setPlan(savedPlan))
    }
  } catch (err) {
    console.warn('对话计划加载失败', err)
  }

  try {
    const submissions = await fetchConversationSubmissionsApi(userId, conversationId)
    if (version === workspaceLoadVersion && chatStore.conversationId === conversationId) {
      applySubmissionResult(submissions[0])
    }
  } catch (err) {
    console.warn('学习成果评分加载失败', err)
  }
}

function handleNewConversation(event: Event) {
  if (chatStore.isStreaming) return
  const conversationId = (event as CustomEvent<{ conversationId: string }>).detail?.conversationId
  if (!conversationId) return
  cancelSubmissionPolling()
  workspaceLoadVersion++
  chatStore.clearMessages()
  chatStore.setConversationId(conversationId)
  profileStore.resetProfile()
  planHasData.value = false
  planCardRef.value?.setPlan(null)
  clearGrowthVoice()
  evaluationResult.value = null
  stopSpeech()
  startWelcomeGreeting()
}

function handleConversationSelected(event: Event) {
  const conversationId = (event as CustomEvent<{ conversationId: string }>).detail?.conversationId
  if (!conversationId) return
  cancelSubmissionPolling()
  stopSpeech()
  clearGrowthVoice()
  evaluationResult.value = null
  void loadConversationWorkspace(conversationId)
}

function handleLearningPlanUpdated(event: Event) {
  const plan = (event as CustomEvent<LearningPlan>).detail
  if (!plan?.weeks?.length) return
  planHasData.value = true
  nextTick(() => planCardRef.value?.setPlan(plan))
}

onMounted(() => {
  messageListRef.value?.addEventListener('scroll', handleMsgScroll)
  document.addEventListener('click', closePlusMenu)
  alignmentObserver = new ResizeObserver(syncSubmissionCenter)
  if (chatViewRef.value) alignmentObserver.observe(chatViewRef.value)
  if (capsuleBarRef.value) alignmentObserver.observe(capsuleBarRef.value)
  window.addEventListener('resize', syncSubmissionCenter)
  window.addEventListener('learning-plan-updated', handleLearningPlanUpdated)
  window.addEventListener('new-conversation', handleNewConversation)
  window.addEventListener('conversation-selected', handleConversationSelected)
  nextTick(syncSubmissionCenter)
})

onUnmounted(() => {
  cancelSubmissionPolling()
  messageListRef.value?.removeEventListener('scroll', handleMsgScroll)
  document.removeEventListener('click', closePlusMenu)
  window.removeEventListener('resize', syncSubmissionCenter)
  window.removeEventListener('learning-plan-updated', handleLearningPlanUpdated)
  window.removeEventListener('new-conversation', handleNewConversation)
  window.removeEventListener('conversation-selected', handleConversationSelected)
  alignmentObserver?.disconnect()
  if (alignmentFrame !== null) cancelAnimationFrame(alignmentFrame)
  if (scrollTimer) clearTimeout(scrollTimer)
  if (titleAnalysisTimer) clearTimeout(titleAnalysisTimer)
  if (greetingTimer) clearInterval(greetingTimer)
  clearStagedImage()
  stopSpeech()
  clearGrowthVoice()
})

watch(sidebarOpen, value => {
  localStorage.setItem(SIDEBAR_OPEN_KEY, String(value))
  nextTick(syncSubmissionCenter)
})
</script>

<style scoped>
.chat-view { display: flex; height: 100%; gap: 0; }
.left-panel { width: 35%; min-width: 280px; display: flex; flex-direction: column; background: transparent; overflow-y: auto; padding: 12px; }
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
.submit-file-btn .ui-icon { width: 16px; height: 16px; }

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
.eval-agent { display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 2px; }
.eval-score-line { display: flex; align-items: baseline; gap: 8px; }
.eval-score { font-size: 22px; font-weight: 700; color: var(--accent); }
.eval-delta { font-size: 13px; font-weight: 700; color: #43a66d; }
.eval-delta.down { color: #c46d6d; }
.eval-baseline { font-size: 11px; color: var(--text-faint); background: var(--bg-input); padding: 2px 7px; border-radius: 999px; }
.eval-close { width: 28px; height: 28px; padding: 0; border: none; border-radius: 8px; background: none; color: var(--text-faint); cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.eval-close:hover { background: var(--bg-hover); color: var(--text-secondary); }
.eval-close .ui-icon { width: 16px; height: 16px; }
.eval-analysis { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin-bottom: 8px; }
.eval-suggestion { font-size: 13px; color: var(--accent); line-height: 1.6; background: var(--accent-hover); padding: 10px 14px; border-radius: 10px; margin: 0; }
.eval-dimensions { display: flex; flex-direction: column; gap: 6px; margin: 10px 0 12px; }
.eval-dimension { display: grid; grid-template-columns: 54px minmax(40px, 1fr) 24px; gap: 7px; align-items: center; font-size: 11px; color: var(--text-muted); }
.eval-dimension b { color: var(--text-secondary); font-weight: 600; text-align: right; }
.eval-progress { height: 6px; background: var(--bg-input); border-radius: 999px; overflow: hidden; }
.eval-progress i { display: block; height: 100%; background: linear-gradient(90deg, #d4916f, #b87858); border-radius: inherit; transition: width .4s ease; }
.eval-section { margin: 10px 0; padding: 10px 12px; border: 1px solid var(--border-solid); border-radius: 10px; }
.eval-section strong { font-size: 12px; color: var(--text-secondary); }
.eval-section ul { margin: 6px 0 0; padding-left: 17px; }
.eval-section li { margin: 3px 0; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.eval-behavior { background: var(--bg-input); }
.eval-blessing { display: flex; flex-direction: column; gap: 8px; margin: 12px 0; padding: 12px; border-radius: 12px; background: linear-gradient(135deg, var(--accent-hover), var(--bg-card)); color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
.eval-blessing button { align-self: flex-start; padding: 5px 9px; border: 1px solid var(--border-solid); border-radius: 8px; background: var(--bg-card); color: var(--accent); cursor: pointer; }
.eval-blessing button:disabled { opacity: .55; cursor: wait; }

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
.message-list { position: relative; z-index: 1; flex: 1; overflow-y: auto; padding: 0 10px; }
.loading-indicator { display: flex; gap: 4px; padding: 12px 0; }
.dot { width: 6px; height: 6px; background: var(--text-placeholder); border-radius: 50%; animation: bounce 1.2s infinite; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); } 30% { transform: translateY(-6px); } }
.input-area { position: relative; z-index: 5; padding: 12px 16px 0; background: transparent; flex-shrink: 0; margin-bottom: 16px; }
.artifact-mode-bar { min-height: 32px; display: flex; align-items: center; gap: 6px; margin: 0 5px 8px; }
.artifact-mode-bar button { min-width: 56px; height: 30px; padding: 0 12px; border: 1px solid var(--border-solid); border-radius: 9px; background: var(--ai-bubble-bg); color: var(--text-faint); font-size: 12px; font-weight: 600; cursor: pointer; transition: color .2s, border-color .2s, background .2s; }
.artifact-mode-bar button:hover:not(:disabled), .artifact-mode-bar button.active { border-color: var(--accent); background: var(--accent-hover); color: var(--accent); }
.artifact-mode-bar button:disabled { opacity: .45; cursor: not-allowed; }
.capsule-bar { display: flex; align-items: flex-end; background: var(--bg-input); backdrop-filter: blur(20px) saturate(1.3); -webkit-backdrop-filter: blur(20px) saturate(1.3); border: 1px solid var(--border-solid); border-radius: 16px; padding: 4px 4px 4px 4px; box-shadow: none; }
.plus-btn { flex-shrink: 0; width: 36px; height: 36px; padding: 0; border: 1px solid transparent; border-radius: 10px; background: transparent; color: var(--text-primary); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background 0.2s, border-color 0.2s; margin-right: 8px; }
.plus-btn .ui-icon { width: 19px; height: 19px; }
.plus-btn:hover { background: transparent; border: 1px solid var(--border-solid); }
.staged-preview { display: flex; padding: 0 0 8px 0; gap: 8px; }
.staged-preview-item { position: relative; width: 72px; height: 72px; border-radius: 12px; overflow: hidden; border: 2px solid var(--border-solid); background: var(--bg-card); }
.staged-preview-item img { width: 100%; height: 100%; object-fit: cover; }
.staged-remove { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border: none; border-radius: 50%; background: rgba(0,0,0,0.55); color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; padding: 0; transition: background 0.2s; }
.staged-remove:hover { background: rgba(0,0,0,0.75); }
.staged-remove .ui-icon { width: 12px; height: 12px; }
.staged-file-item { position: relative; display: flex; align-items: center; gap: 8px; padding: 8px 32px 8px 10px; border-radius: 12px; border: 2px solid var(--border-solid); background: var(--bg-card); max-width: 240px; }
.file-icon { flex-shrink: 0; color: var(--accent); }
.file-icon .ui-icon { width: 24px; height: 24px; }
.file-info { display: flex; flex-direction: column; min-width: 0; }
.file-name { font-size: 13px; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { font-size: 11px; color: var(--text-faint); }
.staged-file-item .staged-remove { top: 50%; right: 6px; transform: translateY(-50%); }
.preview-slide-enter-active, .preview-slide-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.preview-slide-enter-from, .preview-slide-leave-to { opacity: 0; transform: translateY(6px); }
.plus-menu { position: absolute; bottom: calc(100% + 8px); left: 16px; min-width: 140px; background: var(--bg-primary); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-radius: 14px; box-shadow: var(--shadow-card); overflow: hidden; z-index: 1100; border: 1px solid var(--border-solid); }
.plus-menu-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; font-size: 13px; color: var(--text-secondary); cursor: pointer; transition: background 0.2s; }
.plus-menu-item .ui-icon { width: 18px; height: 18px; }
.plus-menu-item:hover { background: var(--accent-hover); }
.plus-menu-drop-enter-active, .plus-menu-drop-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.plus-menu-drop-enter-from, .plus-menu-drop-leave-to { opacity: 0; transform: translateY(6px); }
.capsule-input { flex: 1; border: none; outline: none; background: transparent; font-size: 14px; color: var(--text-primary); padding: 8px 0; resize: none; overflow-y: auto; max-height: 120px; font-family: inherit; line-height: 1.5; }
.capsule-input::placeholder { color: var(--text-placeholder); }
.capsule-send { flex-shrink: 0; display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; padding: 0; margin-right: 1px; border: none; border-radius: 10px; background: var(--accent); color: #fff; cursor: pointer; transition: opacity 0.2s; }
.capsule-send .ui-icon { width: 19px; height: 19px; }
.capsule-send:disabled { opacity: 0.5; cursor: not-allowed; }
.capsule-send:not(:disabled):hover { opacity: 0.85; }
.expand-plan-content { max-height: 65vh; overflow-y: auto; }
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
  content: none;
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
.expand-plan-btn .ui-icon { width: 17px; height: 17px; }
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
.sidebar-toggle .ui-icon { width: 15px; height: 15px; }
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
  align-self: stretch;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
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
  grid-template-columns: minmax(0, 1fr);
  gap: 2px;
}
.left-panel .summary-row {
  min-height: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  text-align: left;
  border-radius: 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border-solid);
  box-shadow: none;
}
.left-panel .summary-row:nth-child(n + 3) {
  grid-column: 1 / -1;
}
.left-panel .summary-row .label {
  flex: 0 0 32px;
  width: 32px;
  margin: 0;
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
.plan-section-header {
  position: absolute;
  top: 20px;
  left: 16px;
  right: 16px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
}
.plan-section-title {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}
.plan-section-actions {
  min-width: 0;
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.generate-plan-btn {
  width: auto;
  min-width: 88px;
  height: 32px;
  margin: 0;
  padding: 0 9px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  text-align: center;
  white-space: nowrap;
}
.generate-plan-btn span {
  display: block;
  margin: 0;
  padding: 0;
  width: 100%;
  line-height: 1;
  text-align: center;
}
.generate-plan-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
.el-dialog { background: var(--bg-primary) !important; backdrop-filter: blur(20px) !important; -webkit-backdrop-filter: blur(20px) !important; border-radius: 20px !important; }
.el-dialog__header, .el-dialog__body { background: transparent !important; }
.el-dialog__title { color: var(--text-secondary) !important; padding-left: 6px; }
.el-dialog__headerbtn { color: var(--text-muted) !important; }
.el-dialog__headerbtn .el-dialog__close { display: none !important; }
.el-dialog__headerbtn::before,
.el-dialog__headerbtn::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 15px;
  height: 1.3px;
  border-radius: 999px;
  background: currentColor;
}
.el-dialog__headerbtn::before { transform: translate(-50%, -50%) rotate(45deg); }
.el-dialog__headerbtn::after { transform: translate(-50%, -50%) rotate(-45deg); }
.el-dialog__headerbtn:hover { color: var(--text-secondary) !important; background: transparent !important; }
.edit-modal-footer { display: flex; justify-content: flex-end; gap: 8px; }
.save-btn.el-button--primary { background: var(--accent) !important; border-color: var(--accent) !important; }
.save-btn.el-button--primary:hover { background: var(--accent-dark) !important; border-color: var(--accent-dark) !important; }
.edit-modal-footer .el-button { border-radius: 8px !important; }
.edit-modal-footer .el-button:last-child { margin-right: 4px; background: var(--bg-input) !important; border-color: var(--border-solid) !important; color: var(--text-muted) !important; }
.edit-modal-footer .el-button:last-child:hover { border-color: var(--accent) !important; color: var(--accent) !important; background: transparent !important; }
.submission-task-tip { margin: 0 0 12px; color: var(--text-muted); font-size: 13px; line-height: 1.6; }
.submission-task-list { display: flex; flex-direction: column; gap: 8px; max-height: 360px; overflow-y: auto; }
.submission-task-option { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; width: 100%; padding: 11px 13px; border: 1px solid var(--border-solid); border-radius: 12px; background: var(--bg-card); color: var(--text-secondary); cursor: pointer; text-align: left; }
.submission-task-option span { color: var(--text-faint); font-size: 11px; }
.submission-task-option strong { font-size: 13px; line-height: 1.5; font-weight: 600; }
.submission-task-option:hover, .submission-task-option.selected { border-color: var(--accent); background: var(--accent-hover); }
</style>
