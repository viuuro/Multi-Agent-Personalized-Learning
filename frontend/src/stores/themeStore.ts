import { defineStore } from 'pinia'
import { onScopeDispose, ref, watch } from 'vue'

/**
 * 主题状态管理
 *
 * 模式：
 *   - 'light'  — 强制浅色
 *   - 'dark'   — 强制深色
 *   - 'auto'   — 跟随系统
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<'light' | 'dark' | 'auto'>(
    (localStorage.getItem('theme-mode') as 'light' | 'dark' | 'auto') || 'auto'
  )

  /** 当前实际是否为深色（由 mode + 系统偏好共同决定） */
  const isDark = ref(false)

  /** 系统是否偏好深色 */
  const systemDark = ref(false)

  /** 监听系统偏好变化 */
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  systemDark.value = mq.matches
  const handleSystemThemeChange = (e: MediaQueryListEvent) => {
    systemDark.value = e.matches
    applyTheme()
  }
  mq.addEventListener('change', handleSystemThemeChange)
  onScopeDispose(() => mq.removeEventListener('change', handleSystemThemeChange))

  /** 计算并应用主题 */
  function applyTheme() {
    const dark = mode.value === 'dark' || (mode.value === 'auto' && systemDark.value)
    isDark.value = dark
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
    localStorage.setItem('theme-mode', mode.value)
  }

  /** 切换模式：auto → light → dark → auto */
  function toggleMode() {
    const order: ('auto' | 'light' | 'dark')[] = ['auto', 'light', 'dark']
    const idx = order.indexOf(mode.value)
    mode.value = order[(idx + 1) % order.length]
    applyTheme()
  }

  /** 直接设置模式 */
  function setMode(m: 'light' | 'dark' | 'auto') {
    mode.value = m
    applyTheme()
  }

  // 初始化
  applyTheme()

  watch(mode, applyTheme)

  return { mode, isDark, systemDark, toggleMode, setMode }
})
