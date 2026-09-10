package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.SrmClient;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.SupplierScore;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpSrmClient implements SrmClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String username;
    private final String password;
    private volatile String token;

    public HttpSrmClient(RestTemplate http, String baseUrl, String username, String password) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
    }

    @Override
    public List<PurchaseSnapshot> fetchPurchaseOrders() {
        List<PurchaseSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/purchase/order/page")) {
            PurchaseSnapshot po = new PurchaseSnapshot();
            po.setDocType("PO");
            po.setCode(HttpSupport.string(row, "code"));
            po.setRefCode(HttpSupport.string(row, "sapPoNo", "sourceCode"));
            po.setSupplierCode(HttpSupport.string(row, "supplierCode"));
            po.setPlantCode(HttpSupport.string(row, "plantCode"));
            po.setStatus(HttpSupport.string(row, "status"));
            po.setAmount(decimal(row, "totalAmount"));
            po.setExpectedDate(date(row, "expectedDate"));
            fillLines(po, row);
            result.add(po);
        }
        return result;
    }

    @Override
    public List<PurchaseSnapshot> fetchAsns() {
        List<PurchaseSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/delivery/asn/page")) {
            PurchaseSnapshot asn = new PurchaseSnapshot();
            asn.setDocType("ASN");
            asn.setCode(HttpSupport.string(row, "code"));
            asn.setRefCode(HttpSupport.string(row, "poCode"));
            asn.setSupplierCode(HttpSupport.string(row, "supplierCode"));
            asn.setPlantCode(HttpSupport.string(row, "plantCode"));
            asn.setStatus(HttpSupport.string(row, "status"));
            asn.setQty(decimal(row, "totalQty"));
            asn.setReceivedQty(decimal(row, "receivedQty"));
            asn.setExpectedDate(date(row, "expectedDate"));
            asn.setReceivedAt(dateTime(row, "receivedAt"));
            fillLines(asn, row);
            result.add(asn);
        }
        return result;
    }

    @Override
    public List<SupplierScore> fetchSupplierScores() {
        List<SupplierScore> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/evaluation/page")) {
            SupplierScore score = new SupplierScore();
            score.setSupplierCode(HttpSupport.string(row, "supplierCode"));
            score.setPeriod(HttpSupport.string(row, "period"));
            score.setReceiptCount((int) HttpSupport.longValue(row, "receiptCount"));
            score.setOnTimeRate(decimal(row, "onTimeRate"));
            score.setQtyAccuracy(decimal(row, "qtyAccuracy"));
            score.setQualityRate(decimal(row, "qualityRate"));
            score.setAvgScore(decimal(row, "avgScore"));
            score.setGrade(HttpSupport.string(row, "grade"));
            result.add(score);
        }
        return result;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> response = HttpSupport.getMap(http, baseUrl + "/api/dashboard", headers());
        Object data = response.get("data");
        return data instanceof Map ? (Map<String, Object>) data : Collections.emptyMap();
    }

    @Override
    public Map<String, Object> execute(ActionCommand command) {
        Map<String, Object> params = command.getParams() == null
                ? Collections.emptyMap() : command.getParams();
        Map<String, Object> result = new LinkedHashMap<>();
        if ("SRM_PURCHASE_SUGGEST".equals(command.getType())) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialCode", params.getOrDefault("sku", command.getTargetKey()));
            line.put("qty", params.get("qty"));
            line.put("requiredDate", params.getOrDefault(
                    "requiredDate", LocalDate.now().plusDays(7).toString()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("plantCode", params.getOrDefault("plantCode", "1000"));
            body.put("requester", "IR-控制塔");
            body.put("department", "供应链控制塔");
            body.put("remark", params.getOrDefault("reason", "控制塔自动补货建议"));
            body.put("lines", Collections.singletonList(line));
            Map<String, Object> created = HttpSupport.postMap(
                    http, baseUrl + "/api/sourcing/pr", body, headers());
            Object data = created.get("data");
            if (data instanceof Map) {
                Map<String, Object> pr = (Map<String, Object>) data;
                result.put("prCode", HttpSupport.string(pr, "code"));
                long id = HttpSupport.longValue(pr, "id");
                if (id > 0 && Boolean.TRUE.equals(params.getOrDefault("submit", Boolean.TRUE))) {
                    HttpSupport.postMap(http, baseUrl + "/api/sourcing/pr/" + id + "/submit",
                            Collections.emptyMap(), headers());
                    result.put("status", "SUBMITTED");
                }
            }
            return result;
        }
        if ("SRM_EXPEDITE_PO".equals(command.getType())) {
            Map<String, Object> po = findPurchaseOrder(command.getTargetKey());
            Map<String, Object> body = new LinkedHashMap<>(po);
            body.put("remark", "控制塔催单: " + params.getOrDefault("reason", "库存风险"));
            HttpSupport.putMap(http, baseUrl + "/api/purchase/order/" + HttpSupport.longValue(po, "id"),
                    body, headers());
            result.put("poCode", command.getTargetKey());
            return result;
        }
        throw new IntegrationException("SRM 不支持的动作: " + command.getType());
    }

    @Override
    public boolean health() {
        try {
            dashboard();
            return true;
        } catch (IntegrationException ex) {
            return false;
        }
    }

    private Map<String, Object> findPurchaseOrder(String code) {
        String url = baseUrl + "/api/purchase/order/page?page=1&size=20&keyword=" + code;
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, headers()))) {
            if (code.equals(HttpSupport.string(row, "code"))) {
                return row;
            }
        }
        throw new IntegrationException("SRM 采购订单不存在: " + code);
    }

    private static void fillLines(PurchaseSnapshot doc, Map<String, Object> row) {
        Object lines = row.get("lines");
        if (lines instanceof List && !((List<?>) lines).isEmpty()) {
            Object first = ((List<?>) lines).get(0);
            if (first instanceof Map) {
                Map<String, Object> line = (Map<String, Object>) first;
                doc.setSku(HttpSupport.string(line, "materialCode", "sku"));
                if (doc.getQty() == null) {
                    doc.setQty(decimal(line, "qty"));
                }
                if (doc.getReceivedQty() == null) {
                    doc.setReceivedQty(decimal(line, "receivedQty"));
                }
            }
        }
        if (doc.getQty() == null) {
            doc.setQty(BigDecimal.ZERO);
        }
        if (doc.getReceivedQty() == null) {
            doc.setReceivedQty(BigDecimal.ZERO);
        }
    }

    private List<Map<String, Object>> pages(String path) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int page = 1;
        int size = 200;
        while (page <= 50) {
            String url = baseUrl + path + "?page=" + page + "&size=" + size;
            List<Map<String, Object>> current =
                    HttpSupport.rows(HttpSupport.getMap(http, url, headers()));
            rows.addAll(current);
            if (current.size() < size) {
                return rows;
            }
            page++;
        }
        return rows;
    }

    private HttpHeaders headers() {
        return HttpSupport.bearer(login());
    }

    private synchronized String login() {
        if (token != null) {
            return token;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        Map<String, Object> response = HttpSupport.postMap(
                http, baseUrl + "/api/auth/login", body, new HttpHeaders());
        Object data = response.get("data");
        if (data instanceof Map) {
            token = HttpSupport.string((Map<String, Object>) data, "token", "accessToken");
        }
        if (token == null) {
            token = HttpSupport.string(response, "token", "accessToken");
        }
        if (token == null) {
            throw new IntegrationException("SRM 登录未返回 token");
        }
        return token;
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }

    private static LocalDate date(Map<String, Object> row, String... names) {
        String value = HttpSupport.string(row, names);
        return value == null ? null : LocalDate.parse(value.substring(0, 10));
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String... names) {
        String value = HttpSupport.string(row, names);
        return value == null ? null : LocalDateTime.parse(value.replace(" ", "T"));
    }
}
