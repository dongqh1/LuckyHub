param(
    [string] $SchemaName = ("luckyhub_phase4_verify_" + [Guid]::NewGuid().ToString("N"))
)

$ErrorActionPreference = 'Stop'

if ($SchemaName -notmatch '^luckyhub_phase4_verify_[a-zA-Z0-9_]+$') {
    throw '临时数据库名称不安全。'
}

$settings = @{}
Get-Content -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot '..\.env') |
    Where-Object { $_ -match '^[A-Z0-9_]+=' } |
    ForEach-Object {
        $key, $value = $_ -split '=', 2
        $settings[$key] = $value.Trim()
    }

$rootPassword = $settings['MYSQL_ROOT_PASSWORD']
$applicationUser = $settings['MYSQL_USER']
if ([string]::IsNullOrWhiteSpace($rootPassword) -or $applicationUser -notmatch '^[a-zA-Z0-9_]+$') {
    throw '缺少安全的 MYSQL_ROOT_PASSWORD 或 MYSQL_USER。'
}

$previousDatabase = $env:MYSQL_DATABASE
$sqlUser = "'$applicationUser'@'%'"
$schemaCreated = $false

try {
    & docker exec -e "MYSQL_PWD=$rootPassword" luckyhub-mysql mysql -uroot -Nse `
        "CREATE DATABASE ``$SchemaName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    if ($LASTEXITCODE -ne 0) { throw '创建临时数据库失败。' }
    $schemaCreated = $true

    & docker exec -e "MYSQL_PWD=$rootPassword" luckyhub-mysql mysql -uroot -Nse `
        "GRANT ALL PRIVILEGES ON ``$SchemaName``.* TO $sqlUser; FLUSH PRIVILEGES;"
    if ($LASTEXITCODE -ne 0) { throw '临时数据库授权失败。' }

    $env:MYSQL_DATABASE = $SchemaName
    & pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Invoke-Maven.ps1') `
        '-Dtest=FulfillmentSchemaContractTests,DatabaseSchemaMigrationTests' test
    if ($LASTEXITCODE -ne 0) { throw 'V1 到 V15 空库迁移验证失败。' }

    $summary = & docker exec -e "MYSQL_PWD=$rootPassword" luckyhub-mysql mysql -uroot -Nse `
        "SELECT CONCAT(MAX(CAST(version AS UNSIGNED)), '|', (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SchemaName' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history')) FROM ``$SchemaName``.flyway_schema_history WHERE success=1;"
    if ($LASTEXITCODE -ne 0 -or $summary -ne '15|39') {
        throw "临时数据库验收摘要不符：$summary"
    }
    Write-Host "Fresh migration verified: V15, 39 business tables."
}
finally {
    $env:MYSQL_DATABASE = $previousDatabase
    if ($schemaCreated) {
        & docker exec -e "MYSQL_PWD=$rootPassword" luckyhub-mysql mysql -uroot -Nse `
            "REVOKE ALL PRIVILEGES ON ``$SchemaName``.* FROM $sqlUser; DROP DATABASE IF EXISTS ``$SchemaName``; FLUSH PRIVILEGES;" 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "临时授权或数据库清理失败：$SchemaName"
        }
        Write-Host "Temporary grant revoked and schema dropped: $SchemaName"
    }
}
