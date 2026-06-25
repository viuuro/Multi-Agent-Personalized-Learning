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

// ========== 通用请求方法 ==========

/**
 * 发送 HTTP 请求并返回解析后的响应数据
 * @param url    接口路径（相对于 /api）
 * @param options 可选的 fetch 配置（method、body 等）
 */
async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const res = await fetch(`${BASE_URL}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  return res.json()
}

// ========== 业务接口 ==========

/**
 * GET /api/profile —— 获取当前用户画像
 * 页面初始化时调用，加载已有画像数据到雷达图
 */
export async function fetchProfile(userId: number): Promise<UserProfile> {
  const res = await request<UserProfile>(`/profile?userId=${userId}`)
  return res.data
}

/**
 * POST /api/plan —— 生成学习计划（多智能体协同）
 * 触发 PlanningAgent + ResourceAgent 协同生成 4 周计划
 */
export async function fetchPlan(userId: number): Promise<LearningPlan> {
  const res = await request<LearningPlan>('/plan', {
    method: 'POST',
    body: JSON.stringify({ userId }),
  })
  return res.data
}

/**
 * GET /api/plan —— 获取用户最近一次保存的学习计划
 */
export async function fetchSavedPlanApi(userId: number): Promise<LearningPlan | null> {
  const res = await request<LearningPlan | null>(`/plan?userId=${userId}`)
  return res.data
}

/**
 * PUT /api/plan —— 保存用户编辑后的学习计划
 */
export async function savePlanApi(userId: number, plan: LearningPlan): Promise<LearningPlan> {
  const res = await request<LearningPlan>('/plan', {
    method: 'PUT',
    body: JSON.stringify({ userId, plan }),
  })
  if (res.code !== 200) throw new Error(res.message)
  return res.data
}

export async function loginApi(username: string, password: string): Promise<AuthUser> {
  const res = await request<AuthUser>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (res.code !== 200) throw new Error(res.message)
  return res.data
}

export async function registerApi(username: string, password: string): Promise<AuthUser> {
  const res = await request<AuthUser>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (res.code !== 200) throw new Error(res.message)
  return res.data
}

/**
 * POST /api/parse-file —— 上传文件并提取文本内容
 * 支持格式：PDF (.pdf)、Word (.docx)、纯文本 (.txt)
 */
export async function parseFileApi(file: File): Promise<{ text: string; filename: string; length: number }> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetch(`${BASE_URL}/parse-file`, {
    method: 'POST',
    body: formData,
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  const result = await res.json()
  if (result.code !== 200) {
    throw new Error(result.message)
  }
  return result.data
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
  if (res.code !== 200) throw new Error(res.message)
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
}

/**
 * GET /api/conversations —— 获取用户最近的对话历史
 */
export async function fetchConversationsApi(userId: number, limit = 50): Promise<ConversationRecord[]> {
  const res = await request<ConversationRecord[]>(`/conversations?userId=${userId}&limit=${limit}`)
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
  if (res.code !== 200) throw new Error(res.message)
}
