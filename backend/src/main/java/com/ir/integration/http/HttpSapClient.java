package com.ir.integration.http;

import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.SapClient;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.InventorySnapshot;
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
    private volatile String token;

    public HttpSapClient(RestTemplate http, String baseUrl, String username, String password) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
    }

    @Override
    public List<InventorySnapshot> fetchStock() {
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
            dashboard();
            return true;
        } catch (IntegrationException ex) {
            return false;
        }
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
            throw new IntegrationException("SAP 登录未返回 token");
        }
        return token;
    }
}
