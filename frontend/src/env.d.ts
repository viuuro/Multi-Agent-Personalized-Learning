/// <reference types="vite/client" />

/**
 * TypeScript 类型声明
 *
 * 为 .vue 单文件组件声明模块类型，
 * 使 TypeScript 能够正确识别 .vue 文件的导入。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
