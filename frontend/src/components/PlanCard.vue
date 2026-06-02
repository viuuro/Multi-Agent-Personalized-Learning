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
                <el-button size="small" text type="primary" @click="addTask(wi)">
                  <el-icon><Plus /></el-icon>
                </el-button>
              </div>
              <div v-for="(task, ti) in week.tasks" :key="ti" class="edit-item">
                <el-input v-model="week.tasks[ti]" size="small" placeholder="任务描述" />
                <el-button size="small" text type="danger" @click="removeTask(wi, ti)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>

            <div class="edit-section">
              <div class="edit-section-header">
                <span>资源</span>
                <el-button size="small" text type="primary" @click="addResource(wi)">
                  <el-icon><Plus /></el-icon>
                </el-button>
              </div>
              <div v-for="(res, ri) in week.resources" :key="ri" class="edit-resource">
                <el-input v-model="res.title" size="small" placeholder="资源标题" />
                <el-input v-model="res.url" size="small" placeholder="URL" />
                <el-input v-model="res.platform" size="small" placeholder="平台" style="width:80px" />
                <el-button size="small" text type="danger" @click="removeResource(wi, ri)">
                  <el-icon><Delete /></el-icon>
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
                <el-icon :size="14"><Check /></el-icon>
                {{ task }}
              </li>
            </ul>
            <h4>推荐资源</h4>
            <div class="resource-list">
              <a
                v-for="(res, j) in week.resources"
                :key="j"
                :href="res.url"
                target="_blank"
                class="resource-link"
              >
                <el-tag :type="res.type === 'video' ? 'danger' : 'primary'" size="small">
                  {{ res.platform }}
                </el-tag>
                <span class="resource-title">{{ res.title }}</span>
                <el-icon :size="12"><Link /></el-icon>
              </a>
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
              <el-icon class="week-arrow"><ArrowRight /></el-icon>
            </div>
          </template>
          <div class="popover-content">
            <h4>本周任务</h4>
            <ul class="task-list">
              <li v-for="(task, i) in week.tasks" :key="i">
                <el-icon :size="14"><Check /></el-icon>
                {{ task }}
              </li>
            </ul>
            <h4>推荐资源</h4>
            <div class="resource-list">
              <a
                v-for="(res, j) in week.resources"
                :key="j"
                :href="res.url"
                target="_blank"
                class="resource-link"
              >
                <el-tag :type="res.type === 'video' ? 'danger' : 'primary'" size="small">
                  {{ res.platform }}
                </el-tag>
                <span class="resource-title">{{ res.title }}</span>
                <el-icon :size="12"><Link /></el-icon>
              </a>
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
import { ref, computed, watch, onMounted } from 'vue'
import { Check, Link, Plus, Delete, ArrowRight } from '@element-plus/icons-vue'
import { fetchPlan } from '../services/api'
import type { LearningPlan } from '../services/api'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

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

const hasPlan = computed(() => plan.value !== null)

onMounted(() => {
  if (props.initialEditing) startEdit()
})

async function generatePlan() {
  loading.value = true
  try {
    const userId = authStore.user?.id
    if (!userId) throw new Error('未登录')
    internalPlan.value = await fetchPlan(userId)
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

function saveEdit() {
  if (editablePlan.value) {
    internalPlan.value = JSON.parse(JSON.stringify(editablePlan.value)) as LearningPlan
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

defineExpose({ generatePlan, hasPlan, plan, saveEdit, cancelEdit })
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
  color: #7A6A60;
  margin-bottom: 4px;
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
  background: #F4F1EC;
  border-radius: 10px;
  overflow: hidden;
}

.expand-week-title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #7A6A60;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
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
  color: #A09080;
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
  background: #F4F1EC;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: #7A6A60;
  transition: background 0.2s;
}

.week-row:hover {
  background: #EDE8E2;
}

.week-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.week-arrow {
  color: #A09080;
  flex-shrink: 0;
  margin-left: 4px;
}

.popover-content {
  max-height: 300px;
  overflow-y: auto;
}

.popover-content h4 {
  font-size: 13px;
  color: #7A6A60;
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
  color: #7A6A60;
  line-height: 1.4;
}

.task-list li .el-icon {
  color: #67c23a;
  margin-top: 2px;
  flex-shrink: 0;
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resource-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  background: #F4F1EC;
  border-radius: 10px;
  text-decoration: none;
  font-size: 12px;
  color: #D4916F;
  transition: background 0.2s;
}

.resource-link:hover {
  background: #F5E8DC;
}

.resource-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-empty {
  color: #B8AFA5;
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
}
</style>

<style>
.week-popover {
  border-radius: 14px !important;
}

.week-popover .el-popover__title {
  display: none;
}

/* 编辑模式输入框圆角 */
.expand-week-card .el-input__wrapper {
  border-radius: 8px !important;
}

</style>
