export interface PublishDraftForm {
  title: string
  desc: string
  price: string
  condition: string
  categoryId: number | undefined
  tradeType: number
  images: string[]
  deliveryFee: string
}
export const draftKey = (userId?: number) => (userId ? `hebeu:publish-draft:${userId}` : null)
export function readDraft(raw: string | null): { savedAt: number; form: PublishDraftForm } | null {
  try {
    if (!raw) return null
    const data = JSON.parse(raw)
    const form = data?.form
    if (!form || typeof form.title !== 'string' || typeof form.desc !== 'string') return null
    if (
      !Number.isFinite(data.savedAt) ||
      data.savedAt > Date.now() ||
      Date.now() - data.savedAt > 30 * 86400000
    )
      return null
    return {
      savedAt: data.savedAt,
      form: {
        title: form.title.slice(0, 100),
        desc: form.desc.slice(0, 5000),
        price: typeof form.price === 'string' ? form.price.slice(0, 20) : '',
        condition: /^[1-9]$|^10$/.test(String(form.condition)) ? String(form.condition) : '9',
        categoryId:
          Number.isSafeInteger(form.categoryId) && form.categoryId > 0
            ? form.categoryId
            : undefined,
        tradeType: [0, 1, 2].includes(form.tradeType) ? form.tradeType : 0,
        images: Array.isArray(form.images)
          ? form.images
              .filter(
                (url: unknown): url is string =>
                  typeof url === 'string' && /^(https?:\/\/|\/(?!\/))/.test(url),
              )
              .slice(0, 9)
          : [],
        deliveryFee: typeof form.deliveryFee === 'string' ? form.deliveryFee.slice(0, 20) : '',
      },
    }
  } catch {
    return null
  }
}
