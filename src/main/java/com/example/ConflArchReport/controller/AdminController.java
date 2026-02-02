package com.example.ConflArchReport.controller;

import com.example.ConflArchReport.service.ReportSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ReportSyncService reportSyncService;

    public AdminController(ReportSyncService reportSyncService) {
        this.reportSyncService = reportSyncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncReports() {
        try {
            ReportSyncService.SyncResult result = reportSyncService.syncFromFilesystem();
            return ResponseEntity.ok(Map.of(
                    "added", result.added(),
                    "total", result.total(),
                    "errors", result.errors()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
