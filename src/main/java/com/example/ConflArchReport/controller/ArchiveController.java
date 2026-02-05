package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.service.ArchivedReportService;
import com.example.ConflArchReport.service.ConfluenceArchiveService;
import com.example.ConflArchReport.service.ZipReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private final ConfluenceArchiveService confluenceArchiveService;
    private final ZipReportService zipReportService;
    private final ArchivedReportService archivedReportService;

    public ArchiveController(ConfluenceArchiveService confluenceArchiveService,
                             ZipReportService zipReportService,
                             ArchivedReportService archivedReportService) {
        this.confluenceArchiveService = confluenceArchiveService;
        this.zipReportService = zipReportService;
        this.archivedReportService = archivedReportService;
    }

    /**
     * Загрузка zip-архива на сервер (вместо экспорта из Confluence).
     * Сохраняет файл в reports/{project}/{archiveId}.zip и возвращает archiveId и pageTitle (из index.html).
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam("project") String project) {
        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Укажите проект"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Выберите zip-файл"));
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл должен быть в формате .zip"));
        }
        try {
            archivedReportService.getOrCreateProject(project);
            String archiveId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            zipReportService.saveUploadedZip(project, archiveId, file.getInputStream());
            String pageTitle = zipReportService.extractPageTitleFromArchive(project, archiveId)
                    .orElse(originalName.replaceAll("\\.zip$", ""));
            return ResponseEntity.ok(Map.of(
                    "archiveId", archiveId,
                    "pageTitle", pageTitle,
                    "project", project
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка сохранения архива: " + e.getMessage()));
        }
    }

    /**
     * Экспорт страницы Confluence и дочерних в zip, сохранение на сервере (устаревший шаг 1, оставлен для совместимости).
     */
    @PostMapping("/export")
    public ResponseEntity<?> exportToZip(@RequestBody Map<String, String> request) {
        String confluenceUrl = request.get("confluenceUrl");
        String project = request.get("project");
        if (confluenceUrl == null || confluenceUrl.isBlank() || project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуются confluenceUrl и project"));
        }
        try {
            var result = confluenceArchiveService.exportToZip(confluenceUrl, project);
            return ResponseEntity.ok(Map.of(
                    "archiveId", result.archiveId(),
                    "pageTitle", result.pageTitle(),
                    "childPageNames", result.childPageNames(),
                    "childPageIds", result.childInfos().stream().map(ConfluenceArchiveService.ChildInfo::id).toList(),
                    "zipPath", result.zipPath()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Ошибка создания архива: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Шаг 2: Удаление дочерних страниц в Confluence.
     * Если передан список childPageIds — используем его.
     * Если нет — получаем список дочерних страниц по URL и удаляем их.
     */
    @PostMapping("/delete-children")
    public ResponseEntity<?> deleteChildren(@RequestBody Map<String, Object> request) {
        String confluenceUrl = (String) request.get("confluenceUrl");
        @SuppressWarnings("unchecked")
        List<String> childPageIds = (List<String>) request.get("childPageIds");
        if (confluenceUrl == null || confluenceUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуется confluenceUrl"));
        }
        try {
            if (childPageIds != null && !childPageIds.isEmpty()) {
                confluenceArchiveService.deleteChildPages(confluenceUrl, childPageIds);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "mode", "explicitIds"
                ));
            } else {
                List<String> deletedNames = confluenceArchiveService.deleteChildPagesByUrl(confluenceUrl);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "mode", "byUrl",
                        "childPageNames", deletedNames
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Шаг 3: Удаление всех вложений со страницы
     */
    @PostMapping("/delete-attachments")
    public ResponseEntity<?> deleteAttachments(@RequestBody Map<String, String> request) {
        String confluenceUrl = request.get("confluenceUrl");
        if (confluenceUrl == null || confluenceUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуется confluenceUrl"));
        }
        try {
            confluenceArchiveService.deleteAttachments(confluenceUrl);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Шаг 4: Замена контента страницы на текст об архивации
     */
    @PostMapping("/replace-content")
    public ResponseEntity<?> replaceContent(@RequestBody Map<String, String> request) {
        String confluenceUrl = request.get("confluenceUrl");
        String pageTitle = request.get("pageTitle");
        String archiveId = request.get("archiveId");
        String project = request.get("project");
        String jiraKey = request.get("jiraKey");
        if (confluenceUrl == null || confluenceUrl.isBlank() || pageTitle == null || archiveId == null || project == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуются confluenceUrl, pageTitle, archiveId, project"));
        }
        try {
            confluenceArchiveService.replacePageContent(confluenceUrl, pageTitle, archiveId, project, jiraKey);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Шаг 5: Сохранение информации в БД
     */
    @PostMapping("/save-to-db")
    public ResponseEntity<?> saveToDb(@RequestBody Map<String, Object> request) {
        String archiveId = (String) request.get("archiveId");
        String name = (String) request.get("name");
        String project = (String) request.get("project");
        @SuppressWarnings("unchecked")
        List<String> childPageNames = (List<String>) request.get("childPageNames");
        String jiraKey = (String) request.get("jiraKey");

        if (archiveId == null || archiveId.isBlank() || name == null || name.isBlank() || project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуются archiveId, name, project"));
        }
        try {
            ArchivedReport report = confluenceArchiveService.saveToDatabase(
                    archiveId, name, project, childPageNames, jiraKey);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", report.getId(),
                    "pk", report.getPk()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
