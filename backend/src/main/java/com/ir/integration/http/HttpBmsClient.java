package com.ir.integration.http;

import com.ir.integration.client.BmsClient;
import com.ir.snapshot.CostRecord;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class HttpBmsClient implements BmsClient {
    private final RestTemplate http; private final String baseUrl;
    public HttpBmsClient(RestTemplate http, String baseUrl) { this.http = http; this.baseUrl = baseUrl; }
    public List<CostRecord> fetchCosts(LocalDate from, LocalDate to) { return Collections.emptyList(); }
    public boolean health() { try { http.getForObject(baseUrl + "/api/open/cost/records", Object.class); return true; } catch (Exception e) { return false; } }
}
