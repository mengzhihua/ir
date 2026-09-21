package com.ir.integration.http;

import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.SapClient;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.entity.InventorySnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpSapClient implements SapClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String apiKey;
    private volatile String token;

    public HttpSapClient(RestTemplate http, String baseUrl, String username, String password) {
        this(http, baseUrl, username, password, null);
    }

    public HttpSapClient(RestTemplate http, String baseUrl, String username, String password, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
    }

    @Override
    public List<InventorySnapshot> fetchStock() {
        if (hasApiKey()) {
            List<InventorySnapshot> result = new ArrayList<>();
            for (Map<String, Object> row : HttpEcosystemClient.snapshots(
                    HttpSupport.getMap(http, baseUrl + "/api/open/ir/snapshots",
                            HttpSupport.apiKey(apiKey)))) {
                if (!"STOCK".equals(HttpSupport.string(row, "dataType"))) {
                    continue;
                }
                InventorySnapshot item = new InventorySnapshot();
                item.setSourceSystem("SAP");
                item.setWarehouseCode(HttpSupport.string(row, "plantCode", "werks"));
                item.setSku(HttpSupport.string(row, "sku", "matnr", "materialCode"));
                BigDecimal qty = BigDecimal.valueOf(HttpSupport.doubleValue(row, "qty", "unrestrictedQty"));
                item.setQtyOnHand(qty);
                item.setQtyReserved(BigDecimal.ZERO);
                item.setQtyAvailable(qty);
                item.setSafetyQty("LOW".equals(HttpSupport.string(row, "status"))
                        ? BigDecimal.TEN : BigDecimal.ZERO);
                result.add(item);
            }
            return result;
        }
        List<InventorySnapshot> result = new ArrayList<>();
        Map<String, Object> response = HttpSupport.getMap(http, baseUrl + "/api/mm/stock", headers());
        for (Map<String, Object> row : HttpSupport.rows(response)) {
            InventorySnapshot item = new InventorySnapshot();
            item.setSourceSystem("SAP");
            item.setWarehouseCode(HttpSupport.string(row, "werks", "plantCode"));
            item.setSku(HttpSupport.string(row, "matnr", "materialCode"));
            BigDecimal qty = BigDecimal.valueOf(HttpSupport.doubleValue(row, "unrestrictedQty", "qty"));
            item.setQtyOnHand(qty);
            item.setQtyReserved(BigDecimal.ZERO);
            item.setQtyAvailable(qty);
            item.setSafetyQty(BigDecimal.ZERO);
            result.add(item);
        }
        return result;
    }

    @Override
    public List<FinanceSnapshot> fetchFinance() {
        if (hasApiKey()) {
            return financeFromSnapshots(HttpEcosystemClient.snapshots(
                    HttpSupport.getMap(http, baseUrl + "/api/open/ir/snapshots",
                            HttpSupport.apiKey(apiKey))));
        }
        List<FinanceSnapshot> result = new ArrayList<>();
        result.add(sum("AP_OPEN", "/api/fi/ap/open-items"));
        result.add(sum("AR_OPEN", "/api/fi/ar/open-items"));
        Map<String, Object> summary = dashboard();
        result.add(value("STOCK_VALUE", summary, "stockValue"));
        result.add(value("MONTH_COST", summary, "monthCost"));
        result.add(value("MONTH_REVENUE", summary, "monthRevenue"));
        return result;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> response = HttpSupport.getMap(
                http, baseUrl + "/api/dashboard/summary", headers());
        Object data = response.get("data");
        return data instanceof Map ? (Map<String, Object>) data : Collections.emptyMap();
    }

    @Override
    public boolean health() {
        try {
            if (hasApiKey()) {
                HttpSupport.getMap(http, baseUrl + "/api/open/ir/snapshots", HttpSupport.apiKey(apiKey));
                return true;
            }
            dashboard();
            return true;
        } catch (IntegrationException ex) {
            return false;
        }
    }

    private static List<FinanceSnapshot> financeFromSnapshots(List<Map<String, Object>> rows) {
        List<FinanceSnapshot> result = new ArrayList<>();
        result.add(sumByType(rows, "AP_OPEN", "AP_OPEN"));
        result.add(sumByType(rows, "AR_OPEN", "AR_OPEN"));
        result.add(sumByType(rows, "STOCK", "STOCK_VALUE"));
        return result;
    }

    private static FinanceSnapshot sumByType(List<Map<String, Object>> rows, String dataType, String metric) {
        double total = 0;
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (dataType.equals(HttpSupport.string(row, "dataType"))) {
                total += HttpSupport.doubleValue(row, "amount", "dmbtr", "wrbtr", "stockValue");
                count++;
            }
        }
        FinanceSnapshot snapshot = new FinanceSnapshot();
        snapshot.setMetric(metric);
        snapshot.setDimension("ALL");
        snapshot.setAmount(BigDecimal.valueOf(total));
        snapshot.setItemCount(count);
        return snapshot;
    }

    private FinanceSnapshot sum(String metric, String path) {
        List<Map<String, Object>> rows = HttpSupport.rows(
                HttpSupport.getMap(http, baseUrl + path, headers()));
        double total = 0;
        for (Map<String, Object> row : rows) {
            total += HttpSupport.doubleValue(row, "amount", "dmbtr", "wrbtr");
        }
        FinanceSnapshot snapshot = new FinanceSnapshot();
        snapshot.setMetric(metric);
        snapshot.setDimension("ALL");
        snapshot.setAmount(BigDecimal.valueOf(total));
        snapshot.setItemCount(rows.size());
        return snapshot;
    }

    private static FinanceSnapshot value(String metric, Map<String, Object> summary, String key) {
        FinanceSnapshot snapshot = new FinanceSnapshot();
        snapshot.setMetric(metric);
        snapshot.setDimension("ALL");
        snapshot.setAmount(BigDecimal.valueOf(HttpSupport.doubleValue(summary, key)));
        snapshot.setItemCount(0);
        return snapshot;
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
            throw new IntegrationException("SAP 登录未返回 token");
        }
        return token;
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
