package com.ir.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.WmsClient;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.WmsOrderSnapshot;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final String apiKey;
    private Map<String, Object> cachedSnapshot;

    public HttpWmsClient(RestTemplate http, String baseUrl, String username, String password) {
        this(http, baseUrl, username, password, null);
    }

    public HttpWmsClient(
            RestTemplate http,
            String baseUrl,
            String username,
            String password,
            String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
    }

    @Override
    public List<WmsOrderSnapshot> fetchOutbound() {
        List<Map<String, Object>> rows = snapshotList("outbound");
        if (rows == null) {
            rows = pages("/api/outbound/order/page");
        }
        List<WmsOrderSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            WmsOrderSnapshot order = new WmsOrderSnapshot();
            order.setCode(HttpSupport.string(row, "code", "orderNo", "orderCode"));
            order.setExternalNo(HttpSupport.string(row, "externalNo", "sourceNo"));
            order.setSku(HttpSupport.string(row, "sku", "skuCode"));
            order.setWarehouseCode(HttpSupport.string(row, "warehouseCode", "warehouse"));
            order.setStatus(HttpSupport.string(row, "status"));
            order.setTotalQty(decimal(row, "totalQty", "qty"));
            order.setPickedQty(decimal(row, "pickedQty"));
            order.setShippedQty(decimal(row, "shippedQty"));
            order.setCarrier(HttpSupport.string(row, "carrier", "carrierCode"));
            order.setTrackingNo(HttpSupport.string(row, "trackingNo"));
            order.setPackedAt(HttpSupport.dateTime(row, "packedAt"));
            order.setShippedAt(HttpSupport.dateTime(row, "shippedAt"));
            result.add(order);
        }
        return result;
    }

    @Override
    public List<InventorySnapshot> fetchInventorySummary() {
        List<Map<String, Object>> rows = snapshotList("inventory");
        if (rows == null) {
            Map<String, Object> response = HttpSupport.getMap(
                    http, baseUrl + "/api/inventory/summary", headers());
            rows = HttpSupport.rows(response);
        }
        List<InventorySnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            InventorySnapshot inventory = new InventorySnapshot();
            inventory.setSourceSystem("WMS");
            inventory.setWarehouseCode(HttpSupport.string(row, "warehouseCode", "warehouse"));
            inventory.setSku(HttpSupport.string(row, "sku", "skuCode", "itemCode"));
            inventory.setQtyOnHand(decimal(row, "qtyOnHand", "quantity", "qty"));
            inventory.setQtyReserved(decimal(row, "qtyReserved", "reserved", "allocatedQty"));
            inventory.setQtyAvailable(decimal(row, "qtyAvailable", "available", "availableQty"));
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
        Map<String, Object> body = openIrBody(command);
        if (hasApiKey()) {
            try {
                HttpSupport.postMap(http, baseUrl + "/api/open/ir/actions", body, HttpSupport.apiKey(apiKey));
            } catch (IntegrationException ex) {
                String path = dedicatedPath(command.getType());
                if (path == null || ex.isOutcomeUnknown()) {
                    throw ex;
                }
                HttpSupport.postMap(http, baseUrl + path, body, HttpSupport.apiKey(apiKey));
            }
            cachedSnapshot = null;
            return;
        }
        if ("WMS_ALLOCATE".equals(command.getType())) {
            long id = resolveOutboundId(command.getTargetKey());
            HttpSupport.postMap(http, baseUrl + "/api/outbound/order/" + id + "/allocate",
                    command.getParams(), headers());
        } else if ("WMS_REPLENISH".equals(command.getType())) {
            Map<String, Object> generate = new LinkedHashMap<>();
            if (command.getParams() != null) {
                generate.putAll(command.getParams());
            }
            String warehouse = first(
                    command.getParams() == null ? null : HttpSupport.string(command.getParams(), "warehouseCode"),
                    command.getTargetKey());
            String toWarehouse = toWmsWarehouse(warehouse);
            generate.put("warehouseCode", toWarehouse);
            Object from = command.getParams() == null ? null : command.getParams().get("fromWarehouseCode");
            String fromWarehouse = from == null ? null : toWmsWarehouse(String.valueOf(from));
            if (fromWarehouse != null && !fromWarehouse.equals(toWarehouse)) {
                Map<String, Object> transfer = new LinkedHashMap<>();
                transfer.put("fromWarehouseCode", fromWarehouse);
                transfer.put("warehouseCode", toWarehouse);
                if (command.getParams() != null) {
                    Object sku = command.getParams().get("sku");
                    Object qty = command.getParams().get("qty");
                    Object owner = command.getParams().get("ownerCode");
                    if (sku != null) {
                        transfer.put("sku", sku);
                    }
                    if (qty != null) {
                        transfer.put("qty", qty);
                    }
                    if (owner != null) {
                        transfer.put("ownerCode", owner);
                    }
                }
                HttpSupport.postMap(http, baseUrl + "/api/inventory/replenish/transfer",
                        transfer, headers());
                return;
            }
            if (command.getIdempotencyKey() != null) {
                generate.put("requestNo", command.getIdempotencyKey());
            }
            HttpSupport.postMap(http, baseUrl + "/api/inventory/replenish/generate",
                    generate, headers());
        }
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

    private List<Map<String, Object>> snapshotList(String name) {
        if (!hasApiKey()) {
            return null;
        }
        try {
            return HttpSupport.namedList(snapshot(), name);
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

    private List<Map<String, Object>> pages(String path) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int page = 1;
        int size = 200;
        while (true) {
            List<Map<String, Object>> current = HttpSupport.rows(HttpSupport.getMap(
                    http, baseUrl + path + "?current=" + page + "&size=" + size, headers()));
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
            throw new IntegrationException("WMS 登录未返回 token");
        }
        return token;
    }

    private HttpHeaders headers() {
        return HttpSupport.bearer(login());
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private long resolveOutboundId(String key) {
        if (key != null && key.matches("\\d+")) {
            return Long.parseLong(key);
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IntegrationException("WMS 分配缺少出库单号");
        }
        String url = baseUrl + "/api/outbound/order/page?current=1&size=20&keyword=" + encode(key.trim());
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, headers()))) {
            if (key.equals(HttpSupport.string(row, "code", "orderCode", "orderNo"))
                    || key.equals(HttpSupport.string(row, "externalNo", "sourceNo"))) {
                long id = HttpSupport.longValue(row, "id");
                if (id > 0) {
                    return id;
                }
            }
        }
        throw new IntegrationException("WMS 出库单不存在: " + key);
    }

    static String toWmsWarehouse(String code) {
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

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static Map<String, Object> openIrBody(ActionCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", command.getType());
        body.put("targetKey", command.getTargetKey());
        body.put("orderCode", command.getTargetKey());
        body.put("warehouseCode", command.getTargetKey());
        body.put("params", command.getParams());
        if (command.getParams() != null) {
            copyParam(body, command.getParams(), "warehouseCode");
            copyParam(body, command.getParams(), "fromWarehouseCode");
            copyParam(body, command.getParams(), "sku");
            copyParam(body, command.getParams(), "qty");
            copyParam(body, command.getParams(), "ownerCode");
        }
        HttpSupport.putIdempotency(body, command);
        return body;
    }

    private static void copyParam(Map<String, Object> body, Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value != null) {
            body.put(name, value);
        }
    }

    private static String dedicatedPath(String type) {
        if ("WMS_ALLOCATE".equals(type)) {
            return "/api/open/ir/allocate";
        }
        if ("WMS_REPLENISH".equals(type)) {
            return "/api/open/ir/replenish";
        }
        return null;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            throw new IntegrationException("URL 编码失败: " + value);
        }
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }
}
