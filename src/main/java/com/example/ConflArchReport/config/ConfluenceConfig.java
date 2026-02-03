package com.example.ConflArchReport.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;

@Configuration
public class ConfluenceConfig {

    @Value("${confluence.email:}")
    private String confluenceEmail;

    @Value("${confluence.api-token:}")
    private String confluenceApiToken;

    /** Путь к JKS-файлу (файловая система или classpath:...) */
    @Value("${confluence.keystore.path:}")
    private String keystorePath;

    /** Пароль к JKS-хранилищу */
    @Value("${confluence.keystore.password:}")
    private String keystorePassword;

    @Bean("confluenceRestTemplate")
    public RestTemplate confluenceRestTemplate() throws Exception {
        RestTemplate restTemplate;

        if (keystorePath != null && !keystorePath.isBlank() && keystorePassword != null) {
            KeyStore keyStore = loadKeyStore(keystorePath, keystorePassword.toCharArray());
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
                    .build();
            @SuppressWarnings("deprecation")
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
            @SuppressWarnings("deprecation")
            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
            HttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
            restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        } else {
            restTemplate = new RestTemplate();
        }

        restTemplate.getInterceptors().add((request, body, execution) -> {
            if (confluenceApiToken != null && !confluenceApiToken.isBlank()
                    && confluenceEmail != null && !confluenceEmail.isBlank()) {
                String auth = confluenceEmail + ":" + confluenceApiToken;
                String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            }
            request.getHeaders().set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            return execution.execute(request, body);
        });
        return restTemplate;
    }

    private KeyStore loadKeyStore(String path, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length()).trim();
            try (InputStream in = getClass().getResourceAsStream(resource.startsWith("/") ? resource : "/" + resource)) {
                if (in == null) {
                    throw new IllegalArgumentException("Keystore не найден в classpath: " + resource);
                }
                keyStore.load(in, password);
            }
        } else {
            Path file = Path.of(path).normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Keystore не найден: " + file.toAbsolutePath());
            }
            try (InputStream in = Files.newInputStream(file)) {
                keyStore.load(in, password);
            }
        }
        return keyStore;
    }
}
