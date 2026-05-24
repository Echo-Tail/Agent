export enum UserRole {
  ADMIN = 'admin',
  USER = 'user',
}

export const UserRoleKeys: Record<string, string> = {
  [UserRole.ADMIN]: 'userRole.admin',
  [UserRole.USER]: 'userRole.user',
}

export enum UserStatus {
  ACTIVE = 'active',
  DISABLED = 'disabled',
}

export const UserStatusKeys: Record<string, string> = {
  [UserStatus.ACTIVE]: 'userStatus.active',
  [UserStatus.DISABLED]: 'userStatus.disabled',
}

export enum ToolCategory {
  WEB = 'web',
  TERMINAL_FILES = 'terminal_files',
  BROWSER = 'browser',
  MEDIA = 'media',
  MEMORY = 'memory',
  MCP = 'mcp',
}

export const ToolCategoryKeys: Record<string, string> = {
  [ToolCategory.WEB]: 'toolCategory.web',
  [ToolCategory.TERMINAL_FILES]: 'toolCategory.terminal_files',
  [ToolCategory.BROWSER]: 'toolCategory.browser',
  [ToolCategory.MEDIA]: 'toolCategory.media',
  [ToolCategory.MEMORY]: 'toolCategory.memory',
  [ToolCategory.MCP]: 'toolCategory.mcp',
}
