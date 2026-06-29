<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Search } from 'lucide-vue-next'

const { t } = useI18n()

const props = defineProps<{
  id?: string
  name?: string
  modelValue: string
  placeholder?: string
  inputClass?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  search: []
}>()

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    emit('search')
  }
}
</script>

<template>
  <div class="relative">
    <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
    <Input
      :id="props.id"
      :name="props.name"
      :value="props.modelValue"
      :placeholder="placeholder || t('common.searchDot')"
      :class="['pl-7 h-8 text-xs', props.inputClass]"
      @input="onInput"
      @keydown="onKeydown"
    />
  </div>
</template>
