# FinScore PDF

Upload an annual-report PDF and get key ratios plus a transparent, rule-based FinScore (0-100).

**Stack:** HTML + CSS + JavaScript (frontend) · Java 26 and Spring Boot 4.1.0 (backend) · Apache PDFBox 3 (PDF reading) · SQLite (storage).

## Requirements
1. **JDK 26** - check with `java -version` (must say 26)
2. **Internet on the first run** (downloads Maven 3.9.11 and the libraries once)

Maven is included: use `mvnw` (Mac/Linux) or `mvnw.cmd` (Windows). You do not need to install Maven.

## Run
Open the folder that contains `pom.xml`, then:

    Windows:    run.bat           (or: mvnw.cmd spring-boot:run)
    Mac/Linux:  ./run.sh          (or: ./mvnw spring-boot:run)

Open **http://localhost:8080**

## Project structure
    pom.xml
    mvnw / mvnw.cmd / .mvn/        Maven wrapper
    src/main/java/com/finscore/
        FinScoreApplication.java   entry point
        ApiController.java         REST API (/api/upload, /api/score, /api/reports, /api/compare)
        PdfExtractor.java          reads the PDF with PDFBox
        ScoringService.java        scoring rules
        ReportRepository.java      SQLite storage
    src/main/resources/static/     frontend (index.html, style.css, app.js)
    src/main/resources/application.properties

## Troubleshooting
- **"FinScore needs JDK 26"** -> JAVA_HOME points to an older JDK. Set JAVA_HOME to your JDK 26 folder, reopen the terminal, check `java -version`.
- **"invalid target release: 26"** -> same cause: the JDK running Maven is older than 26.
- **"Port 8080 already in use"** -> change `server.port` in `application.properties`.
- **Download fails on first run** -> check internet/proxy, then run again.
- **Red errors in VS Code** -> install "Extension Pack for Java" and open the folder that contains `pom.xml`.

The frontend also works on its own (static hosting such as Vercel): it reads PDFs in the browser and uses the Spring Boot server automatically when it is running.

## Push to GitHub

    git init
    git add .
    git commit -m "FinScore - Spring Boot, Java 26"
    git branch -M main
    git remote add origin https://github.com/YOUR-USERNAME/finscore.git
    git push -u origin main

If `./mvnw` says "permission denied" after cloning on Mac/Linux, run `chmod +x mvnw run.sh`.
The included GitHub Action (`.github/workflows/build.yml`) builds the project with JDK 26 on every push.
