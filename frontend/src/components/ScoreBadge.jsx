import './ScoreBadge.css'

const LABELS = {
  READY: 'Ready to match',
  NEEDS_WORK: 'Fix before matching',
  WILL_FAIL: 'Cannot match yet',
}

export default function ScoreBadge({ score }) {
  const key = score?.toLowerCase()?.replace('_', '-') ?? 'will-fail'
  return (
    <span className={`score-badge badge-${key}`}>
      {LABELS[score] ?? score}
    </span>
  )
}
