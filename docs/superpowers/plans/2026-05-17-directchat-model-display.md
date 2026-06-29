# DirectChatView 模型信息显示 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在默认对话页面（DirectChatView）面包屑栏中显示当前使用模型的名称和提供商，并支持点击切换模型。

**Architecture:** 纯前端变更，复用 ChatView 已有的 `listModelsApi`、模型计算属性 `currentModelLabel` 和 `n-dropdown` 模式。将模型药丸嵌入 DirectChatView 的面包屑栏右侧区域，在 `isDirectMode` 为 true 时显示。

**Tech Stack:** Vue 3, TypeScript, Naive UI (n-dropdown, n-icon), Pinia

---

### Task 1: DirectChatView 模型展示与切换

**Files:**
- Modify: `EcomAgentsFront/src/views/chat/DirectChatView.vue`

- [ ] **Step 1: 在 script setup 中添加模型相关状态和逻辑**

在现有 imports 区域添加：

```typescript
import type { DropdownOption } from 'naive-ui'
import { listModelsApi } from '../../api/model'
import { updateAgentApi } from '../../api/agent'
import type { Agent } from '../../types/agent'
import type { AiModel } from '../../types/api'
```

`Agent` 和 `AiModel` 类型可能已经导入，检查后去重。

在 `systemAgent` 相关 ref 之后添加：

```typescript
/* ====== Model display and switching ====== */
const models = ref<AiModel[]>([])
const modelsLoading = ref(false)

async function loadModels() {
  if (models.value.length > 0) return
  modelsLoading.value = true
  try {
    const res = await listModelsApi()
    if (res.data.code === 200) {
      models.value = res.data.data ?? []
    }
  } catch {
    // ignore
  } finally {
    modelsLoading.value = false
  }
}

const currentModelLabel = computed(() => {
  if (!systemAgent.value?.modelId) return ''
  const m = models.value.find((x) => x.id === systemAgent.value!.modelId)
  return m ? `${m.name} (${m.provider})` : ''
})

const modelMenuOptions = computed(() => {
  const grouped = new Map<string, DropdownOption[]>()
  for (const m of models.value) {
    if (!grouped.has(m.provider)) {
      grouped.set(m.provider, [])
    }
    grouped.get(m.provider)!.push({
      label: m.name,
      key: `model_${m.id}`,
    })
  }
  return Array.from(grouped.entries()).map(([provider, children]) => ({
    label: provider,
    key: `provider_${provider}`,
    type: 'submenu' as const,
    children,
  }))
})

async function handleModelSelect(key: string) {
  if (!key.startsWith('model_')) return
  const modelId = Number(key.slice(6))
  if (!systemAgent.value || modelId === systemAgent.value.modelId) return

  const modelName = models.value.find((m) => m.id === modelId)?.name
  if (!modelName) return

  try {
    const res = await updateAgentApi(systemAgent.value.id, { modelId })
    if (res.data.code === 200) {
      systemAgent.value = res.data.data
      message.success('模型已切换')
    } else {
      message.error(res.data.message || '模型切换失败')
    }
  } catch {
    message.error('模型切换失败')
  }
}
```

在 `init` 函数中 `fetchTools()` 之后添加 `loadModels()`：

```typescript
async function init() {
  fetchTools()
  loadModels()          // <-- 添加此行
  agentStore.fetchAgents()
  await loadSystemAgent()
  // ... rest of init
}
```

- [ ] **Step 2: 在面包屑栏中添加模型药丸**

在 `<div class="breadcrumb-bar">` 中找到 `breadcrumb-right` 区域。在 `新对话` 按钮之前添加模型下拉菜单：

```html
<div class="breadcrumb-bar">
  <div class="breadcrumb-left">
    <span
      v-if="isDirectMode"
      class="breadcrumb-item active"
    >
      💬 默认对话
    </span>
    <n-dropdown
      v-if="currentModelLabel && isDirectMode"
      trigger="click"
      :options="modelMenuOptions"
      @select="handleModelSelect"
    >
      <span class="model-pill">
        {{ currentModelLabel }}
        <n-icon size="12">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 10l5 5 5-5z"/>
          </svg>
        </n-icon>
      </span>
    </n-dropdown>
    <template v-else>
      <span class="breadcrumb-item link" @click="handleSwitchToDirect">
        💬 默认对话
      </span>
      <!-- ... separator + agent name remain unchanged ... -->
    </template>
  </div>
  <div class="breadcrumb-right">
    <!-- model pill for non-direct mode could go here or be omitted -->
    <n-button size="tiny" quaternary @click="handleNewSession">
      <!-- ... existing button ... -->
    </n-button>
  </div>
</div>
```

需要注意：当前 `DirectChatView.vue` 的面包屑栏中，`isDirectMode` 为 true 时显示 `💬 默认对话`，为 false 时显示 "默认对话 | 🤖 对话中" 的导航路径。模型药丸紧跟在 `💬 默认对话` 文本之后。

实际上，更好的写法是在 `isDirectMode` 块内合并模型药丸：

```html
<div class="breadcrumb-left">
  <span
    v-if="isDirectMode"
    class="breadcrumb-item active"
  >
    💬 默认对话
  </span>
  <n-dropdown
    v-if="currentModelLabel && isDirectMode"
    trigger="click"
    :options="modelMenuOptions"
    @select="handleModelSelect"
  >
    <span class="model-pill">
      {{ currentModelLabel }}
      <n-icon size="12">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
          <path d="M7 10l5 5 5-5z"/>
        </svg>
      </n-icon>
    </span>
  </n-dropdown>
  <template v-else>
    <!-- existing: non-direct breadcrumb -->
  </template>
</div>
```

- [ ] **Step 3: 添加模型药丸样式**

在 `<style scoped>` 末尾添加：

```css
.model-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
  background: var(--tag-bg, #f0f0f0);
  color: var(--text-color, #666);
  cursor: pointer;
  transition: background 0.15s;
  margin-left: 8px;
  white-space: nowrap;
}

.model-pill:hover {
  background: var(--hover-bg, #e0e0e0);
}
```

- [ ] **Step 4: 验证前端编译通过**

Run: `cd EcomAgentsFront && npm run build`
Expected: 编译成功，无 TypeScript 或 lint 错误

- [ ] **Step 5: 运行前端测试**

Run: `cd EcomAgentsFront && npm test`
Expected: 全部测试通过（现有测试不应受此次变更影响）

- [ ] **Step 6: Commit**

```bash
git add EcomAgentsFront/src/views/chat/DirectChatView.vue
git commit -m "feat: show model info in DirectChatView breadcrumb bar

- Add model label pill next to default conversation title
- Support model switching via dropdown (reuses ChatView pattern)
- Load models on mount via existing listModelsApi
- Persist model change to system agent via updateAgentApi

Closes #2"
```
