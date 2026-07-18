import type { Message } from '../stores/chatStore'

function storageKey(userId: number) {
  return `edu-conversation-titles:${userId}`
}

export function readConversationTitles(userId: number): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(storageKey(userId)) || '{}')
  } catch {
    return {}
  }
}

export function saveConversationTitle(userId: number, conversationId: string, title: string) {
  if (!conversationId || !title.trim()) return
  const titles = readConversationTitles(userId)
  titles[conversationId] = title.trim()
  localStorage.setItem(storageKey(userId), JSON.stringify(titles))
}

export function deleteConversationTitle(userId: number, conversationId: string) {
  const titles = readConversationTitles(userId)
  if (!(conversationId in titles)) return
  delete titles[conversationId]
  localStorage.setItem(storageKey(userId), JSON.stringify(titles))
}

/** 历史会话尚未有 AI 标题时的本地简化标题。 */
export function fallbackConversationTitle(messages: Message[]): string {
  const userMessages = messages.filter(message => message.role === 'user')
  const source = userMessages[userMessages.length - 1]?.content || userMessages[0]?.content || ''
  const simplified = source
    .replace(/```[\s\S]*?```/g, '')
    .replace(/---[\s\S]*/g, '')
    .replace(/^(请问|请帮我|帮我|我想要?|我要|如何|怎么)/, '')
    .replace(/[\s#>*_`\[\](){}，。！？:：;；]+/g, '')
  return simplified.slice(0, 16) || '新对话'
}
