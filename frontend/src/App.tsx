import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { SecurityDetailPage } from "./pages/SecurityDetailPage";
import { LoginPage } from "./pages/LoginPage";
import { UserProvisioningPage } from "./pages/UserProvisioningPage";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ScreenerPage } from "./pages/ScreenerPage";
import { PortfolioPage } from "./pages/PortfolioPage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { DashboardPage } from "./pages/DashboardPage";
import { AdminSeedPage } from "./pages/AdminSeedPage";
import { SecurityReviewPage } from "./pages/SecurityReviewPage";
import { SeedUniversePage } from "./pages/SeedUniversePage";
import { OAuthCallbackPage } from "./pages/OAuthCallbackPage";
import { AccountPage } from "./pages/AccountPage";
import { AuditPage } from "./pages/AuditPage";
import { ChecklistPage } from "./pages/ChecklistPage";
import { UniverseCurationPage } from "./pages/UniverseCurationPage";
import { AdminJobsPage } from "./pages/AdminJobsPage";
import { MarketDataFallbacksPage } from "./pages/MarketDataFallbacksPage";

export default function App(): JSX.Element {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route path="auth/oauth2/callback" element={<OAuthCallbackPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="checklists" element={<ChecklistPage />} />
          <Route path="screener" element={<ScreenerPage />} />
          <Route path="securities/:symbol/review" element={<SecurityReviewPage />} />
          <Route path="securities/:symbol" element={<SecurityDetailPage />} />
          <Route path="portfolio" element={<PortfolioPage />} />
          <Route path="watchlist" element={<WatchlistPage />} />
          <Route path="seed" element={<SeedUniversePage />} />
          <Route path="universe-curation" element={<UniverseCurationPage />} />
          <Route path="admin/seed" element={<AdminSeedPage />} />
          <Route path="admin/jobs" element={<AdminJobsPage />} />
          <Route path="admin/fallbacks" element={<MarketDataFallbacksPage />} />
          <Route path="admin/users" element={<UserProvisioningPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}
