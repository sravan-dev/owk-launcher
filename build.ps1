# Builds Owk Launcher without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
$ErrorActionPreference = 'Stop'
$root   = $PSScriptRoot
$sdk    = 'E:\Android\sdk'
$bt     = "$sdk\build-tools\36.0.0"
$jar    = "$sdk\platforms\android-35\android.jar"
$jdk    = 'E:\Android\jdk\bin'
$out    = "$root\build"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

# Generated images (icon, banner) and bundled wallpaper.
$nodpi = "$root\res\drawable-nodpi"
New-Item -ItemType Directory -Force $nodpi | Out-Null
Add-Type -AssemblyName System.Drawing
function New-Art([string]$path, [int]$w, [int]$h, [string]$text, [float]$size) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.Color]::FromArgb(255, 14, 92, 99)), ([System.Drawing.Color]::FromArgb(255, 88, 36, 120)), 30
    $g.FillRectangle($brush, $rect)
    $font = New-Object System.Drawing.Font 'Segoe UI Semibold', $size, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
    $g.DrawString($text, $font, [System.Drawing.Brushes]::White, [System.Drawing.RectangleF]$rect, $fmt)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}
New-Art "$nodpi\banner.png" 320 180 'Owk' 72
New-Art "$nodpi\ic_launcher.png" 192 192 'O' 120
Copy-Item "$root\wallpaper.jpg" "$nodpi\wallpaper.jpg" -Force

# Resources + manifest.
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }
& "$bt\aapt2.exe" link -I $jar --manifest "$root\AndroidManifest.xml" -o "$out\unsigned.apk" `
    --java "$out\gen" --min-sdk-version 21 --target-sdk-version 30 `
    --version-code 1 --version-name 1.0 "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

# Java -> dex.
$sources = Get-ChildItem "$root\src", "$out\gen" -Recurse -Filter *.java | ForEach-Object FullName
& "$jdk\javac.exe" -source 11 -target 11 -Xlint:-options -encoding UTF-8 -cp $jar -d "$out\classes" @sources
if ($LASTEXITCODE) { throw 'javac failed' }
$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --release --min-api 21 --lib $jar --output "$out\dex" @classes
if ($LASTEXITCODE) { throw 'd8 failed' }

# Add classes.dex, align, sign.
Push-Location "$out\dex"
& "$jdk\jar.exe" -u -f "$out\unsigned.apk" -M classes.dex
Pop-Location
if ($LASTEXITCODE) { throw 'adding dex failed' }
& "$bt\zipalign.exe" -p -f 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'zipalign failed' }

# Keep one signing key so future builds install as updates.
$ks = "$root\owk.jks"
if (-not (Test-Path $ks)) {
    & "$jdk\keytool.exe" -genkeypair -keystore $ks -alias owk -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass owkpass -keypass owkpass -dname 'CN=Owk Launcher'
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:owkpass --key-pass pass:owkpass --out "$root\OwkLauncher.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner failed' }
Write-Host "Built $root\OwkLauncher.apk"
