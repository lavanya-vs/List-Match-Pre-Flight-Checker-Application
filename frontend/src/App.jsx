import { useState, useCallback } from 'react'

import FileUpload from './components/FileUpload'

import ManualMappingPanel from './components/ManualMappingPanel'

import ResultPanel from './components/ResultPanel'

import HistoryPanel from './components/HistoryPanel'

import { previewHeaders, uploadAndCheck } from './api'

import './App.css'



export default function App() {

  const [result, setResult] = useState(null)

  const [loading, setLoading] = useState(false)

  const [progress, setProgress] = useState(0)

  const [progressMsg, setProgressMsg] = useState('')

  const [error, setError] = useState(null)

  const [historyKey, setHistoryKey] = useState(0)

  const [pendingFile, setPendingFile] = useState(null)

  const [headerPreview, setHeaderPreview] = useState(null)



  const handleUpload = useCallback(async (file) => {

    setLoading(true)

    setError(null)

    setResult(null)

    setProgress(0)

    setProgressMsg('Reading headers…')



    try {

      const preview = await previewHeaders(file)

      setPendingFile(file)

      setHeaderPreview(preview)

    } catch (err) {

      setError(err.message)

    } finally {

      setLoading(false)

      setProgress(0)

      setProgressMsg('')

    }

  }, [])



  const handleRunCheck = useCallback(async (file, columnMappings) => {

    setLoading(true)

    setError(null)

    setResult(null)

    setProgress(0)

    setProgressMsg('Running pre-flight check…')



    try {

      const data = await uploadAndCheck(file, columnMappings, (pct, msg) => {

        setProgress(pct)

        setProgressMsg(msg)

      })

      setResult(data)

      setPendingFile(null)

      setHeaderPreview(null)

      setHistoryKey((k) => k + 1)

    } catch (err) {

      setError(err.message)

    } finally {

      setLoading(false)

      setProgress(0)

      setProgressMsg('')

    }

  }, [])



  const handleBack = () => {

    setPendingFile(null)

    setHeaderPreview(null)

  }



  return (

    <div className="app">

      <header className="header">

        <div className="header-inner">

          <div className="header-brand">

            <h1>List Match Pre-Flight Checker</h1>

            <p className="subtitle">

              Validate any client file before IQVIATools list matching

            </p>

          </div>

          <img src="/iqvia-logo.png" alt="IQVIA" className="header-logo" />

        </div>

      </header>



      <main className="main">

        {!headerPreview && (

          <FileUpload

            onUpload={handleUpload}

            loading={loading}

            progress={progress}

            progressMsg={progressMsg}

          />

        )}



        {headerPreview && pendingFile && (

          <ManualMappingPanel

            file={pendingFile}

            preview={headerPreview}

            onRunCheck={handleRunCheck}

            onBack={handleBack}

            loading={loading}

          />

        )}



        {loading && headerPreview && (

          <p className="progress-inline">{progressMsg} {progress > 0 ? `${progress}%` : ''}</p>

        )}



        {error && (

          <div className="alert alert-error" role="alert">

            {error}

          </div>

        )}



        {result && (

          <ResultPanel

            result={result.check ?? result}

            analysis={result.analysis ?? null}

          />

        )}



        <HistoryPanel refreshKey={historyKey} />

      </main>



      <footer className="footer">

        Upload any CSV or Excel · Column mapping QC · AD export clash detection

      </footer>

    </div>

  )

}


