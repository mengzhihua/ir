package com.ir.sandbox;

import com.ir.action.CtAction;
import com.ir.common.CodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统自动沙盘：按成本/效率策略网格批量推演，打出综合分并标出推荐方案。
 */
@Service
public class AutoSandboxService {
    private final SandboxService sandbox;
    private final CodeGenerator codes;

    @Value("${ir.sandbox.cost-weight:0.5}")
    private BigDecimal costWeight;

    @Value("${ir.sandbox.efficiency-weight:0.5}")
    private BigDecimal efficiencyWeight;

    @Value("${ir.sandbox.auto-apply:false}")
    private boolean autoApply;

    @Value("${ir.sandbox.auto-queue:true}")
    private boolean autoQueue;

    @Value("${ir.sandbox.auto-enabled:true}")
    private boolean autoEnabled;

    public AutoSandboxService(SandboxService sandbox, CodeGenerator codes) {
        this.sandbox = sandbox;
        this.codes = codes;
    }

    public synchronized Map<String, Object> run() {
        sandbox.ensureBaseline();
        String runNo = codes.next("ASR");
        List<CtScenario> rows = new ArrayList<>();
        for (Candidate candidate : candidates()) {
            rows.add(sandbox.persistAuto(candidate.name, candidate.params, runNo));
        }
        sandbox.rescore(rows, costWeight, efficiencyWeight);
        CtScenario recommended = pickRecommended(rows);
        sandbox.markRecommended(rows, recommended == null ? null : recommended.getId());

        List<CtAction> actions = new ArrayList<>();
        if (recommended != null && (autoApply || autoQueue)) {
            actions.addAll(sandbox.apply(recommended.getId(), autoApply));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runNo", runNo);
        result.put("costWeight", costWeight);
        result.put("efficiencyWeight", efficiencyWeight);
        result.put("recommended", recommended);
        result.put("scenarios", rows);
        result.put("actions", actions);
        result.put("autoQueue", autoQueue);
        result.put("autoApply", autoApply);
        return result;
    }

    public Map<String, Object> latest() {
        String runNo = sandbox.latestAutoRunNo();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runNo", runNo);
        result.put("costWeight", costWeight);
        result.put("efficiencyWeight", efficiencyWeight);
        if (runNo == null) {
            result.put("recommended", null);
            result.put("scenarios", new ArrayList<CtScenario>());
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
        result.put("recommended", recommended);
        result.put("scenarios", rows);
        return result;
    }

    @Scheduled(cron = "${ir.sandbox.auto-cron:0 30 */6 * * ?}")
    public void scheduled() {
        if (autoEnabled) {
            run();
        }
    }

    private CtScenario pickRecommended(List<CtScenario> rows) {
        CtScenario best = null;
        for (CtScenario row : rows) {
            if (best == null
                    || nz(row.getBalanceScore()).compareTo(nz(best.getBalanceScore())) > 0) {
                best = row;
            }
        }
        return best;
    }

    private List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        result.add(candidate("自动·就近分配（效率优先）",
                params("NEAREST", null, 3, 3, 0.3, 0.7, mix(0.4, 0.3, 0.3))));
        result.add(candidate("自动·最低成本",
                params("LOWEST_COST", null, 3, 3, 0.7, 0.3, mix(0.2, 0.3, 0.5))));
        result.add(candidate("自动·成本效率均衡",
                params("BALANCED", null, 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3))));
        result.add(candidate("自动·上海单仓",
                params("SINGLE_WAREHOUSE", "WH-SH", 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3))));
        result.add(candidate("自动·北京单仓",
                params("SINGLE_WAREHOUSE", "WH-BJ", 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3))));
        result.add(candidate("自动·广州单仓",
                params("SINGLE_WAREHOUSE", "WH-GZ", 3, 3, 0.5, 0.5, mix(0.4, 0.3, 0.3))));
        result.add(candidate("自动·时效承运（顺丰为主）",
                params("NEAREST", null, 3, 2, 0.3, 0.7, mix(0.7, 0.2, 0.1))));
        result.add(candidate("自动·经济承运",
                params("LOWEST_COST", null, 3, 4, 0.7, 0.3, mix(0.1, 0.3, 0.6))));
        result.add(candidate("自动·高安全库存 + 短交期",
                params("BALANCED", null, 7, 1, 0.4, 0.6, mix(0.5, 0.3, 0.2))));
        result.add(candidate("自动·低安全库存 + 最低成本",
                params("LOWEST_COST", null, 1, 5, 0.8, 0.2, mix(0.1, 0.2, 0.7))));
        return result;
    }

    private Candidate candidate(String name, ScenarioParams params) {
        Candidate candidate = new Candidate();
        candidate.name = name;
        candidate.params = params;
        return candidate;
    }

    private ScenarioParams params(
            String allocation,
            String warehouse,
            int safetyDays,
            int replenishLeadDays,
            double costW,
            double efficiencyW,
            Map<String, BigDecimal> carrierMix) {
        ScenarioParams params = new ScenarioParams();
        params.setHorizonDays(14);
        params.setAllocationStrategy(allocation);
        params.setSingleWarehouse(warehouse);
        params.setSafetyDays(safetyDays);
        params.setReplenishLeadDays(replenishLeadDays);
        params.setCostWeight(BigDecimal.valueOf(costW));
        params.setEfficiencyWeight(BigDecimal.valueOf(efficiencyW));
        params.setCarrierMix(carrierMix);
        return params;
    }

    private Map<String, BigDecimal> mix(double sf, double jd, double self) {
        Map<String, BigDecimal> mix = new LinkedHashMap<>();
        mix.put("SF", BigDecimal.valueOf(sf));
        mix.put("JD", BigDecimal.valueOf(jd));
        mix.put("SELF01", BigDecimal.valueOf(self));
        return mix;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static class Candidate {
        private String name;
        private ScenarioParams params;
    }
}
