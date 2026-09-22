export function parsePriceQuery(raw: unknown): string {
  const first = Array.isArray(raw) ? raw[0] : raw
  if (typeof first !== 'string' || !/^\d+(\.\d{1,2})?$/.test(first.trim())) return ''
  const number = Number(first)
  return Number.isFinite(number) && number >= 0 && number <= 99999999 ? String(number) : ''
}

export function validatePriceRange(min: string, max: string): string {
  if ((min.trim() && parsePriceQuery(min) === '') || (max.trim() && parsePriceQuery(max) === '')) {
    return '请输入有效价格，最多两位小数且不能为负数'
  }
  if (min.trim() && max.trim() && Number(min) > Number(max)) return '最低价不能高于最高价'
  return ''
}
