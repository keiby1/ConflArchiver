package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.service.ArchivedReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * API для отображения HTML страниц из архивов.
 * Эндпоинт: /{project}/{id}
 */
@RestController
public class ReportApiController {

    private final ArchivedReportService archivedReportService;

    public ReportApiController(ArchivedReportService archivedReportService) {
        this.archivedReportService = archivedReportService;
    }

    @GetMapping("/{project}/{id}")
    public ResponseEntity<String> getReportPage(@PathVariable String project, @PathVariable String id) {
        Optional<String> htmlContent = archivedReportService.getHtmlContent(project, id);

        if (htmlContent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");

        return new ResponseEntity<>(htmlContent.get(), headers, HttpStatus.OK);
    }
}
