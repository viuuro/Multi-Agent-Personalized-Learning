import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 构建配置
 *
 * 开发服务器运行在 localhost:5173，
 * /api 路径的请求代理到后端 Spring Boot (localhost:8080)，
 * 解决前后端分离开发时的跨域问题。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,  // 前端开发服务器端口
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 后端 Spring Boot 地址
        changeOrigin: true,               // 修改请求头中的 origin
      },
    },
  },
})
