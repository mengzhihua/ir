package com.ir.integration.client;

import java.util.List;
import java.util.Map;

/** SAP / SRM / BOM / INV / CRM / DMS / OA 统一快照与指令客户端。 */
public interface EcosystemClient {
    List<Map<String, Object>> fetchSnapshots();

    void execute(ActionCommand command);

    boolean health();
}
