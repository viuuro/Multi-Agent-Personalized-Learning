<template>
  <section class="practice-stats-pie" aria-label="练习进度统计">
    <div class="pie-summary">
      <span>练习概览</span>
      <strong>{{ accuracy }}% <small>正确率</small></strong>
    </div>
    <div ref="chartRef" class="pie-chart" aria-hidden="true"></div>
    <div class="pie-legend">
      <div v-for="item in legendItems" :key="item.key">
        <i :style="{ background: item.color }"></i>
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { PracticeQuestion } from '../services/api'
import { useThemeStore } from '../stores/themeStore'

const props = defineProps<{ questions: PracticeQuestion[] }>()
const themeStore = useThemeStore()
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const counts = computed(() => ({
  correct: props.questions.filter(question => question.status === 'SUBMITTED' && question.correct).length,
  wrong: props.questions.filter(question => question.status === 'SUBMITTED' && !question.correct).length,
  draft: props.questions.filter(question => question.status === 'DRAFT').length,
  unanswered: props.questions.filter(question => question.status === 'UNANSWERED').length,
}))

const submittedCount = computed(() => counts.value.correct + counts.value.wrong)
const accuracy = computed(() => submittedCount.value
  ? Math.round(counts.value.correct * 100 / submittedCount.value)
  : 0)

function cssColor(name: string, fallback: string) {
  const target = chartRef.value || document.documentElement
  return getComputedStyle(target).getPropertyValue(name).trim() || fallback
}

const legendItems = computed(() => {
  const isDark = themeStore.isDark
  return [
    { key: 'correct', label: '正确', value: counts.value.correct, color: cssColor('--practice-success', '#82d89a') },
    { key: 'wrong', label: '错误', value: counts.value.wrong, color: cssColor('--practice-danger', '#ff9082') },
    { key: 'draft', label: '作答中', value: counts.value.draft, color: cssColor('--accent', '#d4916f') },
    { key: 'unanswered', label: '待作答', value: counts.value.unanswered, color: cssColor('--practice-unanswered', isDark ? '#6e6864' : '#cec8c1') },
  ]
})

function renderChart() {
  if (!chart || !chartRef.value) return
  const items = legendItems.value
  const total = props.questions.length
  const textPrimary = cssColor('--text-primary', '#3d4255')
  const textFaint = cssColor('--text-faint', '#918781')
  const emptyColor = cssColor('--bg-hover', '#eee9e5')
  const data = total
    ? items.map(item => ({ name: item.label, value: item.value, itemStyle: { color: item.color } }))
        .filter(item => item.value > 0)
    : [{ name: '暂无题目', value: 1, itemStyle: { color: emptyColor } }]

  chart.setOption({
    animationDuration: 360,
    tooltip: {
      show: total > 0,
      trigger: 'item',
      appendTo: 'body',
      confine: false,
      formatter: '{b}<br/>{c} 题 · {d}%',
      backgroundColor: cssColor('--bg-primary', '#fff'),
      borderColor: cssColor('--border-solid', '#e0dcd6'),
      borderWidth: 1,
      textStyle: { color: textPrimary, fontSize: 11 },
      extraCssText: 'border-radius:10px;box-shadow:0 6px 20px rgba(0,0,0,.12);',
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '39%',
        style: { text: String(total), fill: textPrimary, fontSize: 23, fontWeight: 600, textAlign: 'center' },
      },
      {
        type: 'text',
        left: 'center',
        top: '56%',
        style: { text: '全部题目', fill: textFaint, fontSize: 9, textAlign: 'center' },
      },
    ],
    series: [{
      type: 'pie',
      radius: ['55%', '78%'],
      center: ['50%', '50%'],
      minAngle: 5,
      silent: total === 0,
      avoidLabelOverlap: true,
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderColor: cssColor('--bg-primary', '#fff'), borderWidth: 2, borderRadius: 4 },
      data,
    }],
  }, true)
}

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
  renderChart()
})

watch([() => props.questions, () => themeStore.isDark], async () => {
  await nextTick()
  renderChart()
}, { deep: true })

onUnmounted(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.practice-stats-pie {
  margin-top: 16px;
  padding: 8px 4px 0;
  border: 0;
  background: transparent;
}
.pie-summary { display: flex; align-items: center; justify-content: space-between; }
.pie-summary > span { color: var(--text-secondary); font-size: 12px; font-weight: 700; }
.pie-summary strong { color: var(--accent); font-size: 13px; font-weight: 650; }
.pie-summary small { color: var(--text-faint); font-size: 9px; font-weight: 500; }
.pie-chart { width: 100%; height: 164px; }
.pie-legend { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px 12px; }
.pie-legend div { min-width: 0; min-height: 30px; box-sizing: border-box; display: grid; grid-template-columns: 7px 1fr auto; align-items: center; gap: 6px; padding: 5px 8px; border: 1px solid var(--border-solid); border-radius: 9px; background: var(--bg-input); }
.pie-legend i { width: 7px; height: 7px; border-radius: 50%; }
.pie-legend span { overflow: hidden; color: var(--text-faint); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.pie-legend strong { color: var(--text-secondary); font-size: 11px; font-variant-numeric: tabular-nums; }
</style>
