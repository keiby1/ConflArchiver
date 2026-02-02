package com.example.ConflArchReport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class ConfluenceConfig {

    @Value("${confluence.email:}")
    private String confluenceEmail;

    @Value("${confluence.api-token:}")
    private String confluenceApiToken;

    @Bean("confluenceRestTemplate")
    public RestTemplate confluenceRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
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
}
