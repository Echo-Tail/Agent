import { describe, it, expect } from 'vitest'
import { validate, usernameRules, passwordRules, emailRules, inviteCodeRules } from '../../utils/validation'

describe('validate()', () => {
  it('returns null when all rules pass', () => {
    expect(validate('hello', [{ pattern: /^[a-z]+$/, message: 'fail' }])).toBeNull()
  })

  it('returns the rule message on required failure', () => {
    const r = validate('', [{ required: true, message: '必填' }])
    expect(r).toBe('必填')
  })

  it('returns the rule message on min length failure', () => {
    const r = validate('ab', [{ min: 3, message: '至少3个' }])
    expect(r).toBe('至少3个')
  })

  it('returns the rule message on max length failure', () => {
    const r = validate('abcdef', [{ max: 5, message: '最多5个' }])
    expect(r).toBe('最多5个')
  })

  it('returns the rule message on pattern failure', () => {
    const r = validate('123', [{ pattern: /^[a-z]+$/, message: '仅字母' }])
    expect(r).toBe('仅字母')
  })

  it('skips min/max/pattern when value is empty and not required', () => {
    const r = validate('', [{ min: 3, message: 'no' }, { pattern: /x/, message: 'no' }])
    expect(r).toBeNull()
  })

  it('returns string from validator function', () => {
    const r = validate('x', [{ validator: (v) => v === 'y' ? 'wrong' : true, message: 'fallback' }])
    expect(r).toBeNull()
  })

  it('returns message when validator returns false', () => {
    const r = validate('x', [{ validator: () => false, message: 'custom fail' }])
    expect(r).toBe('custom fail')
  })

  it('checks rules in order, returns first failure', () => {
    const rules = [
      { required: true, message: '必填' },
      { min: 5, message: '太短' },
    ]
    expect(validate('ab', rules)).toBe('太短')
  })
})

describe('usernameRules', () => {
  it('passes valid usernames', () => {
    expect(validate('张三', usernameRules)).toBeNull()
    expect(validate('admin123', usernameRules)).toBeNull()
    expect(validate('test_user', usernameRules)).toBeNull()
  })

  it('requires non-empty', () => {
    expect(validate('', usernameRules)).toBe('请输入用户名')
  })

  it('rejects too short', () => {
    expect(validate('1', usernameRules)).toBe('用户名至少 2 个字符')
  })

  it('rejects too long', () => {
    expect(validate('a'.repeat(21), usernameRules)).toBe('用户名最多 20 个字符')
  })

  it('rejects special characters', () => {
    expect(validate('hello@world', usernameRules)).toContain('字母、数字')
    expect(validate('foo bar', usernameRules)).toContain('字母、数字')
  })
})

describe('passwordRules', () => {
  it('passes valid passwords', () => {
    expect(validate('123456', passwordRules)).toBeNull()
    expect(validate('abcdefgh', passwordRules)).toBeNull()
  })

  it('requires non-empty', () => {
    expect(validate('', passwordRules)).toBe('请输入密码')
  })

  it('rejects too short', () => {
    expect(validate('12345', passwordRules)).toBe('密码至少 6 位')
  })

  it('rejects too long', () => {
    expect(validate('x'.repeat(33), passwordRules)).toBe('密码最多 32 位')
  })
})

describe('emailRules', () => {
  it('passes valid emails', () => {
    expect(validate('test@example.com', emailRules)).toBeNull()
  })

  it('allows empty (optional field)', () => {
    expect(validate('', emailRules)).toBeNull()
  })

  it('rejects invalid format', () => {
    expect(validate('not-an-email', emailRules)).toBe('请输入正确的邮箱格式')
  })
})

describe('inviteCodeRules', () => {
  it('requires non-empty', () => {
    expect(validate('', inviteCodeRules)).toBe('请输入邀请码')
  })

  it('rejects too short', () => {
    expect(validate('AB', inviteCodeRules)).toBe('邀请码至少 4 位')
  })

  it('passes valid code', () => {
    expect(validate('FREE001', inviteCodeRules)).toBeNull()
  })
})
