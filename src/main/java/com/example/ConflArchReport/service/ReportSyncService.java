package com.example.ConflArchReport.service;

import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.entity.Project;
import com.example.ConflArchReport.repository.ArchivedReportRepository;
import com.example.ConflArchReport.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Синхронизирует архивы из папки reports с базой данных.
 */
@Service
public class ReportSyncService {

    @Value("${app.reports.path:reports}")
    private String reportsBasePath;

    private final ArchivedReportRepository archivedReportRepository;
    private final ProjectRepository projectRepository;

    public ReportSyncService(ArchivedReportRepository archivedReportRepository,
                             ProjectRepository projectRepository) {
        this.archivedReportRepository = archivedReportRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public SyncResult syncFromFilesystem() throws IOException {
        Path reportsPath = Paths.get(reportsBasePath).normalize();
        if (!Files.exists(reportsPath) || !Files.isDirectory(reportsPath)) {
            return new SyncResult(0, 0, List.of("Папка reports не найдена: " + reportsPath));
        }

        List<String> errors = new ArrayList<>();
        int added = 0;
        int total = 0;

        try (DirectoryStream<Path> projectDirs = Files.newDirectoryStream(reportsPath, Files::isDirectory)) {
            for (Path projectDir : projectDirs) {
                String projectName = projectDir.getFileName().toString();
                Project project = projectRepository.findByName(projectName)
                        .orElseGet(() -> projectRepository.save(new Project(projectName)));

                try (DirectoryStream<Path> zipFiles = Files.newDirectoryStream(projectDir, "*.zip")) {
                    for (Path zipPath : zipFiles) {
                        total++;
                        String fileName = zipPath.getFileName().toString();
                        String archiveId = fileName.substring(0, fileName.length() - 4);

                        if (archivedReportRepository.findByProjectNameAndId(projectName, archiveId).isEmpty()) {
                            ArchivedReport report = new ArchivedReport(archiveId, fileName, project);
                            archivedReportRepository.save(report);
                            added++;
                        }
                    }
                }
            }
        }

        return new SyncResult(added, total, errors);
    }

    public record SyncResult(int added, int total, List<String> errors) {}
}
