import { useChatStore } from '../stores/chatStore'
import { useProfileStore } from '../stores/profileStore'
import type { UserProfile } from '../stores/profileStore'
import { apiFetch, type LearningPlan } from './api'

const STREAM_INACTIVITY_TIMEOUT_MS = 90_000

/**
 * SSE（Server-Sent Events）服务层
 *
 * 与 POST /api/chat 建立 SSE 连接，处理以下事件：
 *   - conversation:   收到会话 ID
 *   - message:        收到 AI 回复文本片段（逐字推送）
 *   - profile_update: 收到画像更新数据 → 刷新雷达图
 *   - done:           流式输出完成
 *   - error:          错误处理
 */

/**
 * 发送聊天消息并建立 SSE 流式连接
 *
 * @param message 用户输入的聊天文本
 * @param imageData 图片 Base64 数据（可选，用于多模态）
 *
 * 处理流程：
 *   1. 标记 streaming 状态开始
 *   2. fetch POST 到 /api/chat，请求体包含消息和会话 ID
 *   3. 读取 ReadableStream，按行解析 SSE 格式
 *   4. 每收到一个 SSE 事件，分发到对应 handler 更新 UI
 *   5. 连接中断时记录错误并结束 streaming
 */
export async function sendMessage(
  message: string,
  imageData?: string,
  _userId?: number,
  metadata?: { displayMessage?: string; attachmentName?: string; attachmentType?: 'image' | 'file' }
): Promise<void> {
  const chatStore = useChatStore()
  const profileStore = useProfileStore()

  // 标记流式接收开始（UI 显示 loading 动画）
  chatStore.startStreaming()
  const controller = new AbortController()
  let inactivityTimer: ReturnType<typeof setTimeout> | undefined
  const resetInactivityTimer = () => {
    if (inactivityTimer) clearTimeout(inactivityTimer)
    inactivityTimer = setTimeout(() => {
      controller.abort(new Error('AI 服务响应超时，请稍后重试'))
    }, STREAM_INACTIVITY_TIMEOUT_MS)
  }
  resetInactivityTimer()

  try {
    const response = await apiFetch('/api/chat', {
      method: 'POST',
      credentials: 'include',
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message,
        conversationId: chatStore.conversationId || undefined,
        imageData: imageData || undefined,
        displayMessage: metadata?.displayMessage || message,
        attachmentName: metadata?.attachmentName,
        attachmentType: metadata?.attachmentType,
      }),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const reader = response.body?.getReader()
    if (!reader) throw new Error('响应体不可读')

    const decoder = new TextDecoder()
    let buffer = ''
    let completed = false
    const processLine = (line: string) => {
      if (!line.startsWith('data:')) return
      const json = line.slice(5).trim()
      if (!json) return
      try {
        completed = handleSSEEvent(JSON.parse(json), chatStore, profileStore) || completed
      } catch (error) {
        console.warn('忽略无法解析的 SSE 数据:', error)
      }
    }

    while (!completed) {
      const { done, value } = await reader.read()
      if (done) break
      resetInactivityTimer()
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() || ''
      lines.forEach(processLine)
    }

    if (!completed) {
      buffer += decoder.decode()
      if (buffer.trim()) processLine(buffer)
    }
    if (!completed) throw new Error('连接提前结束')
  } catch (error) {
    const err = controller.signal.aborted
      ? new Error('AI 服务响应超时，请稍后重试')
      : error instanceof Error ? error : new Error(String(error))
    console.error('SSE 连接错误:', err)
    if (chatStore.isStreaming) {
      chatStore.appendStreamContent(`\n\n[错误: 连接失败 - ${err.message}]`)
      chatStore.finishStreaming()
    }
  } finally {
    if (inactivityTimer) clearTimeout(inactivityTimer)
    if (metadata?.attachmentType === 'image') {
      window.dispatchEvent(new Event('file-history-updated'))
    }
  }
}

/**
 * SSE 事件分发器
 *
 * 根据事件类型更新对应的 Pinia Store：
 *   - conversation_id → chatStore.setConversationId
 *   - content        → chatStore.appendStreamContent（打字机效果）
 *   - profile_update → profileStore.setProfile（雷达图实时刷新）
 *   - done           → chatStore.finishStreaming
 */
function handleSSEEvent(
  data: { type: string; content: string },
  chatStore: ReturnType<typeof useChatStore>,
  profileStore: ReturnType<typeof useProfileStore>
): boolean {
  switch (data.type) {
    case 'conversation_id':
      // 首次对话时，后端返回生成的会话 ID
      chatStore.setConversationId(data.content)
      return false

    case 'content':
      // 逐字符追加文本 → UI 实时渲染打字机效果
      chatStore.appendStreamContent(data.content)
      return false

    case 'profile_update':
      // 画像更新：解析 JSON 并刷新 Pinia Store → 雷达图响应式更新
      try {
        const profile: UserProfile = typeof data.content === 'string'
          ? JSON.parse(data.content)
          : (data.content as unknown as UserProfile)
        profileStore.setProfile(profile)
      } catch {
        console.warn('画像更新数据解析失败')
      }
      return false

    case 'plan_update':
      // 对话触发的计划修订：通知计划卡片立即换成新版本。
      try {
        const plan: LearningPlan = typeof data.content === 'string'
          ? JSON.parse(data.content)
          : (data.content as unknown as LearningPlan)
        window.dispatchEvent(new CustomEvent('learning-plan-updated', { detail: plan }))
      } catch {
        console.warn('学习计划更新数据解析失败')
      }
      return false

    case 'done':
      // 流式输出完成：将缓存的文本正式添加到消息列表
      chatStore.finishStreaming()
      window.dispatchEvent(new Event('learning-activity-updated'))
      window.dispatchEvent(new Event('conversation-list-updated'))
      return true

    case 'error':
      // 后端返回的错误信息
      chatStore.appendStreamContent(`\n\n[错误: ${data.content}]`)
      chatStore.finishStreaming()
      return true
  }
  return false
}
