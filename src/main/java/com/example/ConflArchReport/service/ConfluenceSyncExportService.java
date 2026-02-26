package com.example.ConflArchReport.service;

import com.example.ConflArchReport.confluence.ConfluenceApiResponse;
import com.example.ConflArchReport.confluence.ConfluenceUrlParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Загрузка архива страницы Confluence через Scroll HTML sync-export API.
 * GET .../rest/scroll-html/1.0/sync-export?exportSchemeId=bundled_default&rootPageId={pageId}
 */
@Service
public class ConfluenceSyncExportService {

    private static final String SYNC_EXPORT_PATH = "/rest/scroll-html/1.0/sync-export";
    private static final String SYNC_EXPORT_QUERY = "exportSchemeId=bundled_default&rootPageId=";

    private final RestTemplate confluenceBinaryRestTemplate;
    private final RestTemplate confluenceRestTemplate;

    public ConfluenceSyncExportService(
            @Qualifier("confluenceBinaryRestTemplate") RestTemplate confluenceBinaryRestTemplate,
            @Qualifier("confluenceRestTemplate") RestTemplate confluenceRestTemplate) {
        this.confluenceBinaryRestTemplate = confluenceBinaryRestTemplate;
        this.confluenceRestTemplate = confluenceRestTemplate;
    }

    /**
     * Скачивает zip-архив страницы и дочерних через sync-export.
     *
     * @param confluenceUrl URL страницы Confluence (из него извлекаются baseUrl и pageId)
     * @return байты zip-архива
     */
    public byte[] fetchZip(String confluenceUrl) {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        String baseUrl = parsed.baseUrl().endsWith("/")
                ? parsed.baseUrl().substring(0, parsed.baseUrl().length() - 1)
                : parsed.baseUrl();
        String url = baseUrl + SYNC_EXPORT_PATH + "?" + SYNC_EXPORT_QUERY + parsed.pageId();

        ResponseEntity<byte[]> response = confluenceBinaryRestTemplate.getForEntity(
                URI.create(url), byte[].class);

        if (response.getBody() == null || response.getBody().length == 0) {
            throw new IllegalStateException("Ответ sync-export пуст");
        }

        // Проверка на типичный JSON-ответ с ошибкой (если API вернул ошибку вместо zip)
        byte[] body = response.getBody();
        if (body.length >= 2 && body[0] == '{' && body[1] != 0) {
            String preview = new String(body, 0, Math.min(200, body.length), StandardCharsets.UTF_8);
            if (preview.trim().startsWith("{")) {
                throw new IllegalStateException(
                        "Sync-export вернул не архив, возможно ошибка авторизации или доступа: " + preview);
            }
        }

        return body;
    }

    /**
     * Получает название страницы через Confluence API (getTitle).
     *
     * @param confluenceUrl URL страницы Confluence
     * @return название страницы
     */
    public String fetchPageTitle(String confluenceUrl) {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        String apiBase = parsed.getApiBaseUrl();
        String url = apiBase + parsed.pageId();
        
        try {
            ResponseEntity<ConfluenceApiResponse> response = confluenceRestTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    ConfluenceApiResponse.class
            );
            ConfluenceApiResponse page = response.getBody();
            if (page == null || page.getTitle() == null || page.getTitle().isBlank()) {
                throw new IllegalStateException("Не удалось получить название страницы из Confluence API");
            }
            return page.getTitle();
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка получения названия страницы: " + e.getMessage(), e);
        }
    }
}
