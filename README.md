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
