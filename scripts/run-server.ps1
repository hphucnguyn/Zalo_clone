$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Test-Path ".env")) {
    Write-Error "Không tìm thấy file .env!"
    exit 1
}

# Đọc file .env và nạp vào Environment của phiên hiện tại
Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $key = $parts[0].Trim()
        $val = $parts[1].Trim()
        [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

Write-Host "[INFO] Đã nạp biến môi trường từ .env. Đang chạy server..." -ForegroundColor Green
java -jar zalo-server\target\zalo-server.jar
