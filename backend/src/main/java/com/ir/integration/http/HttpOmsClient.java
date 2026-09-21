package com.ir.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.OmsClient;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.SalesPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpOmsClient implements OmsClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String username;
    private final String password;
    private volatile String token;

    private final String apiKey;
    private Map<String, Object> cachedSnapshot;

    public HttpOmsClient(RestTemplate http, String baseUrl, String username, String password) {
        this(http, baseUrl, username, password, null);
    }

    public HttpOmsClient(
            RestTemplate http,
            String baseUrl,
            String username,
            String password,
            String apiKey) {
        this.http = http;
        this.baseUrl = trim(baseUrl);
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
    }

    @Override
    public List<OrderSnapshot> fetchOrders() {
        List<Map<String, Object>> rows = snapshotList("orders");
        if (rows == null) {
            rows = pages("/api/order/page");
        }
        List<OrderSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            OrderSnapshot order = new OrderSnapshot();
            order.setOrderNo(HttpSupport.string(row, "orderNo", "orderSn", "orderCode"));
            order.setSku(HttpSupport.string(row, "sku", "skuCode"));
            order.setChannelCode(HttpSupport.string(row, "channelCode", "channel"));
            order.setShopCode(HttpSupport.string(row, "shopCode", "shopName"));
            order.setWarehouseCode(HttpSupport.string(row, "warehouseCode"));
            order.setProvince(HttpSupport.string(row, "province"));
            order.setCity(HttpSupport.string(row, "city"));
            order.setStatus(HttpSupport.string(row, "status"));
            order.setPriority(integer(row, "priority"));
            order.setPayAmount(decimal(row, "payAmount", "amount"));
            order.setFreight(decimal(row, "freight"));
            order.setQty(decimal(row, "qty", "totalQty"));
            order.setOrderTime(HttpSupport.dateTime(row, "orderTime", "createTime"));
            order.setPayTime(HttpSupport.dateTime(row, "payTime"));
            order.setShipTime(HttpSupport.dateTime(row, "shipTime", "shippedAt"));
            order.setCompleteTime(HttpSupport.dateTime(row, "completeTime", "completedAt"));
            order.setCarrierCode(HttpSupport.string(row, "carrierCode", "carrier"));
            order.setTrackingNo(HttpSupport.string(row, "trackingNo", "logisticsNo"));
            result.add(order);
        }
        return result;
    }

    @Override
    public List<InventorySnapshot> fetchInventory() {
        List<Map<String, Object>> rows = snapshotList("inventory");
        if (rows == null) {
            rows = pages("/api/inventory/page");
        }
        List<InventorySnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            InventorySnapshot inventory = new InventorySnapshot();
            inventory.setSourceSystem("OMS");
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
    public List<SalesPoint> fetchDailySales(int days) {
        List<Map<String, Object>> rows = snapshotList("sales");
        if (rows == null) {
            String url = baseUrl + "/api/report/order-daily?days=" + days;
            rows = HttpSupport.rows(HttpSupport.getMap(http, url, headers()));
        }
        List<SalesPoint> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = HttpSupport.localDate(row, "salesDate", "date", "orderDay", "order_day");
            String sku = HttpSupport.string(row, "sku", "skuCode");
            if (date == null || sku == null) {
                continue;
            }
            SalesPoint point = new SalesPoint();
            point.setSalesDate(date);
            point.setSku(sku);
            point.setWarehouseCode(HttpSupport.string(row, "warehouseCode", "warehouse"));
            point.setChannelCode(HttpSupport.string(row, "channelCode", "channel"));
            point.setQty(decimal(row, "qty", "quantity"));
            point.setAmount(decimal(row, "amount", "salesAmount"));
            result.add(point);
        }
        return result;
    }

    @Override
    public Map<String, Object> dashboard() {
        return HttpSupport.getMap(http, baseUrl + "/api/dashboard", headers());
    }

    @Override
    public void execute(ActionCommand command) {
        if (hasApiKey()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", command.getType());
            body.put("targetKey", command.getTargetKey());
            body.put("params", command.getParams());
            HttpSupport.putIdempotency(body, command);
            HttpSupport.postMap(http, baseUrl + "/api/open/ir/actions", body, HttpSupport.apiKey(apiKey));
            cachedSnapshot = null;
            return;
        }
        String orderNo = command.getTargetKey();
        String path;
        if ("OMS_HOLD".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/hold";
        } else if ("OMS_UNHOLD".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/unhold";
        } else if ("OMS_REROUTE_WAREHOUSE".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/reroute";
        } else if ("OMS_AUTO_PROCESS".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/auto";
        } else if ("OMS_CANCEL".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/cancel";
        } else if ("OMS_PRIORITIZE".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/remark";
        } else {
            return;
        }
        HttpSupport.postMap(http, baseUrl + path, command.getParams(), headers());
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
            String url = baseUrl + path + "?current=" + page + "&size=" + size;
            List<Map<String, Object>> current =
                    HttpSupport.rows(HttpSupport.getMap(http, url, headers()));
            rows.addAll(current);
            if (current.size() < size) {
                return rows;
            }
            page++;
        }
    }

    private HttpHeaders headers() {
        return HttpSupport.bearer(login());
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
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
            throw new IntegrationException("OMS 登录未返回 token");
        }
        return token;
    }

    private static Integer integer(Map<String, Object> row, String... names) {
        for (String name : names) {
            Object value = row.get(name);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                try {
                    return Integer.parseInt(String.valueOf(value).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }

    private static String trim(String value) {
        return value == null ? "" : value.replaceAll("/$", "");
    }
}
