param(
  [Parameter(Mandatory=$true)][string]$Apk
)
$ErrorActionPreference = 'Stop'
$resolved = (Resolve-Path $Apk).Path
Add-Type -AssemblyName System.IO.Compression
$zip = [System.IO.Compression.ZipFile]::OpenRead($resolved)
try {
  $entries = @($zip.Entries | Where-Object { $_.Name -ne '' })
  $totalCompressed = ($entries | Measure-Object -Property CompressedLength -Sum).Sum
  Write-Host "APK: $resolved"
  Write-Host ("Размер на диске: {0:N2} MB" -f ((Get-Item $resolved).Length / 1MB))
  Write-Host ("Сжатое содержимое: {0:N2} MB" -f ($totalCompressed / 1MB))
  Write-Host "Самые крупные файлы в APK (сжатый размер):"
  $entries | Sort-Object CompressedLength -Descending | Select-Object -First 25 |
    ForEach-Object { "{0,8:N2} MB  {1}" -f ($_.CompressedLength / 1MB), $_.FullName }
  Write-Host "Вклад главных каталогов (сжатый размер):"
  $entries | Group-Object { ($_.FullName -split '/')[0] } |
    ForEach-Object {
      [pscustomobject]@{ Directory = $_.Name;
                        SizeMB = [math]::Round((($_.Group | Measure-Object CompressedLength -Sum).Sum)/1MB, 2) }
    } | Sort-Object SizeMB -Descending | Format-Table -AutoSize
} finally {
  $zip.Dispose()
}
