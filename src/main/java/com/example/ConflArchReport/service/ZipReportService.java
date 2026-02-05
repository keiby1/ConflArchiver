package com.example.ConflArchReport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipReportService {

    @Value("${app.reports.path:reports}")
    private String reportsBasePath;

    /** Кодировка имён записей в zip: ISO-8859-1 принимает любые байты, избегает "bad entry name" для архивов из Windows/других кодировок. */
    private static final Charset ZIP_ENTRY_CHARSET = Charset.forName("ISO-8859-1");

    /**
     * Извлекает HTML страницу из zip архива и возвращает её содержимое.
     * Ищет index.html, затем первый найденный .html файл.
     */
    public Optional<String> extractHtmlContent(String project, String id) throws IOException {
        Path zipPath = getZipPath(project, id);
        if (!Files.exists(zipPath)) {
            return Optional.empty();
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), ZIP_ENTRY_CHARSET)) {
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

    /**
     * Извлекает файл из zip архива по относительному пути (например "my.css", "PageName.html").
     * Путь нормализуется; содержащий ".." отклоняется из соображений безопасности.
     *
     * @param project проект
     * @param id      идентификатор архива
     * @param path    относительный путь к файлу в архиве (без ведущего слеша)
     * @return содержимое файла или empty, если архив/файл не найден
     */
    public Optional<byte[]> getFileContent(String project, String id, String path) throws IOException {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            return Optional.empty();
        }
        Path zipPath = getZipPath(project, id);
        if (!Files.exists(zipPath)) {
            return Optional.empty();
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), ZIP_ENTRY_CHARSET)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace('\\', '/');
                // Точное совпадение пути
                if (entryName.equals(normalized)) {
                    return Optional.of(zis.readAllBytes());
                }
                // Проверка совпадения с учетом возможного ведущего слеша в entryName
                if (entryName.startsWith("/") && entryName.substring(1).equals(normalized)) {
                    return Optional.of(zis.readAllBytes());
                }
                // Проверка совпадения, если normalized начинается со слеша, а entryName - нет
                if (!entryName.startsWith("/") && ("/" + entryName).equals(normalized)) {
                    return Optional.of(zis.readAllBytes());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Сохраняет загруженный zip в каталог проекта.
     *
     * @param project   название проекта
     * @param archiveId идентификатор архива (без .zip)
     * @param zipStream содержимое zip-файла
     */
    public void saveUploadedZip(String project, String archiveId, InputStream zipStream) throws IOException {
        Path zipPath = getZipPath(project, archiveId);
        Files.createDirectories(zipPath.getParent());
        Files.copy(zipStream, zipPath);
    }

    private static final Pattern TITLE_TAG = Pattern.compile("<title[^>]*>\\s*([^<]+)\\s*</title>", Pattern.CASE_INSENSITIVE);

    /**
     * Извлекает текст из тега &lt;title&gt; в HTML архива (index.html или первый .html).
     *
     * @param project   проект
     * @param archiveId идентификатор архива
     * @return заголовок страницы или empty
     */
    public Optional<String> extractPageTitleFromArchive(String project, String archiveId) throws IOException {
        Optional<String> html = extractHtmlContent(project, archiveId);
        if (html.isEmpty()) {
            return Optional.empty();
        }
        Matcher m = TITLE_TAG.matcher(html.get());
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
