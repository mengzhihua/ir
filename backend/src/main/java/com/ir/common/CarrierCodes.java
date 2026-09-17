package com.ir.common;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IR 沙盘承运商与 TMS 主数据对齐：SF / JD / SELF01。
 * 历史别名 JDL、SELF、ZTO 在下发前映射过去，避免 HTTP 换商找不到承运商。
 */
public final class CarrierCodes {
    public static final String SF = "SF";
    public static final String JD = "JD";
    public static final String SELF01 = "SELF01";

    public static final List<String> TMS = Arrays.asList(SF, JD, SELF01);

    private CarrierCodes() {
    }

    public static String toTms(String code) {
        if (code == null || code.trim().isEmpty()) {
            return SELF01;
        }
        String value = code.trim().toUpperCase();
        if ("SF".equals(value) || "SFEXPRESS".equals(value) || value.contains("顺丰")) {
            return SF;
        }
        if ("JD".equals(value) || "JDL".equals(value) || "JINGDONG".equals(value)
                || value.contains("京东")) {
            return JD;
        }
        if ("SELF".equals(value) || "SELF01".equals(value) || "FLEET".equals(value)
                || value.contains("自建") || value.contains("车队")) {
            return SELF01;
        }
        if ("ZTO".equals(value) || "YTO".equals(value) || "STO".equals(value)
                || "YUNDA".equals(value)) {
            return JD;
        }
        return JD;
    }

    /** 相对运价，越大越贵。与沙盘默认费率一致。 */
    public static BigDecimal rate(String code) {
        String carrier = toTms(code);
        if (SF.equals(carrier)) {
            return BigDecimal.valueOf(2.2);
        }
        if (JD.equals(carrier)) {
            return BigDecimal.valueOf(1.8);
        }
        return BigDecimal.valueOf(1.4);
    }

    /** 相对时效系数，越大越慢。 */
    public static BigDecimal lead(String code) {
        String carrier = toTms(code);
        if (SF.equals(carrier)) {
            return BigDecimal.valueOf(0.8);
        }
        if (JD.equals(carrier)) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(1.4);
    }

    public static String oneStepCheaper(String code) {
        String carrier = toTms(code);
        if (SF.equals(carrier)) {
            return JD;
        }
        if (JD.equals(carrier)) {
            return SELF01;
        }
        return null;
    }

    public static Map<String, BigDecimal> mergeMix(Map<String, BigDecimal> mix) {
        Map<String, BigDecimal> result = new LinkedHashMap<String, BigDecimal>();
        if (mix == null) {
            return result;
        }
        for (Map.Entry<String, BigDecimal> entry : mix.entrySet()) {
            String code = toTms(entry.getKey());
            BigDecimal weight = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            result.put(code, result.getOrDefault(code, BigDecimal.ZERO).add(weight));
        }
        return result;
    }

    public static Map<String, BigDecimal> mergeRates(Map<String, BigDecimal> rates) {
        Map<String, BigDecimal> result = new LinkedHashMap<String, BigDecimal>();
        result.put(SF, rate(SF));
        result.put(JD, rate(JD));
        result.put(SELF01, rate(SELF01));
        if (rates == null) {
            return result;
        }
        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            if (entry.getValue() != null) {
                result.put(toTms(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    public static Map<String, BigDecimal> mergeLeads(Map<String, BigDecimal> leads) {
        Map<String, BigDecimal> result = new LinkedHashMap<String, BigDecimal>();
        result.put(SF, lead(SF));
        result.put(JD, lead(JD));
        result.put(SELF01, lead(SELF01));
        if (leads == null) {
            return result;
        }
        for (Map.Entry<String, BigDecimal> entry : leads.entrySet()) {
            if (entry.getValue() != null) {
                result.put(toTms(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
