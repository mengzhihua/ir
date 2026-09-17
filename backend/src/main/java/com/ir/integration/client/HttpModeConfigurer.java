package com.ir.integration.client;

import com.ir.integration.entity.CtSystem;
import com.ir.integration.mapper.CtSystemMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpModeConfigurer implements ApplicationRunner {
    private final CtSystemMapper systems;
    private final String httpSystems;

    public HttpModeConfigurer(
            CtSystemMapper systems,
            @Value("${ir.http.systems:}") String httpSystems) {
        this.systems = systems;
        this.httpSystems = httpSystems;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (httpSystems == null || httpSystems.trim().isEmpty()) {
            return;
        }
        for (CtSystem system : systems.selectList(null)) {
            if (ClientFactory.forcedHttp(httpSystems, system.getCode())
                    && !"HTTP".equalsIgnoreCase(system.getMode())) {
                system.setMode("HTTP");
                systems.updateById(system);
            }
        }
    }
}
