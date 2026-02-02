package com.example.ConflArchReport.service;

import com.example.ConflArchReport.confluence.ConfluenceApiResponse;
import com.example.ConflArchReport.confluence.ConfluenceUrlParser;
import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.entity.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ConfluenceArchiveService {

    private static final String EXPAND = "body.export_view,body.storage,body.view,children.page";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final ArchivedReportService archivedReportService;

    @Value("${app.reports.path:reports}")
    private String reportsBasePath;

    @Value("${app.base-url:}")
    private String appBaseUrl;

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
        Project project = archivedReportService.getOrCreateProject(projectName);

        String pageId = parsed.pageId();
        String apiBase = parsed.getApiBaseUrl();

        // Получаем главную страницу
        ConfluenceApiResponse mainPage = fetchPage(apiBase, pageId);
        if (mainPage == null) {
            throw new IllegalStateException("Страница не найдена: " + pageId);
        }

        String pageTitle = mainPage.getTitle();
        String mainHtml = extractHtmlFromPage(mainPage);

        // Собираем все страницы: главная + дочерние
        List<PageContent> pages = new ArrayList<>();
        pages.add(new PageContent("index.html", pageTitle, mainHtml));

        List<ChildInfo> childInfos = new ArrayList<>();
        List<ConfluenceApiResponse.ChildRef> childRefs = getChildPages(mainPage);

        for (ConfluenceApiResponse.ChildRef child : childRefs) {
            ConfluenceApiResponse childPage = fetchPage(apiBase, child.getId());
            if (childPage != null) {
                String childHtml = extractHtmlFromPage(childPage);
                String safeName = sanitizeFilename(child.getTitle()) + ".html";
                pages.add(new PageContent(safeName, child.getTitle(), childHtml));
                childInfos.add(new ChildInfo(child.getId(), child.getTitle()));
            }
        }

        // Генерируем ID архива
        String archiveId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Создаём zip и сохраняем
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
                                         List<String> childPageNames, String jiraKey, String digrep) {
        Project project = archivedReportService.getOrCreateProject(projectName);
        ArchivedReport report = new ArchivedReport(archiveId, name, project);
        report.setJiraKey(jiraKey != null && !jiraKey.isBlank() ? jiraKey : null);
        report.setDigrep(digrep != null && digrep.length() > 100 ? digrep.substring(0, 100) : digrep);

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

    private ConfluenceApiResponse fetchPage(String apiBase, String pageId) {
        String url = apiBase + pageId + "?expand=" + EXPAND;
        try {
            return restTemplate.getForObject(url, ConfluenceApiResponse.class);
        } catch (Exception e) {
            return null;
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
}
