package com.ir.integration.client;

import lombok.Data;
import java.util.Map;

@Data
public class ActionCommand {
    private String type;
    private String targetKey;
    private Map<String, Object> params;
}
