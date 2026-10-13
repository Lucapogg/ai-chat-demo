#Requires -Version 5.1
<#
.SYNOPSIS
  AI 饮食健康 demo 一键启动 (后端 + 前端 + 自动开浏览器)

.DESCRIPTION
  - 从同目录 .env 文件读取 AI_PRIMARY_API_KEY / AI_SECONDARY_API_KEY
  - 后端: Maven Spring Boot (端口 8081)
  - 前端: Vite dev server (端口 5173)
  - 启动后自动打开浏览器到 http://localhost:5173

.NOTES
  需要:
    - JDK 21+ (本机: C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.5)
    - Maven (本机: D:\software\apache-maven-3.9.11)
    - Node.js 18+
#>

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir  = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend"

# ---- 加载 .env (如有) ----
$envFile = Join-Path $ProjectRoot ".env"
if (Test-Path $envFile) {
    Write-Host "[start-demo] loading .env ..."
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.+?)\s*$') {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
            Write-Host ("  {0} = ***hidden***" -f $matches[1])
        }
    }
} else {
    Write-Host "[start-demo] .env not found -- using current session env vars"
}

if (-not $env:AI_PRIMARY_API_KEY) {
    Write-Host "[start-demo] ERROR: AI_PRIMARY_API_KEY not set. Create a .env file or `$env:AI_PRIMARY_API_KEY='sk-...'" -ForegroundColor Red
    exit 1
}

# ---- 选 JDK 21 ----
$JavaHome = "C:\Program Files\Java\jdk-21_windows-x64_bin\jdk-21.0.5"
if (Test-Path (Join-Path $JavaHome "bin\java.exe")) {
    $env:JAVA_HOME = $JavaHome
} else {
    Write-Host "[start-demo] WARN: default JDK 21 path not found, falling back to PATH"
}

# ---- 启动后端 (新窗口) ----
Write-Host "[start-demo] starting backend on :8081 ..."
$BackendCmd = "cd /d `"$BackendDir`" && `"$env:JAVA_HOME\bin\java.exe`" -version && `"D:\software\apache-maven-3.9.11\bin\mvn.cmd`" -o spring-boot:run"
Start-Process cmd.exe -ArgumentList '/k', $BackendCmd -WindowStyle Normal

# ---- 启动前端 (新窗口) ----
Write-Host "[start-demo] starting frontend on :5173 ..."
$FrontendCmd = "cd /d `"$FrontendDir`" && npm run dev"
Start-Process cmd.exe -ArgumentList '/k', $FrontendCmd -WindowStyle Normal

# ---- 等服务起来,自动开浏览器 ----
Write-Host "[start-demo] waiting for services ..."
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8081/api/chat/health" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host ("  waiting ... {0}s" -f ($i * 2))
}

if ($ready) {
    Write-Host "[start-demo] ready! opening browser ..." -ForegroundColor Green
    Start-Process "http://localhost:5173"
} else {
    Write-Host "[start-demo] backend not ready after 60s -- check the backend window for errors" -ForegroundColor Yellow
    Start-Process "http://localhost:5173"
}
