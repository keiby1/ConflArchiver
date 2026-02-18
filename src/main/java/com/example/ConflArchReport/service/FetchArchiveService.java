package com.example.ConflArchReport.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Шаг 1 цепочки: выгрузка архива со страницами через внешний REST API.
 * Реализацию вызова вашего API нужно добавить в метод {@link #fetchZipFromExternalApi(String)}.
 */
@Service
public class FetchArchiveService {

    private final ZipReportService zipReportService;
    private final ArchivedReportService archivedReportService;

    public FetchArchiveService(ZipReportService zipReportService,
                               ArchivedReportService archivedReportService) {
        this.zipReportService = zipReportService;
        this.archivedReportService = archivedReportService;
    }

    /**
     * Результат выгрузки архива: идентификатор, заголовок страницы, списки дочерних страниц для последующих шагов.
     * Работает как раньше: если childPageIds/childPageNames заданы — шаг 2 (delete-children) удалит по этим id,
     * а childPageNames пойдут в БД. Если списки пустые — бэкенд получит дочерние по Confluence URL (режим byUrl)
     * и вернёт childPageNames в ответе, как при старой загрузке zip пользователем.
     */
    public record FetchResult(String archiveId, String pageTitle,
                              List<String> childPageNames, List<String> childPageIds) {}

    /**
     * Данные от вашего REST API: zip-поток и опционально списки дочерних страниц.
     * Если childPageIds/childPageNames не возвращает ваш API — передайте null или List.of();
     * тогда шаг 2 получит список дочерних по URL Confluence (как при старой загрузке zip).
     */
    public record ExternalZipData(InputStream zipStream,
                                  List<String> childPageIds,
                                  List<String> childPageNames) {}

    /**
     * Выгружает архив через внешний API, сохраняет zip на сервере и возвращает метаданные для цепочки шагов.
     *
     * @param confluenceUrl URL страницы Confluence (для вашего API)
     * @param project       название проекта
     * @return archiveId, pageTitle, childPageNames, childPageIds
     */
    public FetchResult fetchAndSave(String confluenceUrl, String project) throws IOException {
        archivedReportService.getOrCreateProject(project);
        String archiveId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ExternalZipData data = fetchZipFromExternalApi(confluenceUrl);

        try (InputStream zipStream = data.zipStream()) {
            zipReportService.saveUploadedZip(project, archiveId, zipStream);
        }

        String pageTitle = zipReportService.extractPageTitleFromArchive(project, archiveId)
                .orElse("Без названия");

        List<String> childPageNames = data.childPageNames() != null ? data.childPageNames() : List.of();
        List<String> childPageIds = data.childPageIds() != null ? data.childPageIds() : List.of();

        return new FetchResult(archiveId, pageTitle, childPageNames, childPageIds);
    }

    /**
     * Получение zip-архива и списков дочерних страниц через ваш REST API.
     * Замените тело метода на вызов вашего API.
     *
     * @param confluenceUrl URL страницы Confluence
     * @return поток zip-файла и опционально списки id и названий дочерних страниц (для шагов 2–5)
     */
    protected ExternalZipData fetchZipFromExternalApi(String confluenceUrl) {
        // TODO: вызвать ваш REST API по confluenceUrl,
        //       получить zip (InputStream) и при необходимости — списки childPageIds и childPageNames.
        //       Пример:
        //       RestTemplate/WebClient -> GET/POST ваш_сервис/export?url=... -> response.getBody() (InputStream или byte[] -> new ByteArrayInputStream)
        //       Вернуть: new ExternalZipData(zipInputStream, childPageIds, childPageNames);
        throw new UnsupportedOperationException(
                "Реализуйте fetchZipFromExternalApi: вызовите ваш REST API для выгрузки архива по URL: " + confluenceUrl);
    }
}
