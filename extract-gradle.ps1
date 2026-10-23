# PowerShell script to extract gradle-wrapper.jar using ZipArchive
$zipPath = "D:\MakayWallpaper\gradle-8.4-all.zip"
$extractDir = "D:\MakayWallpaper\gradle\wrapper"

# Create extraction directory if not exists
if (-not (Test-Path $extractDir)) {
    New-Item -ItemType Directory -Path $extractDir | Out-Null
}

# Get the zip archive
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)

# Extract all entries
foreach ($entry in $archive.Entries) {
    $targetPath = Join-Path $extractDir $entry.FullName
    
    # Create subdirectories if needed
    $dir = Split-Path $targetPath -Parent
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    
    # Extract the file
    $entry.ExtractToFile($targetPath, $true) | Out-Null
    Write-Host "Extracted: $($entry.FullName)"
}

$archive.Dispose()

# Look for gradle-wrapper.jar
$jarPath = Join-Path $extractDir "gradle-wrapper.jar"
if (Test-Path $jarPath) {
    $length = (Get-Item $jarPath).Length
    Write-Host "Found gradle-wrapper.jar ($length bytes)"
} else {
    # Search for any .jar file
    $jars = Get-ChildItem -Path $extractDir -Filter "*.jar" -Recurse
    if ($jars.Count -gt 0) {
        Write-Host "No gradle-wrapper.jar found, but found $($jars.Count) jar(s):"
        foreach ($jar in $jars) {
            Write-Host "  - $($jar.Name) ($($jar.Length) bytes)"
        }
        # Copy the first one as gradle-wrapper.jar
        Copy-Item $jars[0].FullName $jarPath -Force
        Write-Host "Copied $($jars[0].Name) as gradle-wrapper.jar"
    } else {
        Write-Error "No jar files found in extracted directory"
    }
}
Write-Host "Extraction complete!"