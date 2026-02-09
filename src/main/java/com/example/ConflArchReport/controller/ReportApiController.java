package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.service.ArchivedReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * API для отображения HTML и статики из архивов.
 * - GET /{project}/{id} → редирект на /{project}/{id}/
 * - GET /{project}/{id}/ или /{project}/{id}/index.html → index.html
 * - GET /{project}/{id}/{path} → файл из архива (CSS, JS, другие HTML и т.д.)
 */
@RestController
public class ReportApiController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "html", "htm", "css", "js", "json", "xml", "txt",
            "log", "yml", "yaml",
            "png", "jpg", "jpeg", "gif", "svg", "ico", "webp",
            "woff", "woff2", "ttf", "eot", "otf",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "zip", "rar", "7z"
    );

    private final ArchivedReportService archivedReportService;

    public ReportApiController(ArchivedReportService archivedReportService) {
        this.archivedReportService = archivedReportService;
    }

    /**
     * Без завершающего слеша — редирект, чтобы относительные ссылки в HTML разрешались от /{project}/{id}/
     */
    @GetMapping("/{project}/{id}")
    public ResponseEntity<Void> redirectToSlash(@PathVariable String project, @PathVariable String id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/" + project + "/" + id + "/"))
                .build();
    }

    /**
     * Со слешем: index или конкретный путь. path приходит без ведущего слеша (пустая строка для /{project}/{id}/).
     * Регулярка {path:.*} нужна, чтобы path не обрезался после точки (Spring по умолчанию трактует .doc, .zip и т.д. как суффикс формата).
     */
    @GetMapping("/{project}/{id}/{*path:.*}")
    public ResponseEntity<?> getReportResource(
            @PathVariable String project,
            @PathVariable String id,
            @PathVariable(required = false) String path) {

        String normalizedPath = path != null ? path.replace('\\', '/').trim() : "";
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        boolean serveIndex = normalizedPath.isEmpty() || "index.html".equalsIgnoreCase(normalizedPath);

        if (serveIndex) {
            Optional<String> htmlContent = archivedReportService.getHtmlContent(project, id);
            if (htmlContent.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_HTML);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
            return new ResponseEntity<>(htmlContent.get(), headers, HttpStatus.OK);
        }

        if (!isAllowedPath(normalizedPath)) {
            return ResponseEntity.notFound().build();
        }

        Optional<byte[]> fileContent = archivedReportService.getFileContent(project, id, normalizedPath);
        if (fileContent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = getMediaType(normalizedPath);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        
        // Для zip, архивов и документов устанавливаем Content-Disposition для правильной обработки браузером
        String filename = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        if (normalizedPath.toLowerCase().endsWith(".zip") || 
            normalizedPath.toLowerCase().endsWith(".rar") || 
            normalizedPath.toLowerCase().endsWith(".7z") ||
            normalizedPath.toLowerCase().endsWith(".doc") ||
            normalizedPath.toLowerCase().endsWith(".docx") ||
            normalizedPath.toLowerCase().endsWith(".pdf") ||
            normalizedPath.toLowerCase().endsWith(".xls") ||
            normalizedPath.toLowerCase().endsWith(".xlsx") ||
            normalizedPath.toLowerCase().endsWith(".ppt") ||
            normalizedPath.toLowerCase().endsWith(".pptx") ||
            normalizedPath.toLowerCase().endsWith(".csv")) {
            headers.setContentDispositionFormData("attachment", filename);
        }

        return new ResponseEntity<>(fileContent.get(), headers, HttpStatus.OK);
    }

    private static boolean isAllowedPath(String path) {
        if (path.startsWith("attachments/")) {
            return true;
        }
        int lastSlash = path.lastIndexOf('/');
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    private static MediaType getMediaType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String ext = path.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "html", "htm" -> MediaType.TEXT_HTML;
            case "css" -> MediaType.valueOf("text/css");
            case "js" -> MediaType.valueOf("application/javascript");
            case "json" -> MediaType.APPLICATION_JSON;
            case "xml" -> MediaType.APPLICATION_XML;
            case "txt", "log" -> MediaType.TEXT_PLAIN;
            case "yml", "yaml" -> MediaType.valueOf("text/yaml");
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "svg" -> MediaType.valueOf("image/svg+xml");
            case "ico" -> MediaType.valueOf("image/x-icon");
            case "webp" -> MediaType.valueOf("image/webp");
            case "woff" -> MediaType.valueOf("font/woff");
            case "woff2" -> MediaType.valueOf("font/woff2");
            case "ttf" -> MediaType.valueOf("font/ttf");
            case "eot" -> MediaType.valueOf("application/vnd.ms-fontobject");
            case "otf" -> MediaType.valueOf("font/otf");
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "doc" -> MediaType.valueOf("application/msword");
            case "docx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls" -> MediaType.valueOf("application/vnd.ms-excel");
            case "xlsx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "ppt" -> MediaType.valueOf("application/vnd.ms-powerpoint");
            case "pptx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "csv" -> MediaType.valueOf("text/csv");
            case "zip" -> MediaType.valueOf("application/zip");
            case "rar" -> MediaType.valueOf("application/vnd.rar");
            case "7z" -> MediaType.valueOf("application/x-7z-compressed");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
