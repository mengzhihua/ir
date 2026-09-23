# IR 供应链控制塔

IR 位于 OMS/TMS/WMS/BMS/SRM/SAP 之上的控制塔，聚合订单、仓储、运输、成本、采购、供应商、SAP 库存与财务信号，围绕业务目标(成本/OTIF/NPS/缺货/供应商准交)自动生成并执行跨系统平衡决策。后端默认 `8090`，前端开发服务默认 `5174`。

当前供应链大脑的分层、接入范围和边界见 [docs/供应链大脑构造.md](docs/供应链大脑构造.md)。分层、算法和表模型见 [docs/供应链大脑架构.md](docs/供应链大脑架构.md)。给同事讲这套系统怎么用、亮点和适用场景，见 [docs/图文导读.md](docs/图文导读.md)。计划员日常操作见 [docs/业务使用手册.md](docs/业务使用手册.md)。项目亮点见 [docs/项目亮点.md](docs/项目亮点.md)。

## 快速开始

```bash
cd backend
mvn spring-boot:run
cd ../frontend
npm install
npm run dev
```

默认账号：`admin/admin123`。后端使用 `Authorization: Bearer <token>`，响应统一为 `{code,msg,data}`。首次启动在 MOCK 模式生成确定性演示数据并同步、评估预警、创建 baseline 场景。

## 目录

每个业务包内部按 `controller` / `service` / `engine` / `entity` / `mapper` 分层，不把 Web、业务、表对象平铺在同一目录。

- `com.ir.integration`：OMS/TMS/WMS/BMS/SRM/SAP MOCK/HTTP 客户端与同步
- `com.ir.objective`：业务目标(`ct_objective`)、KPI 计算与 NPS 估算
- `com.ir.balance`：自动平衡引擎(策略目录、决策、护栏、审批、定时运行)
- `com.ir.supply`：供应商协同、采购单/ASN、SAP-WMS 库存对账与财务指标
- `com.ir.snapshot`：统一快照（`entity`）与 Mapper、确定性演示数据
- `com.ir.tower` / `trace`：控制塔看板和全链路追踪
- `com.ir.alert` / `action`：规则预警和跨系统协同指令
- `com.ir.forecast`：MA、SES、Holt、Seasonal Naive、AUTO 预测和补货
- `com.ir.sandbox`：人工沙盘与系统自动沙盘（成本/效率综合分）
- `com.ir.cost`：成本汇总、明细和目标
- `com.ir.system`：鉴权、审计、启动热身 `BootstrapService`

## 前端

前端使用 Vite + Vue 3 + vue-router 4 + Element Plus + ECharts/vue-echarts，位于
`frontend/`。开发服务监听 `5174`，并将 `/api` 代理到 `http://localhost:8090`。
登录令牌保存在 `localStorage` 的 `ir_token`，用户信息保存在 `ir_user`。
未登录可打开 `/intro`，查看和科捷金库四件套的对照、已经有的能力，以及后面要补的运配评级、逆向和计件。登录后顶栏也能进入这一页。
控制塔、业务目标、自动平衡、供应协同、追踪、预警、规则、动作、预测、补货、沙盘、场景对比、成本、系统集成、
用户和操作日志页面均已提供；前端写操作按 ADMIN/PLANNER 与 VIEWER 角色隐藏。

## 对接

接入配置预置 OMS / TMS / WMS / BMS / SRM / SAP / OA / BOM / INV / CRM / DMS 为 `MOCK`。切到 HTTP 后，读快照优先 `GET /api/open/ir/snapshots`（请求头 `X-Api-Key`）。没有 Key 时，OMS / WMS / TMS / SRM / SAP 回退各系统自己的分页和看板接口。BMS 成本始终走 `GET /api/open/cost/records?from&to`，不接受写指令。

写指令看有没有 API Key。

**有 Key**：先 `POST /api/open/ir/actions`，请求体带 `idempotencyKey`。对方返回可判定的失败（例如旧版本没有统一口，HTTP 404）时，按指令类型回退专用口；超时或结果不明则不再重试，避免重复下发。专用口都挂在 `/api/open/ir` 下：

| 系统 | 专用口 |
| --- | --- |
| OMS | `/hold` `/unhold` `/reroute` `/auto` `/cancel` `/prioritize` |
| WMS | `/allocate` `/replenish` |
| TMS | `/dispatch` `/sync-track` `/switch-carrier` |
| SRM | `/purchase-suggest` `/submit-pr` `/approve-pr` `/expedite-po` |
| SAP | `/create-pr` `/release-pr` `/release-mo` |
| BOM | `/explode` `/submit-ecn` `/approve-ecn` `/implement-ecn` |
| INV | `/verify-input` `/submit-request` `/approve-request` |
| OA | `/start-workflow` `/approve-task` |
| CRM | `/advance-stage` `/escalate-case` |
| DMS | `/replenish-shortage` `/push-replenish` |

**没有 Key（登录模式）**：用接入配置里的账号密码登录，再打业务口。仓号 `WH-SH` / `WH-BJ` / `WH-GZ` 下发 WMS 时映成 `WH01` / `WH02` / `WH03`。

| 指令 | 业务口 |
| --- | --- |
| OMS 挂起 / 解挂 / 改仓 / 一键处理 / 取消 | `POST /api/order/{orderNo}/hold`、`/unhold`、`/reroute`、`/auto`、`/cancel` |
| OMS 加急 | `POST /api/order/{orderNo}/remark` |
| WMS 分配 | 按出库单号查出 id，`POST /api/outbound/order/{id}/allocate` |
| WMS 补货 | 源仓与目标仓不同：`POST /api/inventory/replenish/transfer`；同仓：`POST /api/inventory/replenish/generate` |
| TMS | 仍打 Open IR `/actions` 与专用口，不走运单登录口 |
| SRM 采购建议 / 提交 / 审批 | `POST /api/sourcing/pr`，再 `/{id}/submit` 或 `/{id}/approve` |
| SRM 催单 | 按采购单号查出 id，`PUT /api/purchase/order/{id}` |
| SAP 建采购申请 | `POST /api/mm/pr` |
| SAP 释放采购申请 / 生产订单 | `POST /api/mm/pr/{banfn}/release`、`POST /api/pp/orders/{aufnr}/release` |
| DMS 按缺货生成 | `POST /api/oms/replenish/from-shortage?dealerCode=` |
| DMS 下发补货 | 按补货单号查出 id，`POST /api/oms/replenish/{id}/push` |
| OA / BOM / INV / CRM | 登录模式不执行，需要 API Key |

OMS 挂起只接受 `CREATED` / `AUDITED`，解除挂起回到 `CREATED`。OA 通用流程登录口不允许带业务单号，所以发起审批仍走 Open IR。

无 Key 时的读数回退：OMS `/api/order/page`、`/api/inventory/page`、`/api/dashboard`、`/api/report/order-daily`；WMS `/api/outbound/order/page`、`/api/inventory/summary`、`/api/dashboard`、`/api/report/kpi`；TMS `/api/waybill/page`、`/api/billing/page`、`/api/dashboard`；SRM `/api/purchase/order/page`、`/api/delivery/asn/page`、`/api/evaluation/page`；SAP `/api/mm/stock`、`/api/fi/ap/open-items`、`/api/fi/ar/open-items`、`/api/dashboard/summary`。

HTTP 集成的 `baseUrl` 只接受 HTTP/HTTPS URL，并实现了 loopback、链路本地和
`169.254.0.0/16` 云元数据地址检查。默认
`ir.integration.allow-private-hosts=true` 以支持本地 OTWB；生产环境建议设置为
`false`。HTTP 客户端连接超时为 3 秒，读取超时为 10 秒。

### 生产部署

生产环境请设置 `IR_ALLOW_PRIVATE_HOSTS=false`，避免集成地址指向内网或云元数据服务，
并立即修改默认管理员密码 `admin/admin123`。
链路本地地址以及 `169.254.*` 地址始终拒绝，即使允许其他私有地址。

## 业务目标与自动平衡

预置目标：单均成本(≤25)、OTIF(≥95%)、NPS 估算(≥50)、缺货率(≤5%)、供应商准交率(≥92%)，按权重计算达成分。NPS 为运营代理估算：准时无异常交付计推荐者，延迟 >24h、异常运单、付费后取消计贬损者，`(推荐-贬损)/样本*100`。

平衡引擎每 30 分钟(`IR_BALANCE_CRON`)或手动 `POST /api/balance/run` 运行，策略包括 STOCK_REBALANCE、SUPPLY_EXPEDITE、DELAY_RECOVERY、CARRIER_COST_OPTIMIZE、ORDER_UNBLOCK、WAREHOUSE_REROUTE，每条决策带依据、预期成本/NPS 影响与风险等级。模式 `OFF/SUGGEST/AUTO`(`IR_BALANCE_MODE`)：AUTO 下仅低风险且在护栏内(单次执行上限、采购金额上限、冷却期、服务护栏)的决策自动执行，其余进入待审批(`/api/balance/decision/{id}/approve|reject`)。当 OTIF/NPS 达成低于 `service-guard-attainment` 时不再自动执行降本类动作。

## 算法和沙盘

预测支持 7 日移动平均、指数平滑、Holt、4 周同星期 Seasonal Naive，AUTO 按最近 14 天回测 MAPE 选最优。沙盘支持 NEAREST、LOWEST_COST、BALANCED、SINGLE_WAREHOUSE，输出成本、服务水平、缺货、日序列和 SKU 汇总。人工沙盘资金盘档位为 10 万 / 百万 / 千万 / 亿 / 十亿，也可自定义金额；可指定最多 10 万 SKU、单 SKU 库存最多 1000 万，并用 `/api/sandbox/capital/sweep` 自动扫完各档验证全流程。过万 SKU 时 2x/5x 在余量足够时投影，策略册复用就近默认。

## 冒烟和构建

后端启动后运行 `bash scripts/smoke.sh`，脚本使用 `curl` 和 `jq` 检查登录、看板、追踪（含卡单筛选）、预警统计、挂起/解挂回写、执行建议、按仓在途、采购 PO、预测、补货、baseline/场景/对比、**资金盘档位和量级扫描**、成本以及健康同步。
脚本还覆盖 SRM/SAP 同步、业务目标、自动平衡与供应协同。
前端构建使用 `cd frontend && npm run build`。

## 发布包（开箱即用）

前端生产构建打进 Spring Boot 可执行 JAR。三种用法：

### 1. 服务端（任意已装 JDK 17 的机器）

```bash
java -jar ir-backend-1.0.0.jar --server.port=8090
```

Linux systemd 示例见发布包 `README.txt`。

### 2. 便携包（需本机已装 Java）

```bash
bash scripts/package-release.sh
unzip release/ir-1.0.0.zip
cd ir-1.0.0
```

| 系统 | 怎么用 |
| --- | --- |
| Linux | `./start.sh` |
| macOS | 双击 `start.command`，或 `./start.sh` |
| Windows | 双击 `start.bat` |

### 3. 原生包（捆绑 JRE，不必装 Java）

合并到默认分支且便携包冒烟通过后，GitHub Actions 自动发布 GitHub Release（也可在 Actions 里手动 `workflow_dispatch`）。分别在 Ubuntu / Windows / macOS 生成：

- `ir-1.0.0-linux-x64.zip` → `bin/ir`
- `ir-1.0.0-windows-x64.zip` → 双击 `ir.exe`
- `ir-1.0.0-macos-arm64.zip` → Apple Silicon（M 系列），双击 `ir.app`
- `ir-1.0.0-macos-x64.zip` → Intel Mac，双击 `ir.app`

浏览器访问 `http://127.0.0.1:8090`。默认账号 `admin / admin123`。

十二套系统可同时启动：OMS 8081 / TMS 8082 / WMS 8083 / BMS 8084 / SAP 8085 / OA 8086 / SRM 8087 / BOM 8088 / INV 8089 / IR 8090 / CRM 8091 / DMS 8092。
