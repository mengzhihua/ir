package com.ir.integration.client;

import com.ir.integration.entity.CtSystem;
import com.ir.integration.http.HttpBmsClient;
import com.ir.integration.http.HttpEcosystemClient;
import com.ir.integration.http.HttpOmsClient;
import com.ir.integration.http.HttpSapClient;
import com.ir.integration.http.HttpSrmClient;
import com.ir.integration.http.HttpTmsClient;
import com.ir.integration.http.HttpWmsClient;
import com.ir.integration.mock.MockBmsClient;
import com.ir.integration.mock.MockEcosystemClient;
import com.ir.integration.mock.MockOmsClient;
import com.ir.integration.mock.MockSapClient;
import com.ir.integration.mock.MockSrmClient;
import com.ir.integration.mock.MockTmsClient;
import com.ir.integration.mock.MockWmsClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class ClientFactory {
    private final MockOmsClient oms;
    private final MockWmsClient wms;
    private final MockTmsClient tms;
    private final MockBmsClient bms;
    private final MockSrmClient srm;
    private final MockSapClient sap;
    private final MockEcosystemClient ecosystem;
    private final BaseUrlValidator validator;
    private final RestTemplate http;
    private final String httpSystems;

    @Autowired
    public ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            MockSrmClient srm,
            MockSapClient sap,
            MockEcosystemClient ecosystem,
            BaseUrlValidator validator,
            @Value("${ir.http.systems:}") String httpSystems) {
        this(oms, wms, tms, bms, srm, sap, ecosystem, validator, null, httpSystems);
    }

    public ClientFactory(
            MockOmsClient oms,
            MockWmsClient wms,
            MockTmsClient tms,
            MockBmsClient bms,
            MockSrmClient srm,
            MockSapClient sap,
            MockEcosystemClient ecosystem,
            BaseUrlValidator validator,
            RestTemplate http,
            String httpSystems) {
        this.oms = oms;
        this.wms = wms;
        this.tms = tms;
        this.bms = bms;
        this.srm = srm;
        this.sap = sap;
        this.ecosystem = ecosystem;
        this.validator = validator;
        this.httpSystems = httpSystems;
        this.http = http == null ? createHttp() : http;
        this.http.getInterceptors().add(this::validateRequest);
    }

    private RestTemplate createHttp() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(3000)
                .setSocketTimeout(10000)
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setDnsResolver(new ValidatingDnsResolver(validator))
                .disableRedirectHandling()
                .build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }

    private ClientHttpResponse validateRequest(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        try {
            validator.validateStructure(request.getURI());
        } catch (IllegalArgumentException ex) {
            throw new IntegrationException("集成地址校验失败", ex);
        }
        return execution.execute(request, body);
    }

    public OmsClient oms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpOmsClient(http, system.getBaseUrl(), system.getUsername(),
                    system.getPassword(), system.getApiKey());
        }
        return oms;
    }

    public WmsClient wms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpWmsClient(http, system.getBaseUrl(), system.getUsername(),
                    system.getPassword(), system.getApiKey());
        }
        return wms;
    }

    public TmsClient tms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpTmsClient(http, system.getBaseUrl(), system.getApiKey());
        }
        return tms;
    }

    public BmsClient bms(CtSystem system) {
        if (httpMode(system)) {
            return new HttpBmsClient(http, system.getBaseUrl(), system.getApiKey());
        }
        return bms;
    }

    public SrmClient srm(CtSystem system) {
        if (httpMode(system)) {
            return new HttpSrmClient(http, system.getBaseUrl(),
                    system.getUsername(), system.getPassword());
        }
        return srm;
    }

    public SapClient sap(CtSystem system) {
        if (httpMode(system)) {
            return new HttpSapClient(http, system.getBaseUrl(),
                    system.getUsername(), system.getPassword());
        }
        return sap;
    }

    public EcosystemClient ecosystem(CtSystem system) {
        if (httpMode(system)) {
            return new HttpEcosystemClient(http, system.getBaseUrl(), system.getApiKey());
        }
        String code = system == null ? "SAP" : system.getCode();
        return new EcosystemClient() {
            @Override
            public List<Map<String, Object>> fetchSnapshots() {
                return MockEcosystemClient.rowsFor(code);
            }

            @Override
            public void execute(ActionCommand command) {
                ecosystem.execute(command);
            }

            @Override
            public boolean health() {
                return true;
            }
        };
    }

    public static boolean ecosystemCode(String code) {
        return "SAP".equals(code) || "SRM".equals(code) || "BOM".equals(code)
                || "INV".equals(code) || "CRM".equals(code) || "DMS".equals(code)
                || "OA".equals(code);
    }

    public static boolean forcedHttp(String httpSystems, String code) {
        if (httpSystems == null || httpSystems.trim().isEmpty() || code == null) {
            return false;
        }
        String value = httpSystems.trim();
        if ("*".equals(value) || "ALL".equalsIgnoreCase(value)) {
            return true;
        }
        for (String part : value.split(",")) {
            if (code.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean httpMode(CtSystem system) {
        if (system == null) {
            return false;
        }
        return "HTTP".equalsIgnoreCase(system.getMode())
                || forcedHttp(httpSystems, system.getCode());
    }
}
