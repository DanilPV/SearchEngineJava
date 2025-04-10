# Поисковый Движок для заданных сайтов

[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

Проект представляет собой поисковый движок для индексации и поиска по заданным веб-сайтам.

## 📌 Основные возможности

- **Индексация сайтов** (одного или всех сразу)
- **Поиск по индексированным страницам**
- **Лемматизация запросов** (поиск по основам слов)
- **Ранжирование результатов** по релевантности
- **REST API для управления** индексацией и поиском
- **Статистика** по индексированным сайтам

### 🛠 Технологии
- **Java 17+**
- **Spring Boot 3.1.5**
- **Hibernate ORM**
- **Jsoup (парсинг HTML)**
- **Lucene Morphology (лемматизация)**
- **MySQL/PostgreSQL (база данных)**
- **Maven (сборка проекта)**

## 🚀 Быстрый старт

### Предварительные требования

- **Java 17 или новее**
- **Maven 3.8+**
- **MySQL 8.0+ (или другая поддерживаемая СУБД)**

### Установка и запуск

1. Клонируйте репозиторий:
   ```bash
   git clone https://github.com/DanilPV/SearchEngineJava.git
   cd search-engine
   
2. Настройте базу данных в application.yaml:

   ``` spring:
    datasource:
        username: root
        password: password
        url: jdbc:mysql://localhost:8003/search_engine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true

3. Соберите проект
   ```
   mvn clean install
   ```  
    
 4. Запустите приложение:

   ````
   java -jar target/SearchEngine-1.0-SNAPSHOT.jar
   ````

## 📌 API Endpoints

 
### Управление индексацией

- GET /api/startIndexing - Запуск полной индексации всех сайтов
- GET /api/stopIndexing - Остановка текущей индексации
- POST /api/indexPage - Индексация конкретной страницы

### Статистика
- GET /api/statistics - Получение статистики по индексации

### Поиск
- GET /api/search?query=... - Поиск по всем сайтам

- GET /api/search?query=...&site=url - Поиск по конкретному сайту
  


### 📂 Структура проекта

````
search-engine/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── searchengine/
│   │   │       ├── classesError/         # Классы для вывода ошибок
│   │   │       ├── config/               # Классы конфигураций
│   │   │       ├── controllers/          # API контроллеры
│   │   │       ├── dto/                  # Data Transfer Objects
│   │   │       ├── enums/                # Сущности ENUM
│   │   │       ├── model/                # Сущности БД
│   │   │       ├── repository/           # Репозитории JPA
│   │   │       ├── serviceRepositoryes/  # Сервисы Репозиторий JPA
│   │   │       ├── services/             # Бизнес-логика
│   │   │       ├── springContext/        # Доступ к Spring Контексту
│   │   │       └── servicesInterface/    # Интерфейсы сервисов
│   │   └── resources/
│   │       ├── db.changelog              # Файл миграции liquibase
│   │       ├── templates                 # Html файлы
│   │       ├── static/                   # Статические ресурсы
│   └──     └── application.yaml          # Конфигурация
├── pom.xml                               # Maven конфигурация
└── README.md                             # Этот файл
````
 
### Автор:
- Примеров Данил – ***pdv_90@mail.ru***

### Проект создан в рамках Курсa:
- Java-разработчик  в 2025 году.

## Этот README содержит:
- **1. Бейджи для визуального выделения ключевой информации**
- **2. Четкое описание возможностей**
- **3. Инструкции по установке**
- **4. Документацию API**
- **5. Описание технологического стека**
- **6. Структуру проекта**

 