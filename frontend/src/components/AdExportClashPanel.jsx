import './AdExportClashPanel.css'

function ClashCard({ clash }) {
  return (
    <div className="clash-card">
      <div className="clash-card-title">
        Duplicate AD export name: <code>{clash.adExportName}</code>
      </div>
      <table className="clash-table">
        <thead>
          <tr>
            <th>Your column</th>
            <th>Mapping</th>
            <th>DB name</th>
            <th>AD export</th>
          </tr>
        </thead>
        <tbody>
          {clash.columns.map((col) => (
            <tr key={col.headerName}>
              <td><code>{col.headerName}</code></td>
              <td>{col.mapped ? 'Mapped for matching' : 'Extra data'}</td>
              <td><code>{col.dbColumnName}</code></td>
              <td><code>{clash.adExportName}</code></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function AdExportClashPanel({ clashes }) {
  if (!clashes?.length) return null

  const names = clashes.map((c) => c.adExportName).join(', ')

  return (
    <section className="ad-export-clash-panel" aria-labelledby="ad-clash-heading">
      <h3 id="ad-clash-heading">AD export column name conflict</h3>

      <div className="clash-section">
        <h4>What is the issue?</h4>
        <p>
          This file would create <strong>{clashes.length} duplicate AD export column name{clashes.length > 1 ? 's' : ''}</strong>
          {' '}(<code>{names}</code>) if loaded into IQVIATools as currently mapped.
          AD export cannot have two columns with the same name.
        </p>
      </div>

      <div className="clash-section">
        <h4>Why is it happening?</h4>
        <p>
          IQVIATools prefixes mapped columns with <code>tl_</code> and converts extra columns from
          {' '}<code>x_</code> to <code>tl_</code>. When one mapped column and one extra column
          resolve to the same AD name, you get a clash — for example{' '}
          <code>CLEAN_CITY</code> mapped to <code>CITY</code> and plain <code>CITY</code> left as extra
          both become <code>tl_CITY</code>.
        </p>
      </div>

      <div className="clash-section">
        <h4>Which columns clash?</h4>
        {clashes.map((clash) => (
          <ClashCard key={clash.adExportName} clash={clash} />
        ))}
      </div>

      <div className="clash-fix">
        <strong>What to do:</strong> On the mapping screen, map only one city/state set for matching,
        or rename/remove the other columns so each AD export name is unique before loading the list.
      </div>
    </section>
  )
}
