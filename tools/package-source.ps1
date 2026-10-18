$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$stage = Join-Path $root ".tools\stage"
$zip = Join-Path (Split-Path -Parent $root) "companion-android-src.zip"

if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force $stage | Out-Null

$items = @(
    "app",
    "gradle",
    "tools",
    "docs",
    ".github",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "README.md",
    ".gitignore",
    "setup-gradle.ps1",
    "build-local.ps1"
)

foreach ($item in $items) {
    $source = Join-Path $root $item
    if (!(Test-Path $source)) { continue }
    Copy-Item -Path $source -Destination $stage -Recurse -Force
}

# 调试产物不进入发布包
Remove-Item (Join-Path $stage "tools\_probe") -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $stage "tools\.tools") -Recurse -Force -ErrorAction SilentlyContinue

# 不要把本机的 SDK 路径带进压缩包
Remove-Item (Join-Path $stage "local.properties") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $stage "app\build") -Recurse -Force -ErrorAction SilentlyContinue

if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zip -CompressionLevel Fastest
Remove-Item $stage -Recurse -Force

"ZIP: $zip"
"Size: {0:N1} MB" -f ((Get-Item $zip).Length / 1MB)
