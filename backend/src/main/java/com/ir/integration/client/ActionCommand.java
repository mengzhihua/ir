package com.ir.integration.client;

import lombok.Data;
import java.util.Map;

@Data
public class ActionCommand {
    private String type;
    private String targetKey;
    private Map<String, Object> params;
    /** 以 actionNo 为键的幂等标识,随外部指令下发,供对端去重. */
    private String idempotencyKey;
}
