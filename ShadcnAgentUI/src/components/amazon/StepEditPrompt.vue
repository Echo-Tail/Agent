<script setup lang="ts">
import { ref, computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Save, BookmarkPlus } from 'lucide-vue-next'
import { toast } from 'sonner'

const emit = defineEmits<{
  save: [data: { structuredBrief: string; finalPrompt: string; negativePrompt: string }]
}>()

const props = defineProps<{
  initialPromptText?: string
  initialBrief?: string
  initialNegativePrompt?: string
  materialFactsJson?: string
  checkedKeys?: string
}>()

const activeTab = ref<'brief' | 'prompt' | 'material'>('prompt')
const promptText = ref(props.initialPromptText || '')
const negativePrompt = ref(props.initialNegativePrompt || '')
const structuredBrief = ref(props.initialBrief || '')
const materialFacts = computed(() => {
  if (!props.materialFactsJson) return []
  try {
    return Object.entries(JSON.parse(props.materialFactsJson)).map(([key]) => key)
  } catch { return [] }
})
const checkedMaterialKeys = ref(new Set<string>(
  (props.checkedKeys || '').split(',').map(k => k.trim()).filter(Boolean)
))

function toggleMaterialKey(key: string) {
  if (checkedMaterialKeys.value.has(key)) {
    checkedMaterialKeys.value.delete(key)
  } else {
    checkedMaterialKeys.value.add(key)
  }
}

function saveToPromptLibrary() {
  if (!promptText.value.trim()) {
    toast.error('请先填写最终提示词')
    return
  }
  // Emit to parent, parent handles save to prompt library API
  toast.success('提示词已复制到剪贴板，请在提示词库中粘贴保存')
  navigator.clipboard.writeText(promptText.value)
}

function confirmSave() {
  emit('save', {
    structuredBrief: structuredBrief.value,
    finalPrompt: promptText.value,
    negativePrompt: negativePrompt.value,
  })
}
</script>

<template>
  <div class="space-y-4">
    <p class="text-sm text-muted-foreground">编辑最终生成提示词。结构化 brief 仅作参考，最终提示词才是生成入口。</p>

    <Tabs :default-value="activeTab">
      <TabsList>
        <TabsTrigger value="prompt">最终提示词</TabsTrigger>
        <TabsTrigger value="brief">结构化 Brief</TabsTrigger>
        <TabsTrigger v-if="materialFacts.length > 0" value="material">素材事实</TabsTrigger>
      </TabsList>

      <TabsContent value="prompt" class="space-y-3 pt-3">
        <div>
          <label class="text-sm font-medium">最终自然语言提示词 <span class="text-destructive">*</span></label>
          <Textarea v-model="promptText" class="mt-1 min-h-[200px]" placeholder="输入最终图生图提示词..." />
        </div>
        <div>
          <label class="text-sm font-medium">负面提示词</label>
          <Textarea v-model="negativePrompt" class="mt-1 min-h-[80px]" placeholder="distorted screen, wrong button layout..." />
        </div>
      </TabsContent>

      <TabsContent value="brief" class="space-y-3 pt-3">
        <div class="rounded-md border bg-muted/20 p-3">
          <p class="text-xs text-muted-foreground mb-2">此 brief 由系统自动生成，不可编辑。请在「最终提示词」Tab 中调整。</p>
          <pre class="whitespace-pre-wrap text-xs">{{ structuredBrief || '暂无 brief，请先生成图片表达结构。' }}</pre>
        </div>
      </TabsContent>

      <TabsContent value="material" class="space-y-3 pt-3">
        <p class="text-xs text-muted-foreground">勾选需要加入 prompt 的素材事实。默认不参与。</p>
        <div class="space-y-2">
          <button
            v-for="key in materialFacts"
            :key="key"
            :class="[
              'w-full rounded-md border px-3 py-2 text-left text-sm transition-colors',
              checkedMaterialKeys.has(key) ? 'border-primary bg-accent' : 'hover:bg-accent/50',
            ]"
            @click="toggleMaterialKey(key)"
          >
            <div class="flex items-center gap-2">
              <input type="checkbox" :checked="checkedMaterialKeys.has(key)" class="h-4 w-4" />
              <span>{{ key }}</span>
            </div>
          </button>
        </div>
      </TabsContent>
    </Tabs>

    <div class="flex items-center justify-between pt-2">
      <Button variant="outline" size="sm" @click="saveToPromptLibrary">
        <BookmarkPlus class="h-4 w-4 mr-1" />保存到提示词库
      </Button>
      <Button @click="confirmSave">
        <Save class="h-4 w-4 mr-1" />保存 Prompt
      </Button>
    </div>
  </div>
</template>
