# LogScanner - Quick Start Guide

## 📦 Download Complete Project

**→ [Download Complete Project ZIP](computer:///mnt/user-data/outputs/logscanner-complete-project.zip)**

This ZIP file contains the entire Spring Boot application with all source code, configurations, and documentation.

## 🚀 How to Run

### Step 1: Extract the Project
```bash
unzip logscanner-complete-project.zip
cd logscanner-main
```

### Step 2: Start Required Services (Elasticsearch & Redis)
```bash
docker-compose up -d
```

### Step 3: Build and Run the Application
```bash
# Using Maven Wrapper (included)
./mvnw clean package
./mvnw spring-boot:run

# OR using system Maven
mvn clean package
mvn spring-boot:run

# OR run the JAR directly after building
java -jar target/logscanner-0.1.0.jar
```

### Step 4: Access the Application
- **API Base URL**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health

## 📁 Project Structure

```
logscanner-main/
├── src/
│   └── main/
│       ├── java/com/star/logscanner/
│       │   ├── config/          # Configuration classes
│       │   ├── controller/      # REST endpoints
│       │   ├── dto/            # Data transfer objects
│       │   ├── entity/         # Domain entities
│       │   ├── exception/      # Custom exceptions
│       │   ├── parser/         # Log parsing logic
│       │   ├── repository/     # Data access layer
│       │   ├── service/        # Business logic
│       │   └── task/           # Scheduled tasks
│       └── resources/
│           ├── application.yml  # Application configuration
│           └── application.properties
├── pom.xml                     # Maven dependencies
├── docker-compose.yml          # Infrastructure setup
└── README.md                   # Full documentation
```

## 🔑 Key Features

1. **Upload Log Files** (up to 50MB)
   - Supports JSON, CSV, TXT, LOG formats
   - Asynchronous processing with progress tracking

2. **Search & Filter**
   - Full-text search
   - Filter by log level, date range, source
   - Sort and paginate results

3. **Real-time Processing Status**
   - Track upload progress (0-100%)
   - Get processing statistics

## 📝 Example API Calls

### Upload a Log File
```bash
curl -X POST http://localhost:8080/logs/upload \
  -F "logfile=@your-log-file.log" \
  -F "timestampFormat=yyyy-MM-dd HH:mm:ss.SSS"
```

### Check Processing Status
```bash
curl http://localhost:8080/logs/status/{jobId}
```

### Search Logs
```bash
curl "http://localhost:8080/logs/search?query=error&levels=ERROR,WARN&page=0&size=20"
```

## 🔧 Configuration

Key settings in `application.yml`:
- Max file size: 50MB
- Elasticsearch URL: http://localhost:9200
- Redis: localhost:6379
- Batch size: 1000 lines
- Retention: 30 days

## 📋 Prerequisites

- Java 17 or higher
- Docker & Docker Compose
- Maven 3.6+
- 4GB RAM minimum

## 🆘 Troubleshooting

1. **Port Already in Use**
   - Change ports in application.yml and docker-compose.yml

2. **Elasticsearch Not Starting**
   ```bash
   docker logs logscanner-elasticsearch
   # May need to increase Docker memory allocation
   ```

3. **Connection Refused**
   - Ensure Docker services are running:
   ```bash
   docker ps
   docker-compose up -d
   ```

## 📚 Full Documentation

See the included files:
- `README.md` - Complete documentation
- `LogScanner_Implementation_Summary.md` - Implementation details
- Access Swagger UI for interactive API documentation

---
**Ready to process your logs!** 🚀
