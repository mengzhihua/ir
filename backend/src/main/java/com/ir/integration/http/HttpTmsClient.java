package com.ir.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.TmsClient;
import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.entity.ShipmentSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpTmsClient implements TmsClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String apiKey;
    private Map<String, Object> cachedSnapshot;

    public HttpTmsClient(RestTemplate http, String baseUrl) {
        this(http, baseUrl, null);
    }

    public HttpTmsClient(RestTemplate http, String baseUrl, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
    }

    @Override
    public List<ShipmentSnapshot> fetchWaybills() {
        List<Map<String, Object>> rows = snapshotList("waybills");
        if (rows == null) {
            rows = pages("/api/waybill/page");
        }
        List<ShipmentSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ShipmentSnapshot shipment = new ShipmentSnapshot();
            shipment.setWaybillCode(HttpSupport.string(row, "waybillCode", "waybillNo", "code"));
            shipment.setSourceNo(HttpSupport.string(row, "sourceNo", "sourceOrderNo"));
            shipment.setCarrierCode(HttpSupport.string(row, "carrierCode", "carrier"));
            shipment.setStatus(HttpSupport.string(row, "status"));
            shipment.setFromSiteCode(HttpSupport.string(row, "fromSiteCode", "fromSite"));
            shipment.setPlannedArriveTime(HttpSupport.dateTime(row, "plannedArriveTime", "planArriveTime"));
            shipment.setActualArriveTime(HttpSupport.dateTime(row, "actualArriveTime", "arriveTime"));
            shipment.setFreightAmount(decimal(row, "freightAmount", "amount"));
            String flag = HttpSupport.string(row, "exceptionFlag");
            shipment.setExceptionFlag(flag != null && Boolean.parseBoolean(flag));
            result.add(shipment);
        }
        return result;
    }

    @Override
    public List<CostRecord> fetchFreightBills() {
        List<Map<String, Object>> rows = snapshotList("bills");
        if (rows == null) {
            rows = pages("/api/billing/page");
        }
        List<CostRecord> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = HttpSupport.localDate(row, "bizDate", "billingDate", "createdAt");
            if (date == null) {
                continue;
            }
            CostRecord cost = new CostRecord();
            cost.setBizDate(date);
            cost.setOrderNo(HttpSupport.string(row, "orderNo", "sourceNo", "orderCode"));
            cost.setCarrierCode(HttpSupport.string(row, "carrierCode", "carrier"));
            cost.setCostType("FREIGHT");
            cost.setAmount(decimal(row, "freightAmount", "amount"));
            cost.setSourceSystem("TMS");
            result.add(cost);
        }
        return result;
    }

    @Override
    public Map<String, Object> dashboard() {
        return HttpSupport.getMap(http, baseUrl + "/api/dashboard", authHeaders());
    }

    @Override
    public void execute(ActionCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", command.getType());
        body.put("targetKey", command.getTargetKey());
        body.put("params", command.getParams());
        HttpSupport.postMap(http, baseUrl + "/api/open/ir/actions", body, authHeaders());
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
            cachedSnapshot = HttpSupport.getMap(http, baseUrl + "/api/open/ir/snapshots", authHeaders());
        }
        return cachedSnapshot;
    }

    private List<Map<String, Object>> pages(String path) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int page = 1;
        int size = 200;
        while (true) {
            List<Map<String, Object>> current = HttpSupport.rows(HttpSupport.getMap(
                    http, baseUrl + path + "?current=" + page + "&size=" + size, authHeaders()));
            rows.addAll(current);
            if (current.size() < size) {
                return rows;
            }
            page++;
        }
    }

    private HttpHeaders authHeaders() {
        return hasApiKey() ? HttpSupport.apiKey(apiKey) : new HttpHeaders();
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }
}
