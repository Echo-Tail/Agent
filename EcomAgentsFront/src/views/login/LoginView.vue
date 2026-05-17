<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { validate, usernameRules, passwordRules } from '../../utils/validation'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const showPassword = ref(false)

async function handleLogin() {
  const usernameErr = validate(username.value, usernameRules)
  if (usernameErr) {
    message.warning(usernameErr)
    return
  }
  const passwordErr = validate(password.value, passwordRules)
  if (passwordErr) {
    message.warning(passwordErr)
    return
  }
  loading.value = true
  const result = await auth.login({
    username: username.value,
    password: password.value,
  })
  loading.value = false
  if (result.success) {
    message.success('登录成功')
    router.push({ name: 'Dashboard' })
  } else {
    message.error(result.message)
  }
}
</script>

<template>
  <n-card class="auth-card" :bordered="false">
    <div class="auth-header">
      <n-icon size="40" color="#C8815F">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
        </svg>
      </n-icon>
      <h2>EcomAgents</h2>
      <p class="auth-subtitle">企业电商智能体管理平台</p>
    </div>

    <n-form @submit.prevent="handleLogin">
      <n-form-item label="用户名" required>
        <n-input
          v-model:value="username"
          placeholder="请输入用户名"
          :disabled="loading"
        />
      </n-form-item>

      <n-form-item label="密码" required>
        <n-input
          v-model:value="password"
          :type="showPassword ? 'text' : 'password'"
          placeholder="请输入密码"
          :disabled="loading"
        >
          <template #suffix>
            <n-button
              quaternary
              size="tiny"
              @click="showPassword = !showPassword"
              tabindex="-1"
              type="button"
            >
              <template #icon>
                <n-icon>
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                    <path v-if="showPassword" d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
                    <path v-else d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/>
                  </svg>
                </n-icon>
              </template>
            </n-button>
          </template>
        </n-input>
      </n-form-item>

      <n-button
        type="primary"
        block
        :loading="loading"
        attr-type="submit"
        size="large"
      >
        登 录
      </n-button>
    </n-form>

    <div class="auth-footer">
      还没有账号？<router-link :to="{ name: 'Register' }">立即注册</router-link>
    </div>
  </n-card>
</template>

<style scoped>
.auth-card {
  width: 400px;
  max-width: 90vw;
}

.auth-header {
  text-align: center;
  margin-bottom: 24px;
}

.auth-header h2 {
  margin: 12px 0 4px;
  font-size: 24px;
}

.auth-subtitle {
  color: #888;
  font-size: 14px;
  margin: 0;
}

.auth-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #888;
}
</style>
