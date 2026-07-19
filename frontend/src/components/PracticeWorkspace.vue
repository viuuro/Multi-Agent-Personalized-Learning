<template>
  <section class="practice-workspace" aria-label="练习空间">
    <aside class="practice-left">
      <div class="practice-brand">
        <h2>练习空间</h2>
        <p>{{ chatStore.conversationTitle || '当前学习对话' }}</p>
      </div>

      <PracticeStatsPie :questions="questions" />
    </aside>

    <main class="practice-main">
      <header class="practice-header">
        <div>
          <h1>把每个小任务变成可完成的题目</h1>
        </div>
        <div class="autosave-state">
          <i :class="globalSaveState"></i>
          {{ globalSaveState === 'saving' ? '正在保存答案' : '答案实时保存' }}
        </div>
      </header>

      <section class="generator-card">
        <label>
          <span>周计划</span>
          <el-select v-model="selectedWeekNumber" class="practice-select" popper-class="practice-select-popper">
            <el-option
              v-for="week in plan?.weeks || []"
              :key="week.weekNumber"
              :value="week.weekNumber"
              :label="`第 ${week.weekNumber} 周 · ${week.topic}`"
            />
          </el-select>
        </label>
        <label class="task-select">
          <span>具体小任务</span>
          <el-select v-model="selectedTaskIndex" class="practice-select" popper-class="practice-select-popper">
            <el-option
              v-for="(task, index) in selectedWeek?.tasks || []"
              :key="index"
              :value="index"
              :label="`${index + 1}. ${task}`"
            />
          </el-select>
        </label>
        <label>
          <span>题型</span>
          <el-select v-model="generationType" class="practice-select" popper-class="practice-select-popper">
            <el-option value="SINGLE_CHOICE" label="单选题" />
            <el-option value="MULTIPLE_CHOICE" label="多选题" />
            <el-option value="TRUE_FALSE" label="判断题" />
            <el-option value="SHORT_ANSWER" label="简答题" />
          </el-select>
        </label>
        <label>
          <span>难度</span>
          <el-select v-model="generationDifficulty" class="practice-select" popper-class="practice-select-popper">
            <el-option value="EASY" label="基础" />
            <el-option value="MEDIUM" label="进阶" />
            <el-option value="HARD" label="挑战" />
          </el-select>
        </label>
        <label class="count-select">
          <span>数量</span>
          <el-select v-model="generationCount" class="practice-select" popper-class="practice-select-popper">
            <el-option :value="3" label="3" />
            <el-option :value="5" label="5" />
            <el-option :value="8" label="8" />
          </el-select>
        </label>
        <button class="generate-question-btn" :disabled="generating || !selectedTaskTitle" @click="generateQuestions">
          <UiIcon name="sparkles" />
          {{ generating ? '出题中…' : '生成题目' }}
        </button>
      </section>

      <p v-if="errorMessage" class="practice-error">{{ errorMessage }}</p>

      <div class="practice-content">
        <nav class="question-board">
          <div class="question-board-header">
            <div><span>题目看板</span><strong>{{ filteredQuestions.length }}</strong></div>
            <button aria-label="刷新题目" :disabled="loading" @click="loadQuestions"><UiIcon name="refresh" /></button>
          </div>
          <div class="question-filter-bar" aria-label="题型筛选">
            <span>题型</span>
            <div class="type-chips">
              <button
                v-for="item in typeFilters"
                :key="item.value"
                :class="{ active: typeFilter === item.value }"
                @click="typeFilter = item.value"
              >{{ item.label }}</button>
            </div>
          </div>
          <div v-if="loading" class="question-empty">正在读取题库…</div>
          <div v-else-if="!filteredQuestions.length" class="question-empty">
            <span>暂无匹配题目</span>
            <p>选择周次和小任务后生成第一组练习。</p>
          </div>
          <div v-else class="question-list">
            <button
              v-for="(question, index) in filteredQuestions"
              :key="question.id"
              class="question-nav-item"
              :class="[
                { active: question.id === activeQuestionId },
                question.status.toLowerCase(),
                question.status === 'SUBMITTED' ? (question.correct ? 'answer-correct' : 'answer-wrong') : '',
              ]"
              @click="activeQuestionId = question.id"
            >
              <span class="question-number">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="question-nav-copy">
                <b>{{ typeLabel(question.questionType) }} · 第 {{ question.weekNumber }} 周</b>
                <small>{{ question.questionText }}</small>
              </span>
              <i v-if="question.status === 'SUBMITTED'" :class="question.correct ? 'correct' : 'wrong'">
                <UiIcon :name="question.correct ? 'check' : 'close'" />
              </i>
              <i v-else class="draft-dot"></i>
            </button>
          </div>
        </nav>

        <article class="answer-card">
          <div v-if="activeQuestion" class="answer-inner">
            <div class="answer-meta">
              <div>
                <span>{{ typeLabel(activeQuestion.questionType) }}</span>
                <span>{{ difficultyLabel(activeQuestion.difficulty) }}</span>
                <span>第 {{ activeQuestion.weekNumber }} 周 · 任务 {{ activeQuestion.taskIndex + 1 }}</span>
              </div>
              <strong :class="activeQuestion.status.toLowerCase()">{{ statusLabel(activeQuestion.status) }}</strong>
            </div>
            <p class="answer-task">{{ activeQuestion.taskTitle }}</p>
            <h2>{{ activeQuestion.questionText }}</h2>

            <div v-if="activeQuestion.questionType !== 'SHORT_ANSWER'" class="answer-options">
              <button
                v-for="(option, index) in activeQuestion.options"
                :key="index"
                :disabled="activeQuestion.status === 'SUBMITTED'"
                :class="optionClass(activeQuestion, optionLetter(index))"
                @click="toggleOption(activeQuestion, optionLetter(index))"
              >
                <span>{{ optionLetter(index) }}</span>
                <p>{{ option }}</p>
              </button>
            </div>

            <div v-else class="short-answer-wrap">
              <textarea
                :value="draftAnswers[activeQuestion.id] || ''"
                :disabled="activeQuestion.status === 'SUBMITTED'"
                placeholder="在这里组织你的答案。内容会自动保存，可以随时离开后继续。"
                @input="updateAnswer(activeQuestion, ($event.target as HTMLTextAreaElement).value)"
              ></textarea>
              <span>{{ (draftAnswers[activeQuestion.id] || '').length }} 字</span>
            </div>

            <div v-if="activeQuestion.status === 'SUBMITTED'" class="answer-feedback" :class="activeQuestion.correct ? 'correct' : 'wrong'">
              <div class="feedback-score">
                <strong>{{ activeQuestion.score }} 分</strong>
                <span>{{ activeQuestion.correct ? '本题已掌握' : '建议结合解析再练一次' }}</span>
              </div>
              <p><b>参考答案：</b>{{ activeQuestion.correctAnswer }}</p>
              <p><b>玛丽解析：</b>{{ activeQuestion.explanation || '暂无解析' }}</p>
            </div>

            <footer class="answer-footer">
              <span>
                {{ saveStates[activeQuestion.id] === 'saving' ? '正在保存…' :
                  saveStates[activeQuestion.id] === 'saved' ? '草稿已保存' : '输入后自动保存' }}
              </span>
              <button
                class="submit-answer-btn"
                :disabled="activeQuestion.status === 'SUBMITTED' || !draftAnswers[activeQuestion.id] || submittingId === activeQuestion.id"
                @click="submitAnswer(activeQuestion)"
              >
                {{ submittingId === activeQuestion.id ? '提交中…' : activeQuestion.status === 'SUBMITTED' ? '已提交' : '提交给智能体' }}
              </button>
            </footer>
          </div>
          <div v-else class="answer-empty">
            <div class="empty-mark">P</div>
            <h2>选择一道题开始作答</h2>
            <p>你的作答会实时保存，提交后可查看评分和解析。</p>
          </div>
        </article>
      </div>
    </main>
  </section>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useChatStore } from '../stores/chatStore'
import {
  fetchPracticeQuestionsApi,
  fetchSavedPlanApi,
  generatePracticeQuestionsApi,
  savePracticeAnswerApi,
  submitPracticeAnswerApi,
} from '../services/api'
import type {
  LearningPlan,
  PracticeDifficulty,
  PracticeQuestion,
  PracticeQuestionStatus,
  PracticeQuestionType,
} from '../services/api'
import UiIcon from './UiIcon.vue'
import PracticeStatsPie from './PracticeStatsPie.vue'

const chatStore = useChatStore()
const plan = ref<LearningPlan | null>(null)
const questions = ref<PracticeQuestion[]>([])
const activeQuestionId = ref<number | null>(null)
const selectedWeekNumber = ref(1)
const selectedTaskIndex = ref(0)
const generationType = ref<PracticeQuestionType>('SINGLE_CHOICE')
const generationDifficulty = ref<PracticeDifficulty>('MEDIUM')
const generationCount = ref(3)
const typeFilter = ref<'ALL' | PracticeQuestionType>('ALL')
const loading = ref(false)
const generating = ref(false)
const submittingId = ref<number | null>(null)
const errorMessage = ref('')
const draftAnswers = reactive<Record<number, string>>({})
const saveStates = reactive<Record<number, 'idle' | 'saving' | 'saved' | 'error'>>({})
const saveTimers = new Map<number, ReturnType<typeof setTimeout>>()

const typeFilters = [
  { value: 'ALL' as const, label: '全部' },
  { value: 'SINGLE_CHOICE' as const, label: '单选' },
  { value: 'MULTIPLE_CHOICE' as const, label: '多选' },
  { value: 'TRUE_FALSE' as const, label: '判断' },
  { value: 'SHORT_ANSWER' as const, label: '简答' },
]

const selectedWeek = computed(() => plan.value?.weeks.find(week => week.weekNumber === selectedWeekNumber.value))
const selectedTaskTitle = computed(() => selectedWeek.value?.tasks[selectedTaskIndex.value] || '')
const filteredQuestions = computed(() => questions.value.filter(question =>
  typeFilter.value === 'ALL' || question.questionType === typeFilter.value))
const activeQuestion = computed(() => questions.value.find(question => question.id === activeQuestionId.value) || filteredQuestions.value[0])
const globalSaveState = computed(() => Object.values(saveStates).includes('saving') ? 'saving' : 'saved')

watch(selectedWeekNumber, () => { selectedTaskIndex.value = 0 })
watch(filteredQuestions, list => {
  if (!list.some(question => question.id === activeQuestionId.value)) activeQuestionId.value = list[0]?.id || null
})
watch(() => chatStore.conversationId, () => { void loadWorkspace() }, { immediate: true })

async function loadWorkspace() {
  const conversationId = chatStore.conversationId
  if (!conversationId) return
  loading.value = true
  errorMessage.value = ''
  try {
    const [savedPlan, savedQuestions] = await Promise.all([
      fetchSavedPlanApi(0, conversationId),
      fetchPracticeQuestionsApi(conversationId),
    ])
    plan.value = savedPlan
    questions.value = savedQuestions
    for (const question of savedQuestions) draftAnswers[question.id] = question.userAnswer || ''
    if (savedPlan?.weeks.length) {
      selectedWeekNumber.value = savedPlan.weeks[0].weekNumber
      selectedTaskIndex.value = 0
    }
    activeQuestionId.value = savedQuestions[0]?.id || null
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '练习空间加载失败'
  } finally {
    loading.value = false
  }
}

async function loadQuestions() {
  const conversationId = chatStore.conversationId
  if (!conversationId) return
  loading.value = true
  try {
    questions.value = await fetchPracticeQuestionsApi(conversationId)
    questions.value.forEach(question => { draftAnswers[question.id] = question.userAnswer || '' })
  } finally { loading.value = false }
}

async function generateQuestions() {
  if (!chatStore.conversationId || !selectedTaskTitle.value) return
  generating.value = true
  errorMessage.value = ''
  try {
    const generated = await generatePracticeQuestionsApi({
      conversationId: chatStore.conversationId,
      weekNumber: selectedWeekNumber.value,
      taskIndex: selectedTaskIndex.value,
      questionType: generationType.value,
      difficulty: generationDifficulty.value,
      count: generationCount.value,
    })
    await loadQuestions()
    if (generated[0]) activeQuestionId.value = generated[0].id
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '题目生成失败'
  } finally { generating.value = false }
}

function replaceQuestion(updated: PracticeQuestion) {
  const index = questions.value.findIndex(question => question.id === updated.id)
  if (index >= 0) questions.value[index] = updated
}

function updateAnswer(question: PracticeQuestion, answer: string) {
  if (question.status === 'SUBMITTED') return
  draftAnswers[question.id] = answer
  saveStates[question.id] = 'saving'
  const existing = saveTimers.get(question.id)
  if (existing) clearTimeout(existing)
  saveTimers.set(question.id, setTimeout(async () => {
    try {
      const updated = await savePracticeAnswerApi(question.id, draftAnswers[question.id] || '')
      replaceQuestion(updated)
      saveStates[question.id] = 'saved'
    } catch {
      saveStates[question.id] = 'error'
    } finally { saveTimers.delete(question.id) }
  }, 650))
}

function selectedLetters(question: PracticeQuestion) {
  return (draftAnswers[question.id] || '').split(',').map(value => value.trim()).filter(Boolean)
}

function toggleOption(question: PracticeQuestion, letter: string) {
  if (question.status === 'SUBMITTED') return
  if (question.questionType === 'MULTIPLE_CHOICE') {
    const selected = new Set(selectedLetters(question))
    selected.has(letter) ? selected.delete(letter) : selected.add(letter)
    updateAnswer(question, Array.from(selected).sort().join(','))
  } else {
    updateAnswer(question, letter)
  }
}

function optionClass(question: PracticeQuestion, letter: string) {
  return {
    selected: selectedLetters(question).includes(letter),
    correct: question.status === 'SUBMITTED' && question.correctAnswer?.split(',').includes(letter),
    wrong: question.status === 'SUBMITTED' && selectedLetters(question).includes(letter)
      && !question.correctAnswer?.split(',').includes(letter),
  }
}

async function submitAnswer(question: PracticeQuestion) {
  const answer = draftAnswers[question.id] || ''
  if (!answer) return
  const timer = saveTimers.get(question.id)
  if (timer) { clearTimeout(timer); saveTimers.delete(question.id) }
  submittingId.value = question.id
  errorMessage.value = ''
  try {
    const updated = await submitPracticeAnswerApi(question.id, answer)
    replaceQuestion(updated)
    saveStates[question.id] = 'saved'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '答案提交失败'
  } finally { submittingId.value = null }
}

function optionLetter(index: number) { return String.fromCharCode(65 + index) }
function typeLabel(type: PracticeQuestionType) {
  return ({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题', SHORT_ANSWER: '简答题' })[type]
}
function difficultyLabel(value: PracticeDifficulty) { return ({ EASY: '基础', MEDIUM: '进阶', HARD: '挑战' })[value] }
function statusLabel(value: PracticeQuestionStatus) { return ({ UNANSWERED: '待作答', DRAFT: '已保存草稿', SUBMITTED: '已提交' })[value] }
onUnmounted(() => saveTimers.forEach(timer => clearTimeout(timer)))
</script>

<style scoped>
.practice-workspace {
  --practice-success: #82d89a;
  --practice-success-soft: rgba(130, 216, 154, .10);
  --practice-success-border: rgba(130, 216, 154, .28);
  --practice-danger: #ff9082;
  --practice-danger-soft: rgba(255, 144, 130, .10);
  --practice-danger-border: rgba(255, 144, 130, .28);
  --practice-unanswered: #cec8c1;
  --practice-selector-column: clamp(190px, 18vw, 260px);
  position: fixed;
  inset: 64px 0 0;
  z-index: 900;
  display: grid;
  grid-template-columns: clamp(244px, 21vw, 320px) minmax(0, 1fr);
  background: var(--bg-primary);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: none;
  border-top: none;
  color: var(--text-primary);
  overflow: hidden;
}
.practice-left {
  position: relative;
  height: 100%;
  box-sizing: border-box;
  padding: 38px 18px;
  overflow: hidden;
}
.practice-brand h2 { margin: 0; color: var(--text-secondary); font-size: 14px; font-weight: 700; line-height: 32px; }
.practice-brand p { margin: 0; color: var(--text-faint); font-size: 11px; line-height: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.type-chips { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 4px; }
.type-chips button { width: 100%; height: 27px; padding: 0 2px; border: 1px solid var(--border-solid); border-radius: 8px; background: transparent; color: var(--text-muted); font-size: 9px; cursor: pointer; }
.type-chips button.active { border-color: var(--accent); color: var(--accent); background: var(--accent-hover); }
.practice-main { min-width: 0; padding: 38px 28px 22px; overflow: hidden; display: flex; flex-direction: column; }
.practice-header { min-height: 32px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.practice-header h1 { margin: 0; color: var(--text-secondary); font-size: 14px; font-weight: 700; line-height: 32px; }
.autosave-state { display: flex; align-items: center; gap: 6px; color: var(--text-faint); font-size: 10px; }
.autosave-state i { width: 6px; height: 6px; border-radius: 50%; background: var(--practice-success); }
.autosave-state i.saving { background: var(--accent); animation: practice-pulse 1s infinite; }
.generator-card { display: grid; grid-template-columns: var(--practice-selector-column) minmax(220px, 1.8fr) .8fr .7fr 64px auto; align-items: end; gap: 8px; padding: 0; border: 0; border-radius: 0; background: transparent; }
.generator-card label { min-width: 0; }
.generator-card label > span { display: block; margin: 0 0 5px 3px; color: var(--text-faint); font-size: 9px; line-height: 10px; }
.practice-select { width: 100%; }
.practice-select :deep(.el-select__wrapper) { min-height: 34px; height: 34px; box-sizing: border-box; padding: 0 26px 0 9px; border: 1px solid var(--border-solid); border-radius: 9px; background: var(--bg-input); box-shadow: none; color: var(--text-secondary); font-size: 11px; transition: background .2s, border-color .2s; }
.practice-select :deep(.el-select__wrapper:hover) { background: var(--bg-hover); }
.practice-select :deep(.el-select__wrapper.is-focused) { border-color: var(--accent); background: var(--bg-input); box-shadow: none; }
.practice-select :deep(.el-select__selected-item) { color: var(--text-secondary); font-size: 11px; }
.practice-select :deep(.el-select__caret) { color: var(--text-faint); }
.generate-question-btn { height: 34px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 0 14px; border: 0; border-radius: 9px; background: var(--accent); color: white; font-size: 11px; cursor: pointer; white-space: nowrap; }
.generate-question-btn .ui-icon { width: 14px; }
.generate-question-btn:disabled { opacity: .5; cursor: not-allowed; }
.practice-error { margin: 8px 3px 0; color: var(--danger); font-size: 11px; }
.practice-content { min-height: 0; flex: 1; display: grid; grid-template-columns: var(--practice-selector-column) minmax(0, 1fr); gap: 8px; margin-top: 12px; }
.question-board, .answer-card { min-height: 0; border: 1px solid var(--border-solid); border-radius: 15px; background: var(--ai-bubble-bg); overflow: hidden; }
.question-board { display: flex; flex-direction: column; }
.question-board-header { height: 48px; flex: 0 0 auto; display: flex; align-items: center; justify-content: space-between; padding: 0 12px; border-bottom: 1px solid var(--border-solid); }
.question-board-header div { display: flex; align-items: center; gap: 7px; color: var(--text-secondary); font-size: 14px; font-weight: 700; }
.question-board-header strong { color: var(--accent); }
.question-board-header button { width: 28px; height: 28px; display: grid; place-items: center; border: 1px solid var(--border-solid); border-radius: 8px; background: transparent; color: var(--accent); cursor: pointer; }
.question-board-header .ui-icon { width: 13px; }
.question-filter-bar { flex: 0 0 auto; padding: 8px 8px 7px; border-bottom: 1px solid var(--border-solid); }
.question-filter-bar > span { display: block; margin: 0 0 5px 3px; color: var(--text-faint); font-size: 9px; }
.question-list { min-height: 0; flex: 1; display: flex; flex-direction: column; gap: 6px; overflow-y: auto; padding: 7px; }
.question-nav-item { width: 100%; display: flex; align-items: center; gap: 8px; padding: 9px 8px; border: 1px solid transparent; border-radius: 10px; background: transparent; color: var(--text-secondary); text-align: left; cursor: pointer; }
.question-nav-item:hover, .question-nav-item.active { background: var(--accent-hover); border-color: color-mix(in srgb, var(--accent) 30%, transparent); }
.question-nav-item.answer-correct { border-color: var(--practice-success); background: var(--practice-success-soft); }
.question-nav-item.answer-wrong { border-color: var(--practice-danger); background: var(--practice-danger-soft); }
.question-number { flex: 0 0 24px; color: var(--accent); font-size: 10px; font-variant-numeric: tabular-nums; }
.question-nav-copy { min-width: 0; flex: 1; }
.question-nav-copy b, .question-nav-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.question-nav-copy b { margin-bottom: 3px; font-size: 10px; font-weight: 600; }
.question-nav-copy small { color: var(--text-faint); font-size: 9px; }
.question-nav-item > i { width: 17px; height: 17px; flex: 0 0 17px; display: grid; place-items: center; border-radius: 50%; }
.question-nav-item > i.correct { background: var(--practice-success-soft); color: var(--practice-success); }
.question-nav-item > i.wrong { background: var(--practice-danger-soft); color: var(--practice-danger); }
.question-nav-item > i .ui-icon { width: 10px; }
.draft-dot { background: transparent; }
.draft-dot::after { width: 6px; height: 6px; border-radius: 50%; background: var(--practice-unanswered); content: ''; }
.question-empty, .answer-empty { flex: 1; display: grid; place-content: center; text-align: center; color: var(--text-faint); }
.question-empty span { font-size: 12px; }
.question-empty p, .answer-empty p { margin: 5px 0 0; font-size: 10px; }
.answer-card { overflow-y: auto; }
.answer-inner { min-height: 100%; display: flex; flex-direction: column; padding: 22px 26px 18px; }
.answer-meta { display: flex; align-items: center; justify-content: space-between; }
.answer-meta > div { display: flex; gap: 5px; }
.answer-meta span, .answer-meta > strong { padding: 4px 7px; border-radius: 7px; background: var(--bg-input); color: var(--text-faint); font-size: 9px; font-weight: 500; }
.answer-meta > strong.submitted { color: var(--practice-success); background: var(--practice-success-soft); }
.answer-task { margin: 16px 0 5px; color: var(--accent); font-size: 10px; }
.answer-inner h2 { margin: 0 0 16px; color: var(--text-primary); font-size: 16px; line-height: 1.65; font-weight: 600; }
.answer-options { display: grid; gap: 8px; }
.answer-options button { display: flex; align-items: center; gap: 10px; min-height: 42px; padding: 8px 11px; border: 1px solid var(--border-solid); border-radius: 11px; background: var(--bg-input); color: var(--text-secondary); text-align: left; cursor: pointer; }
.answer-options button > span { width: 25px; height: 25px; flex: 0 0 25px; display: grid; place-items: center; border: 1px solid var(--border-solid); border-radius: 8px; color: var(--accent); font-size: 10px; }
.answer-options button p { margin: 0; font-size: 11px; line-height: 1.5; }
.answer-options button:hover:not(:disabled), .answer-options button.selected { border-color: var(--accent); background: var(--accent-hover); }
.answer-options button.correct { border-color: var(--practice-success); background: var(--practice-success-soft); }
.answer-options button.wrong { border-color: var(--practice-danger); background: var(--practice-danger-soft); }
.short-answer-wrap { position: relative; }
.short-answer-wrap textarea { width: 100%; min-height: 180px; resize: vertical; padding: 13px; border: 1px solid var(--border-solid); border-radius: 12px; outline: 0; background: var(--bg-input); color: var(--text-primary); font: inherit; font-size: 12px; line-height: 1.7; box-sizing: border-box; }
.short-answer-wrap textarea:focus { border-color: var(--accent); }
.short-answer-wrap > span { position: absolute; right: 11px; bottom: 9px; color: var(--text-faint); font-size: 9px; }
.answer-feedback { margin-top: 14px; padding: 13px; border-radius: 12px; background: var(--practice-success-soft); border: 1px solid var(--practice-success-border); }
.answer-feedback.wrong { background: var(--practice-danger-soft); border-color: var(--practice-danger-border); }
.feedback-score { display: flex; align-items: baseline; gap: 8px; margin-bottom: 7px; }
.feedback-score strong { color: var(--practice-success); font-size: 18px; }
.answer-feedback.wrong .feedback-score strong { color: var(--practice-danger); }
.feedback-score span, .answer-feedback p { color: var(--text-secondary); font-size: 10px; }
.answer-feedback p { margin: 5px 0; line-height: 1.6; }
.answer-footer { margin-top: auto; display: flex; align-items: center; justify-content: space-between; padding-top: 17px; }
.answer-footer > span { color: var(--text-faint); font-size: 9px; }
.submit-answer-btn { height: 36px; padding: 0 17px; border: 0; border-radius: 10px; background: var(--accent); color: white; font-size: 11px; cursor: pointer; }
.submit-answer-btn:disabled { opacity: .45; cursor: not-allowed; }
.answer-empty { height: 100%; }
.empty-mark { width: 42px; height: 42px; margin: 0 auto 10px; display: grid; place-items: center; border: 1px solid var(--border-solid); border-radius: 13px; color: var(--accent); font-size: 17px; }
.answer-empty h2 { margin: 0; color: var(--text-secondary); font-size: 14px; }
@keyframes practice-pulse { 50% { opacity: .35; } }
@media (max-width: 980px) {
  .practice-workspace { grid-template-columns: 230px minmax(0,1fr); }
  .generator-card { grid-template-columns: var(--practice-selector-column) minmax(0, 1.4fr) 1fr; }
  .generate-question-btn { grid-column: span 1; }
}
</style>

<style>
[data-theme="dark"] .practice-workspace {
  --practice-success: #82d89a;
  --practice-success-soft: rgba(130, 216, 154, .14);
  --practice-success-border: rgba(130, 216, 154, .34);
  --practice-danger: #ff9082;
  --practice-danger-soft: rgba(255, 144, 130, .14);
  --practice-danger-border: rgba(255, 144, 130, .34);
  --practice-unanswered: #6e6864;
}

.practice-select-popper.el-popper {
  padding: 5px 0 !important;
  border: 1px solid var(--border-solid) !important;
  border-radius: 14px !important;
  background: var(--bg-primary) !important;
  box-shadow: var(--shadow-card) !important;
  backdrop-filter: blur(20px) saturate(1.3) !important;
  -webkit-backdrop-filter: blur(20px) saturate(1.3) !important;
}
.practice-select-popper .el-select-dropdown,
.practice-select-popper .el-select-dropdown__wrap {
  border-radius: 12px;
  background: transparent;
}
.practice-select-popper .el-select-dropdown__list { padding: 3px 5px; }
.practice-select-popper .el-select-dropdown__item {
  height: 34px;
  margin: 2px 0;
  padding: 0 10px;
  border-radius: 9px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 34px;
}
.practice-select-popper .el-select-dropdown__item:hover,
.practice-select-popper .el-select-dropdown__item.is-hovering {
  background: var(--bg-hover);
}
.practice-select-popper .el-select-dropdown__item.is-selected {
  background: var(--accent-hover);
  color: var(--accent);
  font-weight: 600;
}
.practice-select-popper .el-popper__arrow { display: none; }
</style>
