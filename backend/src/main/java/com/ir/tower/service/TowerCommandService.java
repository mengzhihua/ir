package com.ir.tower.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.mapper.CtActionMapper;
import com.ir.action.service.ActionService;
import com.ir.alert.entity.CtAlert;
import com.ir.alert.mapper.CtAlertMapper;
import com.ir.alert.service.AlertEngine;
import com.ir.balance.BalanceEngine;
import com.ir.balance.CtBalanceDecision;
import com.ir.balance.CtBalanceDecisionMapper;
import com.ir.common.BizException;
import com.ir.sandbox.entity.CtScenario;
import com.ir.sandbox.service.SandboxService;
import com.ir.system.auth.CurrentUser;
import com.ir.system.entity.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 控制塔指令队列：把开放预警、待办指令、待审批平衡决策收成一条可执行清单。
 */
@Service
public class TowerCommandService {
    static final int QUEUE_LIMIT = 12;
    static final int BATCH_LIMIT = 8;

    private final CtAlertMapper alerts;
    private final CtActionMapper actions;
    private final CtBalanceDecisionMapper decisions;
    private final AlertEngine alertEngine;
    private final ActionService actionService;
    private final BalanceEngine balanceEngine;
    private final SandboxService sandbox;

    public TowerCommandService(
            CtAlertMapper alerts,
            CtActionMapper actions,
            CtBalanceDecisionMapper decisions,
            AlertEngine alertEngine,
            ActionService actionService,
            BalanceEngine balanceEngine,
            SandboxService sandbox) {
        this.alerts = alerts;
        this.actions = actions;
        this.decisions = decisions;
        this.alertEngine = alertEngine;
        this.actionService = actionService;
        this.balanceEngine = balanceEngine;
        this.sandbox = sandbox;
    }

    public Map<String, Object> queue(Map<String, Object> recommendation) {
        List<Map<String, Object>> items = nextActions(recommendation);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nextActions", items);
        result.put("counts", counts(items));
        return result;
    }

    public List<Map<String, Object>> nextActions(Map<String, Object> recommendation) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (recommendation != null && recommendation.get("id") != null) {
            Map<String, Object> sandboxItem = sandboxItem(recommendation);
            if (sandboxItem != null) {
                items.add(sandboxItem);
            }
        }
        for (CtAlert alert : alerts.selectList(new LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getStatus, "OPEN")
                .last(severityThenTime("severity", "created_at", 40)))) {
            if (alert.getSuggestedAction() == null || alert.getSuggestedAction().trim().isEmpty()) {
                continue;
            }
            items.add(alertItem(alert));
        }
        for (CtAction action : actions.selectList(new LambdaQueryWrapper<CtAction>()
                .eq(CtAction::getStatus, "PENDING")
                .orderByDesc(CtAction::getCreatedAt)
                .last("LIMIT 20"))) {
            items.add(actionItem(action));
        }
        for (CtBalanceDecision decision : decisions.selectList(
                new LambdaQueryWrapper<CtBalanceDecision>()
                        .eq(CtBalanceDecision::getStatus, "PENDING")
                        .last(severityThenTime("risk_level", "id", 20)))) {
            items.add(decisionItem(decision));
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> row) -> (Integer) row.get("rank"))
                .reversed()
                .thenComparing((Map<String, Object> row) -> (LocalDateTime) row.get("createdAt"),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (items.size() > QUEUE_LIMIT) {
            items = new ArrayList<>(items.subList(0, QUEUE_LIMIT));
        }
        for (Map<String, Object> item : items) {
            item.remove("rank");
        }
        return items;
    }

    public Map<String, Object> execute(Map<String, Object> request) {
        if (request == null) {
            throw new BizException("指令不能为空");
        }
        String kind = text(request.get("kind"));
        Long id = idOf(request.get("id"));
        if (kind.isEmpty() || id == null) {
            throw new BizException("kind 与 id 不能为空");
        }
        boolean execute = request.get("execute") != null
                && Boolean.parseBoolean(String.valueOf(request.get("execute")));
        return dispatch(kind.toUpperCase(Locale.ROOT), id, execute);
    }

    public Map<String, Object> executeBatch(Map<String, Object> request) {
        List<Map<String, Object>> items = itemsOf(request);
        if (items.isEmpty()) {
            String severity = request == null ? "HIGH" : text(request.get("severity"));
            if (severity.isEmpty()) {
                severity = "HIGH";
            }
            for (Map<String, Object> row : nextActions(null)) {
                if (severity.equalsIgnoreCase(String.valueOf(row.get("severity")))
                        && executable(row)) {
                    items.add(row);
                }
            }
        } else {
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> row : items) {
                if (executable(row)) {
                    filtered.add(row);
                }
            }
            items = filtered;
        }
        if (items.size() > BATCH_LIMIT) {
            items = new ArrayList<>(items.subList(0, BATCH_LIMIT));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (Map<String, Object> item : items) {
            try {
                Map<String, Object> executed = execute(item);
                results.add(executed);
                if (ok(executed.get("status"))) {
                    success++;
                } else {
                    failed++;
                }
            } catch (RuntimeException ex) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("kind", text(item.get("kind")));
                error.put("id", item.get("id"));
                error.put("status", "FAILED");
                error.put("result", ex.getMessage());
                results.add(error);
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("results", results);
        return result;
    }

    private Map<String, Object> dispatch(String kind, Long id, boolean execute) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("id", id);
        if ("ALERT".equals(kind)) {
            CtAction action = alertEngine.executeSuggested(id);
            if (action == null) {
                throw new BizException("预警不存在或没有建议动作");
            }
            fillAction(result, action);
            return result;
        }
        if ("ACTION".equals(kind)) {
            CtAction action = actionService.executePending(id);
            if (action == null) {
                throw new BizException("指令不存在");
            }
            if ("PENDING".equals(action.getStatus())) {
                throw new BizException("指令不是待执行状态");
            }
            fillAction(result, action);
            return result;
        }
        if ("DECISION".equals(kind)) {
            CtBalanceDecision decision = balanceEngine.approve(id);
            result.put("status", decision.getStatus());
            result.put("type", decision.getActionType());
            result.put("targetKey", decision.getTargetKey());
            result.put("actionId", decision.getActionId());
            result.put("result", decision.getReason());
            return result;
        }
        if ("SANDBOX".equals(kind)) {
            CtScenario scenario = sandbox.get(id);
            if (scenario == null) {
                throw new BizException("沙盘方案不存在");
            }
            List<CtAction> applied = sandbox.apply(id, execute);
            result.put("status", "APPLIED");
            result.put("type", "APPLY_SANDBOX");
            result.put("targetKey", scenario.getName());
            result.put("actionCount", applied.size());
            result.put("executed", execute);
            return result;
        }
        throw new BizException("不支持的指令类型: " + kind);
    }

    private Map<String, Object> sandboxItem(Map<String, Object> recommendation) {
        Object verdict = recommendation.get("capitalVerdict");
        if ("RELIABLE".equals(String.valueOf(verdict))) {
            return null;
        }
        Map<String, Object> row = base(
                "SANDBOX",
                recommendation.get("id"),
                "采用自动沙盘推荐 " + String.valueOf(recommendation.get("name")),
                "资金盘 "
                        + String.valueOf(verdict == null ? "-" : verdict)
                        + "，把推荐安全库存/提前期落到待办",
                "INSUFFICIENT".equals(String.valueOf(verdict)) ? "HIGH" : "MEDIUM",
                "APPLY_SANDBOX",
                recommendation.get("name"),
                null,
                null);
        row.put("rank", "INSUFFICIENT".equals(String.valueOf(verdict)) ? 110 : 88);
        return row;
    }

    private Map<String, Object> alertItem(CtAlert alert) {
        Map<String, Object> row = base(
                "ALERT",
                alert.getId(),
                alert.getTitle(),
                alert.getDetail(),
                alert.getSeverity() == null ? "MEDIUM" : alert.getSeverity(),
                alert.getSuggestedAction(),
                alert.getTargetKey(),
                alert.getWarehouseCode(),
                alert.getCreatedAt());
        row.put("rank", rank("ALERT", row.get("severity")));
        return row;
    }

    private Map<String, Object> actionItem(CtAction action) {
        Map<String, Object> row = base(
                "ACTION",
                action.getId(),
                "待执行 " + action.getType(),
                action.getTargetSystem() + " / " + action.getTargetKey(),
                "HIGH",
                action.getType(),
                action.getTargetKey(),
                null,
                action.getCreatedAt());
        row.put("rank", rank("ACTION", "HIGH"));
        return row;
    }

    private Map<String, Object> decisionItem(CtBalanceDecision decision) {
        String severity = decision.getRiskLevel() == null ? "MEDIUM" : decision.getRiskLevel();
        Map<String, Object> row = base(
                "DECISION",
                decision.getId(),
                "平衡决策 " + decision.getStrategy(),
                decision.getReason(),
                severity,
                decision.getActionType(),
                decision.getTargetKey(),
                null,
                decision.getCreatedAt());
        row.put("rank", rank("DECISION", severity));
        if (!canApprove(severity)) {
            row.put("executable", false);
            String reason = decision.getReason() == null ? "" : decision.getReason();
            row.put("reason", reason.isEmpty()
                    ? "高风险决策仅管理员可审批"
                    : reason + "；高风险决策仅管理员可审批");
        }
        return row;
    }

    private static Map<String, Object> base(
            String kind,
            Object id,
            String title,
            String reason,
            String severity,
            String suggestedType,
            Object targetKey,
            String warehouseCode,
            LocalDateTime createdAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("id", id);
        row.put("title", title);
        row.put("reason", reason);
        row.put("severity", severity);
        row.put("suggestedType", suggestedType);
        row.put("targetKey", targetKey);
        row.put("warehouseCode", warehouseCode);
        row.put("createdAt", createdAt);
        row.put("executable", true);
        return row;
    }

    private static Map<String, Integer> counts(List<Map<String, Object>> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("total", items.size());
        counts.put("alerts", 0);
        counts.put("actions", 0);
        counts.put("decisions", 0);
        counts.put("sandbox", 0);
        counts.put("high", 0);
        for (Map<String, Object> item : items) {
            String kind = String.valueOf(item.get("kind"));
            if ("ALERT".equals(kind)) {
                counts.merge("alerts", 1, Integer::sum);
            } else if ("ACTION".equals(kind)) {
                counts.merge("actions", 1, Integer::sum);
            } else if ("DECISION".equals(kind)) {
                counts.merge("decisions", 1, Integer::sum);
            } else if ("SANDBOX".equals(kind)) {
                counts.merge("sandbox", 1, Integer::sum);
            }
            if ("HIGH".equals(item.get("severity"))) {
                counts.merge("high", 1, Integer::sum);
            }
        }
        return counts;
    }

    private static int rank(String kind, Object severity) {
        int grade = 1;
        if ("HIGH".equals(severity)) {
            grade = 3;
        } else if ("MEDIUM".equals(severity)) {
            grade = 2;
        }
        if ("ALERT".equals(kind)) {
            return 40 + grade * 20;
        }
        if ("DECISION".equals(kind)) {
            return 45 + grade * 16;
        }
        return 85;
    }

    private static void fillAction(Map<String, Object> result, CtAction action) {
        result.put("status", action.getStatus());
        result.put("type", action.getType());
        result.put("targetKey", action.getTargetKey());
        result.put("actionId", action.getId());
        result.put("result", action.getResult());
    }

    private static boolean ok(Object status) {
        return "SUCCESS".equals(status) || "EXECUTED".equals(status) || "APPLIED".equals(status);
    }

    private static boolean executable(Map<String, Object> row) {
        return row == null || !Boolean.FALSE.equals(row.get("executable"));
    }

    static boolean canApprove(String severity) {
        if (!"HIGH".equals(severity)) {
            return true;
        }
        User user = CurrentUser.get();
        return user == null || User.ADMIN.equals(user.getRole());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> itemsOf(Map<String, Object> request) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (request == null || !(request.get("items") instanceof List)) {
            return items;
        }
        for (Object row : (List<?>) request.get("items")) {
            if (row instanceof Map) {
                items.add((Map<String, Object>) row);
            }
        }
        return items;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String severityThenTime(String severityColumn, String timeColumn, int limit) {
        return "ORDER BY CASE " + severityColumn
                + " WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC, "
                + timeColumn + " DESC LIMIT " + limit;
    }

    private static Long idOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            java.math.BigDecimal decimal = value instanceof java.math.BigDecimal
                    ? (java.math.BigDecimal) value
                    : new java.math.BigDecimal(text);
            return decimal.toBigIntegerExact().longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new BizException("id 必须为整数");
        }
    }
}
