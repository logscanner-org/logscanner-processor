# Log Scanner Application

This is a Java Spring Boot application that allows users to upload log files and provides tools to filter, sort, and search through the logs efficiently.

## Features

- Upload log files via a user-friendly interface
- Filter logs by date, level, or custom fields
- Sort logs by timestamp or other attributes
- Search logs using keywords or regular expressions
- RESTful API endpoints for integration

## Technologies Used

- Java
- Spring Boot
- Maven

## Getting Started

1. **Clone the repository:**
   ```
   git clone git@github.com:logscanner-org/logscanner.git
   cd logscanner
   ```

2. **Build the project:**
   ```
   mvn clean install
   ```

3. **Run the application:**
   ```
   mvn spring-boot:run
   ```

4. **Access the application:**
   - Open your browser and go to `http://localhost:8080`

## API Endpoints

- `POST /logs/upload` - Upload a log file
- `GET /logs` - Retrieve, filter, sort, and search logs

## Configuration

- Configure application properties in `src/main/resources/application.properties` as needed.

## License

This project is licensed under the Apache-2.0 license.
