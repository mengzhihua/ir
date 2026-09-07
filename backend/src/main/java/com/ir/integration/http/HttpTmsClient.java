package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.TmsClient;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.ShipmentSnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpTmsClient implements TmsClient {
    private final RestTemplate http;
    private final String baseUrl;

    public HttpTmsClient(RestTemplate http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    @Override
    public List<ShipmentSnapshot> fetchWaybills() {
        List<ShipmentSnapshot> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/waybill/page")) {
            ShipmentSnapshot shipment = new ShipmentSnapshot();
            shipment.setWaybillCode(HttpSupport.string(row, "waybillCode", "waybillNo", "code"));
            shipment.setSourceNo(HttpSupport.string(row, "sourceNo", "sourceOrderNo"));
            shipment.setCarrierCode(HttpSupport.string(row, "carrierCode", "carrier"));
            shipment.setStatus(HttpSupport.string(row, "status"));
            shipment.setFromSiteCode(HttpSupport.string(row, "fromSiteCode", "fromSite"));
            shipment.setPlannedArriveTime(dateTime(row, "plannedArriveTime", "planArriveTime"));
            shipment.setActualArriveTime(dateTime(row, "actualArriveTime", "arriveTime"));
            shipment.setFreightAmount(decimal(row, "freightAmount", "amount"));
            shipment.setExceptionFlag(Boolean.valueOf(HttpSupport.string(row, "exceptionFlag")));
            result.add(shipment);
        }
        return result;
    }

    @Override
    public List<CostRecord> fetchFreightBills() {
        List<CostRecord> result = new ArrayList<>();
        for (Map<String, Object> row : pages("/api/billing/page")) {
            CostRecord cost = new CostRecord();
            cost.setBizDate(java.time.LocalDate.parse(HttpSupport.string(row, "bizDate", "billingDate")));
            cost.setOrderNo(HttpSupport.string(row, "orderNo", "sourceNo"));
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
        return HttpSupport.getMap(http, baseUrl + "/api/dashboard", new HttpHeaders());
    }

    @Override
    public void execute(ActionCommand command) {
        String suffix = "/api/waybill/" + command.getTargetKey();
        if ("TMS_DISPATCH".equals(command.getType())) {
            suffix += "/dispatch";
        } else if ("TMS_SYNC_TRACK".equals(command.getType())) {
            suffix += "/sync-track";
        } else if ("TMS_SWITCH_CARRIER".equals(command.getType())) {
            suffix += "/dispatch";
        } else {
            return;
        }
        HttpSupport.postMap(http, baseUrl + suffix, command.getParams(), new HttpHeaders());
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
                    http, baseUrl + path + "?page=" + page + "&size=" + size, new HttpHeaders()));
            rows.addAll(current);
            if (current.size() < size) {
                return rows;
            }
            page++;
        }
    }

    private static BigDecimal decimal(Map<String, Object> row, String... names) {
        return BigDecimal.valueOf(HttpSupport.doubleValue(row, names));
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String... names) {
        String value = HttpSupport.string(row, names);
        return value == null ? null : LocalDateTime.parse(value.replace(" ", "T"));
    }
}
