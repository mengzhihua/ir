package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.SrmClient;
import com.ir.snapshot.PurchaseSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ir.snapshot.SupplierScore;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpSrmClient implements SrmClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestTemplate http;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String apiKey;
    private volatile String token;
    private Map<String, Object> cachedSnapshot;

    public HttpSrmClient(RestTemplate http, String baseUrl, String username, String password) {
        this(http, baseUrl, username, password, null);
    }

    public HttpSrmClient(RestTemplate http, String baseUrl, String username, String password, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
    }

    @Override
    public List<PurchaseSnapshot> fetchPurchaseOrders() {
        List<PurchaseSnapshot> fromOpen = openSnapshots("PO");
        if (fromOpen != null) {
            return fromOpen;
        }
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
        List<PurchaseSnapshot> fromOpen = openSnapshots("ASN");
        if (fromOpen != null) {
            return fromOpen;
        }
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
        List<SupplierScore> fromOpen = openScores();
        if (fromOpen != null) {
            return fromOpen;
        }
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
        if (hasApiKey()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", command.getType());
            body.put("targetKey", command.getTargetKey());
            body.put("sku", params.getOrDefault("sku", command.getTargetKey()));
            body.put("qty", params.get("qty"));
            body.put("plantCode", params.get("plantCode"));
            body.put("remark", params.getOrDefault("reason", params.get("remark")));
            body.put("params", params);
            if ("SRM_SUBMIT_PR".equals(command.getType()) || "SRM_APPROVE_PR".equals(command.getType())) {
                body.put("code", params.getOrDefault("code", command.getTargetKey()));
            }
            if ("SRM_EXPEDITE_PO".equals(command.getType())) {
                body.put("poCode", params.getOrDefault("poCode", command.getTargetKey()));
            }
            if (command.getIdempotencyKey() != null) {
                body.put("idempotencyKey", command.getIdempotencyKey());
            }
            Map<String, Object> response;
            try {
                response = HttpSupport.postMap(
                        http, baseUrl + "/api/open/ir/actions", body, HttpSupport.apiKey(apiKey));
            } catch (IntegrationException ex) {
                String path = dedicatedPath(command.getType());
                if (path == null || ex.isOutcomeUnknown()) {
                    throw ex;
                }
                response = HttpSupport.postMap(
                        http, baseUrl + path, body, HttpSupport.apiKey(apiKey));
            }
            Object data = response.get("data");
            if (data instanceof Map) {
                result.putAll((Map<String, Object>) data);
            } else if (data != null) {
                result.put("result", data);
            }
            result.put("type", command.getType());
            result.put("targetKey", command.getTargetKey());
            cachedSnapshot = null;
            return result;
        }
        if ("SRM_PURCHASE_SUGGEST".equals(command.getType())) {
            BigDecimal qty = params.get("qty") == null ? BigDecimal.ZERO
                    : new BigDecimal(String.valueOf(params.get("qty")));
            if (qty.signum() <= 0) {
                throw new IntegrationException("SRM 采购申请缺少有效数量 qty");
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialCode", params.getOrDefault("sku", command.getTargetKey()));
            line.put("qty", qty);
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
        if ("SRM_SUBMIT_PR".equals(command.getType()) || "SRM_APPROVE_PR".equals(command.getType())) {
            Map<String, Object> pr = findPurchaseRequisition(command.getTargetKey());
            String action = "SRM_SUBMIT_PR".equals(command.getType()) ? "submit" : "approve";
            Map<String, Object> response = HttpSupport.postMap(
                    http, baseUrl + "/api/sourcing/pr/" + HttpSupport.longValue(pr, "id") + "/" + action,
                    Collections.emptyMap(), headers());
            Object data = response.get("data");
            if (data instanceof Map) {
                result.putAll((Map<String, Object>) data);
            }
            result.put("prCode", command.getTargetKey());
            return result;
        }
        throw new IntegrationException("SRM 不支持的动作: " + command.getType());
    }

    @Override
    public boolean health() {
        try {
            if (hasApiKey()) {
                snapshot();
            } else {
                dashboard();
            }
            return true;
        } catch (IntegrationException ex) {
            return false;
        }
    }

    private List<PurchaseSnapshot> openSnapshots(String dataType) {
        if (!hasApiKey()) {
            return null;
        }
        try {
            List<PurchaseSnapshot> result = new ArrayList<>();
            for (Map<String, Object> raw : HttpEcosystemClient.snapshots(snapshot())) {
                if (!dataType.equals(HttpSupport.string(raw, "dataType"))) {
                    continue;
                }
                PurchaseSnapshot doc = new PurchaseSnapshot();
                doc.setDocType(dataType);
                doc.setCode(HttpSupport.string(raw, "bizKey", "code"));
                doc.setRefCode(HttpSupport.string(raw, "refCode", "poCode"));
                doc.setSupplierCode(HttpSupport.string(raw, "supplierCode"));
                doc.setPlantCode(HttpSupport.string(raw, "plantCode"));
                doc.setStatus(HttpSupport.string(raw, "status"));
                doc.setSku(HttpSupport.string(raw, "sku", "skuCode"));
                doc.setQty(decimal(raw, "qty"));
                doc.setAmount(decimal(raw, "amount"));
                String expected = HttpSupport.string(raw, "expectedDate");
                if (expected != null && expected.length() >= 10) {
                    doc.setExpectedDate(LocalDate.parse(expected.substring(0, 10)));
                }
                fillLines(doc, raw);
                result.add(doc);
            }
            return result;
        } catch (IntegrationException ex) {
            return null;
        }
    }

    private List<SupplierScore> openScores() {
        if (!hasApiKey()) {
            return null;
        }
        try {
            List<SupplierScore> result = new ArrayList<>();
            for (Map<String, Object> raw : HttpEcosystemClient.snapshots(snapshot())) {
                if (!"SUPPLIER".equals(HttpSupport.string(raw, "dataType"))) {
                    continue;
                }
                SupplierScore score = new SupplierScore();
                score.setSupplierCode(HttpSupport.string(raw, "supplierCode", "bizKey"));
                score.setPeriod(HttpSupport.string(raw, "period"));
                if (score.getPeriod() == null || score.getPeriod().trim().isEmpty()) {
                    LocalDate today = LocalDate.now();
                    score.setPeriod(today.getYear() + "-" + String.format("%02d", today.getMonthValue()));
                }
                score.setAvgScore(decimal(raw, "avgScore", "amount", "qty"));
                score.setGrade(HttpSupport.string(raw, "grade"));
                result.add(score);
            }
            return result;
        } catch (IntegrationException ex) {
            return null;
        }
    }

    private Map<String, Object> snapshot() {
        if (cachedSnapshot == null) {
            cachedSnapshot = HttpSupport.getMap(
                    http, baseUrl + "/api/open/ir/snapshots", HttpSupport.apiKey(apiKey));
        }
        return cachedSnapshot;
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private Map<String, Object> findPurchaseRequisition(String code) {
        String url = baseUrl + "/api/sourcing/pr/page?current=1&size=20&keyword=" + encode(code);
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, headers()))) {
            if (code.equals(HttpSupport.string(row, "code"))) {
                return row;
            }
        }
        throw new IntegrationException("SRM 采购申请不存在: " + code);
    }

    private Map<String, Object> findPurchaseOrder(String code) {
        String url = baseUrl + "/api/purchase/order/page?current=1&size=20&keyword=" + encode(code);
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, headers()))) {
            if (code.equals(HttpSupport.string(row, "code"))) {
                return row;
            }
        }
        throw new IntegrationException("SRM 采购订单不存在: " + code);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            throw new IntegrationException("URL 编码失败: " + value);
        }
    }

    /** 拼接结果受列宽限制(VARCHAR(1024)),超出部分丢弃完整 sku 而非截断半个编码. */
    static String joinSkus(List<String> skus) {
        StringBuilder sb = new StringBuilder();
        for (String sku : skus) {
            int next = sb.length() + sku.length() + (sb.length() == 0 ? 0 : 1);
            if (next > PurchaseSnapshot.SKU_MAX_LENGTH) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(sku);
        }
        return sb.length() == 0 ? skus.get(0).substring(0,
                Math.min(skus.get(0).length(), PurchaseSnapshot.SKU_MAX_LENGTH)) : sb.toString();
    }

    /** 多行单据按整单聚合:sku 逗号拼接,数量/已收数量汇总. */
    private static void fillLines(PurchaseSnapshot doc, Map<String, Object> row) {
        Object lines = row.get("lines");
        if (lines instanceof List && !((List<?>) lines).isEmpty()) {
            List<String> skus = new ArrayList<>();
            BigDecimal qty = BigDecimal.ZERO;
            BigDecimal received = BigDecimal.ZERO;
            List<Map<String, Object>> lineRows = new ArrayList<>();
            for (Object item : (List<?>) lines) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> line = (Map<String, Object>) item;
                String sku = HttpSupport.string(line, "materialCode", "sku");
                if (sku != null && !skus.contains(sku)) {
                    skus.add(sku);
                }
                BigDecimal lineQty = decimal(line, "qty");
                BigDecimal lineReceived = decimal(line, "receivedQty");
                qty = qty.add(lineQty);
                received = received.add(lineReceived);
                Map<String, Object> lineRow = new LinkedHashMap<>();
                lineRow.put("sku", sku);
                lineRow.put("qty", lineQty);
                lineRow.put("receivedQty", lineReceived);
                lineRows.add(lineRow);
            }
            doc.setSku(skus.isEmpty() ? null : joinSkus(skus));
            try {
                doc.setLinesJson(OBJECT_MAPPER.writeValueAsString(lineRows));
            } catch (Exception ex) {
                throw new IntegrationException("采购单行解析失败", ex);
            }
            if (doc.getQty() == null) {
                doc.setQty(qty);
            }
            if (doc.getReceivedQty() == null) {
                doc.setReceivedQty(received);
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
            String url = baseUrl + path + "?current=" + page + "&size=" + size;
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
        Map<String, Object> response = HttpSupport.loginPost(
                http, baseUrl + "/api/auth/login", body);
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

    private static String dedicatedPath(String type) {
        if ("SRM_PURCHASE_SUGGEST".equals(type)) {
            return "/api/open/ir/purchase-suggest";
        }
        if ("SRM_SUBMIT_PR".equals(type)) {
            return "/api/open/ir/submit-pr";
        }
        if ("SRM_APPROVE_PR".equals(type)) {
            return "/api/open/ir/approve-pr";
        }
        if ("SRM_EXPEDITE_PO".equals(type)) {
            return "/api/open/ir/expedite-po";
        }
        return null;
    }
}
