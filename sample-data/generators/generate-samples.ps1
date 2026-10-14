# Generates demo files with distinct outcomes:
# perfect = READY, okayish = NEEDS_WORK, bad = WILL_FAIL,
# odd-headers = READY with non-standard column names.
# Run: powershell -ExecutionPolicy Bypass -File generate-samples.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outDirs = @(
    (Join-Path $root "sample-data\demos"),
    (Join-Path $root "backend\src\main\resources\static\samples")
)

$generated = @(
    "sample_perfect.csv",
    "sample_okayish.csv",
    "sample_bad.csv",
    "sample_odd_headers.csv"
)
foreach ($dir in $outDirs) {
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    foreach ($name in $generated) {
        $existing = Join-Path $dir $name
        if (Test-Path $existing) { Remove-Item $existing -Force }
    }
}

function Get-LuhnNpi([int]$seed) {
    $base = "1" + ($seed.ToString("D8"))
    $payload = "80840" + $base
    $sum = 0
    # The check digit will be appended on the right, so the current rightmost
    # payload digit is doubled during Luhn check-digit calculation.
    $alt = $true
    for ($i = $payload.Length - 1; $i -ge 0; $i--) {
        $n = [int][string]$payload[$i]
        if ($alt) { $n *= 2; if ($n -gt 9) { $n -= 9 } }
        $sum += $n
        $alt = -not $alt
    }
    $check = (10 - ($sum % 10)) % 10
    return $base + $check
}

$firstNames = @("James","Maria","Robert","Priya","Michael","Sarah","David","Jennifer","Carlos","Emily")
$lastNames = @("Smith","Johnson","Williams","Patel","Brown","Garcia","Miller","Chen","Davis","Martinez")
$cities = @(
    @{ City="Boston"; State="MA"; Zip="02115" },
    @{ City="Chicago"; State="IL"; Zip="60611" },
    @{ City="Houston"; State="TX"; Zip="77030" },
    @{ City="Seattle"; State="WA"; Zip="98101" },
    @{ City="Denver"; State="CO"; Zip="80204" }
)
$streets = @("Main St","Oak Ave","Maple Dr","Park Blvd","Lake Rd")

function Write-Csv($path, $header, $rows) {
    @($header) + $rows | Set-Content -Path $path -Encoding UTF8
}

$header = "FIRST_NAME,LAST_NAME,NPI,ADDRESS,CITY,STATE,ZIP,SPECIALTY"
$specialties = @("Cardiology","Oncology","Neurology","Primary Care","Pediatrics")

# 1) Perfect — 300 rows, complete NPI + name + zip → READY
$perfect = @()
for ($i = 1; $i -le 300; $i++) {
    $fn = $firstNames[$i % $firstNames.Count]
    $ln = $lastNames[($i * 2) % $lastNames.Count]
    $loc = $cities[$i % $cities.Count]
    $npi = Get-LuhnNpi (10000000 + $i)
    $number = (($i * 11) % 8999) + 100
    $addr = "{0} {1}" -f $number, $streets[$i % $streets.Count]
    $specialty = $specialties[$i % $specialties.Count]
    $perfect += "$fn,$ln,$npi,$addr,$($loc.City),$($loc.State),$($loc.Zip),$specialty"
}
Write-Csv (Join-Path $outDirs[0] "sample_perfect.csv") $header $perfect
Write-Csv (Join-Path $outDirs[1] "sample_perfect.csv") $header $perfect

# 2) Okayish — usable NPI coverage, but too many gaps/invalid values for READY
$okayish = @()
for ($i = 1; $i -le 250; $i++) {
    $fn = $firstNames[($i + 3) % $firstNames.Count]
    $ln = $lastNames[($i + 1) % $lastNames.Count]
    $loc = $cities[($i + 2) % $cities.Count]
    $number = (($i * 7) % 7999) + 200
    $addr = "{0} {1}" -f $number, $streets[($i + 1) % $streets.Count]
    $bucket = $i % 20
    $npi = if ($bucket -le 13) {
        Get-LuhnNpi (20000000 + $i)
    } elseif ($bucket -le 16) {
        ""
    } else {
        "1234567890"
    }
    # Add a small duplicate-NPI cluster; duplicate identifiers are not safe match keys.
    if ($i -eq 41 -or $i -eq 42) { $npi = Get-LuhnNpi 20000040 }
    $zip = if ($i % 5 -eq 0) { "" } else { $loc.Zip }
    if ($i % 10 -eq 0) { $ln = "" }
    if ($i % 12 -eq 0) { $addr = "" }
    $specialty = $specialties[($i + 2) % $specialties.Count]
    $okayish += "$fn,$ln,$npi,$addr,$($loc.City),$($loc.State),$zip,$specialty"
}
Write-Csv (Join-Path $outDirs[0] "sample_okayish.csv") $header $okayish
Write-Csv (Join-Path $outDirs[1] "sample_okayish.csv") $header $okayish

# 3) Bad — contact/reference data only; no reliable provider match path
$badHeader = "FIRST_NAME,EMAIL,REGION,NOTES"
$bad = @()
for ($i = 1; $i -le 60; $i++) {
    $fn = $firstNames[$i % $firstNames.Count]
    $email = if ($i % 4 -eq 0) { "not-an-email" } else { "$($fn.ToLower())$i@example.test" }
    $region = @("North","South","East","West")[$i % 4]
    $note = if ($i % 3 -eq 0) { "No provider identifier supplied" } else { "Imported contact record" }
    $bad += "$fn,$email,$region,$note"
}
Write-Csv (Join-Path $outDirs[0] "sample_bad.csv") $badHeader $bad
Write-Csv (Join-Path $outDirs[1] "sample_bad.csv") $badHeader $bad

# 4) Odd headers — same quality as perfect, but client-style column names
$oddHeader = "HCP_Given,HCP_Family,Enumeration_Key,Practice_Line,Locale_Name,Admin_Area,Postal_Zone,Inbox,Dial"
$odd = @()
for ($i = 1; $i -le 80; $i++) {
    $fn = $firstNames[($i + 1) % $firstNames.Count]
    $ln = $lastNames[($i + 4) % $lastNames.Count]
    $loc = $cities[($i + 1) % $cities.Count]
    $npi = Get-LuhnNpi (30000000 + $i)
    $number = (($i * 13) % 6999) + 150
    $addr = "{0} {1}" -f $number, $streets[($i + 2) % $streets.Count]
    $email = "$($fn.ToLower()).$($ln.ToLower())$i@clinic.example"
    $phone = "617555{0:D4}" -f (1000 + $i)
    $odd += "$fn,$ln,$npi,$addr,$($loc.City),$($loc.State),$($loc.Zip),$email,$phone"
}
Write-Csv (Join-Path $outDirs[0] "sample_odd_headers.csv") $oddHeader $odd
Write-Csv (Join-Path $outDirs[1] "sample_odd_headers.csv") $oddHeader $odd

Write-Host "Generated demo files in:"
$outDirs | ForEach-Object { Write-Host "  $_" }
