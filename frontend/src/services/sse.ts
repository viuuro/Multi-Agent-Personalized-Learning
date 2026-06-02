import { useChatStore } from '../stores/chatStore'
import { useProfileStore } from '../stores/profileStore'
import type { UserProfile } from '../stores/profileStore'

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
export function sendMessage(message: string, imageData?: string, userId?: number) {
  const chatStore = useChatStore()
  const profileStore = useProfileStore()

  // 标记流式接收开始（UI 显示 loading 动画）
  chatStore.startStreaming()

  fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message,
      // 如果已有会话 ID 则复用（同一轮对话），否则后端会自动生成新的
      conversationId: chatStore.conversationId || undefined,
      // 图片 Base64 数据（可选）
      imageData: imageData || undefined,
      // 用户 ID，用于画像按用户隔离
      userId: userId || undefined,
    }),
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      // 获取 ReadableStream 读取器，逐块读取 SSE 数据
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('响应体不可读')
      }

      const decoder = new TextDecoder()
      let buffer = ''  // 缓冲区：处理跨 chunk 的不完整行

      while (true) {
        const { done, value } = await reader.read()
        if (done) break  // 流结束

        // 将 Uint8Array 解码为文本并追加到缓冲区
        buffer += decoder.decode(value, { stream: true })

        // 按行分割，最后一行可能不完整，保留在缓冲区
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          // SSE 格式：data:{...json...} 或 data: {...json...}
          if (line.startsWith('data:')) {
            try {
              const json = line.slice(5).trim()
              if (!json) continue
              const data = JSON.parse(json)
              handleSSEEvent(data, chatStore, profileStore)
            } catch {
              // 跳过解析失败的 JSON（可能是不完整行）
            }
          }
        }
      }
    })
    .catch((err) => {
      console.error('SSE 连接错误:', err)
      chatStore.appendStreamContent(`\n\n[错误: 连接失败 - ${err.message}]`)
      chatStore.finishStreaming()
    })
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
) {
  switch (data.type) {
    case 'conversation_id':
      // 首次对话时，后端返回生成的会话 ID
      chatStore.setConversationId(data.content)
      break

    case 'content':
      // 逐字符追加文本 → UI 实时渲染打字机效果
      chatStore.appendStreamContent(data.content)
      break

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
      break

    case 'done':
      // 流式输出完成：将缓存的文本正式添加到消息列表
      chatStore.finishStreaming()
      break

    case 'error':
      // 后端返回的错误信息
      chatStore.appendStreamContent(`\n\n[错误: ${data.content}]`)
      chatStore.finishStreaming()
      break
  }
}
