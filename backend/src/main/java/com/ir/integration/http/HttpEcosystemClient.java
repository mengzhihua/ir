package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.EcosystemClient;
import com.ir.integration.client.IntegrationException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpEcosystemClient implements EcosystemClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String apiKey;

    public HttpEcosystemClient(RestTemplate http, String baseUrl, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
    }

    @Override
    public List<Map<String, Object>> fetchSnapshots() {
        Map<String, Object> response = HttpSupport.getMap(
                http, baseUrl + "/api/open/ir/snapshots", headers());
        return snapshots(response);
    }

    @Override
    public void execute(ActionCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", command.getType());
        body.put("targetKey", command.getTargetKey());
        body.put("params", command.getParams());
        HttpSupport.putIdempotency(body, command);
        if (command.getParams() != null) {
            copyParam(body, command.getParams(), "invoiceNo");
            copyParam(body, command.getParams(), "requestNo");
            copyParam(body, command.getParams(), "orderNo");
        }
        try {
            HttpSupport.postMap(http, baseUrl + "/api/open/ir/actions", body, headers());
        } catch (IntegrationException ex) {
            String path = dedicatedPath(command.getType());
            if (path == null || ex.isOutcomeUnknown()) {
                throw ex;
            }
            HttpSupport.postMap(http, baseUrl + path, body, headers());
        }
    }

    @Override
    public boolean health() {
        try {
            HttpSupport.getMap(http, baseUrl + "/api/open/ir/snapshots", headers());
            return true;
        } catch (Exception ex) {
            try {
                http.getForEntity(baseUrl + "/api/auth/login", String.class);
                return true;
            } catch (HttpStatusCodeException status) {
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private HttpHeaders headers() {
        return HttpSupport.apiKey(apiKey);
    }

    private static void copyParam(Map<String, Object> body, Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value != null) {
            body.put(name, value);
        }
    }

    private static String dedicatedPath(String type) {
        if ("INV_VERIFY_INPUT".equals(type)) {
            return "/api/open/ir/verify-input";
        }
        if ("INV_SUBMIT_REQUEST".equals(type)) {
            return "/api/open/ir/submit-request";
        }
        if ("INV_APPROVE_REQUEST".equals(type)) {
            return "/api/open/ir/approve-request";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> snapshots(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof Map) {
            Object rows = ((Map<String, Object>) data).get("snapshots");
            if (rows instanceof List) {
                return (List<Map<String, Object>>) rows;
            }
        }
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return new ArrayList<>(Collections.emptyList());
    }
}
