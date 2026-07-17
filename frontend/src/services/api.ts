import type { UserProfile } from '../stores/profileStore'

/**
 * REST API 服务层
 *
 * 封装与后端的所有 HTTP 通信，所有接口基础路径为 /api。
 * 返回数据遵循统一格式：{ code: number, message: string, data: T }
 */

const BASE_URL = '/api'

// ========== 类型定义 ==========

/** 统一 API 响应格式 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 单周学习计划 */
export interface LearningPlanWeek {
  weekNumber: number
  topic: string
  tasks: string[]
  resources: ResourceItem[]
}

/** 学习资源项 */
export interface ResourceItem {
  title: string
  url: string
  platform: string   // B站 / 慕课网 / GitHub 等
  type: string       // video / course / article / practice
}

/** 完整的 4 周学习计划 */
export interface LearningPlan {
  weeks: LearningPlanWeek[]
}

/** 登录/注册返回的用户信息 */
export interface AuthUser {
  id: number
  username: string
  avatar: string
}

/** 单日真实学习活跃度 */
export interface DailyLearningActivity {
  date: string
  conversationCount: number
  submissionCount: number
  evaluatedCount: number
  score: number
  level: number
}

export interface UploadedFileRecord {
  id: number
  userId: number
  conversationId?: string
  fileName: string
  sizeBytes: number
  purpose: 'CHAT' | 'SUBMISSION'
  uploadedAt: string
}

export interface SubmissionEvaluation {
  id: number
  score: number
  analysis: string
  suggestion: string
  weaknessesJson?: string
  recommendedActionsJson?: string
  evaluationTime: string
}

export interface SubmissionDetail {
  id: number
  taskId: number
  userId: number
  conversationId?: string
  fileName?: string
  fileSize?: number
  content: string
  submissionTime: string
  status: 'PENDING' | 'EVALUATED' | 'ERROR'
  errorMessage?: string
  evaluation?: SubmissionEvaluation
}

// ========== 通用请求方法 ==========

/**
 * 发送 HTTP 请求并返回解析后的响应数据
 * @param url    接口路径（相对于 /api）
 * @param options 可选的 fetch 配置（method、body 等）
 */
async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const headers = new Headers(options?.headers)
  if (options?.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(`${BASE_URL}${url}`, {
    ...options,
    headers,
  })
  let payload: ApiResponse<T> | null = null
  try {
    payload = await res.json() as ApiResponse<T>
  } catch {
    // 非 JSON 响应会在下方按 HTTP 状态转换成统一错误。
  }
  if (!res.ok || !payload || payload.code >= 400) {
    throw new Error(payload?.message || `HTTP ${res.status}: ${res.statusText || '请求失败'}`)
  }
  return payload
}

// ========== 业务接口 ==========

/**
 * GET /api/profile —— 获取当前用户画像
 * 页面初始化时调用，加载已有画像数据到雷达图
 */
export async function fetchProfile(userId: number, conversationId: string): Promise<UserProfile> {
  const res = await request<UserProfile>(
    `/profile?userId=${userId}&conversationId=${encodeURIComponent(conversationId)}`
  )
  return res.data
}

/**
 * POST /api/plan —— 生成学习计划（多智能体协同）
 * 触发 PlanningAgent + ResourceAgent 协同生成 4 周计划
 */
export async function fetchPlan(userId: number, conversationId: string): Promise<LearningPlan> {
  const res = await request<LearningPlan>('/plan', {
    method: 'POST',
    body: JSON.stringify({ userId, conversationId }),
  })
  return res.data
}

/**
 * GET /api/plan —— 获取用户最近一次保存的学习计划
 */
export async function fetchSavedPlanApi(userId: number, conversationId: string): Promise<LearningPlan | null> {
  const res = await request<LearningPlan | null>(
    `/plan?userId=${userId}&conversationId=${encodeURIComponent(conversationId)}`
  )
  return res.data
}

/**
 * PUT /api/plan —— 保存用户编辑后的学习计划
 */
export async function savePlanApi(
  userId: number,
  conversationId: string,
  plan: LearningPlan
): Promise<LearningPlan> {
  const res = await request<LearningPlan>('/plan', {
    method: 'PUT',
    body: JSON.stringify({ userId, conversationId, plan }),
  })
  return res.data
}

/** GET /api/activity —— 按对话和成果提交统计真实学习活跃度 */
export async function fetchLearningActivityApi(
  userId: number,
  days = 112
): Promise<DailyLearningActivity[]> {
  const res = await request<DailyLearningActivity[]>(`/activity?userId=${userId}&days=${days}`)
  return res.data || []
}

export async function loginApi(username: string, password: string): Promise<AuthUser> {
  const res = await request<AuthUser>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  return res.data
}

export async function registerApi(username: string, password: string): Promise<AuthUser> {
  const res = await request<AuthUser>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  return res.data
}

/**
 * POST /api/parse-file —— 上传文件并提取文本内容
 * 支持格式：PDF (.pdf)、Word (.docx)、纯文本 (.txt)
 */
export async function parseFileApi(
  file: File,
  context?: { userId?: number; conversationId?: string; purpose?: 'CHAT' | 'SUBMISSION' }
): Promise<{ text: string; filename: string; length: number }> {
  const formData = new FormData()
  formData.append('file', file)
  if (context?.userId) formData.append('userId', String(context.userId))
  if (context?.conversationId) formData.append('conversationId', context.conversationId)
  if (context?.purpose) formData.append('purpose', context.purpose)

  const res = await request<{ text: string; filename: string; length: number }>('/parse-file', {
    method: 'POST',
    body: formData,
  })
  return res.data
}

/** 使用当前登录用户的克隆音色生成 WAV；signal 用于切换消息时取消过期请求。 */
export async function fetchVoiceAudio(
  userId: number,
  username: string,
  text: string,
  style = '',
  signal?: AbortSignal,
): Promise<Blob> {
  const res = await fetch(`${BASE_URL}/voice/synthesize`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': String(userId),
    },
    body: JSON.stringify({ username, text, style }),
    signal,
  })
  if (!res.ok) {
    throw new Error(`语音生成失败：HTTP ${res.status}`)
  }
  return res.blob()
}

export async function fetchUploadedFilesApi(userId: number, limit = 50): Promise<UploadedFileRecord[]> {
  const res = await request<UploadedFileRecord[]>(`/files?userId=${userId}&limit=${limit}`)
  return res.data || []
}

export async function updateProfileApi(
  userId: number,
  username?: string,
  currentPassword?: string,
  password?: string,
  avatar?: string
): Promise<AuthUser> {
  const res = await request<AuthUser>('/auth/profile', {
    method: 'PUT',
    body: JSON.stringify({
      userId: String(userId),
      username: username || '',
      currentPassword: currentPassword || '',
      password: password || '',
      avatar: avatar || '',
    }),
  })
  return res.data
}

/** 对话记录 */
export interface ConversationRecord {
  id: number
  content: string
  role: string
  userId: number
  conversationId: string
  timestamp: string
  attachmentName?: string
  attachmentType?: 'image' | 'file'
  attachmentData?: string
  conversationTitle?: string
}

/**
 * GET /api/conversations —— 获取用户最近的对话历史
 */
export async function fetchConversationsApi(userId: number, limit = 50): Promise<ConversationRecord[]> {
  const res = await request<ConversationRecord[]>(`/conversations?userId=${userId}&limit=${limit}`)
  return res.data || []
}

/** POST /api/conversations/title —— 分析整体对话并返回简化标题 */
export async function generateConversationTitleApi(
  userId: number,
  conversationId: string,
  conversationContext: string
): Promise<string> {
  const res = await request<{ title: string }>('/conversations/title', {
    method: 'POST',
    body: JSON.stringify({ userId, conversationId, conversationContext }),
  })
  return res.data?.title?.trim() || '新对话'
}

export async function submitLearningResultApi(
  userId: number,
  conversationId: string,
  file: File,
  content: string
): Promise<number> {
  const res = await request<{ submissionId: number }>('/submissions', {
    method: 'POST',
    headers: { 'X-User-Id': String(userId) },
    body: JSON.stringify({
      conversationId,
      fileName: file.name,
      fileSize: file.size,
      content,
    }),
  })
  return res.data.submissionId
}

export async function fetchSubmissionApi(userId: number, submissionId: number): Promise<SubmissionDetail> {
  const res = await request<SubmissionDetail>(`/submissions/${submissionId}`, {
    headers: { 'X-User-Id': String(userId) },
  })
  return res.data
}

export async function fetchConversationSubmissionsApi(
  userId: number,
  conversationId: string
): Promise<SubmissionDetail[]> {
  const res = await request<SubmissionDetail[]>(
    `/conversations/${encodeURIComponent(conversationId)}/submissions`,
    { headers: { 'X-User-Id': String(userId) } }
  )
  return res.data || []
}

/**
 * DELETE /api/auth/account —— 注销账号
 * 验证密码后删除用户及其所有关联数据
 */
export async function deleteAccountApi(userId: number, password: string): Promise<void> {
  const res = await request<void>('/auth/account', {
    method: 'DELETE',
    body: JSON.stringify({
      userId: String(userId),
      password,
    }),
  })
}
