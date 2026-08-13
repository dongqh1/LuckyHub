param(
    [string] $SchemaName = ("luckyhub_phase6_verify_" + [Guid]::NewGuid().ToString("N"))
)

$ErrorActionPreference = 'Stop'

if ($SchemaName -notmatch '^luckyhub_phase6_verify_[0-9a-f]{32}$') {
    throw '临时数据库名称不安全：必须使用 luckyhub_phase6_verify_ 加 32 位十六进制 GUID。'
}

$envFile = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw '缺少 .env，无法安全读取本地 MySQL 验证配置。'
}

$settings = @{}
Get-Content -Encoding UTF8 -LiteralPath $envFile |
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

$mysqlContainer = 'luckyhub-mysql'
$sqlUser = "'$applicationUser'@'%'"
$databaseEnvironmentExisted = Test-Path Env:MYSQL_DATABASE
$previousDatabase = $env:MYSQL_DATABASE
$schemaCreated = $false
$grantCreated = $false
$operationError = $null
$cleanupErrors = [System.Collections.Generic.List[string]]::new()

try {
    $existingSchema = & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
        "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$SchemaName';"
    if ($LASTEXITCODE -ne 0) { throw '检查临时数据库名称冲突失败。' }
    if ($existingSchema -ne '0') { throw "拒绝复用已存在的临时数据库：$SchemaName" }

    & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
        "CREATE DATABASE ``$SchemaName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    if ($LASTEXITCODE -ne 0) { throw '创建临时数据库失败。' }
    $schemaCreated = $true

    & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
        "GRANT ALL PRIVILEGES ON ``$SchemaName``.* TO $sqlUser;"
    if ($LASTEXITCODE -ne 0) { throw '临时数据库授权失败。' }
    $grantCreated = $true

    & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse 'FLUSH PRIVILEGES;'
    if ($LASTEXITCODE -ne 0) { throw '刷新临时数据库授权失败。' }

    $env:MYSQL_DATABASE = $SchemaName
    & pwsh -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Invoke-Maven.ps1') `
        '-Dtest=ShippingSchemaContractTests,DatabaseSchemaMigrationTests,LotteryMigrationGuardTests' test
    if ($LASTEXITCODE -ne 0) { throw 'V1 到 V17 空库迁移验证失败。' }

    $summary = & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
        "SELECT CONCAT(MAX(CAST(version AS UNSIGNED)), '|', (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$SchemaName' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history')) FROM ``$SchemaName``.flyway_schema_history WHERE success=1;"
    if ($LASTEXITCODE -ne 0 -or $summary -ne '17|48') {
        throw "临时数据库验收摘要不符：$summary"
    }

    Write-Host 'Fresh migration verified: V17, 48 business tables.'
}
catch {
    $operationError = $_
}
finally {
    if ($databaseEnvironmentExisted) {
        $env:MYSQL_DATABASE = $previousDatabase
    }
    else {
        Remove-Item Env:MYSQL_DATABASE -ErrorAction SilentlyContinue
    }

    if ($grantCreated) {
        & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
            "REVOKE ALL PRIVILEGES ON ``$SchemaName``.* FROM $sqlUser; FLUSH PRIVILEGES;" 2>$null
        if ($LASTEXITCODE -ne 0) { $cleanupErrors.Add("临时授权清理失败：$SchemaName") }
    }
    if ($schemaCreated) {
        & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
            "DROP DATABASE ``$SchemaName``;" 2>$null
        if ($LASTEXITCODE -ne 0) { $cleanupErrors.Add("临时数据库清理失败：$SchemaName") }
    }

    if ($schemaCreated -or $grantCreated) {
        $residue = & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -Nse `
            "SELECT CONCAT((SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$SchemaName'), '|', (SELECT COUNT(*) FROM information_schema.schema_privileges WHERE table_schema='$SchemaName' AND grantee=CONCAT(CHAR(39),'$applicationUser',CHAR(39),'@',CHAR(39),'%',CHAR(39))));" 2>$null
        if ($LASTEXITCODE -ne 0) {
            $cleanupErrors.Add("临时资源残留检查失败：$SchemaName")
        }
        elseif ($residue -ne '0|0') {
            $cleanupErrors.Add("临时资源仍有残留：$SchemaName ($residue)")
        }
    }
}

if ($operationError -and $cleanupErrors.Count -gt 0) {
    throw "验证失败：$($operationError.Exception.Message)；清理也失败：$($cleanupErrors -join '；')"
}
if ($operationError) { throw $operationError }
if ($cleanupErrors.Count -gt 0) { throw ($cleanupErrors -join '；') }

Write-Host "Temporary grant revoked, schema dropped, and residue check passed: $SchemaName"
