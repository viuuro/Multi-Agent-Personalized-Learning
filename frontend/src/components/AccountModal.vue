<template>
  <el-dialog
    v-model="visible"
    :align-center="true"
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="false"
    :lock-scroll="true"
    width="420px"
    modal-class="plan-expand-overlay"
  >
    <h2 class="acct-title">账号管理</h2>

    <div class="acct-form">
      <!-- 头像编辑 -->
      <div class="avatar-section">
        <label class="acct-label">头像</label>
        <div class="avatar-row">
          <el-avatar :size="56" :src="avatarPreview || undefined" :style="{ background: '#D4916F' }">
            {{ authStore.user?.username?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
          <el-button size="small" @click="triggerFileInput">选择图片</el-button>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            style="display:none"
            @change="handleFileChange"
          />
        </div>
      </div>

      <!-- 用户名 -->
      <div class="acct-field">
        <label class="acct-label">用户名</label>
        <el-input v-model="editUsername" size="large" placeholder="请输入新用户名" clearable />
      </div>

      <!-- 原密码 -->
      <div class="acct-field">
        <label class="acct-label">原密码</label>
        <el-input v-model="currentPassword" type="password" size="large" placeholder="如需改密码请先输入原密码" show-password autocomplete="new-password" />
      </div>

      <!-- 新密码 -->
      <div class="acct-field">
        <label class="acct-label">新密码（留空则不修改）</label>
        <el-input v-model="editPassword" type="password" size="large" placeholder="请输入新密码" show-password autocomplete="new-password" />
      </div>
    </div>

    <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
    <p v-if="successMsg" class="success-msg">{{ successMsg }}</p>

    <div class="acct-footer">
      <el-button size="large" class="cancel-btn" @click="handleClose">取消</el-button>
      <button class="submit-btn" :disabled="saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '../stores/authStore'
import { updateProfileApi } from '../services/api'

const emit = defineEmits<{
  (e: 'close'): void
}>()

const visible = defineModel<boolean>({ required: true })

const authStore = useAuthStore()
const editUsername = ref(authStore.user?.username || '')
const currentPassword = ref('')
const editPassword = ref('')
const avatarPreview = ref(authStore.user?.avatar || '')
const avatarBase64 = ref('')
const errorMsg = ref('')
const successMsg = ref('')
const saving = ref(false)
const fileInputRef = ref<HTMLInputElement>()

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.size > 2 * 1024 * 1024) {
    errorMsg.value = '图片大小不能超过 2MB'
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    const result = reader.result as string
    avatarPreview.value = result
    avatarBase64.value = result
  }
  reader.readAsDataURL(file)
}

async function handleSave() {
  saving.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const user = authStore.user
    if (!user) throw new Error('未登录')

    if (editPassword.value && !currentPassword.value) {
      errorMsg.value = '请输入原密码'
      saving.value = false
      return
    }

    const updated = await updateProfileApi(
      user.id,
      editUsername.value.trim() || undefined,
      currentPassword.value || undefined,
      editPassword.value || undefined,
      avatarBase64.value || undefined
    )
    authStore.updateUser({
      username: updated.username,
      avatar: updated.avatar,
    })
    successMsg.value = '保存成功'
    setTimeout(() => { visible.value = false; emit('close') }, 800)
  } catch (e: any) {
    errorMsg.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function handleClose() {
  visible.value = false
  emit('close')
}
</script>

<style scoped>
.acct-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: #7A6A60;
  margin-bottom: 24px;
}

.acct-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.acct-label {
  font-size: 13px;
  color: #A09080;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-row .el-button {
  border-radius: 8px;
}

.acct-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.error-msg {
  color: #e57373;
  font-size: 13px;
  text-align: center;
  margin-top: 12px;
}

.success-msg {
  color: #67c23a;
  font-size: 13px;
  text-align: center;
  margin-top: 12px;
}

.acct-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

.cancel-btn {
  background: #fff !important;
  border: 1px solid #D8D0C8 !important;
  color: #8A7565 !important;
  border-radius: 12px !important;
  padding: 8px 24px !important;
  font-size: 14px !important;
  height: 40px !important;
}

.submit-btn {
  padding: 8px 24px;
  height: 40px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #D4916F, #B87858);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.submit-btn:not(:disabled):hover {
  opacity: 0.85;
}
</style>
