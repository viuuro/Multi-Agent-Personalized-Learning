import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户画像状态管理 (Pinia Store)
 *
 * 管理 6 维学习画像数据，驱动右侧雷达图实时更新：
 *   1. knowledgeBase  - 知识基础 (1-10)
 *   2. cognitiveStyle - 学习风格 (visual/verbal/kinesthetic)
 *   3. weaknessPoints - 薄弱点列表
 *   4. learningPace   - 学习节奏 (1-10)
 *   5. interestAreas  - 兴趣领域列表
 *   6. shortTermGoal  - 短期目标
 */

/** 6 维用户画像数据结构 */
export interface UserProfile {
  id?: number
  knowledgeBase: number
  cognitiveStyle: string
  weaknessPoints: string[]
  learningPace: number
  interestAreas: string[]
  shortTermGoal: string
  updatedAt?: string
}

export const useProfileStore = defineStore('profile', () => {
  /** 当前用户画像 */
  const profile = ref<UserProfile>({
    knowledgeBase: 0,
    cognitiveStyle: '',
    weaknessPoints: [],
    learningPace: 0,
    interestAreas: [],
    shortTermGoal: '',
  })

  /**
   * 更新画像数据
   *
   * 调用时机：
   *   1. 页面初始化时调用 GET /api/profile 获取已有画像
   *   2. SSE 收到 profile_update 事件时实时更新
   *
   * 使用 ?? 空值合并确保维度值不为 null/undefined
   */
  function setProfile(p: UserProfile) {
    profile.value = {
      knowledgeBase: p.knowledgeBase ?? 5,
      cognitiveStyle: p.cognitiveStyle ?? 'visual',
      weaknessPoints: p.weaknessPoints ?? [],
      learningPace: p.learningPace ?? 5,
      interestAreas: p.interestAreas ?? [],
      shortTermGoal: p.shortTermGoal ?? '',
      updatedAt: p.updatedAt,
    }
  }

  /** 重置画像为空状态（用于新账号或退出登录） */
  function resetProfile() {
    profile.value = {
      knowledgeBase: 0,
      cognitiveStyle: '',
      weaknessPoints: [],
      learningPace: 0,
      interestAreas: [],
      shortTermGoal: '',
    }
  }

  return { profile, setProfile, resetProfile }
})
