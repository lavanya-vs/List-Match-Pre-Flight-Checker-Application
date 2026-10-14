import { useState } from 'react'
import './WarningsTable.css'

const PAGE_SIZE = 25

export default function WarningsTable({ warnings, total }) {
  const [page, setPage] = useState(0)
  const totalPages = Math.ceil(warnings.length / PAGE_SIZE)
  const slice = warnings.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <div className="warnings-table">
      <h3>Row warnings ({total.toLocaleString()} rows)</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Row</th>
              <th>Warnings</th>
            </tr>
          </thead>
          <tbody>
            {slice.map((row) => (
              <tr key={row.rowNumber}>
                <td className="row-num">{row.rowNumber}</td>
                <td>
                  {row.warnings.map((w, i) => (
                    <span key={i} className="warning-tag">{w}</span>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPages > 1 && (
        <div className="pagination">
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  )
}
