package com.example.ConflArchReport.service;

import com.example.ConflArchReport.confluence.ConfluenceApiResponse;
import com.example.ConflArchReport.confluence.ConfluenceUrlParser;
import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.entity.Project;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Экспорт контента Confluence в zip через REST API.
 * HTML страниц + вложения (картинки, файлы) выгружаются и сохраняются в zip;
 * ссылки на вложения в HTML заменяются на относительные пути к файлам в архиве.
 */
@Service
public class ConfluenceArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceArchiveService.class);
    private static final String EXPAND = "body.export_view,body.storage,body.view,children.page";
    private static final String ATTACHMENTS_DIR = "attachments";

    private final RestTemplate restTemplate;
    private final ArchivedReportService archivedReportService;

    @Value("${app.reports.path:reports}")
    private String reportsBasePath;

    @Value("${app.base-url:}")
    private String appBaseUrl;

    /** Контекстный путь Confluence, если не в корне (например /confluence). Пустой — REST в корне. */
    @Value("${confluence.context-path:}")
    private String confluenceContextPath;

    public ConfluenceArchiveService(@Qualifier("confluenceRestTemplate") RestTemplate restTemplate,
                                    ArchivedReportService archivedReportService) {
        this.restTemplate = restTemplate;
        this.archivedReportService = archivedReportService;
    }

    /**
     * Шаг 1: Экспорт страницы и дочерних в HTML, сохранение в zip на сервере
     */
    public ExportResult exportToZip(String confluenceUrl, String projectName) throws IOException {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        archivedReportService.getOrCreateProject(projectName);

        String pageId = parsed.pageId();
        String baseUrl = parsed.baseUrl().endsWith("/") ? parsed.baseUrl().substring(0, parsed.baseUrl().length() - 1) : parsed.baseUrl();
        String contextPath = (confluenceContextPath != null ? confluenceContextPath.trim() : "").replaceAll("^/+|/+$", "");
        String effectiveBase = contextPath.isEmpty() ? baseUrl : (baseUrl + "/" + contextPath);
        String apiBase = effectiveBase + "/rest/api/content/";
        String webBase = effectiveBase + "/";

        // Карта: ключ "pageId/filename" -> путь в zip (attachments/attachmentId_safeName)
        Map<String, String> attachmentUrlToZipPath = new HashMap<>();

        // Получаем главную страницу
        ConfluenceApiResponse mainPage = fetchPageOrThrow(apiBase, pageId, "корневая страница");
        String pageTitle = mainPage.getTitle();
        String mainHtml = extractHtmlFromPage(mainPage);

        List<PageContent> pages = new ArrayList<>();
        List<AttachmentEntry> attachmentEntries = new ArrayList<>();

        // Вложения корневой страницы
        collectAttachments(apiBase, webBase, pageId, attachmentUrlToZipPath, attachmentEntries);

        pages.add(new PageContent("index.html", pageTitle, rewriteAttachmentUrlsInHtml(mainHtml, pageId, attachmentUrlToZipPath)));

        List<ChildInfo> childInfos = new ArrayList<>();
        List<ConfluenceApiResponse.ChildRef> childRefs = getChildPages(mainPage);

        for (ConfluenceApiResponse.ChildRef child : childRefs) {
            try {
                ConfluenceApiResponse childPage = fetchPageOrThrow(apiBase, child.getId(), "дочерняя: " + child.getTitle());
                String childHtml = extractHtmlFromPage(childPage);
                collectAttachments(apiBase, webBase, child.getId(), attachmentUrlToZipPath, attachmentEntries);
                String safeName = sanitizeFilename(child.getTitle()) + ".html";
                pages.add(new PageContent(safeName, child.getTitle(), rewriteAttachmentUrlsInHtml(childHtml, child.getId(), attachmentUrlToZipPath)));
                childInfos.add(new ChildInfo(child.getId(), child.getTitle()));
            } catch (Exception e) {
                log.warn("Пропуск дочерней страницы id={} title={}: {}", child.getId(), child.getTitle(), e.getMessage());
            }
        }

        String archiveId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path projectDir = Path.of(reportsBasePath, projectName);
        Files.createDirectories(projectDir);
        Path zipPath = projectDir.resolve(archiveId + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (PageContent pc : pages) {
                ZipEntry entry = new ZipEntry(pc.filename());
                zos.putNextEntry(entry);
                zos.write(pc.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            for (AttachmentEntry att : attachmentEntries) {
                if (att.data() == null || att.data().length == 0) continue;
                ZipEntry entry = new ZipEntry(att.zipPath());
                zos.putNextEntry(entry);
                zos.write(att.data());
                zos.closeEntry();
            }
        }

        return new ExportResult(
                archiveId,
                pageTitle,
                childInfos.stream().map(ChildInfo::title).collect(Collectors.toList()),
                childInfos,
                zipPath.toString()
        );
    }

    /**
     * Собирает список вложений страницы, скачивает их и добавляет в карту для подмены URL в HTML.
     */
    @SuppressWarnings("unchecked")
    private void collectAttachments(String apiBase, String webBase, String pageId,
                                   Map<String, String> attachmentUrlToZipPath,
                                   List<AttachmentEntry> attachmentEntries) {
        String url = apiBase + pageId + "?expand=children.attachment";
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) return;
            Object childrenObj = body.get("children");
            if (childrenObj == null) return;
            Map<String, Object> children = (Map<String, Object>) childrenObj;
            Object attachmentObj = children.get("attachment");
            if (attachmentObj == null) return;
            Map<String, Object> attachmentData = (Map<String, Object>) attachmentObj;
            List<Map<String, Object>> results = (List<Map<String, Object>>) attachmentData.get("results");
            if (results == null) return;

            for (Map<String, Object> att : results) {
                String attId = (String) att.get("id");
                String title = (String) att.get("title");
                if (attId == null || title == null || title.isBlank()) continue;
                String safeFileName = sanitizeFilename(title);
                String zipPath = ATTACHMENTS_DIR + "/" + attId + "_" + safeFileName;
                attachmentUrlToZipPath.put(pageId + "/" + title, zipPath);
                attachmentUrlToZipPath.put(pageId + "/" + safeFileName, zipPath);

                Object linksObj = att.get("_links");
                String downloadPath = null;
                if (linksObj instanceof Map<?, ?> links) {
                    Object download = links.get("download");
                    if (download != null) downloadPath = download.toString();
                }
                if (downloadPath == null || downloadPath.isBlank()) {
                    downloadPath = "/download/attachments/" + pageId + "/" + title;
                }
                byte[] data = downloadAttachment(webBase, downloadPath);
                attachmentEntries.add(new AttachmentEntry(zipPath, data));
            }
        } catch (Exception e) {
            log.warn("Не удалось загрузить вложения страницы {}: {}", pageId, e.getMessage());
        }
    }

    private byte[] downloadAttachment(String webBase, String downloadPath) {
        try {
            String fullUrl = downloadPath.startsWith("http") ? downloadPath : webBase + (downloadPath.startsWith("/") ? downloadPath.substring(1) : downloadPath);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(fullUrl),
                    org.springframework.http.HttpMethod.GET,
                    null,
                    byte[].class
            );
            return response.getBody() != null ? response.getBody() : new byte[0];
        } catch (Exception e) {
            log.warn("Ошибка скачивания вложения {}: {}", downloadPath, e.getMessage());
            return new byte[0];
        }
    }

    /** Подмена в HTML ссылок вида /download/attachments/{pageId}/{filename} на относительный путь в архиве. */
    private static final Pattern DOWNLOAD_LINK = Pattern.compile(
            "(?i)(href|src)=[\"']([^\"']*?/download/attachments/)(\\d+)/([^\"'?]+)([^\"']*)[\"']");

    private String rewriteAttachmentUrlsInHtml(String html, String pageId, Map<String, String> attachmentUrlToZipPath) {
        Matcher m = DOWNLOAD_LINK.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            String attr = m.group(1);
            String pid = m.group(3);
            String filename = m.group(4);
            String key = pid + "/" + filename;
            String keyDecoded;
            try {
                keyDecoded = pid + "/" + java.net.URLDecoder.decode(filename.replace('+', ' '), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                keyDecoded = key;
            }
            String zipPath = attachmentUrlToZipPath.get(key);
            if (zipPath == null) zipPath = attachmentUrlToZipPath.get(keyDecoded);
            if (zipPath == null) zipPath = attachmentUrlToZipPath.get(pid + "/" + sanitizeFilename(filename));
            if (zipPath != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(attr + "=\"" + zipPath + "\""));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Шаг 2: Удаление дочерних страниц в Confluence
     */
    public void deleteChildPages(String confluenceUrl, List<String> childPageIds) {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        String apiBase = parsed.getApiBaseUrl();

        for (String childId : childPageIds) {
            try {
                restTemplate.delete(apiBase + childId + "?status=trashed");
            } catch (Exception e) {
                // Пробуем без status для старых версий
                try {
                    restTemplate.delete(apiBase + childId);
                } catch (Exception ex) {
                    throw new RuntimeException("Не удалось удалить страницу " + childId + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Удаляет все дочерние страницы, найденные по URL корневой страницы, и возвращает их названия.
     * Используется, когда заранее нет списка childPageIds.
     */
    public List<String> deleteChildPagesByUrl(String confluenceUrl) {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        String apiBase = parsed.getApiBaseUrl();
        String pageId = parsed.pageId();

        ConfluenceApiResponse mainPage = fetchPageOrThrow(apiBase, pageId, "корневая страница");
        List<ConfluenceApiResponse.ChildRef> childRefs = getChildPages(mainPage);
        if (childRefs.isEmpty()) {
            return List.of();
        }
        List<String> ids = childRefs.stream().map(ConfluenceApiResponse.ChildRef::getId).toList();
        deleteChildPages(confluenceUrl, ids);
        return childRefs.stream().map(ConfluenceApiResponse.ChildRef::getTitle).toList();
    }

    /**
     * Шаг 3: Удаление всех вложений со страницы
     */
    public void deleteAttachments(String confluenceUrl) {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        String apiBase = parsed.getApiBaseUrl();
        String pageId = parsed.pageId();

        String url = apiBase + pageId + "?expand=children.attachment";
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> body = response.getBody();
        if (body == null) return;

        Object childrenObj = body.get("children");
        if (childrenObj == null) return;

        Map<String, Object> children = (Map<String, Object>) childrenObj;
        Object attachmentObj = children.get("attachment");
        if (attachmentObj == null) return;

        Map<String, Object> attachmentData = (Map<String, Object>) attachmentObj;
        List<Map<String, Object>> results = (List<Map<String, Object>>) attachmentData.get("results");
        if (results == null) return;

        for (Map<String, Object> att : results) {
            String attId = (String) att.get("id");
            if (attId != null) {
                try {
                    restTemplate.delete(apiBase + attId);
                } catch (Exception e) {
                    // Логируем и продолжаем
                }
            }
        }
    }

    /**
     * Шаг 4: Замена контента страницы на текст об архивации
     */
    public void replacePageContent(String confluenceUrl, String pageTitle, String archiveId, String projectName, String jiraKey) {
        ConfluenceUrlParser.ParsedUrl parsed = ConfluenceUrlParser.parse(confluenceUrl);
        String apiBase = parsed.getApiBaseUrl();
        String pageId = parsed.pageId();

        String viewUrl = appBaseUrl != null && !appBaseUrl.isBlank()
                ? (appBaseUrl.endsWith("/") ? appBaseUrl : appBaseUrl + "/") + projectName + "/" + archiveId
                : "[URL приложения]/" + projectName + "/" + archiveId;

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        String ticket = (jiraKey != null && !jiraKey.isBlank()) ? jiraKey : "TICKET-PLACEHOLDER";

        String newContent = """
                <p>Заключение НТ %s по тикету %s было архивировано %s.</p>
                <p>Для просмотра используйте: <a href="%s">%s</a></p>
                """.formatted(pageTitle, ticket, now, viewUrl, viewUrl);

        int nextVersion = getCurrentVersion(apiBase, pageId) + 1;
        Map<String, Object> request = Map.of(
                "id", pageId,
                "type", "page",
                "title", pageTitle,
                "body", Map.of(
                        "storage", Map.of(
                                "value", newContent,
                                "representation", "storage"
                        )
                ),
                "version", Map.of("number", nextVersion)
        );

        restTemplate.put(apiBase + pageId, request);
    }

    /**
     * Шаг 5: Сохранение информации в БД
     */
    public ArchivedReport saveToDatabase(String archiveId, String name, String projectName,
                                         List<String> childPageNames, String jiraKey) {
        Project project = archivedReportService.getOrCreateProject(projectName);
        ArchivedReport report = new ArchivedReport(archiveId, name, project);
        report.setJiraKey(jiraKey != null && !jiraKey.isBlank() ? jiraKey : null);

        if (childPageNames != null && !childPageNames.isEmpty()) {
            report.setJsonInfo(Map.of("childPages", childPageNames));
        }

        return archivedReportService.saveReport(report);
    }

    private int getCurrentVersion(String apiBase, String pageId) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apiBase + pageId,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        Map<String, Object> version = (Map<String, Object>) response.getBody().get("version");
        return (Integer) version.get("number");
    }

    /**
     * Загружает страницу по REST API. При ошибке (404, 401, сеть) бросает исключение с URL и причиной.
     */
    private ConfluenceApiResponse fetchPageOrThrow(String apiBase, String pageId, String label) {
        String url = apiBase + pageId + "?expand=" + EXPAND;
        try {
            ResponseEntity<ConfluenceApiResponse> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    ConfluenceApiResponse.class
            );
            ConfluenceApiResponse page = response.getBody();
            if (page == null) {
                throw new IllegalStateException(label + ": пустой ответ от " + url);
            }
            return page;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String hint = "";
            if (e.getStatusCode().value() == 401) {
                hint = " Проверьте confluence.api-token (Bearer) в application-secret.properties.";
            } else if (e.getStatusCode().value() == 404) {
                hint = " Если Confluence установлен не в корне, укажите confluence.context-path в application.properties (например /confluence).";
            }
            throw new IllegalStateException(label + " (id=" + pageId + "): HTTP " + e.getStatusCode().value()
                    + " — " + url + hint, e);
        } catch (Exception e) {
            throw new IllegalStateException(label + " (id=" + pageId + ") не найдена или недоступна: " + url + " — " + e.getMessage(), e);
        }
    }

    private String extractHtmlFromPage(ConfluenceApiResponse page) {
        if (page.getBody() == null) return "<p>Нет контента</p>";

        // Пробуем export_view (HTML), затем view, затем storage
        String html = getBodyValue(page, "export_view");
        if (html == null) html = getBodyValue(page, "view");
        if (html == null) html = getBodyValue(page, "storage");

        if (html == null) return "<p>Нет контента</p>";

        return wrapInHtmlDocument(page.getTitle(), html);
    }

    private String getBodyValue(ConfluenceApiResponse page, String representation) {
        if (page.getBody() == null) return null;
        ConfluenceApiResponse.BodyRepresentation br = page.getBody().get(representation);
        return br != null ? br.getValue() : null;
    }

    private List<ConfluenceApiResponse.ChildRef> getChildPages(ConfluenceApiResponse page) {
        if (page.getChildren() == null) return List.of();
        ConfluenceApiResponse.ChildrenWrapper pageChildren = page.getChildren().get("page");
        if (pageChildren == null || pageChildren.getResults() == null) return List.of();
        return pageChildren.getResults();
    }

    private String wrapInHtmlDocument(String title, String body) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>%s</title></head>
                <body>%s</body>
                </html>
                """.formatted(escapeHtml(title), body);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "page";
        return name.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ\\s_-]", "_").replaceAll("\\s+", "_");
    }

    public record ExportResult(String archiveId, String pageTitle, List<String> childPageNames,
                               List<ChildInfo> childInfos, String zipPath) {}

    public record ChildInfo(String id, String title) {}
    private record PageContent(String filename, String title, String content) {}
    private record AttachmentEntry(String zipPath, byte[] data) {}
}
