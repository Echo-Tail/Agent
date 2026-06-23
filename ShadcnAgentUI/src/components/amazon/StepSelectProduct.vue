<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import { listProductProfiles, type ProductProfile } from '@/api/product-profiles'
import { CheckCircle, FileText } from 'lucide-vue-next'

const emit = defineEmits<{
  select: [profile: ProductProfile]
}>()

const profiles = ref<ProductProfile[]>([])
const loading = ref(true)
const selected = ref<ProductProfile | null>(null)

onMounted(async () => {
  try {
    const res = await listProductProfiles({ size: 50 })
    profiles.value = (res.content ?? []).filter(p => p.status === 'CONFIRMED')
  } finally {
    loading.value = false
  }
})

function confirmSelect() {
  if (selected.value) emit('select', selected.value)
}
</script>

<template>
  <div class="space-y-4">
    <p class="text-sm text-muted-foreground">选择一个已确认的产品资料作为本次生成任务的目标产品。</p>

    <div v-if="loading" class="py-8 text-center text-sm text-muted-foreground">加载产品资料列表...</div>

    <div v-else-if="profiles.length === 0" class="py-8 text-center text-sm text-muted-foreground">
      暂无已确认的产品资料。请先在「产品资料」菜单上传并确认产品信息。
    </div>

    <div v-else class="space-y-2 max-h-[400px] overflow-y-auto">
      <button
        v-for="p in profiles"
        :key="p.id"
        :class="[
          'w-full rounded-md border p-3 text-left transition-colors',
          selected?.id === p.id ? 'border-primary bg-accent' : 'hover:bg-accent/50',
        ]"
        @click="selected = p"
      >
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-2 min-w-0">
            <FileText class="h-4 w-4 text-muted-foreground shrink-0" />
            <span class="font-medium truncate">{{ p.productName }}</span>
          </div>
          <CheckCircle v-if="selected?.id === p.id" class="h-4 w-4 text-primary shrink-0" />
        </div>
        <div class="mt-1 flex flex-wrap gap-1 text-xs text-muted-foreground">
          <span v-if="p.brand">{{ p.brand }}</span>
          <span v-if="p.sku">SKU: {{ p.sku }}</span>
          <span v-if="p.modelNumber">型号: {{ p.modelNumber }}</span>
        </div>
      </button>
    </div>

    <div class="flex justify-end pt-2">
      <Button :disabled="!selected" @click="confirmSelect">确认选择</Button>
    </div>
  </div>
</template>
