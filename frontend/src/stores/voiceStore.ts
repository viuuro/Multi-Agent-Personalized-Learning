import { defineStore } from 'pinia'
import { ref } from 'vue'

const VOICE_ENABLED_KEY = 'voice_enabled'
const AUTO_READ_ENABLED_KEY = 'voice_auto_read_enabled'

function readBoolean(key: string, fallback: boolean): boolean {
  const stored = localStorage.getItem(key)
  return stored === null ? fallback : stored === 'true'
}

/**
 * 全局语音偏好。
 *
 * 自动朗读依赖语音：切换自动朗读会同步语音总开关；关闭语音时也会
 * 关闭自动朗读。用户仍可在自动朗读关闭后单独开启语音，按需朗读消息。
 */
export const useVoiceStore = defineStore('voice', () => {
  const voiceEnabled = ref(readBoolean(VOICE_ENABLED_KEY, true))
  const autoReadEnabled = ref(readBoolean(AUTO_READ_ENABLED_KEY, false))

  if (!voiceEnabled.value) autoReadEnabled.value = false

  function persist() {
    localStorage.setItem(VOICE_ENABLED_KEY, String(voiceEnabled.value))
    localStorage.setItem(AUTO_READ_ENABLED_KEY, String(autoReadEnabled.value))
  }

  function setVoiceEnabled(enabled: boolean) {
    voiceEnabled.value = enabled
    if (!enabled) autoReadEnabled.value = false
    persist()
  }

  function setAutoReadEnabled(enabled: boolean) {
    autoReadEnabled.value = enabled
    voiceEnabled.value = enabled
    persist()
  }

  return {
    voiceEnabled,
    autoReadEnabled,
    setVoiceEnabled,
    setAutoReadEnabled,
  }
})
