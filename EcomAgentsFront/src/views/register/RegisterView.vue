<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { validate, usernameRules, passwordRules, emailRules, inviteCodeRules } from '../../utils/validation'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const email = ref('')
const inviteCode = ref('')
const loading = ref(false)
const showPassword = ref(false)

async function handleRegister() {
  const usernameErr = validate(username.value, usernameRules)
  if (usernameErr) { message.warning(usernameErr); return }
  const passwordErr = validate(password.value, passwordRules)
  if (passwordErr) { message.warning(passwordErr); return }
  const emailErr = validate(email.value, emailRules)
  if (emailErr) { message.warning(emailErr); return }
  const codeErr = validate(inviteCode.value, inviteCodeRules)
  if (codeErr) { message.warning(codeErr); return }

  if (password.value !== confirmPassword.value) {
    message.warning('两次密码输入不一致')
    return
  }

  loading.value = true
  const result = await auth.register({
    username: username.value,
    password: password.value,
    email: email.value || undefined,
    inviteCode: inviteCode.value,
  })
  loading.value = false

  if (result.success) {
    message.success('注册成功，请登录')
    router.push({ name: 'Login' })
  } else {
    message.error(result.message)
  }
}
</script>

<template>
  <n-card class="auth-card" :bordered="false">
    <div class="auth-header">
      <h2>注册账号</h2>
      <p class="auth-subtitle">创建你的 EcomAgents 账号</p>
    </div>

    <n-form @submit.prevent="handleRegister">
      <n-form-item label="用户名" required>
        <n-input v-model:value="username" placeholder="2-20个字符" :disabled="loading" />
      </n-form-item>

      <n-form-item label="邮箱" optional>
        <n-input v-model:value="email" placeholder="选填" :disabled="loading" />
      </n-form-item>

      <n-form-item label="密码" required>
        <n-input
          v-model:value="password"
          :type="showPassword ? 'text' : 'password'"
          placeholder="至少6位"
          :disabled="loading"
        />
      </n-form-item>

      <n-form-item label="确认密码" required>
        <n-input
          v-model:value="confirmPassword"
          :type="showPassword ? 'text' : 'password'"
          placeholder="再次输入密码"
          :disabled="loading"
        />
      </n-form-item>

      <n-form-item label="邀请码" required>
        <n-input v-model:value="inviteCode" placeholder="请输入邀请码" :disabled="loading" />
      </n-form-item>

      <n-button
        type="primary"
        block
        :loading="loading"
        attr-type="submit"
        size="large"
      >
        注 册
      </n-button>
    </n-form>

    <div class="auth-footer">
      已有账号？<router-link :to="{ name: 'Login' }">返回登录</router-link>
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
  margin: 0 0 4px;
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
