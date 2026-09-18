# IR 供应链控制塔

IR 位于 OMS/TMS/WMS 之上的控制塔，聚合订单、仓储、运输、成本、预警、预测和沙盘推演。后端默认 `8090`，前端开发服务默认 `5174`。

当前供应链大脑的分层、接入范围和边界见 [docs/供应链大脑构造.md](docs/供应链大脑构造.md)。

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

- `com.ir.integration`：OMS/TMS/WMS/BMS/SRM/SAP/BOM/INV/CRM/DMS/OA MOCK/HTTP 客户端与同步
- `com.ir.snapshot`：统一快照（`entity`）与 Mapper
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
控制塔、追踪、预警、规则、动作、预测、补货、人工沙盘、系统自动沙盘、场景对比、成本、系统集成、
用户和操作日志页面均已提供；前端写操作按 ADMIN/PLANNER 与 VIEWER 角色隐藏。

## 对接

系统接入配置预置 OMS/TMS/WMS/BMS/SRM/SAP/OA/BOM/INV/CRM/DMS 为 `MOCK`。切换 HTTP 时：OMS/WMS/TMS 与 SRM/SAP/BOM/INV/CRM/DMS/OA 一样走 `GET /api/open/ir/snapshots` 与 `POST /api/open/ir/actions`；BMS 成本走 `GET /api/open/cost/records?from&to`。仓配联调种子：OMS `IR-SO-STUCK`、WMS `SO-IR-STUCK`、TMS `WB-IR-DELAY`。采购联调：SAP `M1099`/`MAT-1000` 低库存执行建议会建 PR 并协同 SRM/OA/WMS 补货。

## 算法和沙盘

预测支持 7 日移动平均、指数平滑、Holt、4 周同星期 Seasonal Naive，AUTO 按最近 14 天回测 MAPE 选最优。沙盘支持 NEAREST、LOWEST_COST、BALANCED、SINGLE_WAREHOUSE，输出成本、服务水平、缺货、日序列和 SKU 汇总。

## 冒烟和构建

后端启动后运行 `bash scripts/smoke.sh`，脚本使用 `curl` 和 `jq` 检查登录、看板、追踪（含卡单筛选）、预警统计、挂起/解挂回写、执行建议、按仓在途、采购 PO、预测、补货、baseline/场景/对比、成本以及健康同步。
前端构建使用 `cd frontend && npm run build`。
