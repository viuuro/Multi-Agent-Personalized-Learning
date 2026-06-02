/**
 * 个性化学习多智能体系统 —— 前端入口文件
 *
 * 初始化 Vue 3 应用，注册以下插件：
 *   - Pinia: 状态管理（聊天状态 + 画像状态）
 *   - Element Plus: UI 组件库（按钮、输入框、折叠面板等）
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'  // Element Plus 全局样式
import App from './App.vue'

const app = createApp(App)
app.use(createPinia())   // 注册 Pinia 状态管理
app.use(ElementPlus)     // 注册 Element Plus UI 库
app.mount('#app')        // 挂载到 index.html 的 #app 元素
