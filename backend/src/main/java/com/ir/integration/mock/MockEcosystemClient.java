package com.ir.integration.mock;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.EcosystemClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockEcosystemClient implements EcosystemClient {
    private volatile String systemCode = "SAP";

    public MockEcosystemClient forSystem(String code) {
        this.systemCode = code == null ? "SAP" : code;
        return this;
    }

    @Override
    public List<Map<String, Object>> fetchSnapshots() {
        return rowsFor(systemCode);
    }

    @Override
    public void execute(ActionCommand command) {
        if (command.getTargetKey() == null || command.getTargetKey().trim().isEmpty()) {
            throw new IllegalArgumentException("指令缺少目标对象");
        }
    }

    @Override
    public boolean health() {
        return true;
    }

    public static List<Map<String, Object>> rowsFor(String code) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if ("SAP".equals(code)) {
            rows.add(row("STOCK", "MAT-1000/1000/0001", "LOW", "MAT-1000",
                    bd("4"), bd("1200"), "1000", "原料 MAT-1000"));
            rows.add(row("STOCK", "MAT-2000/1000/0001", "OK", "MAT-2000",
                    bd("80"), bd("6400"), "1000", "成品 MAT-2000"));
            rows.add(row("PR", "PR000100", "CREATED", "MAT-1000",
                    bd("20"), bd("6000"), "1000", "采购申请 PR000100"));
            rows.add(row("PO", "4500001000", "OPEN", "MAT-1000",
                    bd("20"), bd("6000"), "1000", "采购订单 4500001000"));
            rows.add(row("MO", "10000100", "CREATED", "MAT-2000",
                    bd("50"), bd("18000"), "1000", "生产订单 10000100"));
        } else if ("SRM".equals(code)) {
            rows.add(row("PR", "PR-IR-001", "DRAFT", "SKU001",
                    bd("30"), null, "P001", "采购申请 PR-IR-001"));
            rows.add(row("PR", "PR-IR-003", "SUBMITTED", "SKU001",
                    bd("18"), null, "P001", "采购申请 PR-IR-003"));
            rows.add(row("PR", "PR-IR-002", "APPROVED", "SKU002",
                    bd("12"), null, "P001", "采购申请 PR-IR-002"));
            rows.add(row("PO", "PO-88001", "SENT", "SKU002",
                    bd("12"), bd("9600"), "P001", "采购订单 PO-88001"));
            rows.add(row("ASN", "ASN-77001", "DELAYED", "SKU002",
                    bd("12"), null, "P001", "发货通知 ASN-77001"));
            rows.get(rows.size() - 1).put("poCode", "PO-88001");
            rows.get(rows.size() - 1).put("refCode", "PO-88001");
            rows.add(row("SUPPLIER", "SUP03", "RISK", null,
                    bd("72"), bd("72"), null, "苏州包装"));
            rows.get(rows.size() - 1).put("supplierCode", "SUP03");
            rows.get(rows.size() - 1).put("grade", "C");
            rows.get(rows.size() - 1).put("avgScore", bd("72"));
        } else if ("BOM".equals(code)) {
            rows.add(row("BOM", "EBOM-A1", "RELEASED", "VEH-A1",
                    bd("1"), null, "P001", "车型 A1 工程 BOM"));
            rows.add(row("ECN", "ECN-2026-01", "DRAFT", "VEH-A1",
                    bd("1"), null, "P001", "电池模组变更"));
            rows.add(row("ECN", "ECN-2026-02", "SUBMITTED", "VEH-A1",
                    bd("1"), null, "P001", "线束规格变更"));
            rows.add(row("ECN", "ECN-2026-03", "APPROVED", "VEH-A1",
                    bd("1"), null, "P001", "内饰面料变更"));
        } else if ("INV".equals(code)) {
            rows.add(row("INVOICE_REQUEST", "IR-INV-001", "DRAFT", "SO000010",
                    bd("1"), bd("12800"), "OMS", "开票申请 IR-INV-001"));
            rows.add(row("INVOICE_REQUEST", "IR-INV-002", "SUBMITTED", "SO000020",
                    bd("1"), bd("8600"), "OMS", "开票申请 IR-INV-002"));
            rows.add(row("INPUT_INVOICE", "10001001", "UNVERIFIED", "032002300001",
                    bd("1"), bd("22600"), "PO-IR-EXPEDITE", "恒信电子进项"));
            rows.add(row("SALES_INVOICE", "00002000", "ISSUED", "031002300112",
                    bd("1"), bd("5650"), "NORMAL", "北方快消品牌有限公司"));
        } else if ("CRM".equals(code)) {
            rows.add(row("OPPORTUNITY", "1", "QUALIFICATION", "华东经销商扩网",
                    bd("1"), bd("2400000"), null, "华东经销商扩网"));
            rows.add(row("OPPORTUNITY", "2", "NEGOTIATION", "华北备件框架",
                    bd("1"), bd("860000"), null, "华北备件框架"));
            rows.add(row("CASE", "CS20260001", "NEW", "交期投诉",
                    bd("1"), null, null, "交期投诉"));
        } else if ("DMS".equals(code)) {
            rows.add(row("SHORTAGE", "D001/P-OIL-01", "SHORT", "P-OIL-01",
                    bd("2"), null, "D001", "机油滤芯缺货"));
            rows.add(row("SHORTAGE", "D002/P-BRAKE-02", "SHORT", "P-BRAKE-02",
                    bd("0"), null, "D002", "刹车片缺货"));
            rows.add(row("REPLENISH", "RPL-10001", "DRAFT", "P-OIL-01",
                    bd("8"), null, "D001", "补货单 RPL-10001"));
        } else if ("OA".equals(code)) {
            rows.add(row("WF_INSTANCE", "WF1001", "RUNNING", "PR-IR-001",
                    bd("1"), bd("6000"), "IR", "采购申请审批 PR-IR-001"));
            rows.add(row("WF_TASK", "8801", "PENDING", "IR-DEMO-WF",
                    bd("1"), null, "WF1001", "IR 控制塔演示待办 · 部门主管"));
        }
        return rows;
    }

    private static Map<String, Object> row(
            String dataType, String bizKey, String status, String sku,
            BigDecimal qty, BigDecimal amount, String plantCode, String title) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dataType", dataType);
        row.put("bizKey", bizKey);
        row.put("status", status);
        row.put("sku", sku);
        row.put("qty", qty);
        row.put("amount", amount);
        row.put("plantCode", plantCode);
        row.put("title", title);
        return row;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
