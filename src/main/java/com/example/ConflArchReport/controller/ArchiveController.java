package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.service.ConfluenceArchiveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private final ConfluenceArchiveService confluenceArchiveService;

    public ArchiveController(ConfluenceArchiveService confluenceArchiveService) {
        this.confluenceArchiveService = confluenceArchiveService;
    }

    /**
     * Шаг 1: Экспорт страницы Confluence и дочерних в zip, сохранение на сервере
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
     * Шаг 2: Удаление дочерних страниц в Confluence
     */
    @PostMapping("/delete-children")
    public ResponseEntity<?> deleteChildren(@RequestBody Map<String, Object> request) {
        String confluenceUrl = (String) request.get("confluenceUrl");
        @SuppressWarnings("unchecked")
        List<String> childPageIds = (List<String>) request.get("childPageIds");
        if (confluenceUrl == null || confluenceUrl.isBlank() || childPageIds == null || childPageIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуются confluenceUrl и childPageIds"));
        }
        try {
            confluenceArchiveService.deleteChildPages(confluenceUrl, childPageIds);
            return ResponseEntity.ok(Map.of("success", true));
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
        String digrep = (String) request.get("digrep");

        if (archiveId == null || archiveId.isBlank() || name == null || name.isBlank() || project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Требуются archiveId, name, project"));
        }
        try {
            ArchivedReport report = confluenceArchiveService.saveToDatabase(
                    archiveId, name, project, childPageNames, jiraKey, digrep);
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
