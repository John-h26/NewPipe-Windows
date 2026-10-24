@echo off
rem Lance NewPipe Windows depuis les sources.
rem Utilise le JDK portable de _tools s'il existe, sinon JAVA_HOME.
setlocal
chcp 65001 >nul
cd /d "%~dp0"

for /d %%D in ("_tools\jdk-21*") do set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME (
  echo JDK 21 introuvable : installez-le ou definissez JAVA_HOME.
  exit /b 1
)

call gradlew.bat :desktopApp:run %*
