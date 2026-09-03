param([switch]$Launch)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdk = 'C:\Program Files\Java\jdk-22\bin'
$sourceRoot = Join-Path $projectRoot 'src\wordbook3'
$binDirectory = Join-Path $projectRoot 'bin'
$binPackage = Join-Path $binDirectory 'wordbook3'
$sqliteJar = Join-Path $projectRoot 'lib\sqlite-jdbc-3.46.1.3.jar'
$jlayerJar = Join-Path $projectRoot 'lib\jlayer-1.0.1.jar'

foreach ($requiredPath in @(
    (Join-Path $jdk 'javac.exe'),
    (Join-Path $jdk 'java.exe'),
    $sourceRoot,
    $sqliteJar,
    $jlayerJar
)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "缺少 wordbook3 编译所需文件：$requiredPath"
    }
}

$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' | ForEach-Object FullName)
if ($sourceFiles.Count -eq 0) {
    throw "未找到 wordbook3 Java 源文件：$sourceRoot"
}

New-Item -ItemType Directory -Force $binDirectory | Out-Null
if (Test-Path -LiteralPath $binPackage) {
    Remove-Item -LiteralPath $binPackage -Recurse -Force
}
New-Item -ItemType Directory -Force $binPackage | Out-Null

$dependencyClasspath = "$sqliteJar;$jlayerJar"
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp $dependencyClasspath -d $binDirectory $sourceFiles
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Copy-Item -LiteralPath (Join-Path $sourceRoot 'data') -Destination $binPackage -Recurse -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot 'audio') -Destination $binPackage -Recurse -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot 'CODE_EXPLANATION.md') -Destination $binPackage -Force

if ($Launch) {
    & (Join-Path $jdk 'java.exe') -cp "$binDirectory;$dependencyClasspath" wordbook3.Wordbook3Application
}
