import { expect, test, type Page } from '@playwright/test'

test.use({ channel: process.env.E2E_BROWSER_CHANNEL })

const user = (userId = 10) => ({ userId, nickName: '测试用户', authStatus: 2, schoolCode: 'SC001', campusCode: 'CP001', money: 0, creditScore: 100 })
const product = { productId: 123, sellerId: 20, sellerName: '卖家甲', categoryId: 1, title: '确认面板测试商品', schoolCode: 'SC001', campusCode: 'CP001', schoolName: '测试学校', campusName: '主校区', itemCondition: 9, price: 88, deliveryFee: 6, tradeType: 1, tradeStatus: 0, reviewStatus: 1, createTime: '2026-09-22 10:00:00' }
const order = { orderId: 1, orderNo: 'O20260922001', buyerId: 10, sellerId: 20, productId: 123, productTitle: product.title, orderAmount: 88, deliveryFee: 6, totalAmount: 94, orderStatus: 0, refundStatus: 0, createTime: '2026-09-22 10:00:00', updateTime: '2026-09-22 10:00:00' }

async function setup(page: Page, orders: Record<string, unknown>[] = [], userId = 10, goods = product) {
  const writes: string[] = []
  await page.addInitScript(value => localStorage.setItem('user-store', JSON.stringify({ userInfo: value, currentCampus: null })), user(userId))
  await page.route('**://*/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() !== 'GET' && path.startsWith('/api/order')) writes.push(path)
    const data = path === '/api/user/info' ? user(userId)
      : path === '/api/goods/123' ? goods
      : path === '/api/order/my' ? { total: orders.length, records: orders }
      : path === '/api/order/1' ? orders[0]
      : path.includes('/recommend/') ? []
      : path.includes('/unread/count') ? 0 : {}
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data, timestamp: Date.now() }) })
  })
  return writes
}

test('下单前确认费用，关闭面板不会创建订单', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/product/123')
  await page.getByRole('button', { name: '立即下单', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认订单' })
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('卖家甲')
  await expect(dialog).toContainText('¥88.00')
  await expect(dialog).toContainText('¥6.00')
  await expect(dialog).toContainText('¥94.00')
  await dialog.getByRole('button', { name: '返回修改' }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes).toEqual([])
})

test('退款卡显示申请信息，退款中无法交付', async ({ page }) => {
  await setup(page, [{ ...order, orderStatus: 1, tradeType: 0, refundStatus: 1, refundReason: '双方未能约定交付时间', refundAmount: 94, refundApplyTime: '2026-09-22 10:15:00', refundDeadline: '2026-09-23 10:15:00' }], 20)
  await page.goto('/profile/my-orders?type=sell')
  const card = page.locator('article').first()
  await expect(card).toContainText('双方未能约定交付时间')
  await expect(card).toContainText('待退金额：¥94.00')
  await expect(card).toContainText('申请时间：')
  await expect(card).toContainText('处理截止：')
  await expect(card.getByRole('button', { name: /确认交付|立即发货/ })).toHaveCount(0)
})

test('余额不足时提示可用路径且不请求付款', async ({ page }) => {
  const writes = await setup(page, [order])
  await page.goto('/profile/my-orders')
  await page.getByRole('button', { name: '余额付款', exact: true }).click()
  await expect(page.getByText(/站内余额不足/)).toBeVisible()
  expect(writes).toEqual([])
})

for (const tradeType of [0, 1] as const) {
  test(`双方式商品选择${tradeType === 0 ? '面交免运费' : '邮寄计运费'}并提交方式`, async ({ page }) => {
    const writes = await setup(page, [], 10, { ...product, tradeType: 2 })
    await page.goto('/product/123')
    await page.getByRole('button', { name: '立即下单', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '确认订单' })
    const optionName = tradeType === 0 ? '校内面交' : '快递邮寄'
    await dialog.locator('label').filter({ hasText: optionName }).click()
    await expect(dialog.getByRole('radio', { name: optionName })).toBeChecked()
    await expect(dialog).toContainText(tradeType === 0 ? '¥0.00' : '¥6.00')
    await expect(dialog).toContainText(tradeType === 0 ? '¥88.00' : '¥94.00')
    const created = page.waitForRequest(request => new URL(request.url()).pathname === '/api/order' && request.method() === 'POST')
    await dialog.getByRole('button', { name: '确认下单', exact: true }).evaluate(element => {
      const button = element as HTMLButtonElement
      button.click()
      button.click()
    })
    expect((await created).postDataJSON()).toMatchObject({ productId: 123, tradeType })
    await expect(page).toHaveURL(/\/profile\/my-orders/)
    expect(writes).toEqual(['/api/order'])
  })
}

test('未交付退款被拒绝后买家可以申诉', async ({ page }) => {
  await setup(page, [{ ...order, orderStatus: 1, refundStatus: 3 }])
  await page.goto('/profile/my-orders')
  await expect(page.getByRole('button', { name: '发起纠纷', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '发起纠纷', exact: true }).click()
  await expect(page).toHaveURL(/\/dispute\/create/)
  await expect(page.getByText(/当前订单暂不满足纠纷条件/)).toHaveCount(0)
  await page.locator('textarea').fill('卖家未交付商品并拒绝退款，希望平台协助处理。')
  await expect(page.getByRole('button', { name: '提交纠纷', exact: true })).toBeEnabled()
})

test('卖家同意退款前可核对金额，关闭确认不退款', async ({ page }) => {
  const writes = await setup(page, [{ ...order, orderStatus: 1, refundStatus: 1, refundAmount: 94, refundReason: '不再需要' }], 20)
  await page.goto('/profile/my-orders?type=sell')
  await page.getByRole('button', { name: '同意退款', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('确认同意退款 ¥94.00？', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '取消', exact: true }).last().click()
  expect(writes).toEqual([])
})
