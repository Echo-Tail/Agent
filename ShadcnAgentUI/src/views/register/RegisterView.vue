<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Bot, Eye, EyeOff, Loader2 } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const email = ref('')
const inviteCode = ref('')
const showPassword = ref(false)
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  error.value = ''
  if (!username.value.trim() || !password.value.trim() || !inviteCode.value.trim()) {
    error.value = t('validation.registerFieldsRequired')
    return
    return
  }
  loading.value = true
  try {
    const result = await auth.register({
      username: username.value.trim(),
      password: password.value.trim(),
      email: email.value.trim() || undefined,
      inviteCode: inviteCode.value.trim(),
    })
    if (result.success) {
      router.push({ name: 'Login' })
    } else {
      error.value = result.message || t('error.registerFailed')
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
      <CardTitle class="text-xl">{{ $t('auth.registerTitle') }}</CardTitle>
      <CardDescription>{{ $t('auth.registerTitle') }}</CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
        {{ error }}
      </div>
      <div class="space-y-2">
        <Label for="username">{{ $t('settings.username') }} <span class="text-destructive">*</span></Label>
        <Input id="username" name="username" v-model="username" :placeholder="$t('placeholder.usernameHint')" :disabled="loading" />
      </div>
      <div class="space-y-2">
        <Label for="password">{{ $t('placeholder.password') }} <span class="text-destructive">*</span></Label>
        <div class="relative">
          <Input
            id="password"
            name="password"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            :placeholder="$t('placeholder.passwordHint')"
            :disabled="loading"
          />
          <Button
            variant="ghost" size="icon"
            class="absolute right-1 top-1/2 -translate-y-1/2 h-7 w-7"
            @click="showPassword = !showPassword" type="button"
          >
            <Eye v-if="!showPassword" class="h-4 w-4" />
            <EyeOff v-else class="h-4 w-4" />
          </Button>
        </div>
      </div>
      <div class="space-y-2">
        <Label for="email">{{ $t('settings.email') }}</Label>
        <Input id="email" name="email" v-model="email" type="email" :placeholder="$t('placeholder.email')" :disabled="loading" />
      </div>
      <div class="space-y-2">
        <Label for="inviteCode">{{ $t('placeholder.inviteCode') }} <span class="text-destructive">*</span></Label>
        <Input id="inviteCode" name="invite-code" v-model="inviteCode" :placeholder="$t('placeholder.inviteCodeHint')" :disabled="loading" />
      </div>
      <Button class="w-full" :disabled="loading" @click="handleRegister">
        <Loader2 v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
        {{ $t('auth.register') }}
      </Button>
    </CardContent>
    <CardFooter class="justify-center">
      <p class="text-sm text-muted-foreground">
        {{ $t('auth.hasAccount') }}
        <router-link to="/login" class="text-primary hover:underline">{{ $t('auth.goLogin') }}</router-link>
      </p>
    </CardFooter>
  </Card>
</template>
