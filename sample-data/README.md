# Sample data

| Folder | Contents |
|--------|----------|
| **`demos/`** | `sample_perfect.csv`, `sample_okayish.csv`, `sample_bad.csv`, `sample_odd_headers.csv` |
| **`ad-export-clash/`** | AD export name clash examples (CSV + `ad_export_issue_example.xlsx`) |
| **`client-lists/`** | Real-world style client list fixtures |
| **`large/`** | Very large files (`US_Healthcare_Providers_Dataset_2026.csv`, generated clash file) |
| **`generators/`** | PowerShell scripts to regenerate demos and large clash CSV |
| **`fixtures/`** | Saved JSON / misc test artifacts |

Built-in UI samples also live under `backend/src/main/resources/static/samples/`.

Regenerate core demos:

```powershell
powershell -ExecutionPolicy Bypass -File sample-data\generators\generate-samples.ps1
```
