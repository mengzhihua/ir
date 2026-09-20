---
name: ir-control-tower-browser-testing
description: Run role-aware browser end-to-end checks for the IR supply-chain control tower in MOCK mode.
---

# IR browser testing

## Environment
- Repo: `/home/ubuntu/repos/ir`; backend Spring Boot on 8090; frontend Vite on 5174 proxies `/api`.
- Reuse existing processes when possible. The database may already contain scenarios/actions from earlier checks, so count-increase assertions must account for action deduplication.
- Use an Aliyun Maven mirror to avoid Central rate limits. The repo blueprint installs `~/.m2/settings.xml`; an existing `/home/ubuntu/maven-settings-ir.xml` can be passed explicitly with `mvn -s`.
- If a clean isolated database is needed, start Spring Boot with an H2 in-memory datasource override; coordinate restarting with other agents first.
- `frontend` already declares Playwright. Passive response/pageerror collection can connect to the GUI Chrome CDP port, found in the Chrome process's `--remote-debugging-port` argument. Do not issue separate authenticated requests or extract browser cookies.

## Credentials and permissions
- Check `backend/src/main/resources/data.sql` rather than guessing passwords from usernames.
- At the tested seed revision, admin, planner, and viewer all use `admin123`; hashes are already PBKDF2.
- Login of those seeds does not test legacy SHA-256 migration. That requires an explicitly authorized legacy-hash fixture.
- The planner can sync and execute actions but cannot save integration system configuration or enter the frontend `/system/user` route. Viewer write controls are hidden on Alert/Sandbox/Action.

## Useful UI paths
- Forecast exposes 自动选择, 移动平均, 季节朴素; horizon14 exercises second-week recurrence.
- Manual Sandbox tables scroll horizontally to reveal 运行/生成指令/立即执行. The create dialog scrolls vertically to carrier shares.
- Lead0 plus higher demand can eliminate stockouts; use a positive lead time scenario if warehouse-SKU replenishment targets are required.
- Compare offers all scenario kinds, including auto candidates; the baseline may be at the bottom of the select menu.
- Batch replenishment defaults to executing immediately and may return an existing action. The demo can have only one shortage row.
- Balance manual runs can return zero new decisions because all candidates are cooling down. Record that result separately from approving/rejecting existing pending decisions.
- Action creation with purchase quantity0 provides a non-destructive FAILED action fixture. Retry creates a new failed row and marks the original RETRIED.
- Rule UI exposes updates, not create/delete. Test JSON validation and persistence, then restore parameters.
- For cost denominator verification without direct API calls, browse Trace at 100 rows/page over all three demo pages; passively inspect orderTime values from those UI responses.
- Integration rejection paths should preserve other edited fields. Use a test username alongside an invalid URL, cancel/reload/reopen, then verify the original URL and username.
- Inspect operation audit results against the successful UI operations just performed, not simply whether the log table loads.

## Devin Secrets Needed
None for the local seeded MOCK environment. Real-system HTTP dispatch or remote UNKNOWN reconciliation requires separately provisioned integration credentials and a controlled test endpoint.
