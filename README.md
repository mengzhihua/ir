# IR 供应链控制塔

IR 位于 OMS/TMS/WMS 之上的控制塔，聚合订单、仓储、运输、成本、预警、预测和沙盘推演。后端默认 `8090`，前端开发服务默认 `5174`。

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

- `com.ir.integration`：OMS/TMS/WMS/BMS MOCK/HTTP 客户端与同步
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
控制塔、追踪、预警、规则、动作、预测、补货、沙盘、场景对比、成本、系统集成、
用户和操作日志页面均已提供；前端写操作按 ADMIN/PLANNER 与 VIEWER 角色隐藏。

## 对接

系统接入配置预置 OMS/TMS/WMS/BMS 为 `MOCK`，SRM 为禁用预留。切换 HTTP 时使用 OTWB 事实表中的端点：OMS `/api/order/page`、`/api/inventory/page`、`/api/dashboard`、`/api/report/order-daily`；WMS `/api/outbound/order/page`、`/api/inventory/summary`、`/api/dashboard`、`/api/report/kpi`；TMS `/api/waybill/page`、`/api/billing/page`、`/api/dashboard`。BMS 约定 `GET /api/open/cost/records?from&to`，尚未上线。

## 算法和沙盘

预测支持 7 日移动平均、指数平滑、Holt、4 周同星期 Seasonal Naive，AUTO 按最近 14 天回测 MAPE 选最优。沙盘支持 NEAREST、LOWEST_COST、BALANCED、SINGLE_WAREHOUSE，输出成本、服务水平、缺货、日序列和 SKU 汇总。

## 冒烟和构建

后端启动后运行 `bash scripts/smoke.sh`，脚本使用 `curl` 和 `jq` 检查登录、看板、追踪、预警、指令、预测、补货、baseline/场景/对比、成本以及健康同步。
前端构建使用 `cd frontend && npm run build`。
