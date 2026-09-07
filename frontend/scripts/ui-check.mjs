import { chromium } from 'playwright'

const baseUrl = process.env.UI_BASE_URL || 'http://localhost:5174'
const cdpUrl = process.env.CDP_URL || 'http://localhost:29229'
const routes = [
  ['/dashboard', null],
  ['/trace', async (page) => page.getByRole('button', { name: '详情' }).first().click()],
  ['/alert', null],
  ['/rule', null],
  ['/action', null],
  ['/forecast', async (page) => page.getByRole('button', { name: '运行预测' }).click()],
  ['/replenish', null],
  ['/sandbox', async (page) => page.locator('.el-table__body-wrapper tbody tr').first().click()],
  [
    '/compare',
    async (page) => {
      const select = page.locator('.el-select').first()
      await select.click()
      const options = page.locator('.el-select-dropdown__item')
      const count = await options.count()
      if (count < 2) {
        throw new Error('场景选项不足两个')
      }
      await options.nth(0).click()
      await select.click()
      await page.locator('.el-select-dropdown__item').nth(1).click({ force: true })
      await page.getByRole('button', { name: '开始对比' }).click()
    }
  ],
  ['/cost', null],
  ['/integration', null],
  ['/system/user', null],
  ['/system/oplog', null]
]

function invalidText(text) {
  return /NaN|undefined|null|\[object/.test(text)
}

async function waitForData(page) {
  await page.waitForTimeout(400)
  await page
    .locator('.el-loading-mask')
    .waitFor({ state: 'hidden', timeout: 5000 })
    .catch(() => {})
}

async function assertPage(page, route) {
  const body = await page.locator('body').innerText()
  if (invalidText(body)) {
    throw new Error('页面包含非法占位文本')
  }
  const charts = await page.locator('.chart-empty').count()
  if (charts > 0) {
    throw new Error(`存在 ${charts} 个暂无数据图表`)
  }
  const tables = page.locator('.el-table')
  const tableCount = await tables.count()
  for (let index = 0; index < tableCount; index += 1) {
    const table = tables.nth(index)
    const hasEmpty = await table.locator('.el-table__empty-text').count()
    const rows = await table.locator('.el-table__body-wrapper tbody tr').count()
    const requiresData =
      (route === '/sandbox' &&
        (await table.locator('.sandbox-sku-table').count()) > 0) ||
      route === '/replenish'
    if ((hasEmpty === 0 || requiresData) && rows === 0) {
      throw new Error(`第 ${index + 1} 个表格没有数据行`)
    }
  }
  const cells = page.locator('.el-table__body-wrapper td')
  const cellText = await cells.allTextContents()
  const raw = cellText.find((value) => /\b(RUN|OPEN|PENDING|MOCK)\b/.test(value))
  if (raw) {
    throw new Error(`表格显示原始状态：${raw.trim()}`)
  }
  return `${route} PASS`
}

async function login(page, username, password) {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await page.evaluate(() => localStorage.clear())
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await page.locator('input').nth(0).fill(username)
  await page.locator('input').nth(1).fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/dashboard/, { timeout: 10000 })
}

let browser
try {
  browser = await chromium.connectOverCDP(cdpUrl)
} catch {
  browser = await chromium.launch({ headless: true })
}

const context = browser.contexts()[0] || (await browser.newContext())
const page = await context.newPage()
const results = []

try {
  await login(page, 'admin', 'admin123')
  for (const [route, interaction] of routes) {
    try {
      await page.goto(`${baseUrl}${route}`, { waitUntil: 'networkidle' })
      await waitForData(page)
      if (interaction) {
        await interaction(page)
        await waitForData(page)
      }
      if (route === '/sandbox') {
        await page.screenshot({
          path: '/home/ubuntu/screenshots/sandbox_after.png',
          fullPage: true
        })
      }
      results.push(await assertPage(page, route))
    } catch (error) {
      results.push(`${route} FAIL ${error.message}`)
    }
  }

  await login(page, 'viewer', 'admin123')
  for (const route of ['/alert', '/sandbox', '/action']) {
    await page.goto(`${baseUrl}${route}`, { waitUntil: 'networkidle' })
    await waitForData(page)
    const buttons = await page.locator('button').allTextContents()
    const writes = buttons.filter((text) =>
      /评估|新建|创建场景|运行|应用到OTW|执行|确认|解决|忽略|重试|下发/.test(text)
    )
    results.push(`${route} viewer ${writes.length === 0 ? 'PASS' : `FAIL (${writes.join(',')})`}`)
  }
} finally {
  console.log('route                         result')
  console.log('----------------------------- ------------------------------')
  for (const result of results) {
    console.log(result)
  }
  if (process.env.UI_CHECK_CLOSE === '1') {
    await browser.close()
  }
}

if (results.some((result) => result.includes('FAIL'))) {
  process.exitCode = 1
}
