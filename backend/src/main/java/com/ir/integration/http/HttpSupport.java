package com.ir.integration.http;

import com.ir.common.R;
import com.ir.integration.client.IntegrationException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class HttpSupport {
    private HttpSupport() {
    }

    static Map<String, Object> getMap(RestTemplate http, String url, HttpHeaders headers) {
        try {
            ResponseEntity<Map<String, Object>> response = http.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return response.getBody() == null ? Collections.emptyMap() : response.getBody();
        } catch (Exception ex) {
            throw new IntegrationException("调用下游系统接口失败: " + url, ex);
        }
    }

    static Map<String, Object> postMap(RestTemplate http, String url, Object body, HttpHeaders headers) {
        try {
            ResponseEntity<Map<String, Object>> response = http.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return response.getBody() == null ? Collections.emptyMap() : response.getBody();
        } catch (Exception ex) {
            throw new IntegrationException("调用下游系统接口失败: " + url, ex);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof Map) {
            Object records = ((Map<String, Object>) data).get("records");
            if (records == null) {
                records = ((Map<String, Object>) data).get("list");
            }
            if (records instanceof List) {
                return (List<Map<String, Object>>) records;
            }
        }
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> namedList(Map<String, Object> response, String... names) {
        Object data = response.get("data");
        if (data instanceof Map) {
            Map<String, Object> payload = (Map<String, Object>) data;
            for (String name : names) {
                Object value = payload.get(name);
                if (value instanceof List) {
                    return (List<Map<String, Object>>) value;
                }
            }
        }
        return rows(response);
    }

    static java.time.LocalDateTime dateTime(Map<String, Object> row, String... names) {
        return dateTime(string(row, names));
    }

    static java.time.LocalDateTime dateTime(String value) {
        if (value == null || value.trim().isEmpty() || "null".equals(value)) {
            return null;
        }
        String normalized = value.trim().replace(" ", "T");
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        return java.time.LocalDateTime.parse(normalized);
    }

    static java.time.LocalDate localDate(Map<String, Object> row, String... names) {
        String value = string(row, names);
        if (value == null || value.trim().isEmpty() || "null".equals(value)) {
            return null;
        }
        String normalized = value.trim().replace(" ", "T");
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        return java.time.LocalDate.parse(normalized);
    }

    @SuppressWarnings("unchecked")
    static String string(Map<String, Object> row, String... names) {
        for (String name : names) {
            Object value = row.get(name);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    static long longValue(Map<String, Object> row, String... names) {
        String value = string(row, names);
        return value == null ? 0 : Long.parseLong(value);
    }

    static double doubleValue(Map<String, Object> row, String... names) {
        String value = string(row, names);
        return value == null ? 0 : Double.parseDouble(value);
    }

    static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    static HttpHeaders apiKey(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            headers.set("X-Api-Key", apiKey);
        }
        return headers;
    }
}
