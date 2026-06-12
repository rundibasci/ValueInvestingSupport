# Phase Z5: Demo UI — Implementation Plan

## Group 1: Project Structure

1.1 Create `demo-ui/` directory at the project root  
1.2 Create `demo-ui/index.html` skeleton: HTML5 boilerplate with CDN tags for React 18, ReactDOM, Babel standalone, and Tailwind CSS  
1.3 Verify the file opens in a browser and renders without console errors  

## Group 2: Core UI Shell

2.1 Ticker input form — controlled input + "Analyze" button; `onSubmit` triggers the fetch to `http://localhost:8080/demo/analyze/{symbol}`  
2.2 Loading state — spinner or "Analyzing…" text while the request is in-flight  
2.3 Error state for 404 — "Symbol not found" message  
2.4 Error state for 503 / network failure — "Service unavailable. Is the backend running?" message  
2.5 Reset state when the user types a new ticker  

## Group 3: Results Display

3.1 Company header card — name, ticker, sector, current price with currency  
3.2 Valuation card — DCF fair value (base / low / high range), Graham Number, composite fair value  
3.3 Handle `valuation.dcf === null` gracefully — show "DCF not available: insufficient FCF history" instead of blank or broken field  
3.4 MoS badge — color-coded pill: green (> 15%), yellow (5–15%), red (< 5% or negative); show the percentage value inside  
3.5 Recommendation label — map `recommendation` string (e.g. `QUALITY_VALUE`) to a human-readable sentence  
3.6 Financial summary row — revenue, net income, FCF, EPS in compact format  

## Group 4: Polish & Validation

4.1 MiFID II disclaimer footer (exact text from Z4 response `disclaimer` field)  
4.2 Keyboard accessibility — Enter key submits the form; focus moves to results after load  
4.3 Manual smoke test against the live backend: `AAPL`, `MSFT`, `INVALID123`, and a stock with no FCF history  
4.4 Confirm no CORS errors in browser DevTools (no Spring Boot changes should be needed)  
4.5 Verify no console errors in Chrome and Firefox  
