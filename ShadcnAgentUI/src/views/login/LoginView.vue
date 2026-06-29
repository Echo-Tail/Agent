<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Bot, Eye, EyeOff, Loader2 } from 'lucide-vue-next'

const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  error.value = ''
  if (!username.value.trim() || !password.value.trim()) {
    error.value = t('auth.usernamePasswordRequired')
    return
  }
  loading.value = true
  try {
    const result = await auth.login({ username: username.value.trim(), password: password.value.trim() })
    if (result.success) {
      router.push({ name: 'Dashboard' })
    } else {
      error.value = result.message || t('error.loginFailed')
    }
  } catch {
    error.value = t('error.networkError')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Card class="w-full max-w-sm mx-4">
    <CardHeader class="text-center">
      <div class="flex justify-center mb-2">
        <Bot class="h-10 w-10 text-primary" />
      </div>
      <CardTitle class="text-xl">EcomAgents</CardTitle>
      <CardDescription>{{ $t('auth.loginTitle') }}</CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
        {{ error }}
      </div>
      <div class="space-y-2">
        <Label for="username">{{ $t('placeholder.username') }}</Label>
        <Input
          id="username"
          name="username"
          v-model="username"
          :placeholder="$t('placeholder.username')"
          :disabled="loading"
          @keyup.enter="handleLogin"
        />
      </div>
      <div class="space-y-2">
        <Label for="password">{{ $t('placeholder.password') }}</Label>
        <div class="relative">
          <Input
            id="password"
            name="password"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            :placeholder="$t('placeholder.password')"
            :disabled="loading"
            @keyup.enter="handleLogin"
          />
          <Button
            variant="ghost"
            size="icon"
            class="absolute right-1 top-1/2 -translate-y-1/2 h-7 w-7"
            @click="showPassword = !showPassword"
            type="button"
          >
            <Eye v-if="!showPassword" class="h-4 w-4" />
            <EyeOff v-else class="h-4 w-4" />
          </Button>
        </div>
      </div>
      <Button class="w-full" :disabled="loading" @click="handleLogin">
        <Loader2 v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
        {{ $t('auth.login') }}
      </Button>
    </CardContent>
    <CardFooter class="justify-center">
      <p class="text-sm text-muted-foreground">
        {{ $t('auth.noAccount') }}
        <router-link to="/register" class="text-primary hover:underline">{{ $t('auth.goRegister') }}</router-link>
      </p>
    </CardFooter>
  </Card>
</template>
