@echo off
REM Dung: set CHAT_CERT_PASSWORD=MatKhauManh && scripts\gen-certs.bat
if "%CHAT_CERT_PASSWORD%"=="" (echo Hay set CHAT_CERT_PASSWORD truoc & exit /b 1)
set HOST=%1
if "%HOST%"=="" set HOST=localhost

if not exist zalo-server\certificates mkdir zalo-server\certificates
if not exist zalo-client\certificates mkdir zalo-client\certificates

keytool -genkeypair -alias server -keyalg RSA -keysize 2048 ^
  -keystore zalo-server\certificates\server.p12 -storetype PKCS12 ^
  -storepass %CHAT_CERT_PASSWORD% -validity 365 ^
  -dname "CN=%HOST%, OU=Lab, O=ZaloClone, L=HCM, C=VN" ^
  -ext "SAN=dns:%HOST%,ip:127.0.0.1"

keytool -exportcert -alias server ^
  -keystore zalo-server\certificates\server.p12 -storetype PKCS12 ^
  -storepass %CHAT_CERT_PASSWORD% -rfc -file zalo-client\certificates\server.crt

keytool -list -keystore zalo-server\certificates\server.p12 -storetype PKCS12 -storepass %CHAT_CERT_PASSWORD%
