# Plan - Phase RCL3: Security Detail Historical Chart And Data Verification Pass

1. Inspect review/security-detail chart data flow.
   - Identify where quote, ratios, financial health, valuation, dividends, growth, and insider data are rendered.
   - Determine whether current components graph sparse or repeated values.

2. Add safe chart data helpers.
   - Detect minimum historical depth.
   - Detect repeated-value series where graphing would be misleading.
   - Provide history-window filtering for real series.

3. Improve review-page rendering.
   - Add history-window controls where historical data exists.
   - Use text-only current-value fallback with unavailable-history copy when needed.
   - Add visible FCF run feedback for all outcomes.

4. Cross-check dividends, growth, and insider availability copy.
   - Ensure unavailable/provider-limited states do not look like true zero activity.

5. Validate.
   - Run frontend typecheck/build and focused backend tests when touched.
   - Record KO route smoke evidence and update Obsidian activity note.
