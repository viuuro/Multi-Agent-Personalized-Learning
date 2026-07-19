<template>
  <!--
    伊洛玛丽 Live2D 看板娘 — 左下角固定，仅显示胸部及以上
    - 左键点击模型：随机表情 → 2.5s 后恢复默认
    - 提示气泡在模型右侧
  -->
  <div
    class="live2d-fixed"
    :style="{ width: containerWidth + 'px', height: containerHeight + 'px' }"
    ref="rootRef"
  >
    <div class="live2d-bg-text">
      <span class="bg-line">I RO</span>
      <span class="mari-wrapper">
        <span class="bg-line">MARI</span>
        <div class="mari-bar"></div>
      </span>
    </div>
    <div ref="live2dRef" class="live2d-stage" :class="{ ready: modelReady }"></div>

    <transition name="voice-menu-pop">
      <div
        v-if="menuOpen"
        class="voice-context-card"
        :style="{ left: `${menuX}px`, top: `${menuY}px` }"
        @click.stop
        @contextmenu.prevent
      >
        <div class="voice-menu-title">语音设置</div>
        <button
          class="voice-setting-row"
          type="button"
          role="switch"
          :aria-checked="voiceStore.voiceEnabled"
          @click="voiceStore.setVoiceEnabled(!voiceStore.voiceEnabled)"
        >
          <span class="voice-setting-label"><UiIcon name="volume" />语音</span>
          <span class="voice-switch" :class="{ active: voiceStore.voiceEnabled }"><i></i></span>
        </button>
        <button
          class="voice-setting-row"
          type="button"
          role="switch"
          :aria-checked="voiceStore.autoReadEnabled"
          @click="voiceStore.setAutoReadEnabled(!voiceStore.autoReadEnabled)"
        >
          <span class="voice-setting-label"><UiIcon name="sparkles" />自动朗读</span>
          <span class="voice-switch" :class="{ active: voiceStore.autoReadEnabled }"><i></i></span>
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { loadOml2d } from 'oh-my-live2d'
import type { Oml2dProperties, Oml2dMethods, Oml2dEvents } from 'oh-my-live2d'
import UiIcon from './UiIcon.vue'
import { useVoiceStore } from '../stores/voiceStore'

type Oml2dInstance = Oml2dProperties & Oml2dMethods & Oml2dEvents
const rootRef = ref<HTMLDivElement>()
const live2dRef = ref<HTMLDivElement>()
const modelReady = ref(false)
const voiceStore = useVoiceStore()
let oml2d: Oml2dInstance | null = null
let disposed = false
const initializationTimers = new Set<ReturnType<typeof setTimeout>>()

const menuOpen = ref(false)
const menuX = ref(126)
const menuY = ref(196)
let contextMenuCleanup: (() => void) | null = null

function isPointOnModel(clientX: number, clientY: number) {
  if (!modelReady.value) return false
  const canvas = live2dRef.value?.querySelector('canvas')
  const model = getInternalModel()
  if (!canvas || !model) return false

  const rect = canvas.getBoundingClientRect()
  if (clientX < rect.left || clientX > rect.right
    || clientY < rect.top || clientY > rect.bottom) return false

  const rendererScreen = (oml2d as any)?.pixiApp?.app?.renderer?.screen
  const stageWidth = Number(rendererScreen?.width) || rect.width
  const stageHeight = Number(rendererScreen?.height) || rect.height
  const point = {
    x: (clientX - rect.left) * stageWidth / rect.width,
    y: (clientY - rect.top) * stageHeight / rect.height,
  }

  try {
    return typeof model.containsPoint === 'function' && model.containsPoint(point)
  } catch {
    return false
  }
}

function attachContextMenu() {
  const openMenu = (event: MouseEvent) => {
    const container = rootRef.value
    if (!container) return
    const rect = container.getBoundingClientRect()
    // 外层容器不接管鼠标事件，因此按 PIXI 中模型的实际边界判断命中。
    // 这样既能响应模型右键，又不会遮挡其透明区域下方的左侧功能。
    if (!isPointOnModel(event.clientX, event.clientY)) return

    event.preventDefault()
    event.stopPropagation()
    const cardWidth = 210
    const cardHeight = 136
    menuX.value = Math.max(8, Math.min(event.clientX - rect.left, rect.width - cardWidth - 8))
    menuY.value = Math.max(8, Math.min(event.clientY - rect.top, rect.height - cardHeight - 8))
    menuOpen.value = true
  }
  const closeMenu = (event: PointerEvent) => {
    const target = event.target as Element | null
    if (!target?.closest('.voice-context-card')) menuOpen.value = false
  }
  const closeOnEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape') menuOpen.value = false
  }

  document.addEventListener('contextmenu', openMenu, true)
  document.addEventListener('pointerdown', closeMenu, true)
  document.addEventListener('keydown', closeOnEscape)
  contextMenuCleanup = () => {
    document.removeEventListener('contextmenu', openMenu, true)
    document.removeEventListener('pointerdown', closeMenu, true)
    document.removeEventListener('keydown', closeOnEscape)
  }
}

// ===== 尺寸 =====
const baseScale = 0.210
const containerWidth = 360
const containerHeight = 410
const currentScale = ref(baseScale)
// 脚底锚点下推 + 左移，确保头部和胸部在可视区
const modelOffsetY = 820
const modelOffsetX = -242

const expressions = [
  { id: 'panic', label: '慌张' },
  { id: 'grin', label: '露齿笑' },
]

// ===== 表情控制 =====
let expressionTimer: ReturnType<typeof setTimeout> | null = null

function getInternalModel(): any {
  return (oml2d as any)?.models?.model ?? null
}

function getExpressionManager(): any {
  const model = getInternalModel()
  return (model as any)?.internalModel?.motionManager?.expressionManager ?? null
}

function triggerExpression(expId: string) {
  const em = getExpressionManager()
  if (em && typeof em.setExpression === 'function') {
    try { em.setExpression(expId) } catch { /* ignore */ }
  }

  if (expressionTimer) clearTimeout(expressionTimer)
  expressionTimer = setTimeout(() => resetExpression(), 2500)
}

function triggerRandomExpression() {
  const exp = expressions[Math.floor(Math.random() * expressions.length)]
  triggerExpression(exp.id)
}

function resetExpression() {
  const em = getExpressionManager()
  if (!em) return

  // setExpression(undefined) 会触发 setRandomExpression
  // resetExpression() 才会回到 defaultExpression（中性表情）
  try {
    if (typeof em.resetExpression === 'function') {
      em.resetExpression()
    }
  } catch { /* ignore */ }
}

// ===== 点击监听（document 捕获阶段 + 容器坐标检测） =====
let docClickCleanup: (() => void) | null = null
function attachClickListener() {
  docClickCleanup?.()
  const handler = (e: MouseEvent) => {
    if (e.button !== 0) return

    // 点击不在容器矩形内 → 放行
    const container = rootRef.value
    if (!container) return
    const r = container.getBoundingClientRect()
    if (e.clientX < r.left || e.clientX > r.right ||
        e.clientY < r.top || e.clientY > r.bottom) return

    // 容器内的点击，但如果落在交互控件上 → 放行给 UI
    const target = e.target as Element
    if (target?.closest('input, button, textarea, a, .el-button, .el-collapse-item__header')) return

    e.stopPropagation()
    e.stopImmediatePropagation()
    triggerRandomExpression()
  }

  document.addEventListener('click', handler, true)
  docClickCleanup = () => document.removeEventListener('click', handler, true)
}

function scheduleInitialization(callback: () => void, delay: number) {
  const timer = setTimeout(() => {
    initializationTimers.delete(timer)
    if (!disposed) callback()
  }, delay)
  initializationTimers.add(timer)
}

// ===== 眼动追踪：直接控制 FocusController，绕开坐标变换 =====
let cleanupFocus: (() => void) | null = null
const eyeRatioX = 0.14 // 脸部中心在 canvas 中的水平位置
const eyeRatioY = 0.3  // 眼位占可见区高度的比例

function setupFocusTracking() {
  const model = getInternalModel()
  if (!model) return

  const fc = (model as any).internalModel?.focusController
  if (!fc) return

  ;(model as any).focus = function (gx: number, gy: number) {
    const canvas = live2dRef.value?.querySelector('canvas')
    if (!canvas) return

    const relX = gx / canvas.width
    const relY = gy / canvas.height

    // X: 光标在左→眼球左转 (fc.targetX < 0)，在右→右转 (fc.targetX > 0)
    if (relX < eyeRatioX) {
      fc.targetX = -(eyeRatioX - relX) / eyeRatioX
    } else {
      fc.targetX = (relX - eyeRatioX) / (1 - eyeRatioX)
    }

    // Y: 光标在上→眼球上转 (fc.targetY > 0)，在下→下转 (fc.targetY < 0)
    if (relY < eyeRatioY) {
      fc.targetY = (eyeRatioY - relY) / eyeRatioY
    } else {
      fc.targetY = -(relY - eyeRatioY) / (1 - eyeRatioY)
    }
  }

  // 鼠标离开 canvas 时回中
  const canvas = live2dRef.value?.querySelector('canvas')
  if (canvas) {
    const onLeave = () => { fc.targetX = 0; fc.targetY = 0 }
    canvas.addEventListener('mouseleave', onLeave)
    cleanupFocus = () => canvas.removeEventListener('mouseleave', onLeave)
  }
}

// ===== 初始化 =====
onMounted(() => {
  disposed = false
  if (!live2dRef.value) return
  attachContextMenu()

  oml2d = loadOml2d({
    dockedPosition: 'left',
    primaryColor: '#D4916F',
    models: [
      {
        name: 'iromari',
        path: '/models/iromari/mari.model3.json',
        scale: baseScale,
        position: [0, 0],
      },
    ],
    statusBar: { disable: true },
    menus: { disable: true },
    tips: {
      style: { display: 'none', backgroundColor: 'transparent', border: '0', boxShadow: 'none', filter: 'none' },
      mobileStyle: { display: 'none', backgroundColor: 'transparent', border: '0', boxShadow: 'none', filter: 'none' },
      idleTips: { wordTheDay: false, message: [] },
      welcomeTips: {
        duration: 0,
        message: { daybreak: '', morning: '', noon: '', afternoon: '', dusk: '', night: '', lateNight: '', weeHours: '' },
      },
      copyTips: { message: [] },
    },
    parentElement: live2dRef.value,
    sayHello: false,
    mobileDisplay: false,
  })

  oml2d.onLoad((status: string) => {
    if (status !== 'success') {
      modelReady.value = false
      return
    }
    if (status === 'success' && oml2d) {
      oml2d.setModelScale(currentScale.value)
      scheduleInitialization(() => {
        oml2d?.setModelPosition({ x: modelOffsetX, y: modelOffsetY })
        modelReady.value = true
      }, 300)
      // 延迟 1 秒启用眼动追踪，避免加载时眼睛卡在异常位置
      scheduleInitialization(() => {
        setupFocusTracking()
      }, 1000)
      nextTick(() => { if (!disposed) attachClickListener() })
      // 兜底：canvas 可能延迟创建
      scheduleInitialization(() => attachClickListener(), 1000)
    }
  })
})

onUnmounted(() => {
  disposed = true
  initializationTimers.forEach(timer => clearTimeout(timer))
  initializationTimers.clear()
  if (docClickCleanup) docClickCleanup()
  if (contextMenuCleanup) contextMenuCleanup()
  if (expressionTimer) clearTimeout(expressionTimer)
  if (cleanupFocus) cleanupFocus()
  if (live2dRef.value) {
    live2dRef.value.innerHTML = ''
  }
})

</script>

<style scoped>
.live2d-fixed {
  position: fixed;
  bottom: 0;
  left: 0;
  z-index: 1000;
  overflow: hidden;
  pointer-events: none;
}

.live2d-bg-text {
  display: none;
}

.bg-line {
  display: none;
}

.mari-wrapper {
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
}

.mari-bar {
  width: 98%;
  height: 104px;
  margin-top: 24px;
  background: var(--live2d-text);
  border-radius: 0;
  position: relative;
  /* 独立微调矩形位置：translateX=左右，translateY=上下（正=下移，负=上移） */
  transform: translateX(10px);
}

.live2d-stage {
  position: relative;
  width: 100%;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  box-shadow: none;
  opacity: 0;
  transition: opacity .18s ease;
}

.live2d-stage.ready { opacity: 1; }

.live2d-stage :deep(#oml2d-stage),
.live2d-stage :deep(#oml2d-canvas),
.live2d-stage :deep(canvas) {
  border: 0 !important;
  outline: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.live2d-stage :deep(#oml2d-tips),
.live2d-stage :deep(#oml2d-statusBar),
.live2d-stage :deep(#oml2d-menus) {
  display: none !important;
}

.voice-context-card {
  position: absolute;
  /* oh-my-live2d 会给 canvas 注入 z-index: 9998，菜单必须位于其上方。 */
  z-index: 10000;
  width: 210px;
  padding: 8px;
  border: 1px solid var(--border-solid);
  border-radius: 14px;
  background: var(--bg-primary);
  box-shadow: none;
  backdrop-filter: blur(18px) saturate(1.15);
  -webkit-backdrop-filter: blur(18px) saturate(1.15);
  pointer-events: auto;
}

.voice-menu-title {
  padding: 5px 9px 7px;
  color: var(--text-faint);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: .04em;
}

.voice-setting-row {
  width: 100%;
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 9px;
  border: 0;
  border-radius: 10px;
  color: var(--text-secondary);
  background: transparent;
  cursor: pointer;
  font: inherit;
  transition: background .18s ease, color .18s ease;
}

.voice-setting-row:hover {
  color: var(--text-primary);
  background: var(--accent-hover);
}

.voice-setting-label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
}

.voice-setting-label .ui-icon { width: 17px; height: 17px; }

.voice-switch {
  position: relative;
  width: 32px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--border-solid);
  transition: background .2s ease;
}

.voice-switch i {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--bg-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, .14);
  transition: transform .2s ease;
}

.voice-switch.active { background: var(--accent); }
.voice-switch.active i { transform: translateX(14px); }

.voice-menu-pop-enter-active,
.voice-menu-pop-leave-active { transition: opacity .16s ease, transform .16s ease; }
.voice-menu-pop-enter-from,
.voice-menu-pop-leave-to { opacity: 0; transform: translateY(4px) scale(.98); }
</style>

<style>
/* 深色模式降低 Live2D 亮度 */
[data-theme="dark"] .live2d-stage canvas {
  filter: brightness(0.85);
}
</style>
