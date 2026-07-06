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
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
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

/** 更新雷达图数据 */
function updateChart() {
  if (!chartInstance) return

  const p = profileStore.profile
  const hasData = p.knowledgeBase > 0 || p.learningPace > 0
  const colors = getAccentColors()

  // 6 个维度的指标定义（简化标签，避免换行遮挡）
  const indicator = [
    { name: '薄弱点', max: 10 },
    { name: '学习节奏', max: 10 },
    { name: '学习风格', max: 10 },
    { name: '目标明确度', max: 10 },
    { name: '兴趣广度', max: 10 },
    { name: '知识基础', max: 10 },
  ]

  // 将画像数据映射到雷达图数值（无数据时全部为 0）
  const cognitiveValue = !hasData ? 0 : p.cognitiveStyle === 'visual' ? 8 : p.cognitiveStyle === 'verbal' ? 6 : 5
  const weaknessValue = !hasData ? 0 : Math.max(1, 10 - p.weaknessPoints.length * 2)
  const interestValue = !hasData ? 0 : Math.min(10, p.interestAreas.length * 2 + 2)
  const goalValue = !hasData ? 0 : p.shortTermGoal ? Math.min(10, p.shortTermGoal.length / 2) : 2

  const option: echarts.EChartsOption = {
    animationDuration: 800,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'item',
      position: (point: [number, number]) => [point[0] + 14, point[1] + 14],
    },
    radar: {
      center: ['50%', '40%'],
      radius: '55%',
      indicator,
      axisName: {
        color: colors.textSecondary,
        fontSize: 11,
        borderRadius: 3,
        padding: [3, 5],
      },
      splitArea: {
        areaStyle: {
          color: [colors.splitLight, colors.splitDark],
        },
      },
      splitLine: {
        lineStyle: {
          color: colors.line,
        },
      },
      axisLine: {
        lineStyle: {
          color: colors.line,
        },
      },
    },
    series: [
      {
        name: '学生画像',
        type: 'radar',
        data: [
          {
            value: [
              weaknessValue,
              p.learningPace,
              cognitiveValue,
              goalValue,
              interestValue,
              p.knowledgeBase,
            ],
            name: '当前画像',
            areaStyle: {
              color: colors.accentGlow,
            },
            lineStyle: {
              color: colors.accent,
              width: 2,
            },
            itemStyle: {
              color: colors.accent,
            },
          },
        ],
      },
    ],
  }

  chartInstance.setOption(option)
}

// ===== 生命周期 =====

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})

function handleResize() {
  chartInstance?.resize()
}

// 监听画像数据变化，自动更新雷达图
watch(() => profileStore.profile, updateChart, { deep: true })

// 监听主题变化，更新雷达图颜色
const themeStore = useThemeStore()
watch(() => themeStore.isDark, updateChart)
</script>

<style scoped>
.radar-chart-container {
  position: relative;
  z-index: 2000;
  width: 100%;
  padding: 0;
}

.radar-canvas {
  width: 100%;
  height: 260px;
}
</style>
