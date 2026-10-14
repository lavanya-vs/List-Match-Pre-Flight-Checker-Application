import './MatchTypes.css'

export default function MatchTypes({ matchTypes }) {
  return (
    <div className="match-types">
      <h3>Match type assessment</h3>
      <div className="match-list">
        {matchTypes.map((mt, index) => (
          <div key={`${mt.type}-${mt.label}-${index}`} className={`match-item ${mt.viable ? 'viable' : 'not-viable'}`}>
            <div className="match-top">
              <span className="match-label">{mt.label}</span>
              <span className="match-coverage">{mt.coveragePercent.toFixed(1)}% coverage</span>
            </div>
            <p className="match-message">{mt.message}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
