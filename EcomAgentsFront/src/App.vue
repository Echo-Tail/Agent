<script setup lang="ts">
import { ref, watch } from 'vue'
import { darkTheme, zhCN, dateZhCN } from 'naive-ui'
import type { GlobalTheme } from 'naive-ui'
import { useThemeStore } from './stores/theme'

const themeStore = useThemeStore()
const naiveTheme = ref<GlobalTheme | null>(null)

/**
 * 和风复古 (Japanese Warm Retro) — 主题覆盖
 * 灵感：日式喫茶店 + 侘寂风
 * 主色：#C8815F 陶土橙  侧边栏：#3D3631 浓缩棕  背景：#F5F0EB 米白
 */

const themeOverrides = {
  common: {
    primaryColor: '#C8815F',
    primaryColorHover: '#D49470',
    primaryColorPressed: '#B0755A',
    primaryColorSuppl: '#C8815F',
    borderRadius: '6px',
    bodyColor: '#F5F0EB',
    cardColor: '#FDFBF9',
    modalColor: '#FDFBF9',
    popoverColor: '#FDFBF9',
    tableColor: '#FDFBF9',
    tableHeaderColor: '#EDE8E0',
    inputColor: '#FDFBF9',
    inputColorDisabled: '#EDE8E0',
    textColor1: '#3D3229',
    textColor2: '#8B8178',
    textColor3: '#B5ADA5',
    placeholderColor: '#B5ADA5',
    borderColor: '#E5DDD5',
    dividerColor: '#E5DDD5',
    actionColor: '#F0EBE5',
    clearColor: '#FDFBF9',
    hoverColor: 'rgba(200,129,95,0.06)',
    successColor: '#8BA888',
    errorColor: '#C47A7A',
    warningColor: '#D4A86A',
    infoColor: '#879EAF',
    boxShadow: '0 2px 8px rgba(61,50,41,0.08)',
    boxShadow2: '0 4px 16px rgba(61,50,41,0.12)',
    fontSize: '14px',
    fontWeight: '400',
    fontWeightStrong: '600',
  },
  Layout: {
    color: '#F5F0EB',
    headerColor: '#FDFBF9',
    siderColor: '#3D3631',
    footerColor: '#FDFBF9',
  },
  Menu: {
    itemTextColor: 'rgba(255,255,255,0.72)',
    itemTextColorHover: '#FDFBF9',
    itemTextColorActive: '#FDFBF9',
    itemIconColor: 'rgba(255,255,255,0.60)',
    itemIconColorHover: '#FDFBF9',
    itemIconColorActive: '#C8815F',
    itemColorActive: 'rgba(200,129,95,0.18)',
    itemColorActiveHover: 'rgba(200,129,95,0.26)',
    itemColorHover: 'rgba(255,255,255,0.06)',
    borderRadius: '4px',
    fontWeight: '500',
    color: '#3D3631',
  },
  Button: {
    borderRadius: '6px',
    borderRadiusRound: '20px',
    fontWeight: '500',
  },
  Card: {
    borderRadius: '8px',
    color: '#FDFBF9',
    borderColor: '#E5DDD5',
  },
  Input: {
    borderRadius: '6px',
    color: '#FDFBF9',
    textColor: '#3D3229',
    placeholderColor: '#B5ADA5',
    borderColor: '#E5DDD5',
    borderHover: '#C8815F',
    borderFocus: '#C8815F',
    loadingColor: '#C8815F',
  },
  InternalSelectMenu: {
    color: '#FDFBF9',
    borderColor: '#E5DDD5',
    optionTextColor: '#3D3229',
    optionTextColorHover: '#3D3229',
    optionColorHover: 'rgba(200,129,95,0.06)',
    optionColorActive: 'rgba(200,129,95,0.12)',
  },
  Table: {
    borderRadius: '8px',
    thColor: '#EDE8E0',
    thTextColor: '#3D3229',
    tdColor: '#FDFBF9',
    tdTextColor: '#3D3229',
    borderColor: '#E5DDD5',
  },
  Tag: {
    borderRadius: '4px',
  },
  Modal: {
    borderRadius: '8px',
    color: '#FDFBF9',
  },
  Dialog: {
    borderRadius: '8px',
    color: '#FDFBF9',
  },
  Notification: {
    borderRadius: '8px',
    color: '#FDFBF9',
  },
  Message: {
    borderRadius: '6px',
  },
  Switch: {
    railColorActive: '#C8815F',
    railColor: '#D5CDC5',
  },
  Tabs: {
    tabTextColorActiveLine: '#C8815F',
    tabTextColorActiveBar: '#C8815F',
    barColor: '#C8815F',
    tabFontWeightActive: '600',
    paneTextColor: '#3D3229',
  },
  DataTable: {
    borderRadius: '8px',
    thColor: '#EDE8E0',
    thTextColor: '#3D3229',
    tdColor: '#FDFBF9',
    tdTextColor: '#3D3229',
    borderColor: '#E5DDD5',
  },
  Form: {
    labelTextColor: '#3D3229',
    feedbackTextColor: '#C47A7A',
  },
  Statistic: {
    labelTextColor: '#8B8178',
    valueTextColor: '#3D3229',
  },
  Spin: {
    color: '#C8815F',
  },
  Progress: {
    railColor: '#E5DDD5',
    color: '#C8815F',
  },
  Badge: {
    color: '#C47A7A',
  },
  Tooltip: {
    color: '#3D3631',
    textColor: '#FDFBF9',
  },
  Upload: {
    borderRadius: '6px',
  },
  Avatar: {
    borderRadius: '6px',
  },
  Divider: {
    color: '#E5DDD5',
  },
  Empty: {
    iconColor: '#B5ADA5',
    textColor: '#8B8178',
  },
  Result: {
    iconColor: '#B5ADA5',
    textColor: '#8B8178',
  },
}

const darkThemeOverrides = {
  common: {
    primaryColor: '#D49470',
    primaryColorHover: '#DFA080',
    primaryColorPressed: '#C8815F',
    primaryColorSuppl: '#D49470',
    borderRadius: '6px',
    bodyColor: '#2A2623',
    cardColor: '#383430',
    modalColor: '#383430',
    popoverColor: '#383430',
    tableColor: '#383430',
    tableHeaderColor: '#32302C',
    inputColor: '#32302C',
    inputColorDisabled: '#2E2A27',
    textColor1: '#E8E0D8',
    textColor2: '#9E958C',
    textColor3: '#7A726A',
    placeholderColor: '#7A726A',
    borderColor: '#4D4641',
    dividerColor: '#4D4641',
    actionColor: '#32302C',
    clearColor: '#383430',
    hoverColor: 'rgba(255,255,255,0.04)',
    successColor: '#8BA888',
    errorColor: '#C47A7A',
    warningColor: '#D4A86A',
    infoColor: '#879EAF',
    boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
    boxShadow2: '0 4px 16px rgba(0,0,0,0.4)',
    fontSize: '14px',
    fontWeight: '400',
    fontWeightStrong: '600',
  },
  Layout: {
    color: '#2A2623',
    headerColor: '#383430',
    siderColor: '#1F1C1A',
    footerColor: '#383430',
  },
  Menu: {
    itemTextColor: 'rgba(255,255,255,0.62)',
    itemTextColorHover: '#FDFBF9',
    itemTextColorActive: '#FDFBF9',
    itemIconColor: 'rgba(255,255,255,0.55)',
    itemIconColorHover: '#FDFBF9',
    itemIconColorActive: '#D49470',
    itemColorActive: 'rgba(212,148,112,0.16)',
    itemColorActiveHover: 'rgba(212,148,112,0.24)',
    itemColorHover: 'rgba(255,255,255,0.05)',
    borderRadius: '4px',
    fontWeight: '500',
    color: '#1F1C1A',
  },
  Button: {
    borderRadius: '6px',
    borderRadiusRound: '20px',
    fontWeight: '500',
  },
  Card: {
    borderRadius: '8px',
    color: '#383430',
    borderColor: '#4D4641',
  },
  Input: {
    borderRadius: '6px',
    color: '#32302C',
    textColor: '#E8E0D8',
    placeholderColor: '#7A726A',
    borderColor: '#4D4641',
    borderHover: '#D49470',
    borderFocus: '#D49470',
    loadingColor: '#D49470',
  },
  InternalSelectMenu: {
    color: '#383430',
    borderColor: '#4D4641',
    optionTextColor: '#E8E0D8',
    optionTextColorHover: '#E8E0D8',
    optionColorHover: 'rgba(255,255,255,0.04)',
    optionColorActive: 'rgba(212,148,112,0.12)',
  },
  Table: {
    borderRadius: '8px',
    thColor: '#32302C',
    thTextColor: '#E8E0D8',
    tdColor: '#383430',
    tdTextColor: '#E8E0D8',
    borderColor: '#4D4641',
  },
  Tag: {
    borderRadius: '4px',
  },
  Modal: {
    borderRadius: '8px',
    color: '#383430',
  },
  Dialog: {
    borderRadius: '8px',
    color: '#383430',
  },
  Notification: {
    borderRadius: '8px',
    color: '#383430',
  },
  Message: {
    borderRadius: '6px',
  },
  Switch: {
    railColorActive: '#D49470',
    railColor: '#4D4641',
  },
  Tabs: {
    tabTextColorActiveLine: '#D49470',
    tabTextColorActiveBar: '#D49470',
    barColor: '#D49470',
    tabFontWeightActive: '600',
    paneTextColor: '#E8E0D8',
  },
  DataTable: {
    borderRadius: '8px',
    thColor: '#32302C',
    thTextColor: '#E8E0D8',
    tdColor: '#383430',
    tdTextColor: '#E8E0D8',
    borderColor: '#4D4641',
  },
  Form: {
    labelTextColor: '#E8E0D8',
    feedbackTextColor: '#C47A7A',
  },
  Statistic: {
    labelTextColor: '#9E958C',
    valueTextColor: '#E8E0D8',
  },
  Spin: {
    color: '#D49470',
  },
  Progress: {
    railColor: '#4D4641',
    color: '#D49470',
  },
  Badge: {
    color: '#C47A7A',
  },
  Tooltip: {
    color: '#1F1C1A',
    textColor: '#E8E0D8',
  },
  Upload: {
    borderRadius: '6px',
  },
  Avatar: {
    borderRadius: '6px',
  },
  Divider: {
    color: '#4D4641',
  },
  Empty: {
    iconColor: '#7A726A',
    textColor: '#9E958C',
  },
  Result: {
    iconColor: '#7A726A',
    textColor: '#9E958C',
  },
}

const activeThemeOverrides = ref(themeOverrides)

function applyTheme(isDark: boolean) {
  naiveTheme.value = isDark ? darkTheme : null
  activeThemeOverrides.value = isDark ? darkThemeOverrides : themeOverrides
  document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light')
}

watch(() => themeStore.isDark, applyTheme, { immediate: true })
</script>

<template>
  <n-config-provider
    :theme="naiveTheme"
    :theme-overrides="activeThemeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
  >
    <n-notification-provider>
      <n-message-provider>
        <n-dialog-provider>
          <router-view />
        </n-dialog-provider>
      </n-message-provider>
    </n-notification-provider>
  </n-config-provider>
</template>

<style>
body {
  margin: 0;
  padding: 0;
}

/* ========== 和风复古 · 全局 CSS 变量 ========== */
:root,
[data-theme="light"] {
  --bg-color: #F5F0EB;
  --card-bg: #FDFBF9;
  --text-color: #3D3229;
  --text-muted: #8B8178;
  --border-color: #E5DDD5;
  --sidebar-bg: #3D3631;
  --primary-color: #C8815F;
  --panel-bg: #F0EBE5;
  --selected-bg: #F0E2D8;
  --message-bg: #FAF6F1;
  --message-assistant-bg: #FAF6F1;
  --message-user-bg: #F0EBE5;
  --hover-bg: rgba(200, 129, 95, 0.08);
}

[data-theme="dark"] {
  --bg-color: #2A2623;
  --card-bg: #383430;
  --text-color: #E8E0D8;
  --text-muted: #9E958C;
  --border-color: #4D4641;
  --sidebar-bg: #1F1C1A;
  --primary-color: #D49470;
  --panel-bg: #2E2A27;
  --selected-bg: rgba(212, 148, 112, 0.2);
  --message-bg: #3D3835;
  --message-assistant-bg: #3D3835;
  --message-user-bg: #32302C;
  --hover-bg: rgba(255, 255, 255, 0.05);
}
</style>
