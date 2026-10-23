param([ref]$OldVersion, [ref]$NewVersion)

# VERSION.txt içeriğini oku
$content = Get-Content -Path "VERSION.txt" -Raw
$content = $content.Trim()

# Version parçalarını ayır
$parts = $content.Split('.')
if ($parts.Count -ne 3) {
    Write-Error "VERSION.txt 3 parçadan oluşmalı (major.minor.patch)"
    exit 1
}

# Current version'yi çıkar
$oldMaj = $parts[0].Trim()
$oldMin = $parts[1].Trim()
$oldPat = $parts[2].Trim()
$OldVersion.Value = "$oldMaj.$oldMin.$oldPat"

# Minor version artır (patch + 1)
$newPat = [int]$oldPat + 1
$newMaj = $oldMaj
$newMin = $oldMin
$NewVersion.Value = "$newMaj.$newMin.$newPat"

Write-Output "$oldMaj.$oldMin.$oldPat $newMaj.$newMin.$newPat"