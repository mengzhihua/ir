package com.ir.balance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 自动平衡护栏配置。可通过 application.yml 初始化,也可在运行期通过 /api/balance/config 调整。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ir.balance")
public class BalanceConfig {
    /** OFF:不运行;SUGGEST:只生成待审批决策;AUTO:低风险决策自动执行. */
    private String mode = "AUTO";
    private boolean scheduleEnabled = true;
    private int maxDecisionsPerRun = 20;
    private int maxAutoExecutePerRun = 10;
    /** 单笔采购金额超过该值需要人工审批. */
    private BigDecimal autoPurchaseAmountLimit = BigDecimal.valueOf(20000);
    /** 同一动作 + 目标在冷却期内不重复生成. */
    private int cooldownHours = 24;
    /** 服务类目标达成率低于该值时,禁止执行会降低服务水平的降本动作. */
    private BigDecimal serviceGuardAttainment = BigDecimal.valueOf(0.9);
    /** 调拨单位成本(元/件),用于估算补货决策的成本影响. */
    private BigDecimal transferCostPerUnit = BigDecimal.valueOf(0.6);
}
