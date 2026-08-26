$ErrorActionPreference = "Stop"

# Use fixed paths under G:\develop and do not depend on the system PATH.
$erlangRoot = "G:\develop\erlang-27.3.4.16"
$rabbitRoot = "G:\develop\rabbitmq_server-4.3.5"
$rabbitData = "G:\develop\rabbitmq-data"
$rabbitLogs = "G:\develop\rabbitmq-logs"

$env:ERLANG_HOME = $erlangRoot
$env:RABBITMQ_BASE = $rabbitData
$env:RABBITMQ_MNESIA_BASE = Join-Path $rabbitData "db"
$env:RABBITMQ_LOG_BASE = $rabbitLogs

# Return successfully when the node is already running.
$listening = Get-NetTCPConnection -LocalPort 5672 -State Listen -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host "RabbitMQ is already running on AMQP port 5672."
    exit 0
}

New-Item -ItemType Directory -Path $rabbitData, $rabbitLogs -Force | Out-Null
$server = Join-Path $rabbitRoot "sbin\rabbitmq-server.bat"
$stdout = Join-Path $rabbitLogs "console.out.log"
$stderr = Join-Path $rabbitLogs "console.err.log"

# Start in the background and redirect console output to the log directory.
$process = Start-Process -FilePath $server `
    -WorkingDirectory (Split-Path $server) `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) {
        throw "RabbitMQ exited with code $($process.ExitCode). See $stderr"
    }
    if (Get-NetTCPConnection -LocalPort 5672 -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "RabbitMQ started: AMQP 5672, management UI http://localhost:15672"
        exit 0
    }
}

throw "RabbitMQ did not listen on 5672 within 30 seconds. See $stdout and $stderr"
