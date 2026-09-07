package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.OmsClient;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.SalesPoint;
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

public class HttpOmsClient implements OmsClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String username;
    private final String password;
    private volatile String token;

    public HttpOmsClient(RestTemplate http, String baseUrl, String username, String password) {
        this.http = http;
        this.baseUrl = trim(baseUrl);
        this.username = username;
        this.password = password;
    }

    @Override
    public List<OrderSnapshot> fetchOrders() {
        List<OrderSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/order/page")) {
            OrderSnapshot order = new OrderSnapshot();
            order.setOrderNo(HttpSupport.string(row, "orderNo", "orderSn", "orderCode"));
            order.setChannelCode(HttpSupport.string(row, "channelCode", "channel"));
            order.setShopCode(HttpSupport.string(row, "shopCode", "shopName"));
            order.setWarehouseCode(HttpSupport.string(row, "warehouseCode"));
            order.setProvince(HttpSupport.string(row, "province"));
            order.setCity(HttpSupport.string(row, "city"));
            order.setStatus(HttpSupport.string(row, "status"));
            order.setPayAmount(decimal(row, "payAmount", "amount"));
            order.setFreight(decimal(row, "freight"));
            order.setQty(decimal(row, "qty", "totalQty"));
            order.setOrderTime(dateTime(row, "orderTime", "createTime"));
            order.setPayTime(dateTime(row, "payTime"));
            order.setShipTime(dateTime(row, "shipTime"));
            order.setCompleteTime(dateTime(row, "completeTime"));
            order.setCarrierCode(HttpSupport.string(row, "carrierCode", "carrier"));
            order.setTrackingNo(HttpSupport.string(row, "trackingNo", "logisticsNo"));
            result.add(order);
        }
        return result;
    }

    @Override
    public List<InventorySnapshot> fetchInventory() {
        List<InventorySnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/inventory/page")) {
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
        String url = baseUrl + "/api/report/order-daily?days=" + days;
        List<SalesPoint> result = new ArrayList<>();
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, headers()))) {
            SalesPoint point = new SalesPoint();
            point.setSalesDate(LocalDate.parse(HttpSupport.string(row, "salesDate", "date")));
            point.setSku(HttpSupport.string(row, "sku", "skuCode"));
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
        String orderNo = command.getTargetKey();
        String path;
        if ("OMS_HOLD".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/hold";
        } else if ("OMS_UNHOLD".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/unhold";
        } else if ("OMS_REROUTE_WAREHOUSE".equals(command.getType())) {
            path = "/api/order/" + orderNo + "/allocate";
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
            String url = baseUrl + path + "?page=" + page + "&size=" + size;
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
            throw new IntegrationException("OMS 登录未返回 token");
        }
        return token;
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String... names) {
        String value = HttpSupport.string(row, names);
        return value == null ? null : LocalDateTime.parse(value.replace(" ", "T"));
    }

    private static String trim(String value) {
        return value == null ? "" : value.replaceAll("/$", "");
    }
}
