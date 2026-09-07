package com.ir.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

public final class Jsons {
    private Jsons() {}
    public static String write(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new BizException(e.getMessage()); }
    }
    public static Map<String, Object> readMap(ObjectMapper mapper, String value) {
        try { return value == null ? Collections.emptyMap() : mapper.readValue(value, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return Collections.emptyMap(); }
    }
}
