import { useRef, useState } from 'react'
import './FileUpload.css'

export default function FileUpload({ onUpload, loading, progress = 0, progressMsg = '' }) {
  const inputRef = useRef(null)
  const [dragOver, setDragOver] = useState(false)
  const [selectedName, setSelectedName] = useState(null)

  const handleFile = (file) => {
    if (!file) return
    const valid = /\.(csv|xlsx|xls)$/i.test(file.name)
    if (!valid) {
      alert('Please upload a CSV or Excel file (.csv, .xlsx)')
      return
    }
    setSelectedName(file.name)
    onUpload(file)
  }

  const onDrop = (e) => {
    e.preventDefault()
    setDragOver(false)
    handleFile(e.dataTransfer.files[0])
  }

  return (
    <section className="upload-section">
      <div
        className={`dropzone ${dragOver ? 'drag-over' : ''} ${loading ? 'loading' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => !loading && inputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => e.key === 'Enter' && inputRef.current?.click()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".csv,.xlsx,.xls"
          hidden
          onChange={(e) => handleFile(e.target.files[0])}
        />

        {loading ? (
          <div className="upload-content">
            <div className="spinner" />
            <p>{progressMsg || 'Checking file…'}</p>
            {progress > 0 && (
              <p className="upload-hint">{progress}%</p>
            )}
          </div>
        ) : (
          <div className="upload-content">
            <div className="upload-icon">↑</div>
            <p className="upload-primary">
              Drop a file here or <span className="link">browse</span>
            </p>
            <p className="upload-hint">CSV or Excel</p>
            {selectedName && (
              <p className="upload-selected">Last: {selectedName}</p>
            )}
          </div>
        )}
      </div>
    </section>
  )
}
