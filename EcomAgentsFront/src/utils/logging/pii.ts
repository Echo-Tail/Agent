const SENSITIVE_KEYS = new RegExp(
  /^(username|email|password|apiKey|token|authorization|secret)$/i,
)

export function maskPII(data: unknown): unknown {
  if (typeof data !== 'object' || data === null) return data
  if (Array.isArray(data)) return data.map(maskPII)

  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(data as Record<string, unknown>)) {
    if (SENSITIVE_KEYS.test(key)) {
      result[key] = '***'
    } else if (typeof value === 'object' && value !== null) {
      result[key] = maskPII(value)
    } else {
      result[key] = value
    }
  }
  return result
}
