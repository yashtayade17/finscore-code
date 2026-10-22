@echo off
cd /d "%~dp0"
echo Starting FinScore on http://localhost:8080 ...
call mvnw.cmd spring-boot:run
pause
