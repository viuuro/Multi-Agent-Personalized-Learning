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
    <div ref="live2dRef" class="live2d-stage"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { loadOml2d } from 'oh-my-live2d'
import type { Oml2dProperties, Oml2dMethods, Oml2dEvents } from 'oh-my-live2d'

type Oml2dInstance = Oml2dProperties & Oml2dMethods & Oml2dEvents
const rootRef = ref<HTMLDivElement>()
const live2dRef = ref<HTMLDivElement>()
let oml2d: Oml2dInstance | null = null

// ===== 尺寸 =====
const baseScale = 0.262
const containerWidth = 485
const containerHeight = 500
const currentScale = ref(baseScale)
// 脚底锚点下推 + 左移，确保头部和胸部在可视区
const modelOffsetY = 1024
const modelOffsetX = -285

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
  if (!live2dRef.value) return

  oml2d = loadOml2d({
    dockedPosition: 'left',
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
    parentElement: live2dRef.value,
    sayHello: false,
    mobileDisplay: false,
  })

  oml2d.onLoad((status: string) => {
    if (status === 'success' && oml2d) {
      oml2d.setModelScale(currentScale.value)
      setTimeout(() => {
        oml2d?.setModelPosition({ x: modelOffsetX, y: modelOffsetY })
      }, 300)
      // 延迟 1 秒启用眼动追踪，避免加载时眼睛卡在异常位置
      setTimeout(() => {
        setupFocusTracking()
      }, 1000)
      nextTick(() => attachClickListener())
      // 兜底：canvas 可能延迟创建
      setTimeout(() => attachClickListener(), 1000)
    }
  })
})

onUnmounted(() => {
  if (docClickCleanup) docClickCleanup()
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
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transform: translate(18px, 0px);
  pointer-events: none;
}

.bg-line {
  font-size: 154px;
  font-weight: 800;
  color: rgba(212, 145, 111, 0.11);
  line-height: 0.85;
  letter-spacing: 0.15em;
  user-select: none;
}

.mari-wrapper {
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
}

.mari-bar {
  width: 98%;
  height: 128px;
  margin-top: 24px;
  background: rgba(212, 145, 111, 0.11);
  border-radius: 0;
  position: relative;
  /* 独立微调矩形位置：translateX=左右，translateY=上下（正=下移，负=上移） */
  transform: translateX(10px);
}

.live2d-stage {
  position: relative;
  width: 100%;
  height: 100%;
}
</style>
