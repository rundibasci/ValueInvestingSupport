import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { ScreenerPage } from '../ScreenerPage'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import * as api from '../../api/screener'

// Mock the API client
vi.mock('../../api/screener', () => ({
  fetchScreener: vi.fn(),
  fetchScreenerPresets: vi.fn(),
  fetchSectors: vi.fn(),
  fetchExchanges: vi.fn(),
}))

const mockPresets: Record<string, api.ScreenerRequest> = {
  graham: {
    minMarginOfSafety: 15,
    minRoic: 10,
    maxDebtToEquity: 1.0,
    sortField: 'totalScore',
    sortDirection: 'DESC',
    page: 0,
    pageSize: 20,
  },
  dividend: {
    minMarginOfSafety: 5,
    minDividendYield: 2.0,
    sortField: 'totalScore',
    sortDirection: 'DESC',
    page: 0,
    pageSize: 20,
  },
  quality: {
    minValueScore: 60,
    minRoic: 15,
    maxDebtToEquity: 1.5,
    sortField: 'totalScore',
    sortDirection: 'DESC',
    page: 0,
    pageSize: 20,
  },
}

const mockSectors = ['Technology', 'Financial Services', 'Healthcare']
const mockExchanges = ['NASDAQ', 'NYSE', 'AMEX']

const mockScreenerResponse = {
  results: [
    {
      symbol: 'AAPL',
      companyName: 'Apple Inc.',
      sector: 'Technology',
      exchange: 'NASDAQ',
      currentPrice: 180.0,
      compositeFairValue: 210.0,
      marginOfSafety: 16.7,
      totalScore: 85,
      mosScore: 80,
      qualityScore: 90,
      safetyScore: 85,
      growthScore: 80,
      dividendScore: 70,
      recommendation: 'STRONG_BUY',
    },
  ],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
}

describe('ScreenerPage', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    })

    vi.mocked(api.fetchScreenerPresets).mockResolvedValue(mockPresets)
    vi.mocked(api.fetchSectors).mockResolvedValue(mockSectors)
    vi.mocked(api.fetchExchanges).mockResolvedValue(mockExchanges)
    vi.mocked(api.fetchScreener).mockResolvedValue(mockScreenerResponse)
  })

  const renderComponent = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/screener']}>
          <ScreenerPage />
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  it('renders presets, filters, and screener results', async () => {
    renderComponent()

    expect(await screen.findByText('Screener')).toBeInTheDocument()
    expect(await screen.findByText('Graham Number')).toBeInTheDocument()
    expect(await screen.findByText('Apple Inc.')).toBeInTheDocument()

    const sectorDropdown = screen.getByLabelText('Sector')
    expect(sectorDropdown).toBeInTheDocument()
    expect(screen.getAllByText('Technology').length).toBeGreaterThan(0)
  })

  it('clicking a preset button updates filters and fetches data', async () => {
    renderComponent()

    const grahamButton = await screen.findByText('Graham Number')
    fireEvent.click(grahamButton)

    await waitFor(() => {
      expect(api.fetchScreener).toHaveBeenCalledWith(
        expect.objectContaining({
          minMarginOfSafety: 15,
          minRoic: 10,
          maxDebtToEquity: 1.0,
        })
      )
    })
  })

  it('resets filters when clear/reset button clicked', async () => {
    renderComponent()

    const resetButton = await screen.findByText('Reset Filters')
    fireEvent.click(resetButton)

    await waitFor(() => {
      expect(api.fetchScreener).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sector: null,
          exchange: null,
          minMarginOfSafety: null,
          maxMarginOfSafety: null,
        })
      )
    })
  })
})
