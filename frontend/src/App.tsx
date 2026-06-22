import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { PlaceholderPage } from "./pages/PlaceholderPage";
import { SecurityDetailPage } from "./pages/SecurityDetailPage";
import { LoginPage } from "./pages/LoginPage";
import { UserProvisioningPage } from "./pages/UserProvisioningPage";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ScreenerPage } from "./pages/ScreenerPage";
import { PortfolioPage } from "./pages/PortfolioPage";
import { WatchlistPage } from "./pages/WatchlistPage";

export default function App(): JSX.Element {
  return (
    <Routes>
      <Route path="login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route
            index
            element={
              <PlaceholderPage
                eyebrow="Value investing advisory platform"
                title="A calmer place to make investment decisions."
                description="The frontend foundation is ready for the workflow from discovery to monitoring, with transparent data and conservative decision support at its center."
                nextPhase="Authentication UI (H2) will add secure account access to this shell."
              />
            }
          />
          <Route path="screener" element={<ScreenerPage />} />
          <Route path="securities/:symbol" element={<SecurityDetailPage />} />
          <Route path="portfolio" element={<PortfolioPage />} />
          <Route path="watchlist" element={<WatchlistPage />} />
          <Route path="admin/users" element={<UserProvisioningPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}
