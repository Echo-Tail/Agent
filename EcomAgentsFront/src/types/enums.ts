export enum UserRole {
  ADMIN = 'admin',
  USER = 'user',
}

export const UserRoleLabels: Record<string, string> = {
  [UserRole.ADMIN]: '管理员',
  [UserRole.USER]: '普通用户',
}

export enum UserStatus {
  ACTIVE = 'active',
  DISABLED = 'disabled',
}

export const UserStatusLabels: Record<string, string> = {
  [UserStatus.ACTIVE]: '正常',
  [UserStatus.DISABLED]: '已禁用',
}

export enum ToolCategory {
  WEB = 'web',
  TERMINAL_FILES = 'terminal_files',
  BROWSER = 'browser',
  MEDIA = 'media',
  MEMORY = 'memory',
  MCP = 'mcp',
}

export const ToolCategoryLabels: Record<string, string> = {
  [ToolCategory.WEB]: 'Web搜索',
  [ToolCategory.TERMINAL_FILES]: '终端与文件',
  [ToolCategory.BROWSER]: '浏览器自动化',
  [ToolCategory.MEDIA]: '图片生成',
  [ToolCategory.MEMORY]: '记忆系统',
  [ToolCategory.MCP]: 'MCP服务',
}
