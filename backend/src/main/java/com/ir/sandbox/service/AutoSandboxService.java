package com.ir.sandbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.ir.action.entity.CtAction;
import com.ir.action.service.ActionService;
import com.ir.alert.service.AlertEngine;
import com.ir.common.CodeGenerator;
import com.ir.sandbox.engine.AutoSandboxPicker;
import com.ir.sandbox.engine.AutoSandboxPlanner;
import com.ir.sandbox.engine.BaselineData;
import com.ir.sandbox.engine.ScenarioParams;
import com.ir.sandbox.entity.CtScenario;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统自动沙盘：固定网格 + 预警/仓网/立场派生方案，打出综合分，
 * 先保服务再选现金，2 倍需求仍可行且现金差不超过 12% 时改推稳健方案。
 */

@Service
public class AutoSandboxService {
    private final SandboxService sandbox;
    private final CodeGenerator codes;
    private final BalancePolicy policy;
    private final ActionService actions;
    private final AlertEngine alerts;

    @Value("${ir.sandbox.auto-apply:false}")
    private boolean autoApply;

    @Value("${ir.sandbox.auto-queue:true}")
    private boolean autoQueue;

    @Value("${ir.sandbox.auto-enabled:true}")
    private boolean autoEnabled;

    public AutoSandboxService(
            SandboxService sandbox,
            CodeGenerator codes,
            BalancePolicy policy,
            ActionService actions,
            AlertEngine alerts) {
        this.sandbox = sandbox;
        this.codes = codes;
        this.policy = policy;
        this.actions = actions;
        this.alerts = alerts;
    }

    public synchronized Map<String, Object> run() {
        sandbox.ensureBaseline();
        String runNo = codes.next("ASR");
        Map<String, Integer> openByType = alerts.openCountsByType();
        List<String> signals = AutoSandboxPlanner.signalsOf(openByType);
        List<AutoSandboxPlanner.Candidate> candidates = AutoSandboxPlanner.plan(
                openByType, sandbox.warehouseCodes(), policy.stance());
        BaselineData data = sandbox.currentBaselineData();
        List<CtScenario> rows = new ArrayList<CtScenario>();
        for (AutoSandboxPlanner.Candidate candidate : candidates) {
            CtScenario row = sandbox.persistAuto(candidate.name, candidate.params, runNo);
            sandbox.annotateCandidate(row, candidate.source, candidate.signal);
            sandbox.attachStress(row, data);
            rows.add(row);
        }
        sandbox.rescore(rows, policy.costWeight(), policy.efficiencyWeight());
        AutoSandboxPicker.Decision<CtScenario> decision = AutoSandboxPicker.decide(
                rows,
                CtScenario::getServiceLevel,
                CtScenario::getStockoutUnits,
                sandbox::cashUsedOf,
                sandbox::stressReliableOf,
                CtScenario::getName);
        CtScenario recommended = decision.recommended;
        if (recommended == null) {
            recommended = pickByBalance(rows);
        }
        sandbox.markRecommended(rows, recommended == null ? null : recommended.getId());
        Map<String, Object> rationale = new LinkedHashMap<String, Object>(decision.rationale);
        rationale.put("signals", signals);
        rationale.put("candidateCount", rows.size());
        rationale.put("gridCount", 10);
        rationale.put("derivedCount", Math.max(0, rows.size() - 10));
        if (recommended != null) {
            ScenarioParams recParams = sandbox.paramsOf(recommended);
            if (recParams != null) {
                policy.updateReplenish(recParams.getSafetyDays(), recParams.getReplenishLeadDays());
            }
            sandbox.mergeIntoResult(recommended, mapOf("pickRationale", rationale, "signals", signals));
        }

        List<CtAction> queued = new ArrayList<CtAction>();
        if (recommended != null && (autoApply || autoQueue)) {
            actions.supersedeOpposing(policy.stance());
            queued.addAll(sandbox.apply(recommended.getId(), autoApply));
        }
        alerts.evaluate();
        return payload(runNo, recommended, rows, queued, openByType, signals, rationale);
    }

    public Map<String, Object> latest() {
        String runNo = sandbox.latestAutoRunNo();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("runNo", runNo);
        result.putAll(policy.snapshot());
        if (runNo == null) {
            result.put("recommended", null);
            result.put("scenarios", new ArrayList<CtScenario>());
            result.put("history", sandbox.listAutoHistory(8));
            return result;
        }
        List<CtScenario> rows = sandbox.listByRunNo(runNo);
        CtScenario recommended = null;
        for (CtScenario row : rows) {
            if (Boolean.TRUE.equals(row.getRecommended())) {
                recommended = row;
                break;
            }
        }
        Map<String, Object> recResult = recommended == null
                ? new LinkedHashMap<String, Object>()
                : sandbox.resultOf(recommended);
        Object rationale = recResult.get("pickRationale");
        Object signals = recResult.get("signals");
        result.put("recommended", recommended);
        result.put("scenarios", rows);
        result.put("rationale", rationale);
        result.put("signals", signals);
        result.put("alerts", alerts.openCount());
        result.put("openAlerts", alerts.openCount());
        result.put("forecastStockoutAlerts", alerts.openCount("FORECAST_STOCKOUT"));
        result.put("openByType", alerts.openCountsByType());
        result.put("history", sandbox.listAutoHistory(8));
        result.put("autoQueue", autoQueue);
        result.put("autoApply", autoApply);
        return result;
    }

    public List<Map<String, Object>> history(int size) {
        return sandbox.listAutoHistory(size);
    }

    @Scheduled(cron = "${ir.sandbox.auto-cron:0 30 */6 * * ?}")
    public void scheduled() {
        if (autoEnabled) {
            run();
        }
    }

    private Map<String, Object> payload(
            String runNo,
            CtScenario recommended,
            List<CtScenario> rows,
            List<CtAction> queued,
            Map<String, Integer> openByType,
            List<String> signals,
            Map<String, Object> rationale) {
        int openAlerts = alerts.openCount();
        int forecastAlerts = alerts.openCount("FORECAST_STOCKOUT");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("runNo", runNo);
        result.putAll(policy.snapshot());
        result.put("recommended", recommended);
        result.put("cashBestName", rationale.get("cashBestName"));
        result.put("robustBestName", rationale.get("robustBestName"));
        result.put("usedRobust", rationale.get("usedRobust"));
        result.put("scenarios", rows);
        result.put("actions", queued);
        result.put("alerts", openAlerts);
        result.put("openAlerts", openAlerts);
        result.put("forecastStockoutAlerts", forecastAlerts);
        result.put("openByType", openByType);
        result.put("signals", signals);
        result.put("rationale", rationale);
        result.put("history", sandbox.listAutoHistory(8));
        result.put("autoQueue", autoQueue);
        result.put("autoApply", autoApply);
        return result;
    }

    private CtScenario pickByBalance(List<CtScenario> rows) {
        CtScenario best = null;
        for (CtScenario row : rows) {
            if (best == null
                    || nz(row.getBalanceScore()).compareTo(nz(best.getBalanceScore())) > 0) {
                best = row;
            }
        }
        return best;
    }

    private Map<String, Object> mapOf(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
