import { useEffect, useState } from 'react'
import ScoreBadge from './ScoreBadge'
import { apiFetch } from '../api'
import './HistoryPanel.css'

export default function HistoryPanel({ refreshKey }) {
  const [history, setHistory] = useState([])
  const [expanded, setExpanded] = useState(false)

  useEffect(() => {
    apiFetch('/api/history')
      .then((r) => r.json())
      .then(setHistory)
      .catch(() => setHistory([]))
  }, [refreshKey])

  if (history.length === 0) return null

  const shown = expanded ? history : history.slice(0, 5)

  return (
    <section className="history-panel">
      <h3>Recent checks</h3>
      <div className="history-list">
        {shown.map((item) => (
          <div key={item.id} className="history-item">
            <div className="history-main">
              <span className="history-file">{item.fileName}</span>
              <span className="history-meta">
                {item.totalRows.toLocaleString()} rows · {formatDate(item.checkedAt)}
              </span>
            </div>
            <ScoreBadge score={item.score} />
          </div>
        ))}
      </div>
      {history.length > 5 && (
        <button className="history-toggle" onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Show less' : `Show all (${history.length})`}
        </button>
      )}
    </section>
  )
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}
