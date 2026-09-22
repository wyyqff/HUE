import { ElMessage, ElMessageBox } from 'element-plus'

export interface ModalOptions {
  type?: 'success' | 'error' | 'warning' | 'info'
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  showCancel?: boolean
  closable?: boolean
}
export interface PromptOptions {
  title?: string
  message?: string
  placeholder?: string
  confirmText?: string
  cancelText?: string
  inputValue?: string
  inputType?: 'text' | 'textarea'
  validator?: (value: string) => string | null
}
function notify(
  type: 'success' | 'error' | 'warning' | 'info',
  message: string,
  title?: string,
): Promise<boolean> {
  ElMessage({
    type,
    message: title ? `${title}：${message}` : message,
    showClose: true,
    grouping: true,
    duration: type === 'error' ? 4500 : 3000,
  })
  return Promise.resolve(true)
}
export const showSuccess = (message: string, title?: string) => notify('success', message, title)
export const showError = (message: string, title?: string) => notify('error', message, title)
export const showWarning = (message: string, title?: string) => notify('warning', message, title)
export const showInfo = (message: string, title?: string) => notify('info', message, title)
export const showUnauthorized = (message = '请先登录后再进行此操作') =>
  showWarning(message, '需要登录')
export const showNeedAuth = (message = '请先完成校园认证') => showWarning(message, '需要认证')
export async function showConfirm(
  message: string,
  title = '请确认',
  options?: Partial<ModalOptions>,
): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      type: options?.type || 'warning',
      confirmButtonText: options?.confirmText || '确定',
      cancelButtonText: options?.cancelText || '取消',
      showClose: options?.closable !== false,
      closeOnClickModal: false,
      autofocus: false,
    })
    return true
  } catch {
    return false
  }
}
export async function showPrompt(
  options: PromptOptions,
): Promise<{ confirmed: boolean; value: string }> {
  try {
    const result = await ElMessageBox.prompt(options.message || '', options.title || '请输入', {
      confirmButtonText: options.confirmText || '确定',
      cancelButtonText: options.cancelText || '取消',
      inputPlaceholder: options.placeholder,
      inputValue: options.inputValue || '',
      inputType: options.inputType || 'text',
      inputValidator: options.validator
        ? (value: string) => options.validator!(value) || true
        : undefined,
      closeOnClickModal: false,
    })
    return { confirmed: true, value: result.value }
  } catch {
    return { confirmed: false, value: options.inputValue || '' }
  }
}
export default {
  success: showSuccess,
  error: showError,
  warning: showWarning,
  info: showInfo,
  confirm: showConfirm,
  prompt: showPrompt,
  unauthorized: showUnauthorized,
  needAuth: showNeedAuth,
}
