package com.ir.integration.http;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.SrmClient;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

public class HttpSrmClient implements SrmClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String apiKey;

    public HttpSrmClient(RestTemplate http, String baseUrl, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
    }

    @Override
    public void execute(ActionCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", command.getType());
        body.put("targetKey", command.getTargetKey());
        body.put("params", command.getParams());
        HttpSupport.postMap(http, baseUrl + "/api/open/ir/actions", body, HttpSupport.apiKey(apiKey));
    }

    @Override
    public boolean health() {
        try {
            http.getForEntity(baseUrl + "/api/auth/login", String.class);
            return true;
        } catch (HttpStatusCodeException ex) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
