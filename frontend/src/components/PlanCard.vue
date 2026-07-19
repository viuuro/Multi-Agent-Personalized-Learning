<template>
  <div class="plan-card-container">
    <!-- 计划已生成 -->
    <div v-if="plan" class="plan-content">
      <!-- ======== 编辑模式：每周一张可编辑卡片 ======== -->
      <div v-if="editing" class="expand-week-list">
        <div v-for="(week, wi) in editablePlan!.weeks" :key="week.weekNumber" class="expand-week-card">
          <div class="expand-week-title edit-week-title">
            <span>第 {{ week.weekNumber }} 周：</span>
            <el-input
              v-model="week.topic"
              size="small"
              class="edit-topic-input"
            />
          </div>
          <div class="expand-week-body">
            <div class="edit-section">
              <div class="edit-section-header">
                <span>任务</span>
                <el-button class="add-btn" size="small" text aria-label="添加任务" @click="addTask(wi)">
                  <UiIcon name="plus" />
                </el-button>
              </div>
              <div v-for="(task, ti) in week.tasks" :key="ti" class="edit-item">
                <el-input v-model="week.tasks[ti]" size="small" placeholder="任务描述" />
                <el-button class="delete-btn" size="small" text aria-label="删除任务" @click="removeTask(wi, ti)">
                  <UiIcon name="delete" />
                </el-button>
              </div>
            </div>

            <div class="edit-section">
              <div class="edit-section-header">
                <span>资源</span>
                <el-button class="add-btn" size="small" text aria-label="添加资源" @click="addResource(wi)">
                  <UiIcon name="plus" />
                </el-button>
              </div>
              <div v-for="(res, ri) in week.resources" :key="ri" class="edit-resource">
                <el-input v-model="res.title" size="small" placeholder="资源标题" />
                <el-input v-model="res.url" size="small" placeholder="URL" />
                <el-input v-model="res.platform" size="small" placeholder="平台" style="width:80px" />
                <el-button class="delete-btn" size="small" text aria-label="删除资源" @click="removeResource(wi, ri)">
                  <UiIcon name="delete" />
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ======== 展开模式：每周一张卡片 ======== -->
      <div v-else-if="variant === 'expand'" class="expand-week-list">
        <div v-for="week in plan.weeks" :key="week.weekNumber" class="expand-week-card">
          <div class="expand-week-title">第 {{ week.weekNumber }} 周：{{ week.topic }}</div>
          <div class="expand-week-body">
            <h4>本周任务</h4>
            <ul class="task-list">
              <li v-for="(task, i) in week.tasks" :key="i">
                <UiIcon v-if="isTaskCompleted(week.weekNumber, i)" name="check" aria-label="已完成" />
                <span v-else class="task-open-circle" role="img" aria-label="未完成"></span>
                {{ task }}
              </li>
            </ul>
            <h4>推荐资源</h4>
            <div class="resource-list">
              <div v-for="(res, j) in week.resources" :key="j" class="resource-item">
                <a :href="res.url" target="_blank" class="resource-link" @click="trackResource(res, 'CLICK')">
                  <el-tag :type="res.type === 'video' ? 'danger' : 'primary'" size="small">
                    {{ res.platform }}
                  </el-tag>
                  <span class="resource-title">{{ res.title }}</span>
                  <UiIcon name="link" />
                </a>
                <div class="resource-feedback">
                  <button type="button" @click="trackResource(res, 'HELPFUL')">有帮助</button>
                  <button type="button" @click="trackResource(res, 'NOT_HELPFUL')">不适合</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ======== 预览模式：Popover 气泡 ======== -->
      <div v-else class="week-list">
        <el-popover
          v-for="week in plan.weeks"
          :key="week.weekNumber"
          placement="right-start"
          :width="320"
          trigger="hover"
          :hide-after="0"
          :show-arrow="false"
          popper-class="week-popover"
        >
          <template #reference>
            <div class="week-row">
              <span class="week-label">第 {{ week.weekNumber }} 周：{{ week.topic }}</span>
              <UiIcon class="week-arrow" name="chevron-right" />
            </div>
          </template>
          <div class="popover-content">
            <h4>本周任务</h4>
            <ul class="task-list">
              <li v-for="(task, i) in week.tasks" :key="i">
                <UiIcon v-if="isTaskCompleted(week.weekNumber, i)" name="check" aria-label="已完成" />
                <span v-else class="task-open-circle" role="img" aria-label="未完成"></span>
                {{ task }}
              </li>
            </ul>
            <h4>推荐资源</h4>
            <div class="resource-list">
              <div v-for="(res, j) in week.resources" :key="j" class="resource-item">
                <a :href="res.url" target="_blank" class="resource-link" @click="trackResource(res, 'CLICK')">
                  <el-tag :type="res.type === 'video' ? 'danger' : 'primary'" size="small">
                    {{ res.platform }}
                  </el-tag>
                  <span class="resource-title">{{ res.title }}</span>
                  <UiIcon name="link" />
                </a>
                <div class="resource-feedback">
                  <button type="button" @click="trackResource(res, 'HELPFUL')">有帮助</button>
                  <button type="button" @click="trackResource(res, 'NOT_HELPFUL')">不适合</button>
                </div>
              </div>
            </div>
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="plan-empty"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { fetchPlan, fetchPlanTaskStatusesApi, recordResourceFeedbackApi, savePlanApi } from '../services/api'
import type { LearningPlan, LearningTaskStatus, ResourceFeedbackEvent, ResourceItem } from '../services/api'
import { useAuthStore } from '../stores/authStore'
import { useChatStore } from '../stores/chatStore'
import UiIcon from './UiIcon.vue'

const authStore = useAuthStore()
const chatStore = useChatStore()

const props = defineProps<{
  planData?: LearningPlan | null
  initialEditing?: boolean
  variant?: 'preview' | 'expand'
}>()

const emit = defineEmits<{
  (e: 'edit-done'): void
}>()

const plan = computed(() => props.planData ?? internalPlan.value)
const internalPlan = ref<LearningPlan | null>(null)
const loading = ref(false)
const editing = ref(false)
const editablePlan = ref<LearningPlan | null>(null)
const taskStatuses = ref<Record<string, LearningTaskStatus>>({})

const hasPlan = computed(() => plan.value !== null)

function setPlan(newPlan: LearningPlan | null) {
  internalPlan.value = newPlan ? JSON.parse(JSON.stringify(newPlan)) : null
  void refreshTaskStatuses()
}

onMounted(() => {
  if (props.initialEditing) startEdit()
  window.addEventListener('learning-activity-updated', refreshTaskStatuses)
  void refreshTaskStatuses()
})

onUnmounted(() => {
  window.removeEventListener('learning-activity-updated', refreshTaskStatuses)
})

function taskStatusKey(weekNumber: number, taskIndex: number) {
  return `${weekNumber}-${taskIndex}`
}

function isTaskCompleted(weekNumber: number, taskIndex: number) {
  return taskStatuses.value[taskStatusKey(weekNumber, taskIndex)] === 'COMPLETED'
}

async function refreshTaskStatuses() {
  const userId = authStore.user?.id
  const conversationId = chatStore.conversationId
  if (!userId || !conversationId || !plan.value) {
    taskStatuses.value = {}
    return
  }
  try {
    const statuses = await fetchPlanTaskStatusesApi(userId, conversationId)
    if (chatStore.conversationId !== conversationId) return
    taskStatuses.value = Object.fromEntries(
      statuses.map(item => [taskStatusKey(item.weekNumber, item.taskIndex), item.status])
    )
  } catch (error) {
    console.warn('任务完成状态加载失败:', error)
    taskStatuses.value = {}
  }
}

async function generatePlan() {
  loading.value = true
  try {
    const userId = authStore.user?.id
    const conversationId = chatStore.conversationId
    if (!userId || !conversationId) throw new Error('未登录或当前对话未初始化')
    internalPlan.value = await fetchPlan(userId, conversationId)
    await refreshTaskStatuses()
  } catch (err) {
    console.error('计划生成失败:', err)
  } finally {
    loading.value = false
  }
}

function startEdit() {
  editablePlan.value = JSON.parse(JSON.stringify(plan.value)) as LearningPlan
  editing.value = true
}

async function saveEdit() {
  if (editablePlan.value) {
    internalPlan.value = JSON.parse(JSON.stringify(editablePlan.value)) as LearningPlan
    // 持久化到后端
    const userId = authStore.user?.id
    const conversationId = chatStore.conversationId
    if (userId && conversationId) {
      try {
        await savePlanApi(userId, conversationId, internalPlan.value)
        await refreshTaskStatuses()
      } catch (e) {
        console.warn('计划保存失败:', e)
      }
    }
  }
  editing.value = false
  emit('edit-done')
}

function cancelEdit() {
  editablePlan.value = null
  editing.value = false
  emit('edit-done')
}

function addTask(wi: number) {
  editablePlan.value!.weeks[wi].tasks.push('')
}

function removeTask(wi: number, ti: number) {
  editablePlan.value!.weeks[wi].tasks.splice(ti, 1)
}

function addResource(wi: number) {
  editablePlan.value!.weeks[wi].resources.push({
    title: '',
    url: '',
    platform: '',
    type: 'article',
  })
}

function removeResource(wi: number, ri: number) {
  editablePlan.value!.weeks[wi].resources.splice(ri, 1)
}

function trackResource(resource: ResourceItem, event: ResourceFeedbackEvent) {
  const conversationId = chatStore.conversationId
  if (!conversationId) return
  void recordResourceFeedbackApi(conversationId, resource, event).catch((error) => {
    console.warn('资源反馈记录失败:', error)
  })
}

defineExpose({ generatePlan, hasPlan, plan, saveEdit, cancelEdit, setPlan })
</script>

<style scoped>
.plan-card-container {
  padding: 4px 0;
}

/* 编辑模式内容区 */
.edit-section {
  margin-top: 8px;
}

.edit-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.add-btn {
  color: var(--text-faint) !important;
  border: 1px solid transparent !important;
  transition: border-color 0.2s;
}
.add-btn:hover,
.add-btn:focus,
.add-btn:active {
  border-color: var(--border-solid) !important;
  background: transparent !important;
  color: var(--text-faint) !important;
}
.add-btn .ui-icon,
.delete-btn .ui-icon {
  width: 16px;
  height: 16px;
}

.delete-btn {
  color: var(--danger) !important;
  border: 1px solid transparent !important;
  transition: border-color 0.2s;
}
.delete-btn:hover,
.delete-btn:focus,
.delete-btn:active {
  border-color: var(--danger) !important;
  background: transparent !important;
  color: var(--danger) !important;
}

.edit-item {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}

.edit-resource {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}

/* 展开模式：每周一张卡片 */
.expand-week-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.expand-week-card {
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px var(--border-solid);
}

.expand-week-title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-light);
}

.edit-week-title {
  display: flex;
  align-items: center;
  gap: 4px;
}

.edit-week-title span {
  white-space: nowrap;
  flex-shrink: 0;
}

.edit-topic-input {
  flex: 1;
  max-width: 280px;
}

.expand-week-body {
  padding: 8px 12px 12px;
}

.expand-week-body h4 {
  font-size: 12px;
  color: var(--text-faint);
  margin: 6px 0 4px;
}

/* 查看模式：周详情 */

/* 查看模式：Popover 周列表 */
.week-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.week-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border-solid);
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: background 0.2s;
}

.week-row:hover {
  background: var(--bg-hover);
}

.week-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.week-arrow {
  width: 14px;
  height: 14px;
  color: var(--text-faint);
  flex-shrink: 0;
  margin-left: 4px;
}

.popover-content {
  max-height: 300px;
  overflow-y: auto;
}

.popover-content h4 {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 8px 0 4px;
}

.popover-content h4:first-child {
  margin-top: 0;
}

.task-list {
  list-style: none;
  padding: 0;
}

.task-list li {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 3px 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.task-list li .ui-icon {
  width: 14px;
  height: 14px;
  color: var(--success);
  margin-top: 2px;
  flex-shrink: 0;
}

.task-open-circle {
  width: 12px;
  height: 12px;
  margin: 3px 1px 0;
  border: 1.5px solid var(--text-faint);
  border-radius: 50%;
  box-sizing: border-box;
  flex-shrink: 0;
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resource-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.resource-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  background: var(--ai-bubble-bg);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--ai-bubble-border);
  border-radius: 10px;
  text-decoration: none;
  font-size: 12px;
  color: var(--accent);
  transition: background 0.2s;
}

.resource-link:hover {
  background: var(--bg-hover);
}
.resource-link .ui-icon { width: 13px; height: 13px; }

.resource-feedback {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 5px 2px;
}

.resource-feedback button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-faint);
  font-size: 10px;
  line-height: 16px;
  cursor: pointer;
}

.resource-feedback button:hover {
  color: var(--accent);
}

.resource-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-empty {
  color: var(--text-placeholder);
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}
</style>

<style>
.week-popover {
  border-radius: 14px !important;
  background: var(--bg-primary) !important;
  backdrop-filter: blur(20px) saturate(1.3) !important;
  -webkit-backdrop-filter: blur(20px) saturate(1.3) !important;
  border: 1px solid var(--border-solid) !important;
  box-shadow: var(--shadow-card) !important;
}

/* 资源平台标签：透明背景+轮廓 */
.resource-link .el-tag {
  background: transparent !important;
  border: 1px solid var(--border-solid) !important;
  color: var(--text-secondary) !important;
}

.resource-link .el-tag--danger {
  border-color: #e88ca5 !important;
  color: #e88ca5 !important;
}

.resource-link .el-tag--primary {
  border-color: #7ab8d8 !important;
  color: #7ab8d8 !important;
}

.week-popover .el-popover__title {
  display: none;
}

/* 编辑模式输入框圆角 */
.expand-week-card .el-input__wrapper {
  border-radius: 8px !important;
}

</style>
