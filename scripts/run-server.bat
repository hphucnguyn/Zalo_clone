@echo off
chcp 65001 >nul
cd /d "%~dp0\.."

if not exist ".env" (
    echo [ERROR] Không tìm thấy file .env!
    exit /b 1
)

for /f "usebackq tokens=1,2 delims==" %%A in (".env") do (
    set "%%A=%%B"
)

echo [INFO] Đang khởi động Zalo Server...
java -jar zalo-server\target\zalo-server.jar
