# Viewpoint FPS Optimization - build script (PowerShell, no Gradle needed)
# Compiles the Java sources against the compile-only stubs in tools/stubs
# and packages ONLY the dev/vpfps package into the mod's jar.
#
# Usage:  .\build.ps1                 (needs javac 17+ on PATH or JAVA_HOME)
#         .\build.ps1 -JavaC "C:\path\to\javac.exe"
#
# If you have the real ZombieBuddy.jar you can compile against it instead
# of the stubs:  javac --release 17 -cp ZombieBuddy.jar -d classes <sources>

param(
    [string]$JavaC,
    [string]$OutputJar
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcMain = Join-Path $root "42\media\java\client\src"
$stubs   = Join-Path $root "tools\stubs"
$build   = Join-Path $root "42\media\java\client\build"
$classes = Join-Path $build "classes"
$jarDir  = Join-Path $build "libs"

# --- locate javac ---
if (-not $JavaC) {
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { $JavaC = $cmd.Source }
    elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        $JavaC = Join-Path $env:JAVA_HOME "bin\javac.exe"
    } else {
        throw "javac not found. Install a JDK 17+ or pass -JavaC <path to javac.exe>"
    }
}
$jarExe = $null

# javapath shims are symlinks to the real JDK home - resolve them first
try {
    $item = Get-Item $JavaC -ErrorAction Stop
    $target = $item.Target
    if ($target) {
        $realJavac = $target.ToString()
        $candidate = Join-Path (Split-Path -Parent $realJavac) "jar.exe"
        if (Test-Path $candidate) { $jarExe = $candidate }
    }
} catch { }

if (-not $jarExe) {
    $candidate = Join-Path (Split-Path -Parent $JavaC) "jar.exe"
    if (Test-Path $candidate) { $jarExe = $candidate }
}
if (-not $jarExe) {
    # scan common JDK locations
    foreach ($base in @("C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium",
                        "C:\Program Files\Microsoft", "C:\Program Files\Zulu", "C:\Program Files\Amazon Corretto")) {
        $hits = Get-ChildItem -Path $base -Filter "jar.exe" -Recurse -Depth 2 -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
        if ($hits) { $jarExe = $hits; break }
    }
}
if (-not $jarExe) {
    $jarCmd = Get-Command jar -ErrorAction SilentlyContinue
    if ($jarCmd) { $jarExe = $jarCmd.Source }
}
if (-not $jarExe) {
    throw "jar.exe not found (looked next to javac: $JavaC, and in common JDK folders)"
}

# --- compile ---
$sources = @(Get-ChildItem -Path $srcMain, $stubs -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName)
Write-Host "compiling $($sources.Count) source files ($([System.IO.Path]::GetFileName($JavaC)))"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& $JavaC --release 17 -encoding UTF-8 -nowarn -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)" }

# --- package (dev/vpfps only; stubs stay out) ---
if (-not $OutputJar) { $OutputJar = Join-Path $jarDir "vpfps.jar" }
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null
if (Test-Path $OutputJar) { Remove-Item $OutputJar -Force }
& $jarExe --create --file $OutputJar -C $classes dev
if ($LASTEXITCODE -ne 0) { throw "jar failed (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "OK: $OutputJar" -ForegroundColor Green
& $jarExe --list --file $OutputJar
