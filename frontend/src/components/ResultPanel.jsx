import ScoreBadge from './ScoreBadge'

import ColumnMapping from './ColumnMapping'

import MatchTypes from './MatchTypes'

import WarningsTable from './WarningsTable'

import AdExportClashPanel from './AdExportClashPanel'

import './ResultPanel.css'



function isAdExportError(msg) {

  return typeof msg === 'string' && msg.toLowerCase().includes('ad export')

}



function filterAdExportNotes(notes) {

  if (!notes?.length) return []

  return notes.filter((n) => !isAdExportError(n) && !n.startsWith('Blocking issue: AD export'))

}



export default function ResultPanel({ result, analysis }) {

  const hasClashes = result.adExportClashes?.length > 0

  const otherErrors = (result.globalErrors ?? []).filter((e) => !isAdExportError(e))

  const warningCount = result.rowWarnings?.length ?? 0

  const filteredRiskNotes = hasClashes

    ? filterAdExportNotes(analysis?.riskNotes)

    : analysis?.riskNotes



  return (

    <section className="result-panel">

      <div className="result-header">

        <div>

          <h2>{result.fileName}</h2>

          <p className="row-count">{result.totalRows.toLocaleString()} data rows</p>

        </div>

        <ScoreBadge score={result.score} />

      </div>



      <div className={`summary-box score-${result.score?.toLowerCase()?.replace('_', '-')}`}>

        <p className="summary-text">{result.summary}</p>

        <p className="difficulty">

          Estimated match difficulty: <strong>{Math.round(result.estimatedMatchDifficulty)}%</strong>

          <span className="difficulty-hint">

            {result.estimatedMatchDifficulty < 40 ? ' (easy)' :

             result.estimatedMatchDifficulty < 70 ? ' (moderate)' : ' (hard)'}

          </span>

        </p>

      </div>



      {hasClashes && <AdExportClashPanel clashes={result.adExportClashes} />}



      {otherErrors.length > 0 && (

        <div className="errors-block">

          <h3>Blocking issues</h3>

          <ul>

            {otherErrors.map((err, i) => (

              <li key={i}>{err}</li>

            ))}

          </ul>

        </div>

      )}



      {analysis && (

        <div className="analysis-block">

          <div className="analysis-heading">

            <h3>Analysis</h3>

            <span>{analysis.analysisSource ?? analysis.source}</span>

          </div>

          <p>{analysis.executiveSummary}</p>

          <p><strong>Recommended strategy:</strong> {analysis.recommendedMatchStrategy}</p>

          {filteredRiskNotes?.length > 0 && (

            <ul>

              {filteredRiskNotes.map((note, index) => (

                <li key={`${note}-${index}`}>{note}</li>

              ))}

            </ul>

          )}

        </div>

      )}



      <ColumnMapping

        detected={result.detectedColumns}

        unmapped={result.unmappedColumns}

      />



      {result.matchTypes?.length > 0 && (

        <MatchTypes matchTypes={result.matchTypes} />

      )}



      {warningCount > 0 && (

        <WarningsTable warnings={result.rowWarnings} total={warningCount} />

      )}

    </section>

  )

}


