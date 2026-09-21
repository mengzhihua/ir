package com.ir.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import com.ir.integration.client.BmsClient;
import com.ir.integration.client.IntegrationException;
import com.ir.snapshot.entity.CostRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpBmsClient implements BmsClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String apiKey;

    public HttpBmsClient(RestTemplate http, String baseUrl, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
    }

    @Override
    public List<CostRecord> fetchCosts(LocalDate from, LocalDate to) {
        if (hasApiKey()) {
            try {
                List<CostRecord> rows = parse(openIrCosts(from, to));
                if (!rows.isEmpty()) {
                    return rows;
                }
            } catch (IntegrationException ignored) {
                // 旧版 BMS 只有 /api/open/cost/records
            }
        }
        String url = baseUrl + "/api/open/cost/records?from=" + from + "&to=" + to;
        return parse(HttpSupport.rows(HttpSupport.getMap(http, url, headers())));
    }

    @Override
    public boolean health() {
        try {
            if (hasApiKey()) {
                HttpSupport.getMap(http, baseUrl + "/api/open/ir/snapshots", headers());
                return true;
            }
            fetchCosts(LocalDate.now().minusDays(1), LocalDate.now());
            return true;
        } catch (IntegrationException ex) {
            return false;
        }
    }

    private List<Map<String, Object>> openIrCosts(LocalDate from, LocalDate to) {
        Map<String, Object> response = HttpSupport.getMap(
                http, baseUrl + "/api/open/ir/snapshots?from=" + from + "&to=" + to, headers());
        List<Map<String, Object>> costs = HttpSupport.namedList(response, "costs");
        if (costs != null && !costs.isEmpty() && costs.get(0).get("orderNo") != null) {
            return costs;
        }
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Map<String, Object> row : HttpEcosystemClient.snapshots(response)) {
            if ("COST".equals(HttpSupport.string(row, "dataType"))) {
                snapshots.add(row);
            }
        }
        return snapshots;
    }

    private List<CostRecord> parse(List<Map<String, Object>> rows) {
        List<CostRecord> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            CostRecord cost = new CostRecord();
            cost.setBizDate(HttpSupport.localDate(row, "bizDate", "date"));
            cost.setOrderNo(HttpSupport.string(row, "orderNo"));
            cost.setWarehouseCode(HttpSupport.string(row, "warehouseCode", "plantCode"));
            cost.setCarrierCode(HttpSupport.string(row, "carrierCode"));
            String costType = HttpSupport.string(row, "costType", "sku");
            cost.setCostType("TRANSPORT".equals(costType) ? "FREIGHT" : costType);
            cost.setAmount(java.math.BigDecimal.valueOf(HttpSupport.doubleValue(row, "amount")));
            cost.setSourceSystem("BMS");
            cost.setRemark(HttpSupport.string(row, "remark", "title"));
            result.add(cost);
        }
        return result;
    }

    private HttpHeaders headers() {
        return HttpSupport.apiKey(apiKey);
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
