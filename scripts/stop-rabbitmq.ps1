$ErrorActionPreference = "Stop"

# Use the same Erlang and data paths so the CLI addresses the correct node.
$env:ERLANG_HOME = "G:\develop\erlang-27.3.4.16"
$env:RABBITMQ_BASE = "G:\develop\rabbitmq-data"
$env:RABBITMQ_MNESIA_BASE = "G:\develop\rabbitmq-data\db"
$env:RABBITMQ_LOG_BASE = "G:\develop\rabbitmq-logs"

$listening = Get-NetTCPConnection -LocalPort 5672 -State Listen -ErrorAction SilentlyContinue
if (-not $listening) {
    Write-Host "RabbitMQ is not running."
    exit 0
}

# Stop gracefully so RabbitMQ can flush data before the node exits.
$control = "G:\develop\rabbitmq_server-4.3.5\sbin\rabbitmqctl.bat"
& $control stop
if ($LASTEXITCODE -ne 0) {
    throw "rabbitmqctl failed to stop the node. Exit code: $LASTEXITCODE"
}

Write-Host "RabbitMQ stopped."
