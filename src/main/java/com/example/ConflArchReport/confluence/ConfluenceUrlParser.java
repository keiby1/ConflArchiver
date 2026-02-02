package com.example.ConflArchReport.confluence;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсит URL страницы Confluence для извлечения базового URL и ID страницы.
 */
public class ConfluenceUrlParser {

    // pageId=123456
    private static final Pattern PAGE_ID_PARAM = Pattern.compile("[?&]pageId=(\\d+)");
    // /pages/123456/ или /rest/api/content/123456
    private static final Pattern PAGE_ID_PATH = Pattern.compile("/pages/(\\d+)/|/rest/api/content/(\\d+)|/pages/(\\d+)$");

    public static record ParsedUrl(String baseUrl, String pageId) {
        public String getApiBaseUrl() {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + "/rest/api/content/";
        }
    }

    public static ParsedUrl parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL не может быть пустым");
        }
        String trimmed = url.trim();

        // Пробуем pageId в query
        Matcher paramMatcher = PAGE_ID_PARAM.matcher(trimmed);
        if (paramMatcher.find()) {
            String pageId = paramMatcher.group(1);
            String baseUrl = extractBaseUrl(trimmed);
            return new ParsedUrl(baseUrl, pageId);
        }

        // Пробуем pageId в path
        Matcher pathMatcher = PAGE_ID_PATH.matcher(trimmed);
        if (pathMatcher.find()) {
            String pageId = pathMatcher.group(1) != null ? pathMatcher.group(1) :
                    (pathMatcher.group(2) != null ? pathMatcher.group(2) : pathMatcher.group(3));
            String baseUrl = extractBaseUrl(trimmed);
            return new ParsedUrl(baseUrl, pageId);
        }

        throw new IllegalArgumentException("Не удалось извлечь pageId из URL: " + url);
    }

    private static String extractBaseUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath() != null ? uri.getPath() : "";

            // Confluence Cloud: .../wiki/spaces/... или .../wiki/rest/...
            // Confluence Server: .../display/... или .../pages/...
            if (path.contains("/wiki/")) {
                path = path.substring(0, path.indexOf("/wiki/") + 5); // включая /wiki
            } else if (path.contains("/display/") || path.contains("/pages/")) {
                int slash = path.indexOf("/", 1);
                path = slash > 0 ? path.substring(0, slash) : "";
            } else {
                path = "";
            }

            StringBuilder base = new StringBuilder();
            base.append(scheme).append("://").append(host);
            if (port > 0 && port != 80 && port != 443) {
                base.append(":").append(port);
            }
            base.append(path);
            return base.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный URL: " + url, e);
        }
    }
}
