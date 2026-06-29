import type { Component } from 'vue'
import {
  Bot,
  MessageSquare,
  Settings,
  Search,
  ShoppingCart,
  Wrench,
  Brain,
  Star,
  Heart,
  Lightbulb,
  Rocket,
  Globe,
} from 'lucide-vue-next'

export interface AgentIconDef {
  key: string
  label: string
  component: Component
}

export const AGENT_ICONS: AgentIconDef[] = [
  { key: 'robot', label: '机器人', component: Bot },
  { key: 'chat', label: '对话', component: MessageSquare },
  { key: 'gear', label: '齿轮', component: Settings },
  { key: 'search', label: '搜索', component: Search },
  { key: 'cart', label: '购物车', component: ShoppingCart },
  { key: 'tools', label: '工具', component: Wrench },
  { key: 'brain', label: '大脑', component: Brain },
  { key: 'star', label: '星星', component: Star },
  { key: 'heart', label: '爱心', component: Heart },
  { key: 'lightbulb', label: '灯泡', component: Lightbulb },
  { key: 'rocket', label: '火箭', component: Rocket },
  { key: 'globe', label: '地球', component: Globe },
]

export const AGENT_ICON_MAP: Record<string, Component> = Object.fromEntries(
  AGENT_ICONS.map(i => [i.key, i.component])
)
