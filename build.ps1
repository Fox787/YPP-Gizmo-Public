# Builds Gizmo.jar from src/ into dist/.
# Usage:  powershell -ExecutionPolicy Bypass -File build.ps1 [-Jdk "C:\Program Files\Java\jdk-17"]
# Copy dist\Gizmo.jar into "%LOCALAPPDATA%\Puzzle Pirates\app\" and use -javaagent:Gizmo.jar.
param(
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-17" }),
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$')]
    [string]$PackageRoot = "main.java"
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$out = Join-Path $root "out"
$dist = Join-Path $root "dist"

if (Test-Path -LiteralPath $out) {
    $directory = Get-Item -LiteralPath $out -Force
    if ($directory.FullName -ne $out -or !$directory.PSIsContainer -or ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Unexpected build directory: $out"
    }
    Remove-Item -LiteralPath $directory.FullName -Recurse -Force
}
New-Item -ItemType Directory -Force $out, $dist | Out-Null

$sourceRoot = Join-Path $root "src\main\java"
$sources = (Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter *.java).FullName
$manifest = Join-Path $root "src\META-INF\MANIFEST.MF"
if ($PackageRoot -ne "main.java") {
    # Existing GizmoBeta installations keep their saved profiles and serialized reports by
    # retaining their runtime package. Generate copies; the maintained source stays unchanged.
    $generatedRoot = Join-Path $root ("out-src\" + $PackageRoot.Replace('.', '\'))
    $generatedSources = @()
    foreach ($source in $sources) {
        $relative = $source.Substring($sourceRoot.Length).TrimStart('\', '/')
        $generated = Join-Path $generatedRoot $relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($generated)) | Out-Null
        [IO.File]::WriteAllText($generated, [IO.File]::ReadAllText($source).Replace("main.java.", "$PackageRoot."), [Text.UTF8Encoding]::new($false))
        $generatedSources += $generated
    }
    $sources = $generatedSources
    $generatedManifest = Join-Path $root "out-src\MANIFEST.MF"
    [IO.File]::WriteAllText($generatedManifest, [IO.File]::ReadAllText($manifest).Replace("main.java.", "$PackageRoot."), [Text.UTF8Encoding]::new($false))
    $manifest = $generatedManifest
}
# --release 16 matches the class version of the Gizmo.jar the client was already running.
& (Join-Path $Jdk "bin\javac.exe") --release 16 -g -encoding UTF-8 -cp (Join-Path $root "lib\jnativehook-2.2.2.jar") -d $out $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# Bundle the cross-platform hotkey helper's classes and native libraries in the single jar.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$hook = [IO.Compression.ZipFile]::OpenRead((Join-Path $root "lib\jnativehook-2.2.2.jar"))
try {
    foreach ($entry in $hook.Entries) {
        if (!$entry.FullName.StartsWith("com/github/kwhat/jnativehook/") -or $entry.FullName.EndsWith("/")) { continue }
        $target = [IO.Path]::GetFullPath((Join-Path $out $entry.FullName))
        if (!$target.StartsWith($out + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "Invalid dependency entry" }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
} finally { $hook.Dispose() }

# Bundled resources (Thalassa's alert sounds) go in the jar as they are.
$res = Join-Path $root "src\main\resources"
if (Test-Path $res) { Copy-Item -Recurse -Force (Join-Path $res "*") $out }

$jar = Join-Path $dist "Gizmo.jar"
& (Join-Path $Jdk "bin\jar.exe") cfm $jar $manifest -C $out .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "Built $jar"
