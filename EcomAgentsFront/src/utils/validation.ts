export interface ValidationRule {
  required?: boolean
  min?: number
  max?: number
  pattern?: RegExp
  message: string
  validator?: (value: string) => boolean | string
}

export function validate(value: string, rules: ValidationRule[]): string | null {
  for (const rule of rules) {
    if (rule.required && !value.trim()) {
      return rule.message
    }
    if (value.trim()) {
      if (rule.min !== undefined && value.length < rule.min) {
        return rule.message
      }
      if (rule.max !== undefined && value.length > rule.max) {
        return rule.message
      }
      if (rule.pattern && !rule.pattern.test(value)) {
        return rule.message
      }
      if (rule.validator) {
        const result = rule.validator(value)
        if (typeof result === 'string') return result
        if (!result) return rule.message
      }
    }
  }
  return null
}

export const usernameRules: ValidationRule[] = [
  { required: true, message: '请输入用户名' },
  { min: 2, message: '用户名至少 2 个字符' },
  { max: 20, message: '用户名最多 20 个字符' },
  { pattern: /^[a-zA-Z0-9_一-龥]+$/, message: '用户名只能包含字母、数字、下划线和中文' },
]

export const passwordRules: ValidationRule[] = [
  { required: true, message: '请输入密码' },
  { min: 6, message: '密码至少 6 位' },
  { max: 32, message: '密码最多 32 位' },
]

export const emailRules: ValidationRule[] = [
  {
    pattern: /^$|^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    message: '请输入正确的邮箱格式',
  },
]

export const inviteCodeRules: ValidationRule[] = [
  { required: true, message: '请输入邀请码' },
  { min: 4, message: '邀请码至少 4 位' },
]
