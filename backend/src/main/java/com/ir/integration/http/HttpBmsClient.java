package com.ir.integration.http;

import com.ir.integration.client.BmsClient;
import com.ir.integration.client.IntegrationException;
import com.ir.snapshot.CostRecord;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

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
        String url = baseUrl + "/api/open/cost/records?from=" + from + "&to=" + to;
        HttpHeaders headers = HttpSupport.apiKey(apiKey);
        List<CostRecord> result = new ArrayList<>();
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, headers))) {
            CostRecord cost = new CostRecord();
            cost.setBizDate(LocalDate.parse(HttpSupport.string(row, "bizDate", "date")));
            cost.setOrderNo(HttpSupport.string(row, "orderNo"));
            cost.setWarehouseCode(HttpSupport.string(row, "warehouseCode"));
            cost.setCarrierCode(HttpSupport.string(row, "carrierCode"));
            cost.setCostType(HttpSupport.string(row, "costType"));
            cost.setAmount(java.math.BigDecimal.valueOf(HttpSupport.doubleValue(row, "amount")));
            cost.setSourceSystem("BMS");
            cost.setRemark(HttpSupport.string(row, "remark"));
            result.add(cost);
        }
        return result;
    }

    @Override
    public boolean health() {
        try {
            fetchCosts(LocalDate.now().minusDays(1), LocalDate.now());
            return true;
        } catch (IntegrationException ex) {
            return false;
        }
    }
}
