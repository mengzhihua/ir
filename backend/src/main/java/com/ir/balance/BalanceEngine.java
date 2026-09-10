package com.ir.balance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.action.ActionService;
import com.ir.action.CtAction;
import com.ir.action.CtActionMapper;
import com.ir.common.BizException;
import com.ir.common.CodeGenerator;
import com.ir.common.Jsons;
import com.ir.objective.ObjectiveService;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.CostRecordMapper;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.OrderSnapshotMapper;
import com.ir.snapshot.PurchaseSnapshotMapper;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.system.CurrentUser;
import com.ir.system.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动平衡引擎:读取目标看板 -> 生成决策 -> 护栏过滤 -> 自动执行/待审批 -> 记录运行结果。
 */
@Service
public class BalanceEngine {
    private static final Logger log = LoggerFactory.getLogger(BalanceEngine.class);

    private final BalanceConfig config;
    private final StrategyCatalog strategies;
    private final ObjectiveService objectives;
    private final ActionService actions;
    private final CtBalanceRunMapper runMapper;
    private final CtBalanceDecisionMapper decisionMapper;
    private final CtActionMapper actionMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final OrderSnapshotMapper orderMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final PurchaseSnapshotMapper purchaseMapper;
    private final CostRecordMapper costMapper;
    private final CodeGenerator codes;
    private final ObjectMapper objectMapper;

    public BalanceEngine(
            BalanceConfig config,
            StrategyCatalog strategies,
            ObjectiveService objectives,
            ActionService actions,
            CtBalanceRunMapper runMapper,
            CtBalanceDecisionMapper decisionMapper,
            CtActionMapper actionMapper,
            InventorySnapshotMapper inventoryMapper,
            OrderSnapshotMapper orderMapper,
            ShipmentSnapshotMapper shipmentMapper,
            PurchaseSnapshotMapper purchaseMapper,
            CostRecordMapper costMapper,
            CodeGenerator codes,
            ObjectMapper objectMapper) {
        this.config = config;
        this.strategies = strategies;
        this.objectives = objectives;
        this.actions = actions;
        this.runMapper = runMapper;
        this.decisionMapper = decisionMapper;
        this.actionMapper = actionMapper;
        this.inventoryMapper = inventoryMapper;
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.purchaseMapper = purchaseMapper;
        this.costMapper = costMapper;
        this.codes = codes;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${ir.balance.cron:0 10/30 * * * ?}")
    public void scheduledRun() {
        if (!config.isScheduleEnabled() || "OFF".equalsIgnoreCase(config.getMode())) {
            return;
        }
        try {
            run("SCHEDULED");
        } catch (RuntimeException ex) {
            log.warn("自动平衡定时运行失败: {}", ex.getMessage());
        }
    }

    @Transactional
    public synchronized CtBalanceRun run(String triggerType) {
        String mode = config.getMode() == null ? "AUTO" : config.getMode().toUpperCase();
        CtBalanceRun run = new CtBalanceRun();
        run.setRunNo(codes.next("BAL"));
        run.setTriggerType(triggerType);
        run.setMode(mode);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        Map<String, Object> before = objectives.scoreboard();
        run.setScoreBefore(ObjectiveServiceAccess.score(before));
        BalanceContext ctx = context(before);
        List<Decision> candidates = strategies.generate(ctx);
        Set<String> seen = recentKeys();
        List<Decision> accepted = new ArrayList<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        candidates.sort(Comparator.comparingDouble(Decision::getPriority).reversed());
        int perStrategyCap = Math.max(3, config.getMaxDecisionsPerRun() / 2);
        Map<String, Integer> perStrategy = new LinkedHashMap<>();
        for (Decision d : candidates) {
            String key = d.getActionType() + "|" + d.getTargetKey();
            if (!seen.add(key)) {
                skipped.merge("COOLDOWN", 1, Integer::sum);
                continue;
            }
            if (accepted.size() >= config.getMaxDecisionsPerRun()
                    || perStrategy.getOrDefault(d.getStrategy(), 0) >= perStrategyCap) {
                skipped.merge("CAPACITY", 1, Integer::sum);
                continue;
            }
            perStrategy.merge(d.getStrategy(), 1, Integer::sum);
            accepted.add(d);
        }

        int executed = 0;
        int pending = 0;
        int failed = 0;
        int autoBudget = config.getMaxAutoExecutePerRun();
        Map<String, Integer> byStrategy = new LinkedHashMap<>();
        BigDecimal costDelta = BigDecimal.ZERO;
        for (Decision d : accepted) {
            CtBalanceDecision row = toEntity(run.getId(), d);
            byStrategy.merge(d.getStrategy(), 1, Integer::sum);
            boolean auto = "AUTO".equals(mode) && !d.isApprovalRequired()
                    && !"HIGH".equals(d.getRiskLevel()) && autoBudget > 0;
            if (auto) {
                autoBudget--;
                decisionMapper.insert(row);
                execute(row, "system");
                if ("EXECUTED".equals(row.getStatus())) {
                    executed++;
                    costDelta = costDelta.add(d.getExpectedCostDelta());
                } else {
                    failed++;
                }
            } else {
                row.setStatus("PENDING");
                decisionMapper.insert(row);
                pending++;
            }
        }
        Map<String, Object> after = executed > 0 ? objectives.scoreboard() : before;
        run.setScoreAfter(ObjectiveServiceAccess.score(after));
        run.setDecisionCount(accepted.size());
        run.setExecutedCount(executed);
        run.setPendingCount(pending);
        run.setStatus("DONE");
        run.setFinishedAt(LocalDateTime.now());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("candidates", candidates.size());
        summary.put("skipped", skipped);
        summary.put("byStrategy", byStrategy);
        summary.put("failed", failed);
        summary.put("expectedCostDelta", costDelta);
        summary.put("metricsBefore", before.get("metrics"));
        summary.put("metricsAfter", after.get("metrics"));
        summary.put("objectives", after.get("objectives"));
        run.setSummaryJson(Jsons.write(objectMapper, summary));
        runMapper.updateById(run);
        return run;
    }

    @Transactional
    public CtBalanceDecision approve(Long id) {
        CtBalanceDecision row = pending(id);
        execute(row, operator());
        return row;
    }

    @Transactional
    public CtBalanceDecision reject(Long id, String reason) {
        CtBalanceDecision row = pending(id);
        row.setStatus("REJECTED");
        row.setDecidedBy(operator());
        row.setDecidedAt(LocalDateTime.now());
        if (reason != null && !reason.isEmpty()) {
            row.setReason(row.getReason() + " | 拒绝原因: " + reason);
        }
        decisionMapper.updateById(row);
        return row;
    }

    public Page<CtBalanceRun> runs(long current, long size) {
        return runMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<CtBalanceRun>().orderByDesc(CtBalanceRun::getStartedAt));
    }

    public Map<String, Object> runDetail(Long id) {
        CtBalanceRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BizException("平衡运行记录不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", run);
        result.put("summary", Jsons.readMap(objectMapper, run.getSummaryJson()));
        result.put("decisions", decisionMapper.selectList(new LambdaQueryWrapper<CtBalanceDecision>()
                .eq(CtBalanceDecision::getRunId, id).orderByDesc(CtBalanceDecision::getId)));
        return result;
    }

    public Page<CtBalanceDecision> decisions(String status, String strategy, long current, long size) {
        LambdaQueryWrapper<CtBalanceDecision> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            query.eq(CtBalanceDecision::getStatus, status);
        }
        if (strategy != null && !strategy.isEmpty()) {
            query.eq(CtBalanceDecision::getStrategy, strategy);
        }
        query.orderByDesc(CtBalanceDecision::getId);
        return decisionMapper.selectPage(new Page<>(current, size), query);
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", config);
        result.put("strategies", StrategyCatalog.CATALOG);
        result.put("pendingDecisions", decisionMapper.selectCount(
                new LambdaQueryWrapper<CtBalanceDecision>().eq(CtBalanceDecision::getStatus, "PENDING")));
        List<CtBalanceRun> latest = runMapper.selectList(new LambdaQueryWrapper<CtBalanceRun>()
                .orderByDesc(CtBalanceRun::getStartedAt).last("LIMIT 1"));
        result.put("lastRun", latest.isEmpty() ? null : latest.get(0));
        BigDecimal saving = BigDecimal.ZERO;
        int executed = 0;
        for (CtBalanceDecision d : decisionMapper.selectList(new LambdaQueryWrapper<CtBalanceDecision>()
                .eq(CtBalanceDecision::getStatus, "EXECUTED")
                .ge(CtBalanceDecision::getDecidedAt, LocalDate.now().minusDays(30).atStartOfDay()))) {
            executed++;
            if (d.getExpectedCostDelta() != null && d.getExpectedCostDelta().signum() < 0) {
                saving = saving.add(d.getExpectedCostDelta().negate());
            }
        }
        result.put("executed30d", executed);
        result.put("saving30d", saving);
        return result;
    }

    public BalanceConfig config() {
        return config;
    }

    public BalanceConfig updateConfig(Map<String, Object> patch) {
        if (patch.get("mode") != null) {
            String mode = String.valueOf(patch.get("mode")).toUpperCase();
            if (!Arrays.asList("OFF", "SUGGEST", "AUTO").contains(mode)) {
                throw new BizException("mode 仅支持 OFF/SUGGEST/AUTO");
            }
            config.setMode(mode);
        }
        if (patch.get("scheduleEnabled") != null) {
            config.setScheduleEnabled(Boolean.parseBoolean(String.valueOf(patch.get("scheduleEnabled"))));
        }
        if (patch.get("maxDecisionsPerRun") != null) {
            config.setMaxDecisionsPerRun(Integer.parseInt(String.valueOf(patch.get("maxDecisionsPerRun"))));
        }
        if (patch.get("maxAutoExecutePerRun") != null) {
            config.setMaxAutoExecutePerRun(Integer.parseInt(String.valueOf(patch.get("maxAutoExecutePerRun"))));
        }
        if (patch.get("autoPurchaseAmountLimit") != null) {
            config.setAutoPurchaseAmountLimit(new BigDecimal(String.valueOf(patch.get("autoPurchaseAmountLimit"))));
        }
        if (patch.get("cooldownHours") != null) {
            config.setCooldownHours(Integer.parseInt(String.valueOf(patch.get("cooldownHours"))));
        }
        if (patch.get("serviceGuardAttainment") != null) {
            config.setServiceGuardAttainment(new BigDecimal(String.valueOf(patch.get("serviceGuardAttainment"))));
        }
        return config;
    }

    private void execute(CtBalanceDecision row, String operator) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", row.getActionType());
        request.put("targetKey", row.getTargetKey());
        request.put("params", Jsons.readMap(objectMapper, row.getParamsJson()));
        if (row.getExpectedCostDelta() != null && row.getExpectedCostDelta().signum() < 0) {
            request.put("expectedSaving", row.getExpectedCostDelta().negate());
        }
        CtAction action;
        try {
            action = actions.createAndExecute(request);
        } catch (RuntimeException ex) {
            row.setStatus("FAILED");
            row.setReason(row.getReason() + " | 执行异常: " + ex.getMessage());
            row.setDecidedBy(operator);
            row.setDecidedAt(LocalDateTime.now());
            decisionMapper.updateById(row);
            return;
        }
        row.setActionId(action.getId());
        row.setStatus("SUCCESS".equals(action.getStatus()) ? "EXECUTED" : "FAILED");
        if (!"SUCCESS".equals(action.getStatus())) {
            row.setReason(row.getReason() + " | 执行失败: " + action.getResult());
        }
        row.setDecidedBy(operator);
        row.setDecidedAt(LocalDateTime.now());
        decisionMapper.updateById(row);
    }

    private CtBalanceDecision pending(Long id) {
        CtBalanceDecision row = decisionMapper.selectById(id);
        if (row == null) {
            throw new BizException("决策不存在");
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new BizException("决策已处理: " + row.getStatus());
        }
        return row;
    }

    private Set<String> recentKeys() {
        Set<String> keys = new HashSet<>();
        LocalDateTime since = LocalDateTime.now().minusHours(config.getCooldownHours());
        for (CtBalanceDecision d : decisionMapper.selectList(new LambdaQueryWrapper<CtBalanceDecision>()
                .ge(CtBalanceDecision::getCreatedAt, since)
                .in(CtBalanceDecision::getStatus, "PENDING", "EXECUTED", "REJECTED"))) {
            keys.add(d.getActionType() + "|" + d.getTargetKey());
        }
        for (CtAction a : actionMapper.selectList(new LambdaQueryWrapper<CtAction>()
                .ge(CtAction::getCreatedAt, since).eq(CtAction::getStatus, "SUCCESS"))) {
            keys.add(a.getType() + "|" + a.getTargetKey());
        }
        return keys;
    }

    private BalanceContext context(Map<String, Object> scoreboard) {
        BalanceContext ctx = new BalanceContext();
        ctx.setNow(LocalDateTime.now());
        ctx.setConfig(config);
        ctx.setMetrics((Map<String, Object>) scoreboard.get("metrics"));
        Map<String, BigDecimal> attainment = new LinkedHashMap<>();
        Map<String, BigDecimal> weight = new LinkedHashMap<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) scoreboard.get("objectives")) {
            attainment.put(String.valueOf(row.get("code")), (BigDecimal) row.get("attainment"));
            weight.put(String.valueOf(row.get("code")), (BigDecimal) row.get("weight"));
        }
        ctx.setAttainment(attainment);
        ctx.setWeight(weight);
        ctx.setInventory(inventoryMapper.selectList(null));
        ctx.setOrders(orderMapper.selectList(null));
        ctx.setShipments(shipmentMapper.selectList(null));
        ctx.setPurchases(purchaseMapper.selectList(null));
        ctx.setFreightCosts(costMapper.selectList(new LambdaQueryWrapper<CostRecord>()
                .eq(CostRecord::getCostType, "FREIGHT")
                .ge(CostRecord::getBizDate, LocalDate.now().minusDays(30))));
        return ctx;
    }

    private CtBalanceDecision toEntity(Long runId, Decision d) {
        CtBalanceDecision row = new CtBalanceDecision();
        row.setRunId(runId);
        row.setStrategy(d.getStrategy());
        row.setObjectiveCode(d.getObjectiveCode());
        row.setTargetSystem(d.getActionType().substring(0, d.getActionType().indexOf('_')));
        row.setActionType(d.getActionType());
        row.setTargetKey(d.getTargetKey());
        row.setParamsJson(Jsons.write(objectMapper, d.getParams()));
        row.setExpectedCostDelta(d.getExpectedCostDelta());
        row.setExpectedNpsDelta(d.getExpectedNpsDelta());
        row.setRiskLevel(d.getRiskLevel());
        row.setApprovalRequired(d.isApprovalRequired());
        row.setReason(d.getReason());
        row.setStatus("PENDING");
        return row;
    }

    private static String operator() {
        User user = CurrentUser.get();
        return user == null ? "system" : user.getUsername();
    }

    /** 从看板结果中读取综合得分. */
    static final class ObjectiveServiceAccess {
        static BigDecimal score(Map<String, Object> scoreboard) {
            Object score = scoreboard.get("score");
            return score instanceof BigDecimal ? (BigDecimal) score : BigDecimal.ZERO;
        }
    }
}
