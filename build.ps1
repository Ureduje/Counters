$ErrorActionPreference = "Stop"
$fwDir = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319"
if (-not (Test-Path (Join-Path $fwDir "csc.exe"))) {
    $fwDir = "C:\Windows\Microsoft.NET\Framework\v4.0.30319"
}
$csc = Join-Path $fwDir "csc.exe"
$wpfDir = Join-Path $fwDir "WPF"

# Ensure UTF-8 with BOM on all C# source files so Slovenian characters compile accurately
$utf8Bom = New-Object System.Text.UTF8Encoding($true)
foreach ($f in @("Program.cs", "Installer.cs", "GenerateIcon.cs")) {
    $p = Join-Path $PSScriptRoot $f
    $txt = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($p, $txt, $utf8Bom)
}

Write-Host "1. Ustvarjanje ikone (app.ico)..."
& $csc /nologo /target:exe /out:GenerateIcon.exe /r:System.Drawing.dll GenerateIcon.cs
if ($LASTEXITCODE -ne 0) { throw "Napaka pri prevajanju GenerateIcon.cs" }
.\GenerateIcon.exe app.ico
Remove-Item .\GenerateIcon.exe -Force -ErrorAction SilentlyContinue

Write-Host "2. Prevajanje glavne WPF aplikacije (VzpenjacaKontrola.exe)..."
$pf = Join-Path $wpfDir "PresentationFramework.dll"
$pc = Join-Path $wpfDir "PresentationCore.dll"
$wb = Join-Path $wpfDir "WindowsBase.dll"
$sx = Join-Path $fwDir "System.Xaml.dll"

& $csc /nologo /optimize+ /target:winexe /out:VzpenjacaKontrola.exe /win32icon:app.ico /win32manifest:app.manifest /r:System.dll /r:System.Core.dll /r:$sx /r:$wb /r:$pc /r:$pf Program.cs
if ($LASTEXITCODE -ne 0) { throw "Napaka pri prevajanju Program.cs" }

Write-Host "3. Prevajanje namestitvenega programa brez skrbniskih pravic (VzpenjacaKontrola-Namestitev.exe)..."
& $csc /nologo /optimize+ /target:winexe /out:VzpenjacaKontrola-Namestitev.exe /win32icon:app.ico /win32manifest:app.manifest /resource:VzpenjacaKontrola.exe,VzpenjacaKontrola.App.exe /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll Installer.cs
if ($LASTEXITCODE -ne 0) { throw "Napaka pri prevajanju Installer.cs" }

Write-Host "`nUspesno zgrajeno:"
Get-ChildItem VzpenjacaKontrola*.exe | Select-Object Name, Length, LastWriteTime
