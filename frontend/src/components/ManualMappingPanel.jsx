import { useEffect, useMemo, useState } from 'react'
import { checkAdExportClashes } from '../api'
import './ManualMappingPanel.css'

export default function ManualMappingPanel({
  file,
  preview,
  onRunCheck,
  onBack,
  loading,
}) {
  const [mappings, setMappings] = useState([])
  const [clashPreview, setClashPreview] = useState(null)
  const [checkingClashes, setCheckingClashes] = useState(false)

  useEffect(() => {
    if (preview?.suggestedMappings) {
      setMappings(preview.suggestedMappings.map((m) => ({ ...m })))
    }
  }, [preview])

  const options = preview?.availableMappedNames ?? []

  const updateMapping = (index, mappedName) => {
    setMappings((prev) => prev.map((row, i) => (i === index ? { ...row, mappedName } : row)))
  }

  const mappingPayload = useMemo(
    () => mappings.map((m) => ({ headerName: m.headerName, mappedName: m.mappedName })),
    [mappings]
  )

  useEffect(() => {
    if (!mappingPayload.length) return
    let cancelled = false
    setCheckingClashes(true)
    checkAdExportClashes(mappingPayload)
      .then((data) => { if (!cancelled) setClashPreview(data) })
      .catch(() => { if (!cancelled) setClashPreview(null) })
      .finally(() => { if (!cancelled) setCheckingClashes(false) })
    return () => { cancelled = true }
  }, [mappingPayload])

  const hasClashes = clashPreview?.hasClashes

  return (
    <section className="manual-mapping">
      <div className="manual-mapping-header">
        <h3>Column mapping (IQVIATools preview)</h3>
        <p>Confirm how each column maps before load. AD export names are checked live.</p>
      </div>

      <div className="mapping-table-wrap">
        <table className="mapping-table">
          <thead>
            <tr>
              <th>Header</th>
              <th>Mapped to</th>
              <th>DB column</th>
              <th>AD export</th>
            </tr>
          </thead>
          <tbody>
            {mappings.map((row, index) => {
              const sim = clashPreview?.clientListHeaders?.find(
                (h) => h.headerName === row.headerName
              )
              const clashNames = new Set(
                (clashPreview?.clashes ?? []).flatMap((c) =>
                  c.columns.map((col) => col.headerName)
                )
              )
              return (
                <tr key={row.headerName} className={clashNames.has(row.headerName) ? 'clash-row' : ''}>
                  <td><code>{row.headerName}</code></td>
                  <td>
                    <select
                      value={row.mappedName}
                      onChange={(e) => updateMapping(index, e.target.value)}
                      disabled={loading}
                    >
                      {options.map((opt) => (
                        <option key={opt} value={opt}>{opt}</option>
                      ))}
                    </select>
                  </td>
                  <td><code>{sim?.dbColumnName ?? '—'}</code></td>
                  <td><code>{sim?.adExportName ?? '—'}</code></td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {checkingClashes && <p className="mapping-hint">Checking AD export names…</p>}
      {hasClashes && (
        <div className="mapping-clash-alert" role="alert">
          <strong>AD export clashes detected</strong>
          <ul>
            {clashPreview.clashes.map((clash) => (
              <li key={clash.adExportName}>
                {clash.adExportName}: {clash.columns.map((c) => c.headerName).join(', ')}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mapping-actions">
        <button type="button" className="btn-secondary" onClick={onBack} disabled={loading}>
          Back
        </button>
        <button
          type="button"
          className="btn-primary"
          onClick={() => onRunCheck(file, mappingPayload)}
          disabled={loading || !mappingPayload.length}
        >
          {hasClashes ? 'Run check anyway' : 'Run pre-flight check'}
        </button>
      </div>
    </section>
  )
}
