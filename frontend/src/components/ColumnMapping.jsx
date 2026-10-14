import './ColumnMapping.css'

const FIELD_LABELS = {
  FIRST_NAME: 'First Name',
  LAST_NAME: 'Last Name',
  NPI: 'NPI',
  ZIP: 'Zip',
  ADDRESS: 'Address',
  CITY: 'City',
  STATE: 'State',
  EMAIL: 'Email',
  PHONE: 'Phone',
}

const FIELD_ORDER = [
  'NPI',
  'FIRST_NAME',
  'LAST_NAME',
  'ADDRESS',
  'CITY',
  'STATE',
  'ZIP',
  'EMAIL',
  'PHONE',
]

export default function ColumnMapping({ detected, unmapped }) {
  const columns = detected || {}
  const foundCount = FIELD_ORDER.filter((field) => columns[field]).length

  return (
    <div className="column-mapping">
      <h3>Standard fields matched to your columns</h3>
      <div className="mapping-grid">
        {FIELD_ORDER.map((field) => {
          const clientColumn = columns[field]
          return (
            <div
              key={field}
              className={clientColumn ? 'mapping-row' : 'mapping-row missing'}
            >
              <span className="field-label">{FIELD_LABELS[field]}</span>
              <span className="field-arrow">→</span>
              {clientColumn ? (
                <code className="field-column">{clientColumn}</code>
              ) : (
                <span className="field-absent">not in your file</span>
              )}
            </div>
          )
        })}
      </div>
      <p className="mapping-count">
        {foundCount} of {FIELD_ORDER.length} standard fields found in your file.
      </p>
      {unmapped?.length > 0 && (
        <div className="unmapped">
          <span className="unmapped-label">Unmapped:</span>
          {unmapped.map((col) => (
            <code key={col} className="unmapped-col">{col}</code>
          ))}
        </div>
      )}
    </div>
  )
}
