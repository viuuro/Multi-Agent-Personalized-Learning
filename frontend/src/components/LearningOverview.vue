<template>
  <main class="learning-overview">
    <header class="overview-header">
      <div>
        <h1>把学习证据沉淀成清晰的课程进度</h1>
        <p>章节掌握度基于当前计划、已生成题目、提交率与正确率综合计算。</p>
      </div>
      <div class="overview-metrics">
        <div><strong>{{ overallMastery }}%</strong><span>综合掌握</span></div>
        <div><strong>{{ submittedCount }}</strong><span>已提交练习</span></div>
        <div><strong>{{ allFavorites.length }}</strong><span>已收藏资源</span></div>
      </div>
    </header>

    <p v-if="errorMessage" class="overview-error">{{ errorMessage }}</p>

    <section class="chapter-section">
      <div class="section-heading">
        <div><span>{{ selectedCourse.name }} · 章节掌握</span><small>优先复习低掌握章节</small></div>
        <span class="evidence-note">练习证据 {{ selectedCourse.evidenceCount }}</span>
      </div>
      <div class="chapter-list">
        <article v-for="(chapter, index) in selectedCourse.chapters" :key="chapter.key" class="chapter-row">
          <span class="chapter-number">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="chapter-copy">
            <div><b>{{ chapter.name }}</b><small>{{ chapter.summary }}</small></div>
            <i><em :style="{ width: `${chapter.mastery}%` }"></em></i>
          </div>
          <div class="chapter-evidence">
            <span>{{ chapter.submitted }}/{{ chapter.generated }} 已提交</span>
            <small>{{ chapter.correct }} 道正确</small>
          </div>
          <strong :class="masteryClass(chapter.mastery)">{{ chapter.mastery }}%</strong>
        </article>
      </div>
    </section>

    <section class="resource-section">
      <div class="section-heading">
        <div><span>智能体推送资源</span><small>来自当前学习计划，可一键归档到课程或自定义收藏夹</small></div>
      </div>
      <div v-if="recommendedResources.length" class="resource-groups">
        <details
          v-for="(group, index) in groupedRecommendedResources"
          :key="group.key"
          class="resource-group"
          :open="index === 0"
        >
          <summary>
            <span>{{ group.label }}</span>
            <small>{{ group.resources.length }} 项资源</small>
            <i>⌄</i>
          </summary>
          <div class="recommendation-grid">
            <article v-for="resource in group.resources" :key="resource.url" class="recommendation-card">
              <div class="resource-type">{{ resourceTypeLabel(resource.type) }}</div>
              <b>{{ resource.title }}</b>
              <p>{{ resource.platform || '学习资源' }} · {{ resource.weekTopic }}</p>
              <div class="resource-actions">
                <a :href="resource.url" target="_blank" rel="noopener noreferrer" @click="trackClick(resource)">直达资源</a>
                <el-select
                  v-model="collectionTargets[resource.url]"
                  class="overview-select"
                  popper-class="practice-select-popper"
                  aria-label="选择收藏夹"
                >
                  <el-option
                    v-for="collection in collections"
                    :key="collection.id"
                    :label="collection.name"
                    :value="collection.id"
                  />
                </el-select>
                <button :disabled="savingUrl === resource.url || !collections.length" @click="saveResource(resource)">
                  {{ isFavorited(resource.url) ? '已收藏' : savingUrl === resource.url ? '保存中…' : '收藏' }}
                </button>
              </div>
            </article>
          </div>
        </details>
      </div>
      <div v-else class="overview-empty">当前学习计划还没有推荐资源，生成计划后会在这里集中展示。</div>
    </section>

    <section class="collection-section">
      <div class="section-heading collection-heading">
        <div><span>我的收藏夹</span><small>资源卡片可直接跳转，课程分类与自定义分类统一管理</small></div>
        <form class="new-collection" @submit.prevent="createCollection">
          <input v-model="newCollectionName" maxlength="80" placeholder="新建自定义收藏夹" />
          <button :disabled="creatingCollection || !newCollectionName.trim()">{{ creatingCollection ? '创建中…' : '创建' }}</button>
        </form>
      </div>
      <div class="collection-tabs">
        <button :class="{ active: activeCollectionId === 'all' }" @click="activeCollectionId = 'all'">
          全部 <span>{{ allFavorites.length }}</span>
        </button>
        <button
          v-for="collection in collections"
          :key="collection.id"
          :class="{ active: activeCollectionId === collection.id }"
          @click="activeCollectionId = collection.id"
        >
          {{ collection.name }} <span>{{ collection.resources.length }}</span>
        </button>
      </div>
      <div v-if="visibleFavorites.length" class="favorite-grid">
        <article v-for="favorite in visibleFavorites" :key="favorite.id" class="favorite-card">
          <a :href="favorite.url" target="_blank" rel="noopener noreferrer" @click="trackFavoriteClick(favorite)">
            <span>{{ favorite.platform || resourceTypeLabel(favorite.type) }}</span>
            <b>{{ favorite.title }}</b>
            <p>{{ collectionName(favorite.collectionId) }}</p>
            <i>打开资源 →</i>
          </a>
          <button aria-label="取消收藏" @click="removeFavorite(favorite)">×</button>
        </article>
      </div>
      <div v-else class="overview-empty">这个收藏夹还是空的，从上方智能体推送资源中收藏第一张卡片吧。</div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createResourceCollectionApi,
  favoriteResourceApi,
  fetchResourceCollectionsApi,
  recordResourceFeedbackApi,
  removeFavoriteResourceApi,
} from '../services/api'
import type {
  FavoriteResource,
  LearningPlan,
  PracticeQuestion,
  ResourceCollection,
  ResourceItem,
} from '../services/api'
import type { CourseProgress } from '../services/learningOverview'

const props = defineProps<{
  plan: LearningPlan | null
  questions: PracticeQuestion[]
  conversationId: string
  courseProgress: CourseProgress[]
  selectedCourseKey: string
}>()

const collections = ref<ResourceCollection[]>([])
const activeCollectionId = ref<'all' | number>('all')
const newCollectionName = ref('')
const creatingCollection = ref(false)
const savingUrl = ref('')
const errorMessage = ref('')
const collectionTargets = reactive<Record<string, number>>({})

const submittedCount = computed(() => props.questions.filter(question => question.status === 'SUBMITTED').length)
const allFavorites = computed(() => collections.value.flatMap(collection => collection.resources))
const visibleFavorites = computed(() => activeCollectionId.value === 'all'
  ? allFavorites.value
  : collections.value.find(collection => collection.id === activeCollectionId.value)?.resources || [])

function matchesAny(text: string, keywords: string[]) {
  const normalized = text.toLowerCase()
  return keywords.some(keyword => normalized.includes(keyword.toLowerCase()))
}

const selectedCourse = computed(() => props.courseProgress.find(
  course => course.key === props.selectedCourseKey) || props.courseProgress[0]!)
const overallMastery = computed(() => Math.round(props.courseProgress.reduce(
  (sum, course) => sum + course.mastery, 0) / Math.max(props.courseProgress.length, 1)))

const recommendedResources = computed(() => {
  const seen = new Set<string>()
  return (props.plan?.weeks || []).flatMap(week => (week.resources || []).map(resource => ({ ...resource, weekTopic: week.topic })))
    .filter(resource => resource.url && !seen.has(resource.url) && Boolean(seen.add(resource.url)))
})
const groupedRecommendedResources = computed(() => {
  const order = ['course', 'video', 'article', 'practice', 'image', 'other']
  const labels: Record<string, string> = {
    course: '系统课程', video: '视频讲解', article: '文章与文档', practice: '练习与实验',
    image: '知识图与图片', other: '其他资源',
  }
  const groups = new Map<string, typeof recommendedResources.value>()
  recommendedResources.value.forEach(resource => {
    const raw = (resource.type || '').toLowerCase()
    const key = order.includes(raw) ? raw : 'other'
    groups.set(key, [...(groups.get(key) || []), resource])
  })
  return order.filter(key => groups.has(key)).map(key => ({ key, label: labels[key], resources: groups.get(key)! }))
})

function inferCourse(resource: ResourceItem & { weekTopic?: string }) {
  const text = `${resource.title} ${resource.platform} ${resource.weekTopic || ''}`
  return props.courseProgress.find(course => matchesAny(text, course.aliases)) || selectedCourse.value
}

function inferChapter(course: CourseProgress, resource: ResourceItem & { weekTopic?: string }) {
  const text = `${resource.title} ${resource.weekTopic || ''}`
  return course.chapters.find(chapter => matchesAny(text, chapter.keywords))
}

function assignDefaultTargets() {
  for (const resource of recommendedResources.value) {
    if (collectionTargets[resource.url]) continue
    const course = inferCourse(resource)
    const target = collections.value.find(collection => collection.courseKey === course.key) || collections.value[0]
    if (target) collectionTargets[resource.url] = target.id
  }
}

async function loadCollections() {
  try {
    collections.value = await fetchResourceCollectionsApi()
    assignDefaultTargets()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '收藏夹加载失败'
  }
}

async function createCollection() {
  const name = newCollectionName.value.trim()
  if (!name) return
  creatingCollection.value = true
  errorMessage.value = ''
  try {
    const created = await createResourceCollectionApi(name)
    collections.value.unshift(created)
    activeCollectionId.value = created.id
    newCollectionName.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '收藏夹创建失败'
  } finally { creatingCollection.value = false }
}

async function saveResource(resource: ResourceItem & { weekTopic: string }) {
  const collectionId = collectionTargets[resource.url]
  if (!collectionId) return
  savingUrl.value = resource.url
  errorMessage.value = ''
  const course = inferCourse(resource)
  const chapter = inferChapter(course, resource)
  try {
    await favoriteResourceApi(collectionId, {
      ...resource,
      conversationId: props.conversationId,
      courseKey: course.key,
      chapterKey: chapter?.key,
    })
    await loadCollections()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '资源收藏失败'
  } finally { savingUrl.value = '' }
}

async function removeFavorite(favorite: FavoriteResource) {
  errorMessage.value = ''
  try {
    await removeFavoriteResourceApi(favorite.collectionId, favorite.id)
    await loadCollections()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '取消收藏失败'
  }
}

function isFavorited(url: string) { return allFavorites.value.some(resource => resource.url === url) }
function collectionName(id: number) { return collections.value.find(collection => collection.id === id)?.name || '收藏夹' }
function resourceTypeLabel(type?: string) {
  return ({ video: '视频', course: '课程', article: '文章', practice: '练习', image: '图片' } as Record<string, string>)[type || ''] || '资源'
}
function masteryClass(value: number) { return value >= 80 ? 'strong' : value >= 50 ? 'medium' : 'weak' }
function trackClick(resource: ResourceItem) {
  if (props.conversationId) void recordResourceFeedbackApi(props.conversationId, resource, 'CLICK')
}
function trackFavoriteClick(resource: FavoriteResource) {
  if (!props.conversationId) return
  void recordResourceFeedbackApi(props.conversationId, {
    title: resource.title, url: resource.url, platform: resource.platform || '', type: resource.type || '',
  }, 'CLICK')
}

watch(() => props.plan, assignDefaultTargets, { deep: true })
onMounted(loadCollections)
</script>

<style scoped>
.learning-overview { min-width: 0; height: 100%; padding: 38px 28px 48px; overflow-y: auto; color: var(--text-primary); }
.overview-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 28px; margin-bottom: 20px; }
.overview-header h1 { margin: 0 0 5px; color: var(--text-secondary); font-size: 14px; line-height: 1.5; }
.overview-header p { margin: 0; color: var(--text-faint); font-size: 12px; }
.overview-metrics { display: grid; grid-template-columns: repeat(3, minmax(92px, 1fr)); gap: 8px; }
.overview-metrics div { min-width: 92px; padding: 11px 13px; border: 1px solid var(--border-solid); border-radius: 12px; background: var(--ai-bubble-bg); }
.overview-metrics strong, .overview-metrics span { display: block; }
.overview-metrics strong { color: var(--accent); font-size: 19px; }
.overview-metrics span { margin-top: 2px; color: var(--text-faint); font-size: 10px; }
.overview-error { margin: -10px 0 16px; padding: 9px 12px; border: 1px solid var(--practice-danger-border); border-radius: 10px; background: var(--practice-danger-soft); color: var(--practice-danger); font-size: 12px; }
.chapter-section, .resource-section, .collection-section { margin-top: 24px; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 14px; margin-bottom: 10px; }
.section-heading > div > span, .section-heading > div > small { display: block; }
.section-heading > div > span { color: var(--text-secondary); font-size: 14px; font-weight: 700; }
.section-heading > div > small, .evidence-note { margin-top: 3px; color: var(--text-faint); font-size: 10px; font-weight: 400; }
.chapter-copy > i { height: 4px; overflow: hidden; border-radius: 999px; background: var(--border-solid); }
.chapter-copy em { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--accent), var(--accent-dark)); }
.chapter-list { overflow: hidden; border: 1px solid var(--border-solid); border-radius: 14px; background: var(--ai-bubble-bg); }
.chapter-row { display: grid; grid-template-columns: 28px minmax(0, 1fr) 92px 45px; align-items: center; gap: 12px; min-height: 61px; padding: 9px 14px; border-bottom: 1px solid var(--border-solid); }
.chapter-row:last-child { border-bottom: 0; }
.chapter-number { color: var(--text-faint); font-size: 10px; font-variant-numeric: tabular-nums; }
.chapter-copy > div { display: flex; align-items: baseline; gap: 8px; margin-bottom: 8px; }
.chapter-copy b { color: var(--text-secondary); font-size: 12px; }
.chapter-copy small { overflow: hidden; color: var(--text-faint); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.chapter-evidence span, .chapter-evidence small { display: block; color: var(--text-faint); font-size: 10px; }
.chapter-evidence small { margin-top: 3px; }
.chapter-row > strong { font-size: 13px; text-align: right; }
.chapter-row > strong.strong { color: var(--practice-success); }
.chapter-row > strong.medium { color: var(--accent); }
.chapter-row > strong.weak { color: var(--text-faint); }
.recommendation-grid, .favorite-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.resource-groups { display: grid; gap: 8px; }
.resource-group { overflow: hidden; border: 1px solid var(--border-solid); border-radius: 13px; background: color-mix(in srgb, var(--ai-bubble-bg) 72%, transparent); }
.resource-group > summary { min-height: 42px; display: flex; align-items: center; gap: 8px; padding: 0 13px; color: var(--text-secondary); cursor: pointer; list-style: none; }
.resource-group > summary::-webkit-details-marker { display: none; }
.resource-group > summary span { font-size: 12px; font-weight: 700; }
.resource-group > summary small { flex: 1; color: var(--text-faint); font-size: 10px; }
.resource-group > summary i { color: var(--accent); font-size: 13px; font-style: normal; transition: transform .2s; }
.resource-group[open] > summary i { transform: rotate(180deg); }
.resource-group .recommendation-grid { padding: 0 9px 9px; }
.recommendation-card { min-width: 0; padding: 13px; border: 1px solid var(--border-solid); border-radius: 13px; background: var(--ai-bubble-bg); }
.resource-type { display: inline-flex; padding: 3px 6px; border-radius: 6px; background: var(--accent-hover); color: var(--accent); font-size: 9px; }
.recommendation-card > b { display: -webkit-box; min-height: 38px; margin: 8px 0 4px; overflow: hidden; color: var(--text-secondary); font-size: 12px; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.recommendation-card > p { overflow: hidden; margin: 0; color: var(--text-faint); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.resource-actions { display: grid; grid-template-columns: auto minmax(72px, 1fr) auto; gap: 5px; margin-top: 11px; }
.resource-actions a, .resource-actions button, .new-collection button, .new-collection input { height: 27px; border: 1px solid var(--border-solid); border-radius: 8px; background: var(--bg-input); color: var(--text-secondary); font-size: 10px; }
.resource-actions a { display: flex; align-items: center; padding: 0 7px; color: var(--accent); text-decoration: none; }
.overview-select { min-width: 0; }
.overview-select :deep(.el-select__wrapper) { min-height: 34px; height: 34px; box-sizing: border-box; padding: 0 26px 0 9px; border: 1px solid var(--border-solid); border-radius: 9px; background: var(--bg-input); box-shadow: none; color: var(--text-secondary); font-size: 11px; transition: background .2s, border-color .2s; }
.overview-select :deep(.el-select__wrapper:hover) { background: var(--bg-hover); }
.overview-select :deep(.el-select__wrapper.is-focused) { border-color: var(--accent); background: var(--bg-input); box-shadow: none; }
.overview-select :deep(.el-select__selected-item) { color: var(--text-secondary); font-size: 11px; }
.overview-select :deep(.el-select__caret) { color: var(--text-faint); }
.resource-actions button, .new-collection button { padding: 0 8px; border-color: transparent; background: var(--accent); color: #fff; cursor: pointer; }
.resource-actions button:disabled, .new-collection button:disabled { opacity: .45; cursor: not-allowed; }
.collection-heading { align-items: center; }
.new-collection { display: flex; gap: 5px; }
.new-collection input { width: 150px; padding: 0 9px; outline: 0; }
.collection-tabs { display: flex; gap: 5px; margin-bottom: 9px; overflow-x: auto; }
.collection-tabs button { height: 29px; flex: 0 0 auto; padding: 0 9px; border: 1px solid var(--border-solid); border-radius: 9px; background: transparent; color: var(--text-faint); font-size: 10px; cursor: pointer; }
.collection-tabs button.active { border-color: var(--accent); background: var(--accent-hover); color: var(--accent); }
.collection-tabs span { margin-left: 3px; opacity: .7; }
.favorite-card { position: relative; min-width: 0; border: 1px solid var(--border-solid); border-radius: 13px; background: var(--ai-bubble-bg); transition: transform .2s, border-color .2s; }
.favorite-card:hover { border-color: color-mix(in srgb, var(--accent) 50%, var(--border-solid)); transform: translateY(-2px); }
.favorite-card > a { display: block; min-height: 126px; padding: 14px; color: inherit; text-decoration: none; }
.favorite-card a > span { color: var(--accent); font-size: 9px; }
.favorite-card a > b { display: -webkit-box; margin: 8px 20px 8px 0; overflow: hidden; color: var(--text-secondary); font-size: 13px; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.favorite-card a > p { margin: 0; color: var(--text-faint); font-size: 10px; }
.favorite-card a > i { display: block; margin-top: 13px; color: var(--accent); font-size: 10px; font-style: normal; }
.favorite-card > button { position: absolute; top: 8px; right: 8px; width: 22px; height: 22px; border: 0; border-radius: 7px; background: transparent; color: var(--text-faint); cursor: pointer; }
.favorite-card > button:hover { background: var(--practice-danger-soft); color: var(--practice-danger); }
.overview-empty { padding: 28px; border: 1px dashed var(--border-solid); border-radius: 13px; color: var(--text-faint); font-size: 11px; text-align: center; }
@media (max-width: 1120px) {
  .overview-header { display: block; }
  .overview-metrics { margin-top: 14px; }
  .recommendation-grid, .favorite-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 860px) {
  .recommendation-grid, .favorite-grid { grid-template-columns: 1fr; }
  .chapter-row { grid-template-columns: 25px minmax(0,1fr) 42px; }
  .chapter-evidence { display: none; }
  .collection-heading { display: block; }
  .new-collection { margin-top: 10px; }
}
</style>
