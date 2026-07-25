param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments = @('--version')
)

$jdkHome = 'C:\Program Files\Java\jdk-17.0.5'
$javaExecutable = Join-Path $jdkHome 'bin\java.exe'

if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "LuckyHub requires Java 17 at $jdkHome"
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$(Join-Path $jdkHome 'bin');$env:Path"

& mvn @MavenArguments
exit $LASTEXITCODE
