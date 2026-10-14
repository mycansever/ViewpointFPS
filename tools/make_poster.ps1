# Viewpoint FPS Optimization - poster generator
# Project Zomboid wants a 256x256 PNG for the mod.info poster.
#
# Usage:
#   .\make_poster.ps1                                   -> 42\poster.png (256x256)
#   .\make_poster.ps1 -Size 512 -Out .\poster512.png   -> any size you like

param(
    [int]$Size = 256,
    [string]$Out = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Out) { $Out = Join-Path $root "..\42\poster.png" }
$Out = [System.IO.Path]::GetFullPath($Out)

Add-Type -AssemblyName System.Drawing

$bmp = New-Object System.Drawing.Bitmap($Size, $Size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAliasGridFit'

# background: dark diagonal gradient
$rect = New-Object System.Drawing.Rectangle(0, 0, $Size, $Size)
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect,
    [System.Drawing.Color]::FromArgb(255, 8, 12, 20),
    [System.Drawing.Color]::FromArgb(255, 22, 58, 40), 45)
$g.FillRectangle($bg, $rect)

# subtle grid
$pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(40, 120, 220, 160), 1)
$step = [Math]::Max(16, [int]($Size / 8))
for ($i = $step; $i -lt $Size; $i += $step) {
    $g.DrawLine($pen, $i, 0, $i, $Size)
    $g.DrawLine($pen, 0, $i, $Size, $i)
}

# everything below is laid out on a 256-unit grid and scaled
$s = $Size / 256.0

# lime accent bar
$accent = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 120, 225, 130))
$g.FillRectangle($accent, (14 * $s), (54 * $s), (75 * $s), (5 * $s))

# title
$white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$lime  = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 140, 235, 150))
$fontTitle = New-Object System.Drawing.Font('Arial', (20 * $s), [System.Drawing.FontStyle]::Bold)
$fontBig   = New-Object System.Drawing.Font('Arial', (26 * $s), [System.Drawing.FontStyle]::Bold)
$fontSub   = New-Object System.Drawing.Font('Arial', (13 * $s), [System.Drawing.FontStyle]::Bold)
$g.DrawString('VIEWPOINT',    $fontTitle, $white, (13 * $s), (65 * $s))
$g.DrawString('FPS',          $fontBig,   $lime,  (13 * $s), (92 * $s))
$g.DrawString('OPTIMIZATION', $fontSub,   $white, (13 * $s), (132 * $s))

# fake HUD readout
$mono = New-Object System.Drawing.Font('Consolas', (9 * $s))
$hud  = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230, 200, 210, 220))
$g.DrawString('FPS 143.7  frame 7.0ms',  $mono, $hud, (14 * $s), (176 * $s))
$g.DrawString('VP render 58%  adaptive', $mono, $hud, (14 * $s), (192 * $s))

# footer
$gray = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(180, 150, 160, 155))
$g.DrawString('PZ Build 42.21', $mono, $gray, (14 * $s), (226 * $s))

$g.Dispose()
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "OK: $Out ($Size x $Size)" -ForegroundColor Green
