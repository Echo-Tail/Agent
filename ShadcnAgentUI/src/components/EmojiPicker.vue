<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { listEmojiPacksApi } from '@/api/group'
import type { EmojiPack } from '@/types/group'
import { Button } from '@/components/ui/button'
import { SmilePlus } from 'lucide-vue-next'

const emit = defineEmits<{
  (e: 'select', emojiUrl: string): void
}>()

const emojiPacks = ref<EmojiPack[]>([])
const showPanel = ref(false)
const categories = ref<string[]>([])
const activeCategory = ref('')

// 硬编码回退 Emoji（API 无数据时使用）
const fallbackEmojis: EmojiPack[] = [
  ...'😀😃😄😁😅😂🤣😊😇🙂😉😌😍🥰😘😗😋😛😜🤪😝🤑'.split('').map((c, i) => ({ id: i, name: c, imageUrl: c, category: 'smileys', createdAt: '' })),
  ...'👍👎👌✌🤞🤟🤙👋🤚✋👏🙌🤲🙏💪'.split('').map((c, i) => ({ id: 100 + i, name: c, imageUrl: c, category: 'gestures', createdAt: '' })),
  ...'❤💛💚💙💜🖤💔💕💖💗💘💝💞💓❣'.split('').map((c, i) => ({ id: 200 + i, name: c, imageUrl: c, category: 'hearts', createdAt: '' })),
  ...'🔥⭐🌟✨💫🎉🎊🎈🎁🎂🎄🎃🎀🎗🎫'.split('').map((c, i) => ({ id: 300 + i, name: c, imageUrl: c, category: 'objects', createdAt: '' })),
]

onMounted(async () => {
  try {
    emojiPacks.value = await listEmojiPacksApi()
  } catch {
    // 静默处理
  }
  if (emojiPacks.value.length === 0) {
    emojiPacks.value = fallbackEmojis
  }
  const cats = new Set(emojiPacks.value.map(e => e.category).filter(Boolean))
  categories.value = Array.from(cats) as string[]
  if (categories.value.length > 0) activeCategory.value = categories.value[0]
})

const filteredEmojis = ref<EmojiPack[]>([])
watch(activeCategory, () => {
  filteredEmojis.value = activeCategory.value
    ? emojiPacks.value.filter(e => e.category === activeCategory.value)
    : emojiPacks.value
}, { immediate: true })

function selectEmoji(emoji: EmojiPack) {
  emit('select', emoji.imageUrl)
  showPanel.value = false
}

function togglePanel() {
  showPanel.value = !showPanel.value
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.emoji-picker-container')) {
    showPanel.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="relative emoji-picker-container">
    <Button variant="outline" size="icon" class="h-[44px] w-[44px] shrink-0" :title="'表情包'" @click="togglePanel">
      <SmilePlus class="h-4 w-4" />
    </Button>

    <Transition name="fade">
      <div
        v-if="showPanel"
        class="absolute bottom-full right-0 mb-1 w-72 rounded-md border bg-popover text-popover-foreground shadow-md z-50"
        @mousedown.prevent
      >
        <div class="p-2">
          <!-- 分类 tabs -->
          <div v-if="categories.length > 1" class="flex gap-1 overflow-x-auto pb-2 border-b mb-2">
            <button
              v-for="cat in categories"
              :key="cat"
              :class="['px-2 py-1 text-xs rounded-md', cat === activeCategory ? 'bg-primary text-primary-foreground' : 'hover:bg-accent']"
              @click="activeCategory = cat"
            >
              {{ cat }}
            </button>
          </div>

          <!-- 表情网格 -->
          <div class="grid grid-cols-6 gap-1 max-h-48 overflow-y-auto">
            <button
              v-for="emoji in filteredEmojis"
              :key="emoji.id"
              class="flex items-center justify-center h-10 w-10 rounded-md hover:bg-accent cursor-pointer"
              :title="emoji.name"
              @click="selectEmoji(emoji)"
            >
              <span v-if="emoji.imageUrl.length <= 2" class="text-xl leading-none">{{ emoji.imageUrl }}</span>
              <img v-else :src="emoji.imageUrl" :alt="emoji.name" class="h-7 w-7 object-contain" />
            </button>
          </div>

          <p v-if="emojiPacks.length === 0" class="text-xs text-muted-foreground text-center py-4">
            暂无表情包
          </p>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
