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
    private final String username;
    private final String password;
    private String token;

    public HttpEcosystemClient(RestTemplate http, String baseUrl, String apiKey) {
        this(http, baseUrl, apiKey, null, null);
    }

    public HttpEcosystemClient(
            RestTemplate http, String baseUrl, String apiKey, String username, String password) {
        this.http = http;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
    }

    @Override
    public List<Map<String, Object>> fetchSnapshots() {
        Map<String, Object> response = HttpSupport.getMap(
                http, baseUrl + "/api/open/ir/snapshots", headers());
        return snapshots(response);
    }

    @Override
    public void execute(ActionCommand command) {
        if (!hasApiKey()) {
            executeLogin(command);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", command.getType());
        body.put("targetKey", command.getTargetKey());
        body.put("params", command.getParams());
        HttpSupport.putIdempotency(body, command);
        if (command.getParams() != null) {
            copyParam(body, command.getParams(), "invoiceNo");
            copyParam(body, command.getParams(), "requestNo");
            copyParam(body, command.getParams(), "orderNo");
            copyParam(body, command.getParams(), "aufnr");
            copyParam(body, command.getParams(), "banfn");
            copyParam(body, command.getParams(), "sku");
            copyParam(body, command.getParams(), "matnr");
            copyParam(body, command.getParams(), "ecnNo");
            copyParam(body, command.getParams(), "bomNo");
            copyParam(body, command.getParams(), "taskId");
            copyParam(body, command.getParams(), "definitionCode");
            copyParam(body, command.getParams(), "businessId");
            copyParam(body, command.getParams(), "opportunityId");
            copyParam(body, command.getParams(), "caseNo");
            copyParam(body, command.getParams(), "dealerCode");
            copyParam(body, command.getParams(), "replenishNo");
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

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private void executeLogin(ActionCommand command) {
        Map<String, Object> params = command.getParams() == null
                ? Collections.<String, Object>emptyMap() : command.getParams();
        String type = command.getType();
        String target = command.getTargetKey();
        if ("SAP_CREATE_PR".equals(type)) {
            String plant = first(str(params.get("werks")), str(params.get("plantCode")), "1000");
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("matnr", first(str(params.get("sku")), str(params.get("matnr")), target));
            item.put("menge", params.get("qty") == null ? Integer.valueOf(1) : params.get("qty"));
            item.put("netpr", Integer.valueOf(0));
            item.put("werks", plant);
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("requester", "IR");
            body.put("werks", plant);
            body.put("items", Collections.singletonList(item));
            HttpSupport.postMap(http, baseUrl + "/api/mm/pr", body, bearer());
            return;
        }
        if ("SAP_RELEASE_PR".equals(type)) {
            HttpSupport.postMap(http, baseUrl + "/api/mm/pr/"
                    + encode(first(str(params.get("banfn")), target)) + "/release",
                    Collections.emptyMap(), bearer());
            return;
        }
        if ("SAP_RELEASE_MO".equals(type)) {
            HttpSupport.postMap(http, baseUrl + "/api/pp/orders/"
                    + encode(first(str(params.get("aufnr")), target)) + "/release",
                    Collections.emptyMap(), bearer());
            return;
        }
        if ("DMS_REPLENISH_SHORTAGE".equals(type)) {
            String dealer = first(str(params.get("dealerCode")), dealerOf(target), target);
            HttpSupport.postMap(http, baseUrl + "/api/oms/replenish/from-shortage?dealerCode="
                    + encode(dealer), Collections.emptyMap(), bearer());
            return;
        }
        if ("DMS_PUSH_REPLENISH".equals(type)) {
            String no = first(str(params.get("replenishNo")), target);
            HttpSupport.postMap(http, baseUrl + "/api/oms/replenish/" + replenishId(no) + "/push",
                    Collections.emptyMap(), bearer());
            return;
        }
        throw new IntegrationException("登录模式不支持的指令: " + type);
    }

    private long replenishId(String replenishNo) {
        if (replenishNo != null && replenishNo.matches("\\d+")) {
            return Long.parseLong(replenishNo);
        }
        String url = baseUrl + "/api/oms/replenish/page?current=1&size=20&keyword=" + encode(replenishNo);
        for (Map<String, Object> row : HttpSupport.rows(HttpSupport.getMap(http, url, bearer()))) {
            if (replenishNo != null && replenishNo.equals(HttpSupport.string(row, "replenishNo", "code"))) {
                long id = HttpSupport.longValue(row, "id");
                if (id > 0) {
                    return id;
                }
            }
        }
        throw new IntegrationException("补货单不存在: " + replenishNo);
    }

    private HttpHeaders bearer() {
        return HttpSupport.bearer(login());
    }

    private synchronized String login() {
        if (token != null) {
            return token;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
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
            throw new IntegrationException("登录未返回 token");
        }
        return token;
    }

    private static String dealerOf(String targetKey) {
        if (targetKey == null) {
            return null;
        }
        int slash = targetKey.indexOf('/');
        return slash > 0 ? targetKey.substring(0, slash) : targetKey;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String encode(String value) {
        if (value == null) {
            throw new IntegrationException("登录模式缺少业务键");
        }
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IntegrationException("URL 编码失败: " + value);
        }
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
        if ("SAP_CREATE_PR".equals(type)) {
            return "/api/open/ir/create-pr";
        }
        if ("SAP_RELEASE_PR".equals(type)) {
            return "/api/open/ir/release-pr";
        }
        if ("SAP_RELEASE_MO".equals(type)) {
            return "/api/open/ir/release-mo";
        }
        if ("BOM_EXPLODE".equals(type)) {
            return "/api/open/ir/explode";
        }
        if ("BOM_SUBMIT_ECN".equals(type)) {
            return "/api/open/ir/submit-ecn";
        }
        if ("BOM_APPROVE_ECN".equals(type)) {
            return "/api/open/ir/approve-ecn";
        }
        if ("BOM_IMPLEMENT_ECN".equals(type)) {
            return "/api/open/ir/implement-ecn";
        }
        if ("OA_START_WORKFLOW".equals(type)) {
            return "/api/open/ir/start-workflow";
        }
        if ("OA_APPROVE_TASK".equals(type) || "OA_COMPLETE_TASK".equals(type)) {
            return "/api/open/ir/approve-task";
        }
        if ("CRM_ADVANCE_STAGE".equals(type)) {
            return "/api/open/ir/advance-stage";
        }
        if ("CRM_ESCALATE_CASE".equals(type)) {
            return "/api/open/ir/escalate-case";
        }
        if ("DMS_REPLENISH_SHORTAGE".equals(type)) {
            return "/api/open/ir/replenish-shortage";
        }
        if ("DMS_PUSH_REPLENISH".equals(type)) {
            return "/api/open/ir/push-replenish";
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
