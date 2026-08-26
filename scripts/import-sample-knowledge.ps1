param(
    # Admin accessToken; PET_ASSISTANT_ADMIN_TOKEN is also supported.
    [string]$AccessToken = $env:PET_ASSISTANT_ADMIN_TOKEN
)

# Resolve paths from this script instead of depending on the CMD working directory.
$projectRoot = Split-Path -Parent $PSScriptRoot
$python = Join-Path $projectRoot "ai-service\.venv\Scripts\python.exe"
$script = Join-Path $projectRoot "ai-service\scripts\import_sample.py"

if ([string]::IsNullOrWhiteSpace($AccessToken)) {
    Write-Error "Missing admin access token. Pass -AccessToken or set PET_ASSISTANT_ADMIN_TOKEN."
    exit 1
}

# Week 11 only creates a review submission; it never writes directly to RAG.
& $python $script --business-url "http://localhost:8080" --access-token $AccessToken
