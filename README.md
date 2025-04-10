# SearchEngineJava
Описание проекта
Этот проект представляет собой поисковый движок, который индексирует заданные веб-сайты и предоставляет возможность поиска по их содержимому. Система анализирует текст страниц, выделяет леммы (нормальные формы слов) и строит индекс для быстрого поиска.

Основные возможности
Индексация одного или нескольких сайтов

Многопоточная обработка страниц

Лемматизация текста (приведение слов к нормальной форме)

Поиск по проиндексированным сайтам с ранжированием результатов

Статистика по индексированным сайтам

Технологический стек
Язык программирования: Java 17

Фреймворк: Spring Boot 3.x

База данных: MySQL/PostgreSQL

Библиотеки:

Jsoup - парсинг HTML

Apache Lucene - полнотекстовый поиск (опционально)

Mystem/morphy - лемматизация (русский язык)

Lombok - сокращение boilerplate кода

Требования к системе
Java 17 или выше

MySQL 8.x или PostgreSQL 14.x

2+ GB оперативной памяти

1+ GB свободного места на диске (зависит от объема индексируемых сайтов)

Установка и запуск
Клонируйте репозиторий:

bash
Copy
git clone https://github.com/your-repo/search-engine.git
cd search-engine
Настройте базу данных:

Создайте новую БД в MySQL/PostgreSQL

Настройте подключение в application.yml:

yaml
Copy
spring:
datasource:
url: jdbc:mysql://localhost:3306/search_engine
username: your_username
password: your_password
Запустите приложение:

bash
Copy
./mvnw spring-boot:run
Приложение будет доступно по адресу: http://localhost:8080

Конфигурация
Основные настройки можно изменить в файле application.yml:

yaml
Copy
app:
search:
max-results: 20       # Максимальное количество результатов поиска
snippet-length: 150    # Длина сниппета в символах
indexing:
threads: 4            # Количество потоков для индексации
timeout: 5000         # Таймаут соединения (мс)
user-agent: Mozilla/5.0 (compatible; SearchEngineBot/1.0)
API Endpoints
Статистика
GET /api/statistics - получить статистику по индексированным сайтам

Управление индексацией
GET /api/startIndexing - запустить полную индексацию всех сайтов

GET /api/stopIndexing - остановить текущую индексацию

POST /api/indexPage - проиндексировать конкретную страницу

Поиск
GET /api/search?query=... - выполнить поиск по всем сайтам

GET /api/search?query=...&site=url - выполнить поиск по конкретному сайту

Примеры запросов
Запуск индексации:

bash
Copy
curl -X GET http://localhost:8080/api/startIndexing
Поиск по всем сайтам:

bash
Copy
curl -X GET "http://localhost:8080/api/search?query=java+programming"
Поиск по конкретному сайту:

bash
Copy
curl -X GET "http://localhost:8080/api/search?query=spring+boot&site=https://example.com"
Структура проекта
Copy
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── searchengine/
│   │           ├── config/        # Конфигурационные классы
│   │           ├── controllers/   # REST контроллеры
│   │           ├── dto/           # Data Transfer Objects
│   │           ├── model/         # Сущности БД
│   │           ├── repositories/  # Репозитории JPA
│   │           ├── services/      # Бизнес-логика
│   │           ├── utils/         # Вспомогательные классы
│   │           └── Application.java
│   └── resources/
│       ├── static/                # Статические ресурсы
│       ├── templates/             # Шаблоны (если есть фронт)
│       ├── application.yml        # Основной конфиг
│       └── application-dev.yml    # Конфиг для разработки
├── test/                          # Тесты
Лицензия
Этот проект распространяется под лицензией MIT License.

Контакты
По вопросам сотрудничества и поддержки обращайтесь:

Email: dev@example.com

Telegram: @search_engine_support
