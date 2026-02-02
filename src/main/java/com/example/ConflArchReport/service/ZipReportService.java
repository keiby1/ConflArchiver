package com.example.ConflArchReport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipReportService {

    @Value("${app.reports.path:reports}")
    private String reportsBasePath;

    /**
     * Извлекает HTML страницу из zip архива и возвращает её содержимое.
     * Ищет index.html, затем первый найденный .html файл.
     */
    public Optional<String> extractHtmlContent(String project, String id) throws IOException {
        Path zipPath = getZipPath(project, id);
        if (!Files.exists(zipPath)) {
            return Optional.empty();
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            String preferredHtml = null;
            String firstHtml = null;

            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".html")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    if ("index.html".equalsIgnoreCase(Paths.get(entry.getName()).getFileName().toString())) {
                        preferredHtml = content;
                        break;
                    }
                    if (firstHtml == null) {
                        firstHtml = content;
                    }
                }
            }

            return Optional.ofNullable(preferredHtml != null ? preferredHtml : firstHtml);
        }
    }

    /**
     * Возвращает путь к zip архиву
     */
    public Path getZipPath(String project, String id) {
        return Paths.get(reportsBasePath, project, id + ".zip").normalize();
    }

    /**
     * Проверяет существование архива
     */
    public boolean archiveExists(String project, String id) {
        return Files.exists(getZipPath(project, id));
    }
}
