package com.ir.integration.http;

import com.ir.integration.client.OmsClient;
import com.ir.integration.client.ActionCommand;
import com.ir.snapshot.*;
import org.springframework.web.client.RestTemplate;
import java.util.*;

public class HttpOmsClient implements OmsClient {
    private final RestTemplate http; private final String baseUrl;
    public HttpOmsClient(RestTemplate http, String baseUrl) { this.http = http; this.baseUrl = baseUrl; }
    public List<OrderSnapshot> fetchOrders() { return Collections.emptyList(); }
    public List<InventorySnapshot> fetchInventory() { return Collections.emptyList(); }
    public List<SalesPoint> fetchDailySales(int days) { return Collections.emptyList(); }
    public Map<String,Object> dashboard() { return http.getForObject(baseUrl + "/api/dashboard", Map.class); }
    public void execute(ActionCommand c) { }
    public boolean health() { try { http.getForObject(baseUrl + "/api/dashboard", Object.class); return true; } catch (Exception e) { return false; } }
}
