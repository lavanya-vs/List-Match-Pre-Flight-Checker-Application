# List Match Pre-Flight Checker

Checks client provider lists **before** loading them into IQVIATools. Upload a CSV or Excel file to see **ready**, **needs cleanup**, or **will likely fail**, plus match-path fit, row warnings, and AD export clashes.

## Requirements

- **Java 11 or newer** ([Adoptium](https://adoptium.net/) or similar)
- **Maven 3.8+** on your PATH, **or** Maven already downloaded under `%USERPROFILE%\.m2\wrapper\dists\` (the start script finds it automatically)

Check from a terminal:

```text
java -version
mvn -version
```

## Run on Windows

1. Get the project folder on your PC (clone from GitHub or unzip).
2. Double-click **`start-all.bat`** in the project root.  
   (Same as **`start-backend.bat`** — both start the server.)
3. A black console window opens. Wait until you see **`Started PreflightApplication`**.  
   The first run can take several minutes while Maven downloads dependencies.
4. Open a browser and go to: **http://localhost:8085**
5. Upload a file, or try a demo from **`sample-data\demos\`** (for example `sample_perfect.csv`).

Leave the console window open while you use the app. To stop the server, close that window or press **Ctrl+C** in it.

## Run from the command line (optional)

```text
cd backend
mvn spring-boot:run
```

Then open **http://localhost:8085**.

## Troubleshooting

| Problem | What to do |
|--------|------------|
| **Maven not found** | Install Maven and add it to PATH, or run on a machine that already has Maven under `.m2`. |
| **Browser cannot connect** | Wait for `Started PreflightApplication` before opening the URL. |
| **Port 8085 already in use** | Close any old server window and run `start-all.bat` again (the script tries to free the port). |

---

## What’s in this project? (files and what they do)

Plain-language map of the repo. **You only need the root `start-*.bat` files to run the app**; everything else is the server code, optional dev UI, sample spreadsheets, or docs.

### Project root (double-click these)

| File | What it does |
|------|----------------|
| **`start-all.bat`** | Opens a console and starts the server; tells you to open http://localhost:8085. |
| **`start-backend.bat`** | Shortcut that runs the real start script under `scripts\`. |
| **`start-frontend.bat`** | Starts the optional React dev UI (for developers; normal users use the built-in page at port 8085). |
| **`README.md`** | This file — how to run and what each part of the project is for. |
| **`.gitignore`** | Tells Git what not to upload (local secrets, build output, huge sample files). |

### `scripts\` (what the root `.bat` files call)

| File | What it does |
|------|----------------|
| **`start-backend.bat`** | Finds Java/Maven, frees port 8085 if needed, runs `mvn spring-boot:run` in `backend\`. |
| **`start-frontend.bat`** | Runs the React app with Vite (needs Node.js installed). |
| **`start-eclipse.bat`** | Helper to open/run the project in Eclipse IDE. |

### `backend\` (the main application — the “engine”)

This is a **Spring Boot** Java app. It reads your file, runs checks, stores recent history, and serves the web page you see in the browser.

| File / folder | What it does |
|---------------|----------------|
| **`pom.xml`** | Maven “recipe” — lists libraries the app needs and how to build/run it. |
| **`PreflightApplication.launch`** | Eclipse one-click run configuration for developers. |
| **`application-local.properties.example`** | Example file for optional AI/API keys; copy to `application-local.properties` (not in Git) if you enable AI. |
| **`.classpath`, `.project`, `.settings\`** | Eclipse IDE project metadata — ignore unless you use Eclipse. |
| **`.gitignore`** | Ignores `target\`, local DB files, and your private `application-local.properties`. |

#### `backend\src\main\java\...` — program logic

| File | What it does |
|------|----------------|
| **`PreflightApplication.java`** | Main entry point — starts the web server when you see `Started PreflightApplication`. |
| **`META-INF\MANIFEST.MF`** | Standard Java packaging metadata (rarely needs attention). |

**`config\` — settings wiring**

| File | What it does |
|------|----------------|
| **`PreflightProperties.java`** | Reads app settings (paths, limits, feature flags) from config files. |
| **`AiAnalysisProperties.java`** | Settings for optional AI summaries (off by default). |
| **`AiAnalysisConfig.java`** | Turns AI-related beans on or off based on those settings. |
| **`WebConfig.java`** | Web setup (e.g. CORS, static files) so the browser can talk to the API. |

**`controller\` — URLs the browser calls**

| File | What it does |
|------|----------------|
| **`PreflightController.java`** | Main API: upload checks, previews, history, job status, results. |
| **`ChunkedUploadController.java`** | API for uploading very large files in pieces. |
| **`GlobalExceptionHandler.java`** | Turns server errors into clear JSON messages for the UI. |

**`service\` — where the checking actually happens**

| File | What it does |
|------|----------------|
| **`PreflightService.java`** | Orchestrates a full pre-flight check from upload to final score. |
| **`PreflightJobService.java`** | Runs long checks in the background and tracks progress. |
| **`FileParserService.java`** | Opens CSV/Excel and reads rows and headers. |
| **`ColumnDetectorService.java`** | Guesses which columns are NPI, name, address, etc. |
| **`ColumnMappingService.java`** | Applies and saves your manual column mappings. |
| **`FieldValidationService.java`** | Validates values (formats, required fields, bad data). |
| **`RulesEngineService.java`** | Applies business rules and builds warnings. |
| **`DataQualityStatsService.java`** | Counts blanks, duplicates, and similar quality stats. |
| **`ColumnInsightService.java`** | Extra per-column notes for the results screen. |
| **`AdExportColumnClashService.java`** | Finds AD export files where column names would clash. |
| **`UploadStorageService.java`** | Saves uploaded files temporarily on disk. |
| **`ChunkedUploadService.java`** | Stitches chunked uploads back into one file. |
| **`IqviaColumnNaming.java`** | Knows expected IQVIA-style column naming patterns. |
| **`LocalAnalysisService.java`** | Builds human-readable summaries without calling an external AI. |
| **`AiAnalysisService.java`** | Optional richer analysis when AI is enabled. |
| **`AiKnowledgeService.java`** | Loads internal markdown hints used for AI context. |
| **`LlmClientService.java`** | Talks to OpenAI/Azure if you turn AI on locally. |
| **`RowSource.java`** | Small helper interface for reading rows uniformly. |

**`dto\` — data shapes sent to/from the API (not user-edited)**

| File | What it does |
|------|----------------|
| **`PreflightResult.java`**, **`PreflightCheckResponse.java`**, **`JobStatusResponse.java`** | Overall check result, response wrapper, and “still running / done” status. |
| **`HeadersPreviewResponse.java`**, **`ClientListHeaderPreview.java`** | First look at file headers before a full run. |
| **`ColumnMappingEntry.java`**, **`ColumnInsight.java`**, **`DataQualityStats.java`** | Mapping lines, column tips, and quality counts. |
| **`AdExportClashCheckRequest.java`**, **`AdExportClashCheckResponse.java`**, **`AdExportColumnClash.java`** | Request/response for AD export clash checks. |
| **`AiAnalysis.java`** | Optional AI summary block in results. |
| **`CheckHistoryItem.java`** | One line in the “past checks” list. |
| **`ChunkedUploadInitRequest.java`** | Starts a chunked upload session. |

**`entity\` + `repository\` — saved history (small local database)**

| File | What it does |
|------|----------------|
| **`CheckJob.java`** | Database record for one running or finished job. |
| **`CheckHistory.java`** | Database record for a completed check you can reopen. |
| **`CheckJobRepository.java`**, **`CheckHistoryRepository.java`** | Load and save those records (H2 database). |

**`model\` — enums and core concepts**

| File | What it does |
|------|----------------|
| **`StandardField.java`** | List of standard field types (NPI, name, etc.). |
| **`MatchType.java`** | Match path types the UI can select. |
| **`ReadinessScore.java`** | Ready / needs cleanup / likely fail levels. |
| **`JobStatus.java`** | Queued, running, done, failed states. |

#### `backend\src\main\resources\` — config and the page you use

| File | What it does |
|------|----------------|
| **`application.properties`** | Default server port (8085), database, upload limits, AI off by default. |
| **`static\index.html`** | **The main UI** served at http://localhost:8085 (upload, results, history). |
| **`static\iqvia-logo.png`** | Logo on that page. |
| **`static\samples\*.csv`** | Small demo files bundled for quick try-from-browser. |
| **`ai\knowledge-base.md`** | Background text for optional AI explanations. |

#### `backend\src\test\java\...` — automated tests (developers)

| File | What it does |
|------|----------------|
| **`PreflightControllerIntegrationTest.java`** | Tests the HTTP API end-to-end. |
| **`FileParserServiceTest.java`**, **`FieldValidationServiceTest.java`**, **`ColumnDetectorServiceTest.java`** | Tests parsing, validation, and column detection. |
| **`AdExportColumnClashServiceTest.java`**, **`AdExportClashIntegrationTest.java`** | Tests AD export clash logic. |
| **`DemoDatasetRegressionTest.java`**, **`RealWorldDatasetTest.java`** | Regression tests on sample CSVs so scores stay stable. |

### `frontend\` (optional — separate React UI for development)

Normal users **do not** need this folder; the app already ships a full UI in `backend\...\static\index.html`. Use `frontend\` only if you are changing the React version with Node.js.

| File | What it does |
|------|----------------|
| **`package.json`** | Node dependencies and scripts (`npm run dev`). |
| **`vite.config.js`** | Dev server settings and proxy to the Java API. |
| **`index.html`** | Shell page for the React dev app. |
| **`src\main.jsx`** | Boots React. |
| **`src\App.jsx`**, **`src\App.css`** | Main screen layout and styles. |
| **`src\api.js`** | Calls the same backend APIs as the built-in UI. |
| **`src\index.css`** | Global styles. |
| **`src\components\FileUpload.jsx`** | Upload area. |
| **`src\components\ColumnMapping.jsx`**, **`ManualMappingPanel.jsx`** | Map columns to standard fields. |
| **`src\components\MatchTypes.jsx`** | Pick match path. |
| **`src\components\ResultPanel.jsx`**, **`ScoreBadge.jsx`**, **`WarningsTable.jsx`** | Show score, summary, and warnings. |
| **`src\components\AdExportClashPanel.jsx`** | AD export clash checker UI. |
| **`src\components\HistoryPanel.jsx`** | Past checks list. |
| **`*.css` next to each component** | Styling for that screen section. |
| **`public\iqvia-logo.png`** | Logo for the dev UI. |

### `sample-data\` (practice files on your disk)

| File / folder | What it does |
|---------------|----------------|
| **`README.md`** | Short index of sample folders. |
| **`demos\*.csv`** | Small files with perfect, okay, bad, or odd headers — good for learning the tool. |
| **`client-lists\*.csv`** | Heavier examples that look like real client uploads. |
| **`ad-export-clash\`** | CSV/Excel examples that trigger “column name clash” warnings. |
| **`fixtures\test-result.json`** | Saved example output for tests. |
| **`generators\generate-samples.ps1`** | Recreates demo CSVs. |
| **`generators\generate-large-clash-file.ps1`** | Builds a big clash file for stress testing (output may go under `large\`, which is not in Git). |

### `docs\`

| File | What it does |
|------|----------------|
| **`SIMPLE-README.md`** | Longer developer guide (architecture, API, testing). |

---

## More detail

Developer guide: [docs/SIMPLE-README.md](docs/SIMPLE-README.md)
