package com.ir.common;

public final class WarehouseCodes {
    private WarehouseCodes() {
    }

    public static String toOms(String code) {
        if (code == null) {
            return null;
        }
        if ("WH01".equals(code)) {
            return "WH-SH";
        }
        if ("WH02".equals(code)) {
            return "WH-BJ";
        }
        if ("WH03".equals(code)) {
            return "WH-GZ";
        }
        return code;
    }

    public static String toWms(String code) {
        if (code == null) {
            return null;
        }
        if ("WH-SH".equals(code)) {
            return "WH01";
        }
        if ("WH-BJ".equals(code)) {
            return "WH02";
        }
        if ("WH-GZ".equals(code)) {
            return "WH03";
        }
        return code;
    }

    /** SAP 工厂 / SRM 工厂 → OMS 仓编码，补货与成本都落在这套仓号上。 */
    public static String fromPlant(String plant) {
        if (plant == null || plant.trim().isEmpty()) {
            return "WH-SH";
        }
        if ("1000".equals(plant) || "P001".equals(plant) || "IR".equals(plant)) {
            return "WH-SH";
        }
        return toOms(plant);
    }
}
