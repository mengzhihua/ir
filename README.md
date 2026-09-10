# IR 供应链控制塔

IR 位于 OMS/TMS/WMS/BMS/SRM/SAP 之上的控制塔，聚合订单、仓储、运输、成本、采购、供应商、SAP 库存与财务信号，围绕业务目标(成本/OTIF/NPS/缺货/供应商准交)自动生成并执行跨系统平衡决策。后端默认 `8090`，前端开发服务默认 `5174`。

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

- `com.ir.integration`：OMS/TMS/WMS/BMS/SRM/SAP MOCK/HTTP 客户端与同步
- `com.ir.objective`：业务目标(`ct_objective`)、KPI 计算与 NPS 估算
- `com.ir.balance`：自动平衡引擎(策略目录、决策、护栏、审批、定时运行)
- `com.ir.supply`：供应商协同、采购单/ASN、SAP-WMS 库存对账与财务指标
- `com.ir.snapshot`：统一快照与确定性演示数据
- `com.ir.tower` / `trace`：控制塔看板和全链路追踪
- `com.ir.alert` / `action`：规则预警和跨系统协同指令
- `com.ir.forecast`：MA、SES、Holt、Seasonal Naive、AUTO 预测和补货
- `com.ir.sandbox`：库存/需求/仓配策略沙盘
- `com.ir.cost`：成本汇总、明细和目标

## 前端

前端使用 Vite + Vue 3 + vue-router 4 + Element Plus + ECharts/vue-echarts，位于
`frontend/`。开发服务监听 `5174`，并将 `/api` 代理到 `http://localhost:8090`。
登录令牌保存在 `localStorage` 的 `ir_token`，用户信息保存在 `ir_user`。
控制塔、业务目标、自动平衡、供应协同、追踪、预警、规则、动作、预测、补货、沙盘、场景对比、成本、系统集成、
用户和操作日志页面均已提供；前端写操作按 ADMIN/PLANNER 与 VIEWER 角色隐藏。

## 对接

系统接入配置预置 OMS/TMS/WMS/BMS/SRM/SAP 为 `MOCK`。SRM HTTP 使用 `/api/purchase/order/page`、`/api/delivery/asn/page`、`/api/evaluation/page`，并通过 `POST /api/sourcing/pr` + `/submit` 下发采购申请、`PUT /api/purchase/order/{id}` 催单；SAP HTTP 使用 `/api/mm/stock`、`/api/fi/ap/open-items`、`/api/fi/ar/open-items`、`/api/dashboard/summary`。切换 HTTP 时使用 OTWB 事实表中的端点：OMS `/api/order/page`、`/api/inventory/page`、`/api/dashboard`、`/api/report/order-daily`；WMS `/api/outbound/order/page`、`/api/inventory/summary`、`/api/dashboard`、`/api/report/kpi`；TMS `/api/waybill/page`、`/api/billing/page`、`/api/dashboard`。BMS 约定 `GET /api/open/cost/records?from&to`，尚未上线。

HTTP 集成的 `baseUrl` 只接受 HTTP/HTTPS URL，并实现了 loopback、链路本地和
`169.254.0.0/16` 云元数据地址检查。默认
`ir.integration.allow-private-hosts=true` 以支持本地 OTWB；生产环境建议设置为
`false`。HTTP 客户端连接超时为 3 秒，读取超时为 10 秒。

### 生产部署

生产环境请设置 `IR_ALLOW_PRIVATE_HOSTS=false`，避免集成地址指向内网或云元数据服务，
并立即修改默认管理员密码 `admin/admin123`。

## 业务目标与自动平衡

预置目标：单均成本(≤25)、OTIF(≥95%)、NPS 估算(≥50)、缺货率(≤5%)、供应商准交率(≥92%)，按权重计算达成分。NPS 为运营代理估算：准时无异常交付计推荐者，延迟 >24h、异常运单、付费后取消计贬损者，`(推荐-贬损)/样本*100`。

平衡引擎每 30 分钟(`IR_BALANCE_CRON`)或手动 `POST /api/balance/run` 运行，策略包括 STOCK_REBALANCE、SUPPLY_EXPEDITE、DELAY_RECOVERY、CARRIER_COST_OPTIMIZE、ORDER_UNBLOCK、WAREHOUSE_REROUTE，每条决策带依据、预期成本/NPS 影响与风险等级。模式 `OFF/SUGGEST/AUTO`(`IR_BALANCE_MODE`)：AUTO 下仅低风险且在护栏内(单次执行上限、采购金额上限、冷却期、服务护栏)的决策自动执行，其余进入待审批(`/api/balance/decision/{id}/approve|reject`)。当 OTIF/NPS 达成低于 `service-guard-attainment` 时不再自动执行降本类动作。

## 算法和沙盘

预测支持 7 日移动平均、指数平滑、Holt、4 周同星期 Seasonal Naive，AUTO 按最近 14 天回测 MAPE 选最优。沙盘支持 NEAREST、LOWEST_COST、BALANCED、SINGLE_WAREHOUSE，输出成本、服务水平、缺货、日序列和 SKU 汇总。

## 冒烟和构建

后端启动后运行 `bash scripts/smoke.sh`，脚本使用 `curl` 和 `jq` 检查登录、看板、追踪、预警、指令、预测、补货、baseline/场景/对比、成本、健康同步、SRM/SAP 同步、目标看板、自动平衡与供应协同。
前端构建使用 `cd frontend && npm run build`。
