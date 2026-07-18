<template>
  <!--
    ECharts 雷达图组件
    展示用户 6 维画像：知识基础、学习节奏、学习风格、薄弱点、兴趣广度、目标明确度
  -->
  <div class="radar-chart-container">
    <div ref="chartRef" class="radar-canvas"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { useProfileStore } from '../stores/profileStore'
import { useThemeStore } from '../stores/themeStore'

/**
 * 6 维画像雷达图组件
 *
 * 通过 pinia profileStore 获取画像数据并绑定到 ECharts 实例，
 * 当 profileStore.profile 变化时自动更新图表（响应式）。
 */

const profileStore = useProfileStore()
const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null
let resizeFrame: number | null = null
let settleTimer: ReturnType<typeof setTimeout> | null = null
let valueAnimationFrame: number | null = null

type RadarValues = [number, number, number, number, number, number]
const RADAR_ANIMATION_DURATION = 800
const EMPTY_RADAR_VALUES: RadarValues = [0, 0, 0, 0, 0, 0]
let displayedValues: RadarValues = [...EMPTY_RADAR_VALUES]
let hasRenderedValues = false

/** 初始化 ECharts 实例 */
function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

/** 获取当前主题的强调色 */
function getAccentColors() {
  const style = getComputedStyle(document.documentElement)
  const accent = style.getPropertyValue('--accent').trim() || '#D4916F'
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const borderColor = style.getPropertyValue('--border-solid').trim() || '#E0DCD6'
  return {
    accent,
    accentGlow: style.getPropertyValue('--accent-glow').trim() || 'rgba(212, 145, 111, 0.35)',
    splitLight: isDark ? 'rgba(212, 145, 111, 0.03)' : 'rgba(212, 145, 111, 0.05)',
    splitDark: isDark ? 'rgba(212, 145, 111, 0.06)' : 'rgba(212, 145, 111, 0.1)',
    textSecondary: style.getPropertyValue('--text-secondary').trim() || '#7A6A60',
    line: borderColor,
  }
}

/** 将画像数据转换为雷达图的 6 个维度值。 */
function getRadarValues(): RadarValues {
  const p = profileStore.profile
  const hasData = p.knowledgeBase > 0 || p.learningPace > 0
  const cognitiveValue = !hasData ? 0 : p.cognitiveStyle === 'visual' ? 8 : p.cognitiveStyle === 'verbal' ? 6 : 5
  const weaknessValue = !hasData ? 0 : Math.max(1, 10 - p.weaknessPoints.length * 2)
  const interestValue = !hasData ? 0 : Math.min(10, p.interestAreas.length * 2 + 2)
  const goalValue = !hasData ? 0 : p.shortTermGoal ? Math.min(10, p.shortTermGoal.length / 2) : 2

  return [weaknessValue, p.learningPace, cognitiveValue, goalValue, interestValue, p.knowledgeBase]
}

/** 使用同一组中间值同步重绘端点、折线和填充面。 */
function renderChart(values: RadarValues) {
  if (!chartInstance) return

  const colors = getAccentColors()
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
  const indicator = [
    { name: '薄弱\n点', max: 10 },
    { name: '学习\n节奏', max: 10 },
    { name: '学习\n风格', max: 10 },
    { name: '目标\n明确度', max: 10 },
    { name: '兴趣\n广度', max: 10 },
    { name: '知识\n基础', max: 10 },
  ]

  const option: echarts.EChartsOption = {
    animation: false,
    tooltip: {
      trigger: 'item',
      renderMode: 'html',
      appendTo: 'body',
      confine: false,
      position: (point, _params, dom) => {
        const tooltipWidth = dom instanceof HTMLElement ? dom.offsetWidth : 100
        return [point[0] - tooltipWidth - 10, point[1] - 10]
      },
      backgroundColor: isDark ? 'rgba(40, 36, 32, 0.95)' : 'rgba(255, 255, 255, 0.95)',
      borderColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.08)',
      borderWidth: 1,
      textStyle: {
        color: isDark ? '#ece6e0' : '#3D4255',
        fontSize: 12,
      },
      extraCssText: 'backdrop-filter: blur(12px); border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,0.15); z-index: 9999;',
    },
    radar: {
      center: ['50%', '50%'],
      radius: '65%',
      indicator,
      axisName: {
        color: colors.textSecondary,
        fontSize: 12,
        lineHeight: 15,
        align: 'center',
        borderRadius: 3,
        padding: [3, 5],
      },
      splitArea: {
        areaStyle: { color: [colors.splitLight, colors.splitDark] },
      },
      splitLine: {
        lineStyle: { color: colors.line },
      },
      axisLine: {
        lineStyle: { color: colors.line },
      },
    },
    series: [
      {
        id: 'learning-profile-radar',
        name: '学生画像',
        type: 'radar',
        data: [
          {
            value: values,
            name: '当前画像',
            areaStyle: { color: colors.accentGlow },
            lineStyle: { color: colors.accent, width: 2 },
            itemStyle: { color: colors.accent },
          },
        ],
      },
    ],
  }

  chartInstance.setOption(option, { lazyUpdate: false })
}

/**
 * 对六个维度统一做数值插值，避免 ECharts 将标记点与雷达面片分开更新。
 */
function updateChart(animate = true) {
  if (!chartInstance) return

  const targetValues = getRadarValues()
  if (valueAnimationFrame !== null) {
    cancelAnimationFrame(valueAnimationFrame)
    valueAnimationFrame = null
  }

  if (!hasRenderedValues) {
    displayedValues = [...EMPTY_RADAR_VALUES]
    renderChart(displayedValues)
    hasRenderedValues = true
  }

  const startValues = [...displayedValues] as RadarValues
  const valuesChanged = targetValues.some((value, index) => Math.abs(value - startValues[index]) > 0.001)
  if (!animate || !valuesChanged) {
    displayedValues = [...targetValues]
    renderChart(displayedValues)
    return
  }

  const startedAt = performance.now()
  const animateFrame = (now: number) => {
    const progress = Math.min(1, (now - startedAt) / RADAR_ANIMATION_DURATION)
    const easedProgress = 1 - Math.pow(1 - progress, 3)
    displayedValues = startValues.map((startValue, index) => (
      startValue + (targetValues[index] - startValue) * easedProgress
    )) as RadarValues
    renderChart(displayedValues)

    if (progress < 1) {
      valueAnimationFrame = requestAnimationFrame(animateFrame)
    } else {
      displayedValues = [...targetValues]
      valueAnimationFrame = null
      renderChart(displayedValues)
    }
  }

  valueAnimationFrame = requestAnimationFrame(animateFrame)
}

// ===== 生命周期 =====

onMounted(() => {
  initChart()
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(scheduleResize)
    resizeObserver.observe(chartRef.value)
  }
  window.addEventListener('resize', handleResize)
  scheduleResize()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  if (resizeFrame !== null) cancelAnimationFrame(resizeFrame)
  if (valueAnimationFrame !== null) cancelAnimationFrame(valueAnimationFrame)
  if (settleTimer) clearTimeout(settleTimer)
  chartInstance?.dispose()
})

function handleResize() {
  scheduleResize()
}

/**
 * 侧边栏动画期间可能短暂出现 0px 或中间宽度。
 * 仅在容器恢复到有效尺寸后重绘，并在动画结束后再校准一次。
 */
function scheduleResize() {
  if (resizeFrame !== null) cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => resizeToContainer())

  if (settleTimer) clearTimeout(settleTimer)
  settleTimer = setTimeout(() => resizeToContainer(), 280)
}

function resizeToContainer() {
  const element = chartRef.value
  if (!element || !chartInstance) return

  const { width, height } = element.getBoundingClientRect()
  if (width < 220 || height < 300) return

  chartInstance.resize({
    width: Math.round(width),
    height: Math.round(height),
    animation: { duration: 0 },
  })
}

// 监听画像数据变化，自动更新雷达图
watch(() => profileStore.profile, () => updateChart(), { deep: true })

// 监听主题变化，更新雷达图颜色
const themeStore = useThemeStore()
watch(() => themeStore.isDark, () => renderChart(displayedValues))
</script>

<style scoped>
.radar-chart-container {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 320px;
  min-height: 320px;
  max-height: 320px;
  padding: 0;
  overflow: visible;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.radar-canvas {
  width: 100%;
  height: 100%;
  min-width: 0;
  margin: 0 auto;
}
</style>
