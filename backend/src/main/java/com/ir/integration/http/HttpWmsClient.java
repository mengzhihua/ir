package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.WmsClient;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.WmsOrderSnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpWmsClient implements WmsClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String username;
    private final String password;
    private volatile String token;

    public HttpWmsClient(RestTemplate http, String baseUrl, String username, String password) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
    }

    @Override
    public List<WmsOrderSnapshot> fetchOutbound() {
        List<WmsOrderSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/outbound/order/page")) {
            WmsOrderSnapshot order = new WmsOrderSnapshot();
            order.setCode(HttpSupport.string(row, "code", "orderNo", "orderCode"));
            order.setExternalNo(HttpSupport.string(row, "externalNo", "sourceNo"));
            order.setWarehouseCode(HttpSupport.string(row, "warehouseCode", "warehouse"));
            order.setStatus(HttpSupport.string(row, "status"));
            order.setTotalQty(decimal(row, "totalQty", "qty"));
            order.setPickedQty(decimal(row, "pickedQty"));
            order.setShippedQty(decimal(row, "shippedQty"));
            order.setCarrier(HttpSupport.string(row, "carrier", "carrierCode"));
            order.setTrackingNo(HttpSupport.string(row, "trackingNo"));
            order.setPackedAt(dateTime(row, "packedAt"));
            order.setShippedAt(dateTime(row, "shippedAt"));
            result.add(order);
        }
        return result;
    }

    @Override
    public List<InventorySnapshot> fetchInventorySummary() {
        List<InventorySnapshot> result = new ArrayList<>();
        Map<String, Object> response = HttpSupport.getMap(
                http, baseUrl + "/api/inventory/summary", headers());
        for (Map<String, Object> row : HttpSupport.rows(response)) {
            InventorySnapshot inventory = new InventorySnapshot();
            inventory.setSourceSystem("WMS");
            inventory.setWarehouseCode(HttpSupport.string(row, "warehouseCode", "warehouse"));
            inventory.setSku(HttpSupport.string(row, "sku", "skuCode"));
            inventory.setQtyOnHand(decimal(row, "qtyOnHand", "quantity"));
            inventory.setQtyReserved(decimal(row, "qtyReserved", "reserved"));
            inventory.setQtyAvailable(decimal(row, "qtyAvailable", "available"));
            inventory.setSafetyQty(decimal(row, "safetyQty", "safeStock"));
            result.add(inventory);
        }
        return result;
    }

    @Override
    public Map<String, Object> dashboard() {
        return HttpSupport.getMap(http, baseUrl + "/api/dashboard", headers());
    }

    @Override
    public void execute(ActionCommand command) {
        if ("WMS_ALLOCATE".equals(command.getType())) {
            HttpSupport.postMap(http, baseUrl + "/api/outbound/order/"
                    + command.getTargetKey() + "/allocate", command.getParams(), headers());
        } else if ("WMS_REPLENISH".equals(command.getType())) {
            HttpSupport.postMap(http, baseUrl + "/api/inventory/replenish/generate",
                    command.getParams(), headers());
        }
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

    private List<Map<String, Object>> pages(String path) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int page = 1;
        int size = 200;
        while (true) {
            List<Map<String, Object>> current = HttpSupport.rows(HttpSupport.getMap(
                    http, baseUrl + path + "?page=" + page + "&size=" + size, headers()));
            rows.addAll(current);
            if (current.size() < size) {
                return rows;
            }
            page++;
        }
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
            throw new IntegrationException("WMS 登录未返回 token");
        }
        return token;
    }

    private HttpHeaders headers() {
        return HttpSupport.bearer(login());
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String... names) {
        String value = HttpSupport.string(row, names);
        return value == null ? null : LocalDateTime.parse(value.replace(" ", "T"));
    }
}
