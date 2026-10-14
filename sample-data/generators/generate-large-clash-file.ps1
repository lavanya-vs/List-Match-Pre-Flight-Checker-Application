# Generates a large CSV with many AD-export clash patterns (paired raw + clean columns).
$out = Join-Path $PSScriptRoot "..\large\ad_export_clash_large.csv"
$rowCount = 300000
$clashPairs = @(
    @("NPI", "NPI_NUMBER"),
    @("FIRST_NAME", "CLEAN_FIRST_NAME"),
    @("LAST_NAME", "CLEAN_LAST_NAME"),
    @("CITY", "CLEAN_CITY"),
    @("STATE", "CLEAN_STATE"),
    @("ZIP", "CLEAN_ZIP"),
    @("ADDRESS1", "CLEAN_ADDRESS1"),
    @("ADDRESS2", "CLEAN_ADDRESS2"),
    @("EMAIL", "CLEAN_EMAIL"),
    @("PHONE", "CLEAN_PHONE")
)
$extraCols = @(
    "BUS_CITY", "BUS_STATE", "BUS_ZIP", "PARENT_ID", "PARENT_NAME",
    "HCP_SPECIALTY", "MARKET_GROUP", "ACCOUNT_ID", "BATCH_ID", "SUFFIX",
    "MIDDLE_NAME", "FULL_NAME", "PFIZER_CUSTOMER_ID", "PDRP_OPT_OUT_FLAG"
)

$headers = @()
foreach ($pair in $clashPairs) { $headers += $pair[0]; $headers += $pair[1] }
$headers += $extraCols

$sw = [System.IO.StreamWriter]::new($out, $false, [System.Text.Encoding]::UTF8)
$sw.WriteLine(($headers -join ","))

$cities = @("Dallas", "Chicago", "Boston", "Houston", "Seattle", "Denver", "Miami", "Atlanta")
$states = @("TX", "IL", "MA", "TX", "WA", "CO", "FL", "GA")
$zips = @("75201", "60601", "02108", "77002", "98101", "80202", "33101", "30301")

for ($i = 1; $i -le $rowCount; $i++) {
    $idx = ($i - 1) % 8
    $npi = (1100000000 + ($i % 900000000)).ToString()
    $fn = "First$i"
    $ln = "Last$i"
    $city = $cities[$idx]
    $cleanCity = $cities[($idx + 1) % 8]
    $state = $states[$idx]
    $cleanState = $states[($idx + 2) % 8]
    $zip = $zips[$idx]
    $cleanZip = $zips[($idx + 3) % 8]
    $addr1 = "$i Main St"
    $cleanAddr1 = "$i Oak Ave"
    $addr2 = "Suite $i"
    $cleanAddr2 = "Floor $i"
    $email = "user$i@clinic.example"
    $phone = "617555" + ($i % 10000).ToString("0000")
    $extras = @(
        $cities[($idx + 4) % 8], $states[($idx + 5) % 8], $zips[($idx + 6) % 8],
        "PARENT-$i", "Parent Biz $i", "Cardiology", ("Market-" + ((($i % 5) + 1).ToString())),
        "ACC-$i", "BATCH-2026Q3", "Jr", "Mid$i", "$fn $ln", "CUST-$i", "N"
    )
    $row = @(
        $npi, $npi,
        $fn, $fn,
        $ln, $ln,
        $city, $cleanCity,
        $state, $cleanState,
        $zip, $cleanZip,
        $addr1, $cleanAddr1,
        $addr2, $cleanAddr2,
        $email, $email,
        $phone, $phone
    ) + $extras
    $sw.WriteLine(($row -join ","))
    if ($i % 10000 -eq 0) { Write-Host "Wrote $i rows..." }
}

$sw.Close()
$sizeMb = [math]::Round((Get-Item $out).Length / 1MB, 2)
Write-Host "Done: $out ($rowCount rows, ${sizeMb} MB)"
