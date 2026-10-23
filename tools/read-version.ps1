param(
    [string]$Version,
    [string]$Code
)

# VERSION.txt içeriğini oku
$content = Get-Content -Path "VERSION.txt" -Raw
$content = $content.Trim()

# Version parçalarını ayır
$parts = $content.Split('.')
if ($parts.Count -ne 3) {
    Write-Error "VERSION.txt 3 parçadan oluşmalı (major.minor.patch)"
    exit 1
}

# Version bilgisini çıkar
$Version = $content

# versionCode hesapla: major * 1_000_000 + minor * 1_000 + patch
$maj = [int]$parts[0]
$min = [int]$parts[1]
$pat = [int]$parts[2]
$code = $maj * 1000000 + $min * 1000 + $pat

# Output'u pipe ile batch'e yaz
Write-Output "$Version|$code"