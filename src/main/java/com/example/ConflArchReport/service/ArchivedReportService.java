package com.example.ConflArchReport.service;

import com.example.ConflArchReport.entity.ArchivedReport;
import com.example.ConflArchReport.entity.Project;
import com.example.ConflArchReport.repository.ArchivedReportRepository;
import com.example.ConflArchReport.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ArchivedReportService {

    private final ArchivedReportRepository archivedReportRepository;
    private final ProjectRepository projectRepository;
    private final ZipReportService zipReportService;

    public ArchivedReportService(ArchivedReportRepository archivedReportRepository,
                                 ProjectRepository projectRepository,
                                 ZipReportService zipReportService) {
        this.archivedReportRepository = archivedReportRepository;
        this.projectRepository = projectRepository;
        this.zipReportService = zipReportService;
    }

    public List<String> getAllProjectNames() {
        return projectRepository.findAll().stream()
                .map(Project::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public Page<ArchivedReport> searchArchives(String projectFilter, String searchTerm, Pageable pageable) {
        String search = (searchTerm == null || searchTerm.isBlank()) ? "" : searchTerm.trim();
        String project = (projectFilter == null || projectFilter.isBlank()) ? null : projectFilter.trim();

        if (project != null && !project.isEmpty()) {
            if (search.isEmpty()) {
                return archivedReportRepository.findByProject(project, pageable);
            } else {
                return archivedReportRepository.findByProjectAndSearch(project, search, pageable);
            }
        } else {
            if (search.isEmpty()) {
                return archivedReportRepository.findAll(pageable);
            } else {
                return archivedReportRepository.findBySearch(search, pageable);
            }
        }
    }

    public Optional<ArchivedReport> getReport(String projectName, String id) {
        return archivedReportRepository.findByProjectNameAndId(projectName, id);
    }

    public Optional<String> getHtmlContent(String project, String id) {
        try {
            return zipReportService.extractHtmlContent(project, id);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Возвращает содержимое файла из архива (CSS, JS, другой HTML и т.д.).
     */
    public Optional<byte[]> getFileContent(String project, String id, String path) {
        try {
            return zipReportService.getFileContent(project, id, path);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public boolean reportExists(String project, String id) {
        return zipReportService.archiveExists(project, id);
    }

    @Transactional
    public Project getOrCreateProject(String name) {
        return projectRepository.findByName(name)
                .orElseGet(() -> projectRepository.save(new Project(name)));
    }

    @Transactional
    public ArchivedReport saveReport(ArchivedReport report) {
        return archivedReportRepository.save(report);
    }
}
