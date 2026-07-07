/**
 * 敏感信息脱敏工具。
 * 用于在日志输出前遮盖认证凭据、密钥和个人信息。
 */

/** 需要脱敏的请求头名称列表（小写比对）。 */
const SENSITIVE_HEADERS = new Set(['authorization', 'x-api-key', 'cookie', 'set-cookie'])

/** 需要脱敏的请求/响应体字段名模式（小写比对，含嵌套）。 */
const SENSITIVE_BODY_FIELDS = new Set([
  'password', 'secret', 'apiKey', 'api_key', 'apikey',
  'token', 'refreshToken', 'refresh_token', 'accessToken',
  'creditCard', 'credit_card', 'cardNumber', 'card_number',
  'cvv', 'cvv2',
])

/** 手机号正则：1开头的11位数字 */
const PHONE_REGEX = /(1[3-9]\d)\d{4}(\d{4})/g

/** 身份证号正则：18位（数字或X结尾） */
const ID_CARD_REGEX = /(\d{6})\d{8}(\d{4}[\dXx])/g

/**
 * 脱敏请求头对象。
 * 返回新对象，不影响原始对象。
 */
export function maskHeaders(headers: Record<string, string | string[] | undefined>): Record<string, string | undefined> {
  const result: Record<string, string | undefined> = {}
  for (const [key, value] of Object.entries(headers)) {
    if (value === undefined) continue
    if (SENSITIVE_HEADERS.has(key.toLowerCase())) {
      result[key] = maskSensitiveValue(String(value))
    } else {
      result[key] = String(value)
    }
  }
  return result
}

/**
 * 递归脱敏对象中的敏感字段。
 * 返回新对象，不影响原始对象。
 */
export function maskBody(body: unknown): unknown {
  if (body === null || body === undefined) return body
  if (typeof body === 'string') return maskString(body)
  if (typeof body === 'number' || typeof body === 'boolean') return body
  if (Array.isArray(body)) return body.map(maskBody)

  if (typeof body === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(body as Record<string, unknown>)) {
      const lowerKey = key.toLowerCase()
      if (SENSITIVE_BODY_FIELDS.has(lowerKey)) {
        result[key] = maskSensitiveValue(String(value ?? ''))
      } else {
        result[key] = maskBody(value)
      }
    }
    return result
  }
  return body
}

/**
 * 脱敏 URL 中的敏感查询参数。
 */
export function maskUrl(url: string): string {
  try {
    const u = new URL(url, window.location.origin)
    const sensitiveParams = ['token', 'access_token', 'api_key', 'apikey', 'secret', 'key']
    let changed = false
    for (const param of sensitiveParams) {
      if (u.searchParams.has(param)) {
        u.searchParams.set(param, '****')
        changed = true
      }
    }
    return changed ? u.toString() : url
  } catch {
    // 不是标准 URL，用正则兜底
    return url.replace(/([?&](?:token|access_token|api_key|secret)=)[^&]+/gi, '$1****')
  }
}

/**
 * 对字符串中的个人信息进行脱敏。
 * - 手机号：138****1234
 * - 身份证号：110101****1234
 */
export function maskString(str: string): string {
  let result = str
    .replace(PHONE_REGEX, '$1****$2')
    .replace(ID_CARD_REGEX, '$1********$2')
  return result.length > 512 ? result.slice(0, 512) + '...(truncated)' : result
}

/**
 * 脱敏一个敏感字段值。
 * 对于 Bearer token 保留前缀，密码类字段完全遮盖。
 */
function maskSensitiveValue(value: string): string {
  if (!value) return ''
  // Bearer token: 保留 "Bearer " 前缀
  const bearerMatch = value.match(/^(Bearer\s+)(.+)$/i)
  if (bearerMatch) {
    const token = bearerMatch[2]
    if (token.length <= 8) return `${bearerMatch[1]}****`
    return `${bearerMatch[1]}${token.slice(0, 4)}****${token.slice(-4)}`
  }
  // API Key 模式 (sk-...)
  if (value.startsWith('sk-') || value.startsWith('sk_')) {
    if (value.length <= 10) return `${value.slice(0, 3)}****`
    return `${value.slice(0, 5)}****${value.slice(-4)}`
  }
  // 一般敏感值
  if (value.length <= 4) return '****'
  return `${value.slice(0, 2)}****${value.slice(-2)}`
}

/**
 * 便捷函数：从 Axios 配置中提取并脱敏请求信息。
 */
export function maskRequestInfo(config: { method?: string; url?: string; headers?: Record<string, unknown>; data?: unknown }) {
  return {
    method: config.method?.toUpperCase() ?? 'UNKNOWN',
    url: maskUrl(config.url ?? ''),
    headers: maskHeaders(config.headers as Record<string, string | string[] | undefined>),
    body: config.data ? maskBody(config.data) : undefined,
  }
}

/**
 * 便捷函数：从 Axios 响应中提取并脱敏响应信息。
 */
export function maskResponseInfo(response: { status: number; data?: unknown; headers?: Record<string, string | string[] | undefined> }) {
  return {
    status: response.status,
    data: response.data ? maskBody(response.data) : undefined,
    headers: response.headers ? maskHeaders(response.headers) : undefined,
  }
}
