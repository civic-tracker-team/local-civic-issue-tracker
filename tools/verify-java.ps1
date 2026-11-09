param(
    [string]$MavenCommand = 'mvn',
    [string]$MavenRepository = '',
    [string]$PostgresImage = 'postgres:18.6'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$token = [Guid]::NewGuid().ToString('N')
$container = "lcit-records-test-$token"
$purpose = 'lcit-records-verification'
$outputRoot = Join-Path $repoRoot ".local/testing/$token"
$names = @('POSTGRES_PASSWORD', 'RECORDS_DB_URL', 'RECORDS_DB_USER',
    'RECORDS_DB_PASSWORD', 'RECORDS_MIGRATION_USER', 'RECORDS_MIGRATION_PASSWORD',
    'RECORDS_STORAGE_ROOT')
$previous = @{}
foreach ($name in $names) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Invoke-DockerChecked {
    param([string[]]$Arguments)
    $result = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Docker command failed.' }
    return $result
}

$created = $false
try {
    Invoke-DockerChecked -Arguments @('info', '--format', '{{.ServerVersion}}') | Out-Null
    New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
    $env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
    $env:RECORDS_DB_USER = 'lcit_records_runtime'
    $env:RECORDS_DB_PASSWORD = [Guid]::NewGuid().ToString('N')
    $env:RECORDS_MIGRATION_USER = 'lcit_records_migrator'
    $env:RECORDS_MIGRATION_PASSWORD = [Guid]::NewGuid().ToString('N')
    Invoke-DockerChecked -Arguments @('run', '-d', '--name', $container,
        '--label', "lcit.purpose=$purpose", '--env', 'POSTGRES_PASSWORD',
        '--env', 'POSTGRES_DB=lcit_records_test', '-p', '127.0.0.1::5432',
        $PostgresImage) | Out-Null
    $created = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $container pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Isolated PostgreSQL did not become ready.' }

    $bootstrap = Get-Content (Join-Path $repoRoot 'infra/postgres/records-bootstrap.sql') -Raw
    $bootstrap | & docker exec -i $container psql -U postgres -d lcit_records_test `
        -v ON_ERROR_STOP=1 -v "migration_role=$env:RECORDS_MIGRATION_USER" `
        -v "migration_password=$env:RECORDS_MIGRATION_PASSWORD" `
        -v "runtime_role=$env:RECORDS_DB_USER" -v "runtime_password=$env:RECORDS_DB_PASSWORD"
    if ($LASTEXITCODE -ne 0) { throw 'Records database provisioning failed.' }
    'CREATE SCHEMA analysis; CREATE SCHEMA coordinator; REVOKE ALL ON SCHEMA analysis,coordinator FROM PUBLIC;' |
        & docker exec -i $container psql -U postgres -d lcit_records_test -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Permission-test schema setup failed.' }

    $binding = Invoke-DockerChecked -Arguments @('port', $container, '5432/tcp')
    $port = ($binding.Trim() -split ':')[-1]
    $env:RECORDS_DB_URL = "jdbc:postgresql://127.0.0.1:$port/lcit_records_test"
    $env:RECORDS_STORAGE_ROOT = Join-Path $outputRoot 'storage'
    $mavenArguments = @('--batch-mode', '--no-transfer-progress', 'clean', 'verify')
    if ($MavenRepository) { $mavenArguments += "-Dmaven.repo.local=$MavenRepository" }
    Push-Location $repoRoot
    try {
        & $MavenCommand @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw 'Java verification failed.' }
    } finally {
        Pop-Location
    }
    Write-Output 'Java verification passed against isolated PostgreSQL.'
    Write-Output 'Synthetic storage fixtures remain in the ignored local testing directory.'
} finally {
    try {
        if ($created) {
            $label = Invoke-DockerChecked -Arguments @('inspect', '--format',
                '{{index .Config.Labels "lcit.purpose"}}', $container)
            if ($label.Trim() -ne $purpose) { throw 'Container ownership check failed.' }
            Invoke-DockerChecked -Arguments @('rm', '--force', '--volumes', $container) | Out-Null
        }
    } finally {
        foreach ($name in $names) {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
}
