# List Match Pre-Flight Checker — Simple Guide

This document is the **detailed companion** to [README.md](README.md) — folder-by-folder and file-by-file detail for developers.

---

## What is this?

We upload a **CSV or Excel** list of healthcare providers **before** loading it into IQVIATools. The app answers:

- Is this file **ready**, **needs work**, or **will fail**?
- Which **match path** fits best (NPI, name + zip, etc.)?
- What **row-level problems** exist (bad NPI, duplicate NPI, missing zip)?
- Would **column names clash** when exported to IQVIATools?

---

## Quick start (Windows)

| What you need | Required? |
|---------------|-----------|
| **Java 11+** | Yes — runs the main app |
| **Maven** (or Maven under your `.m2` folder) | Yes — to start the backend |
| **Node.js** | No — only if you develop the alternate React UI |

**Easiest way:** double-click **`start-all.bat`** in the project root.

1. Wait until the backend window shows **`Started PreflightApplication`**.
2. Open **http://localhost:8085** in your browser.
3. Upload a file (try samples in **`sample-data/demos/`**).

Other scripts (same folder as this file):

| File | Purpose |
|------|---------|
| **`start-all.bat`** | Opens the Spring Boot backend |
| **`start-backend.bat`** | Java API + built-in web UI (port **8085**) |
| **`start-frontend.bat`** | React dev UI (port **5173**) — optional |

---

## Major features 

1. **File upload** — CSV, `.xls`, `.xlsx`; large files use chunked upload and background jobs.
2. **Column detection** — Maps your headers to standard fields (NPI, last name, zip, etc.) using names and, when needed, sample cell values.
3. **Validation** — Checks NPI format/checksum, zip, address, email, phone, and related rules.
4. **Duplicate NPI** — Flags the same valid NPI on more than one row.
5. **Match path ranking** — Shows which IQVIATools-style paths your file can support and **coverage %** per path.
6. **Traffic-light score** — **READY** / **NEEDS WORK** / **WILL FAIL** from rule-based coverage (not AI).
7. **AD export clash check** — Warns if two columns would get the **same export name** in IQVIATools.
8. **Human-readable analysis** — Short summary, recommended strategy, and risk bullets.
9. **Check history** — Last 20 runs stored in a local H2 database.
10. **Large files** — Chunked upload and streaming CSV parsing in Java.

---

## How a check works (end to end)

```
You upload a file in the browser (port 8085)
        │
        ▼
Java parses the file (FileParserService)
        │
        ▼
Columns are suggested or use your manual mapping (ColumnMappingService, ColumnDetectorService)
        │
        ▼
Rules engine scans rows (RulesEngineService)
  • valid/invalid/missing fields
  • duplicate NPIs
  • best match path + READY / NEEDS WORK / WILL FAIL
        │
        ▼
AD export name clashes checked (AdExportColumnClashService)
        │
        ▼
Stats + column insights built (DataQualityStatsService, ColumnInsightService)
        │
        ▼
Friendly text written (LocalAnalysisService Java templates; optional LLM)
        │
        ▼
JSON result shown in UI + saved to history
```

**Important:** All **scoring, counts, and default narrative text** come from **Java** (Spring services).

---

## Project layout (folders and what they are for)

```
List Match Pre-Flight Checker/
│
├── start-all.bat          ← starts backend (calls scripts\start-backend.bat)
├── README.md
├── backend/               ← Main application (Java / Spring Boot)
├── frontend/              ← Optional React UI for developers
├── sample-data/           ← Organized CSV fixtures (see sample-data/README.md)
├── scripts/               ← start-backend.bat, start-frontend.bat, start-eclipse.bat
└── docs/                  ← This guide and other docs
```

---

## Backend (`backend/`) — the brain

**Location:** `backend/`  
**Runs on:** http://localhost:8085  
**Config:** `backend/src/main/resources/application.properties`  
**Secrets (gitignored):** copy `application-local.properties.example` → `application-local.properties`

### Built-in web UI

| Location | Role |
|----------|------|
| `backend/src/main/resources/static/index.html` | Main upload and results page (what you use at :8085) |
| `backend/src/main/resources/static/samples/` | Some demo CSVs copied for the UI |

### Main Java packages (`backend/src/main/java/com/iqvia/preflight/`)

| Area | Folder / examples | What it does |
|------|-------------------|--------------|
| **HTTP API** | `controller/PreflightController.java`, `ChunkedUploadController.java` | Upload, check, jobs, history, health |
| **Orchestration** | `service/PreflightService.java` | Ties parse → rules → clashes → analysis together |
| **Parsing** | `service/FileParserService.java` | Reads CSV/Excel, headers, sample rows |
| **Column mapping** | `service/ColumnDetectorService.java`, `ColumnMappingService.java` | Guess or apply NPI/zip/name columns |
| **Rules & score** | `service/RulesEngineService.java` | Match paths, coverage, warnings, READY/NEEDS WORK/WILL FAIL |
| **Field checks** | `service/FieldValidationService.java` | NPI checksum, zip, etc. |
| **Export clashes** | `service/AdExportColumnClashService.java`, `IqviaColumnNaming.java` | Simulate IQVIATools export column names |
| **Stats** | `service/DataQualityStatsService.java` | Counts for duplicates, blanks, warning types |
| **Analysis text** | `service/AiAnalysisService.java`, `LocalAnalysisService.java` | Build the narrative panel |
| **Big files** | `service/ChunkedUploadService.java`, `PreflightJobService.java` | Chunked upload and async jobs |
| **History** | `repository/CheckHistoryRepository.java`, `entity/` | H2 database for recent checks |
| **Models / DTOs** | `model/`, `dto/` | Scores, match types, API response shapes |

### Tests

`backend/src/test/java/` — automated tests for parser, column detection, rules, and clashes.

---

## Frontend (`frontend/`) — optional developer UI

**Location:** `frontend/`  
**Dev URL:** http://localhost:5173 (after `npm install` and `npm run dev`)

| File / folder | Role |
|---------------|------|
| `src/App.jsx` | Main app shell |
| `src/components/FileUpload.jsx` | Upload control |
| `src/components/ResultPanel.jsx` | Score, analysis, warnings |
| `src/components/ColumnMapping.jsx`, `ManualMappingPanel.jsx` | Column mapping UI |
| `src/components/AdExportClashPanel.jsx` | Export name clash display |
| `package.json` | Node dependencies |

Most users only need the **built-in UI on port 8085**; this folder is for React development.

---

## Sample data (`sample-data/`)

See **`sample-data/README.md`** for the full layout. Highlights:

| Location | Use |
|----------|-----|
| `demos/sample_perfect.csv` | Should score **READY** |
| `demos/sample_okayish.csv` | **NEEDS WORK** |
| `demos/sample_bad.csv` | **WILL FAIL** |
| `demos/sample_odd_headers.csv` | **READY** with unusual column names |
| `ad-export-clash/` | Duplicate AD export name examples |
| `generators/*.ps1` | Regenerate demo and large clash files |

---

## Scores (simple meaning)

| Score | Meaning |
|-------|---------|
| **READY** | Strong path (usually NPI or name+zip) with high **validated** coverage (~85%+). |
| **NEEDS WORK** | Some rows can match, but many gaps or only a weaker path. |
| **WILL FAIL** | No usable path or coverage too low to rely on. |

Match paths are ranked from **most precise** (NPI) to **broadest** (name only). See [README.md](../README.md) for the full precision table.

---

## Duplicate detection (two different ideas)

1. **Duplicate NPI (rows)** — Same valid NPI appears on multiple rows (`RulesEngineService`).
2. **AD export clash (columns)** — Two source columns would map to the same IQVIATools export name (`AdExportColumnClashService`).

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Backend won’t start, “maximum heap size” | Your Java may be 32-bit; lower `MAVEN_OPTS` in `start-backend.bat` (e.g. `-Xmx512m`). |
| `mvn` not found | Install Maven or use the copy under `%USERPROFILE%\.m2\wrapper\dists\...\mvn.cmd` (see `start-backend.bat`). |
| `npm` not found | Ignore frontend; use **http://localhost:8085**. |

**Health check:** `GET http://localhost:8085/api/health` — shows backend status and upload thresholds.

