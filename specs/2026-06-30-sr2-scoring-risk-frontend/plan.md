# SR2 Scoring & Risk Frontend Plan

1. Inspect existing frontend contracts and surfaces.
   - Locate review-page API types, score widgets, screener table columns, and comparison view components.
   - Confirm where SR1 backend fields are already present.
   - Identify the smallest typed model changes needed for risk intelligence rendering.

2. Add shared scoring/risk UI helpers.
   - Add or extend display helpers for score gate status, sector weight profile, Piotroski interpretation, Altman zone, cyclicality, and earnings quality.
   - Keep missing-data messaging consistent with existing availability conventions.
   - Add focused unit coverage for classification and formatting helpers where practical.

3. Extend the review page.
   - Show MoS gate status, raw score versus capped score, and sector weight profile details.
   - Add Piotroski F-Score, Altman Z-Score, cyclicality, and earnings-quality sections.
   - Preserve MiFID II decision-support framing and avoid buy/sell copy.

4. Extend screener and comparison surfaces.
   - Add screener columns for Piotroski score and Altman zone.
   - Add comparison cells for Piotroski, Altman, cyclicality, and earnings quality if a comparison component exists.
   - Ensure compact views handle unavailable metrics without layout breakage.

5. Validate and merge readiness.
   - Run frontend build and available tests.
   - Run backend tests only if frontend changes require backend contract changes.
   - Review git diff for scope and update this spec if implementation constraints changed the plan.
