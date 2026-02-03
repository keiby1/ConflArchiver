# ConflArchReport

Spring Boot приложение для просмотра HTML-страниц из zip-архивов с поддержкой поиска через PostgreSQL.

## Структура данных

```
<jar-директория>/
├── ConflArchReport.jar
└── reports/
    └── <project>/
        ├── archive1.zip
        ├── archive2.zip
        └── ...
```

## API

### Просмотр отчёта
```
GET /{project}/{id}
```
- `project` — название проекта (папка в reports)
- `id` — имя архива без расширения .zip

Возвращает HTML-страницу из архива (index.html или первый .html файл).

### Синхронизация архивов с БД
```
POST /admin/sync
```
Сканирует папку `reports` и добавляет новые архивы в базу данных.

## UI

Главная страница `/`:
- Выбор проекта (фильтр)
- Поиск по подстроке (название, id, Jira ключ)
- Таблица архивов с ссылками на просмотр
- Кнопка **Добавить архив** — модальное окно для архивации страниц Confluence

### Добавление архива из Confluence

1. Нажмите «Добавить архив»
2. Введите URL страницы Confluence и выберите проект
3. Выполните шаги по порядку:
   - **1** — экспорт страницы и дочерних в HTML, сохранение в zip на сервере
   - **2** — сохранение списка дочерних страниц (в json_info) и удаление их в Confluence
   - **3** — удаление вложений со страницы
   - **4** — замена контента на текст об архивации
   - **5** — сохранение в БД

## База данных PostgreSQL

### Создание БД
```sql
CREATE DATABASE conflarchreport;
```

### Настройка (application.properties)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/conflarchreport
spring.datasource.username=postgres
spring.datasource.password=postgres
app.reports.path=reports
app.base-url=https://reports.your-domain.com
```

### Confluence API (application-secret.properties)

Для архивации страниц Confluence используется авторизация Bearer-токеном:

```properties
confluence.api-token=your-bearer-token
```

Таблицы создаются автоматически (Hibernate ddl-auto=update).

### Схема

**projects** — проекты
- id (PK)
- name (уникальное)

**archived_reports** — архивы отчётов
- pk (PK, auto)
- archive_id — имя архива без .zip (уникально в рамках проекта)
- name — название (до 1000 символов)
- project_id (FK)
- jira_key — ключ тикета Jira
- digrep — URL (до 100 символов)
- json_info — JSON (доп. информация, nullable)

## Сборка и запуск

```bash
# Сборка
./mvnw package -DskipTests

# Запуск (Linux)
java -jar target/ConflArchReport-0.0.1-SNAPSHOT.jar

# С указанием пути к reports
java -Dapp.reports.path=/opt/app/reports -jar target/ConflArchReport-0.0.1-SNAPSHOT.jar
```

## Первый запуск

1. Создайте БД PostgreSQL
2. Настройте application.properties
3. Разместите архивы в `reports/<project>/*.zip`
4. Вызовите `POST /admin/sync` для заполнения БД
5. Откройте `/` для поиска и просмотра
