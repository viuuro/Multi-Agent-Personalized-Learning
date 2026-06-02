import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 聊天状态管理 (Pinia Store)
 *
 * 管理核心聊天数据：
 *   - messages:        消息列表（含用户消息和 AI 回复）
 *   - conversationId:  当前会话 ID（用于关联同一轮对话）
 *   - isStreaming:     是否正在接收流式响应
 *   - streamingContent: 流式接收过程中累积的内容缓存
 */

/** 单条消息的数据结构 */
export interface Message {
  id: string                        // 消息唯一 ID
  role: 'user' | 'assistant' | 'system'  // 消息发送者
  content: string                   // 消息文本内容
  timestamp: number                 // 消息时间戳
  imageUrl?: string                 // 图片 URL（可选，用于显示预览）
  imageData?: string                // 图片 Base64 数据（可选，用于发送给后端）
}

export const useChatStore = defineStore('chat', () => {
  /** 当前会话的全部消息列表 */
  const messages = ref<Message[]>([])

  /** 当前会话 ID，首次对话时后端返回 */
  const conversationId = ref<string>('')

  /** 是否正在接收 SSE 流式响应 */
  const isStreaming = ref(false)

  /** 流式接收中的文本缓存（UI 实时渲染） */
  const streamingContent = ref('')

  /** 添加一条消息到列表 */
  function addMessage(msg: Message) {
    messages.value.push(msg)
  }

  /** 开始接收流式响应：设置标志位并清空缓存 */
  function startStreaming() {
    isStreaming.value = true
    streamingContent.value = ''
  }

  /** 追加流式文本片段（每次 SSE 推送调用） */
  function appendStreamContent(text: string) {
    streamingContent.value += text
  }

  /** 流式响应完成：将缓存的文本添加到消息列表并重置状态 */
  function finishStreaming() {
    if (streamingContent.value) {
      messages.value.push({
        id: Date.now().toString(),
        role: 'assistant',
        content: streamingContent.value,
        timestamp: Date.now(),
      })
    }
    streamingContent.value = ''
    isStreaming.value = false
  }

  /** 设置会话 ID（首次对话时由 SSE conversation 事件触发） */
  function setConversationId(id: string) {
    conversationId.value = id
  }

  /** 清空当前会话 */
  function clearMessages() {
    messages.value = []
    conversationId.value = ''
  }

  return {
    messages,
    conversationId,
    isStreaming,
    streamingContent,
    addMessage,
    startStreaming,
    appendStreamContent,
    finishStreaming,
    setConversationId,
    clearMessages,
  }
})
