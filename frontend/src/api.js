const DEFAULT_API = 'http://localhost:8085'

const CHUNK_THRESHOLD = 50 * 1024 * 1024

const DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024



export function getApiBase() {

  const { protocol, hostname, port } = window.location

  if (protocol === 'file:') return DEFAULT_API

  if (port === '5173' || port === '3000') return DEFAULT_API

  if (port === '8085' || port === '') {

    return `${protocol}//${hostname}${port ? `:${port}` : ''}`

  }

  return DEFAULT_API

}



export async function apiFetch(path, options) {

  const url = `${getApiBase()}${path}`

  try {

    return await fetch(url, options)

  } catch (err) {

    const hint = err.name === 'AbortError'

      ? 'Upload timed out — very large files may take several minutes.'

      : 'Could not connect while uploading. If the file is very large, wait a few minutes or check the backend terminal for errors.'

    throw new Error(

      `${hint} Server: ${getApiBase()}. Start the backend with start-backend.bat, then open http://localhost:8085`

    )

  }

}



export async function previewHeaders(file) {

  const formData = new FormData()

  formData.append('file', file)

  const res = await apiFetch('/api/headers/preview', { method: 'POST', body: formData })

  const data = await res.json()

  if (!res.ok) throw new Error(data.error || 'Could not preview headers')

  return data

}



export async function checkAdExportClashes(columnMappings) {

  const res = await apiFetch('/api/ad-export-clash-check', {

    method: 'POST',

    headers: { 'Content-Type': 'application/json' },

    body: JSON.stringify({ columnMappings }),

  })

  const data = await res.json()

  if (!res.ok) throw new Error(data.error || 'Clash check failed')

  return data

}



async function pollJob(jobId, onProgress) {

  while (true) {

    const res = await apiFetch(`/api/jobs/${jobId}`)

    const job = await res.json()

    if (!res.ok) throw new Error(job.error || 'Could not read job status')

    onProgress?.(job.progress ?? 0, job.message ?? 'Processing…')

    if (job.status === 'COMPLETE') {
      if (job.result) return job.result
      if (job.resultJson) return JSON.parse(job.resultJson)
      throw new Error('Analysis finished but no result was returned.')
    }

    if (job.status === 'FAILED') throw new Error(job.error || job.message || 'Processing failed')

    await new Promise((r) => setTimeout(r, 1000))

  }

}



async function uploadChunked(file, columnMappings, onProgress) {

  onProgress?.(0, 'Starting chunked upload…')

  const initRes = await apiFetch('/api/upload/init', {

    method: 'POST',

    headers: { 'Content-Type': 'application/json' },

    body: JSON.stringify({

      fileName: file.name,

      fileSize: file.size,

      chunkSize: DEFAULT_CHUNK_SIZE,

    }),

  })

  const init = await initRes.json()

  if (!initRes.ok) throw new Error(init.error || 'Could not start chunked upload')



  const { uploadId, chunkSize, totalChunks } = init

  let offset = 0

  for (let index = 0; index < totalChunks; index++) {

    const chunk = file.slice(offset, offset + chunkSize)

    offset += chunkSize

    const chunkRes = await apiFetch(`/api/upload/${uploadId}/chunk/${index}`, {

      method: 'PUT',

      headers: { 'Content-Type': 'application/octet-stream' },

      body: chunk,

    })

    if (!chunkRes.ok) {

      const err = await chunkRes.json()

      throw new Error(err.error || `Chunk ${index + 1} failed`)

    }

    const pct = Math.round(((index + 1) / totalChunks) * 40)

    onProgress?.(pct, `Uploading chunk ${index + 1} of ${totalChunks}…`)

  }



  onProgress?.(45, 'Finalizing upload…')

  let completeUrl = `/api/upload/${uploadId}/complete?saveHistory=true`

  if (columnMappings?.length) {

    completeUrl += `&columnMappings=${encodeURIComponent(JSON.stringify(columnMappings))}`

  }

  const completeRes = await apiFetch(completeUrl, { method: 'POST' })

  const complete = await completeRes.json()

  if (!completeRes.ok) throw new Error(complete.error || 'Could not complete upload')

  return pollJob(complete.jobId, onProgress)

}



async function uploadSimple(file, columnMappings, onProgress) {

  onProgress?.(5, 'Uploading…')

  const formData = new FormData()

  formData.append('file', file)

  if (columnMappings?.length) {

    formData.append('columnMappings', JSON.stringify(columnMappings))

  }

  const useAsync = file.size >= CHUNK_THRESHOLD
  const res = await apiFetch(`/api/check?async=${useAsync}&saveHistory=true`, { method: 'POST', body: formData })

  const data = await res.json()

  if (res.status === 202 && data.jobId) {

    return pollJob(data.jobId, onProgress)

  }

  if (!res.ok) throw new Error(data.error || 'Upload failed')

  return data

}



/** Upload any file — uses chunked upload for files over 50 MB. */

export async function uploadAndCheck(file, columnMappings, onProgress) {

  if (file.size >= CHUNK_THRESHOLD) {

    return uploadChunked(file, columnMappings, onProgress)

  }

  return uploadSimple(file, columnMappings, onProgress)

}


