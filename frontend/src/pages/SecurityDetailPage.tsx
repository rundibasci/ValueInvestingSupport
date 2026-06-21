import { useParams } from 'react-router-dom'
import { PlaceholderPage } from './PlaceholderPage'

export function SecurityDetailPage(): JSX.Element {
  const { symbol = 'security' } = useParams()

  return (
    <PlaceholderPage
      eyebrow="Security research"
      title={symbol.toUpperCase()}
      description="A focused workspace for company fundamentals, valuation assumptions, financial resilience, and transparent source data."
      nextPhase="Security Detail UI (H4) will connect this route to the existing security-detail APIs."
    />
  )
}
