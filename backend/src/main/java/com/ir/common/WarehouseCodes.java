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
}
