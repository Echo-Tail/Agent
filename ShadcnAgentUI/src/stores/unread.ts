/**
 * 未读消息状态管理
 *
 * 维护私聊和群聊的未读消息计数，支持：
 * - 从后端批量拉取未读汇总（fetchAll）
 * - 通过 SSE 事件增量更新（incrementPrivate / incrementGroup）
 * - 进入聊天时清除未读（clearPrivate / clearGroup）
 * - 侧边栏展示总未读数（totalPrivate / totalGroup）
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/api/request'
import type { RequestConfig } from '@/api/request'

const silentPollingConfig: RequestConfig = { silent: true, skipRetry: true }

export const useUnreadStore = defineStore('unread', () => {
  /** 私聊未读映射：key 为对方用户 ID，value 为未读消息数 */
  const privateMessages = ref<Record<number, number>>({})
  /** 群聊未读映射：key 为群 ID，value 为未读消息数 */
  const groupMessages = ref<Record<number, number>>({})

  /** 计算所有私聊未读总数 */
  const totalPrivate = () => Object.values(privateMessages.value).reduce((s, c) => s + c, 0)
  /** 计算所有群聊未读总数 */
  const totalGroup = () => Object.values(groupMessages.value).reduce((s, c) => s + c, 0)

  /**
   * 从后端批量拉取所有未读汇总数据
   * 同时请求私聊和群聊两个接口，合并结果到 store
   */
  async function fetchAll() {
    try {
      const [privateData, groupData] = await Promise.all([
        http.get<any, Array<{ userId: number; count: number }>>('/messages/unread-summary', silentPollingConfig),
        http.get<any, Array<{ groupId: number; count: number }>>('/groups/unread-summary', silentPollingConfig),
      ])
      const pm: Record<number, number> = {}
      for (const item of privateData ?? []) pm[item.userId] = item.count
      privateMessages.value = pm

      const gm: Record<number, number> = {}
      for (const item of groupData ?? []) gm[item.groupId] = item.count
      groupMessages.value = gm
    } catch { /* 静默处理，保持上次的未读数据 */ }
  }

  /** 增量增加某私聊会话的未读数（由 SSE unread_private 事件触发） */
  function incrementPrivate(otherUserId: number) {
    privateMessages.value = { ...privateMessages.value, [otherUserId]: (privateMessages.value[otherUserId] ?? 0) + 1 }
  }

  /** 清除某私聊会话的未读数（进入聊天时调用） */
  function clearPrivate(otherUserId: number) {
    const copy = { ...privateMessages.value }
    delete copy[otherUserId]
    privateMessages.value = copy
  }

  /** 增量增加某群的未读数（由 SSE unread_group 事件触发） */
  function incrementGroup(groupId: number) {
    groupMessages.value = { ...groupMessages.value, [groupId]: (groupMessages.value[groupId] ?? 0) + 1 }
  }

  /** 清除某群的未读数（进入群聊时调用） */
  function clearGroup(groupId: number) {
    const copy = { ...groupMessages.value }
    delete copy[groupId]
    groupMessages.value = copy
  }

  return {
    privateMessages, groupMessages,
    totalPrivate, totalGroup,
    fetchAll, incrementPrivate, clearPrivate, incrementGroup, clearGroup,
  }
})
