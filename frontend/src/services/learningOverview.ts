import type { LearningPlan, PracticeQuestion } from './api'

export type CourseDefinition = {
  key: string
  name: string
  short: string
  aliases: string[]
  chapters: Array<{ key: string; name: string; summary: string; keywords: string[] }>
}

export type ChapterProgress = CourseDefinition['chapters'][number] & {
  generated: number
  submitted: number
  correct: number
  mastery: number
}

export type CourseProgress = Omit<CourseDefinition, 'chapters'> & {
  chapters: ChapterProgress[]
  mastery: number
  evidenceCount: number
}

export const COURSE_CATALOG: CourseDefinition[] = [
  {
    key: 'data-structures', name: '数据结构', short: 'DS', aliases: ['数据结构', '算法'],
    chapters: [
      { key: 'intro-complexity', name: '绪论与复杂度分析', summary: '抽象数据类型、渐进复杂度与分析方法', keywords: ['绪论', '复杂度', '大o', '渐进', '抽象数据类型'] },
      { key: 'linear-list', name: '线性表', summary: '顺序表、链表与复杂度分析', keywords: ['线性表', '顺序表', '链表'] },
      { key: 'stack-queue-array', name: '栈、队列与数组', summary: '受限线性结构、多维数组及其应用', keywords: ['栈', '队列', '数组', '矩阵'] },
      { key: 'string', name: '串', summary: '字符串存储与模式匹配', keywords: ['串', '字符串', '模式匹配', 'kmp'] },
      { key: 'tree', name: '树与二叉树', summary: '遍历、哈夫曼树与结构转换', keywords: ['树', '二叉树', '遍历', '哈夫曼'] },
      { key: 'balanced-tree', name: 'BST 与平衡树', summary: '二叉搜索树、AVL 与红黑树', keywords: ['bst', '二叉搜索树', '平衡树', 'avl', '红黑树'] },
      { key: 'heap', name: '堆与优先队列', summary: '堆化、优先队列与 Top-K', keywords: ['堆', '优先队列', 'top-k', 'topk'] },
      { key: 'hash', name: '散列表', summary: '哈希函数、冲突处理与装载因子', keywords: ['散列表', '哈希', '散列', '装载因子'] },
      { key: 'graph', name: '图', summary: '遍历、最短路与拓扑结构', keywords: ['图', '最短路径', '拓扑', '生成树'] },
      { key: 'sort', name: '排序', summary: '内部排序与外部排序', keywords: ['排序', '快排', '归并', '基数'] },
      { key: 'search-index', name: '查找与索引', summary: '查找策略、B 树与 B+ 树索引', keywords: ['查找', '搜索', '索引', 'b树', 'b+树'] },
      { key: 'recursion-design', name: '递归与算法设计', summary: '递归、分治、贪心与动态规划', keywords: ['递归', '分治', '贪心', '动态规划'] },
      { key: 'np-completeness', name: 'NP-完全性', summary: '归约、P/NP 与可计算性边界', keywords: ['np', '归约', 'p问题', '完全性'] },
      { key: 'advanced-topic', name: '进阶专题', summary: '并查集、Trie 与高级结构', keywords: ['并查集', 'trie', '高级数据结构', '进阶专题'] },
    ],
  },
  {
    key: 'computer-organization', name: '计算机组成原理', short: 'CO', aliases: ['计算机组成', '组成原理', '体系结构'],
    chapters: [
      { key: 'system-overview', name: '计算机系统概论', summary: '层次结构、性能指标与系统组成', keywords: ['系统概论', '层次结构', '性能指标', '冯诺依曼'] },
      { key: 'data-representation', name: '数据表示与编码', summary: '数制、定点数与浮点数', keywords: ['数据表示', '编码', '补码', '浮点', '数制'] },
      { key: 'memory', name: '存储系统', summary: 'Cache、主存与虚拟存储', keywords: ['存储', 'cache', '缓存', '虚拟内存', '主存'] },
      { key: 'instruction', name: '指令系统', summary: '寻址方式与指令格式', keywords: ['指令', '寻址', 'isa'] },
      { key: 'cpu', name: '中央处理器', summary: '数据通路、控制器、时序与流水线', keywords: ['cpu', '处理器', '数据通路', '流水线', '控制器', '微程序'] },
      { key: 'bus', name: '总线', summary: '总线仲裁、定时与传输协议', keywords: ['总线', '仲裁', '总线周期', '同步总线'] },
      { key: 'io', name: '输入输出系统', summary: '中断、DMA 与外设接口', keywords: ['输入输出', 'i/o', 'io', '中断', 'dma', '外设'] },
      { key: 'digital-logic', name: '数字逻辑基础', summary: '组合逻辑、时序逻辑与存储元件', keywords: ['数字逻辑', '组合逻辑', '时序逻辑', '触发器'] },
      { key: 'parallel', name: '并行处理', summary: '多核、并行层次与加速比', keywords: ['并行', '多核', 'simd', '加速比'] },
      { key: 'secondary-storage', name: '辅助存储与 I/O 设备', summary: '磁盘、RAID 与常用外设', keywords: ['辅助存储', '磁盘', 'raid', 'i/o设备'] },
      { key: 'isa-comparison', name: '指令集对比', summary: '不同指令集设计取舍与比较', keywords: ['指令集对比', 'risc', 'cisc', 'isa对比'] },
      { key: 'review-resources', name: '综合复盘与资源', summary: '课程公式、工具与复习路径', keywords: ['附录', '资源速查', '综合复盘', '公式速查'] },
    ],
  },
]

function matchesAny(text: string, keywords: string[]) {
  const normalized = text.toLowerCase()
  return keywords.some(keyword => normalized.includes(keyword.toLowerCase()))
}

function searchableQuestion(question: PracticeQuestion) {
  return `${question.weekTopic} ${question.taskTitle} ${question.knowledgePoint || ''} ${question.learningObjective || ''}`.toLowerCase()
}

export function buildCourseProgress(plan: LearningPlan | null, questions: PracticeQuestion[]): CourseProgress[] {
  return COURSE_CATALOG.map(course => {
    const chapters = course.chapters.map(chapter => {
      const planned = plan?.weeks.some(week => matchesAny(`${week.topic} ${week.tasks.join(' ')}`, chapter.keywords)) || false
      const chapterQuestions = questions.filter(question => matchesAny(searchableQuestion(question), chapter.keywords))
      const submitted = chapterQuestions.filter(question => question.status === 'SUBMITTED')
      const correct = submitted.filter(question => question.correct).length
      let mastery = planned ? 10 : 0
      if (chapterQuestions.length) {
        const submissionRate = submitted.length / chapterQuestions.length
        const accuracy = submitted.length ? correct / submitted.length : 0
        mastery = Math.round(submissionRate * 20 + accuracy * 65 + Math.min(chapterQuestions.length / 5, 1) * 15)
        if (planned) mastery = Math.max(10, mastery)
      }
      return {
        ...chapter,
        generated: chapterQuestions.length,
        submitted: submitted.length,
        correct,
        mastery,
      }
    })
    const mastery = Math.round(chapters.reduce((sum, chapter) => sum + chapter.mastery, 0) / chapters.length)
    const evidenceCount = chapters.reduce((sum, chapter) => sum + chapter.generated, 0)
    return { ...course, chapters, mastery, evidenceCount }
  })
}
