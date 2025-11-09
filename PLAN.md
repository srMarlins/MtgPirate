# US MTG Proxy Order CSV Automation – Implementation Plan (UI Version, Multiplatform Update)

Goal: Build a desktop GUI tool that takes a Moxfield-exported decklist (plain text) and produces a seller‑compatible CSV (plus summary) by matching cards against the usmtgproxy.com single card list. Provide interactive resolution for ambiguous or missing matches.

---
## 0. Assumptions & Clarifications (Adjust Later if Needed)
- Input: Plain text list (e.g. `example-input.txt`) in Moxfield deck export style: `<qty> <Card Name>` lines; optional sections like `SIDEBOARD:` or commander lines at end. Blank lines allowed.
- Sideboard & commanders excluded by default; user can toggle inclusion via UI checkboxes.
- Output CSV columns: `Card Name,Set,SKU,Card Type,Quantity,Base Price` followed by blank line and summary block like `example-output.csv`.
- Pricing: Base Price sourced from site for selected variant (Regular default). User can change variant per card in UI.
- Website source: `https://www.usmtgproxy.com/wp-content/uploads/singlecardslist.html` – static HTML table. Cached locally.
- SKU & Set code taken exactly from site.
- Card Type values: Regular, Holo, Foil (adapt if site differs).
- Provide manual override for name, set, variant if automatic match fails.
- No automated tests (per request); rely on manual verification.

---
## 1. High-Level Architecture (UI Application)
1. CatalogFetcher + Cache (download & persist JSON)
2. CatalogParser (HTML -> model)
3. Normalizer (name normalization & indexing)
4. DecklistParser (raw text -> entries)
5. Matcher (exact + normalized + optional fuzzy suggestions)
6. UI Layer (Compose for Desktop or JavaFX) – screens & state management
7. Manual Resolution Layer (user selects among candidates)
8. Exporter (CSV + summary)
9. Settings & Persistence (user preferences: preferred sets, default variant, cache age)

---
## 2. Data Models (Kotlin data classes)
- CardVariant { nameOriginal, nameNormalized, setCode, sku, variantType, priceInCents: Int }
- Catalog { variants: List<CardVariant>, indexByName: Map<String, List<CardVariant>> }
- DeckEntry { originalLine, qty: Int, cardName, section: Section, include: Boolean }
- MatchCandidate { variant: CardVariant, score: Int, reason: String }
- MatchStatus { UNRESOLVED, AUTO_MATCHED, AMBIGUOUS, NOT_FOUND, MANUAL_SELECTED }
- DeckEntryMatch { deckEntry, status, selectedVariant?, candidates: List<MatchCandidate>, notes }
- Preferences { includeSideboard: Boolean, includeCommanders: Boolean, variantPriority: List<String>, setPriority: List<String>, fuzzyEnabled: Boolean, cacheMaxAgeHours: Int }
- AppState { catalog?, deckEntries, matches, preferences }

---
## 3. Normalization Rules
(Same as previous plan) Deterministic normalization:
- Lowercase
- Remove punctuation (commas, apostrophes, quotes); dashes -> space; collapse spaces
- Replace fancy quotes/dashes with ASCII
- Trim
- Split/adventure/modal: include primary part before ` // ` plus full form index
- Strip diacritics

Maintain both original & normalized names; build maps for fast lookup.

---
## 4. Matching Strategy
Order:
1. Exact (case-sensitive)
2. Case-insensitive
3. Normalized exact
4. Set preference filter (if multiple sets)
5. Variant priority (Regular > Foil > Holo by default)
6. Fuzzy (if enabled) using Levenshtein distance threshold (<=2 for len<=15 else <=3)

Results classified:
- AUTO_MATCHED: one deterministic best candidate
- AMBIGUOUS: multiple viable candidates (user must choose)
- NOT_FOUND: no candidates (user may type search or override)
- MANUAL_SELECTED: user picks variant in UI

Fuzzy suggestions displayed in a drawer/modal for NOT_FOUND or AMBIGUOUS.

---
## 5. Variant & Price Selection
- Global default variant (dropdown: Regular/Foil/Holo). Applies to AUTO_MATCHED if variant exists; fallback to Regular.
- Per-card variant selector (combo box) updates deckEntryMatch.selectedVariant.
- Price displayed per unit; extended total shown in summary pane.

---
## 6. UI Design & Screens
Framework Choice: Compose for Desktop (modern Kotlin, easier reactive state) OR JavaFX. Choose Compose for Desktop.

Primary Windows/Panels:
1. Startup Screen:
   - Load Decklist (File chooser) or Paste Text (textarea)
   - Buttons: Parse & Proceed
2. Main Workspace (Split Layout):
   - Left: Deck Entries Table
     - Columns: Qty | Card Name | Status | Chosen Set | Variant | Price
     - Row highlighting: AMBIGUOUS (yellow), NOT_FOUND (red), MANUAL_SELECTED (green), AUTO_MATCHED (default)
   - Right: Detail Panel (for selected entry)
     - Original line, normalized name
     - Candidate list (radio buttons) with set, variant, price, match reason, score
     - Search box: live filter catalog by text
     - Actions: Apply Selection, Clear Selection, Mark Excluded
3. Top Bar:
   - Refresh Catalog button (shows spinner)
   - Preferences button (opens modal)
   - Export CSV button
4. Bottom Summary Bar:
   - Counts: Regular / Holo / Foil / Unresolved / Not Found
   - Total Price
5. Preferences Modal:
   - Include Sideboard / Commanders (checkboxes)
   - Variant priority reorder (drag list)
   - Set priority (comma-separated list input)
   - Fuzzy matching enabled (toggle)
   - Cache max age hours
6. Manual Override Dialog:
   - Fields: Card Name Override, Force Set Code, Force Variant, Force SKU
   - Apply to selected entry
7. Logs & Diagnostics Panel (optional collapsible):
   - Recent operations, errors, fetch status.

User Flow:
- Load decklist -> parse -> initial matching automatically runs -> unresolved counts visible -> user resolves AMBIGUOUS/NOT_FOUND -> export when all required entries have selectedVariant.

---
## 7. Catalog Fetching & Caching
- On app start: check `data/catalog.json` timestamp vs preferences.cacheMaxAgeHours.
- If stale or missing: download & parse, else load cached.
- Provide progress indicator and error toast on failure (fallback to existing cache if available).
- Cache schema version included to force refresh on model changes.

---
## 8. HTML Parsing Strategy
(As before) KSoup parse largest table or matching header row containing required columns.
- Map headers case-insensitive to: name, set, sku, type, price.
- Clean price -> Int (cents).
- Deduplicate on (normalizedName, setCode, variantType) keeping lowest price variant if duplicates.

---
## 9. Fuzzy Matching Implementation (UI Integration)
- Lazy compute candidate lists only when user selects a NOT_FOUND entry (performance optimization) OR initial match pass if fuzzyEnabled.
- Display sorted by ascending score, then price.
- Provide distance and reason label.

---
## 10. Manual Overrides & Persistence
- Overrides applied immediately to selected entry (re-run match pipeline restricted to forced parameters).
- Save all overrides to `data/overrides.json` automatically; reload on next start.
- Option to export overrides file for sharing.

---
## 11. Export Logic (CSV + Summary)
Steps:
1. Validate all included entries have selectedVariant (unless user allows exporting with unresolved -> show confirmation dialog).
2. Aggregate rows by (cardName, setCode, sku, variant) summing quantities.
3. Write header + rows.
4. Append blank line + summary lines.
5. Show success toast & open file location button.

Edge Cases:
- Zero matched entries (warn user).
- Duplicate deck lines (merged automatically).
- Mixed variants for same card name allowed (user picks distinct rows).

---
## 12. Error Handling & User Feedback
- Network failures: non-blocking; show dialog with Retry / Use Cache / Cancel.
- Parsing failures: show raw snippet of unexpected HTML for user screenshot.
- Unresolved entries: highlight and disable Export until acknowledged OR override.
- Exceptions: global handler -> error dialog + log entry.

---
## 13. Logging
- Simple in-memory log list + file append `data/app.log`.
- Levels: INFO, WARN, ERROR, DEBUG (toggle debug in preferences).
- Display last N (e.g., 200) in diagnostics panel.

---
## 14. Performance Considerations
- Catalog indexing O(n).
- UI filtering uses precomputed normalized map.
- Fuzzy distance computed only for subset filtered by length difference (|len(card)-len(entry)| <= 3).
- Deck sizes small (<300 entries) – operations negligible.

---
## 15. Accessibility & UX Notes
- Keyboard navigation: Up/Down to move deck entries; Enter to open candidate selection; Ctrl+F to focus search.
- Color + icon indicators (not color-only) for status.
- Persistent window size & position saved in preferences.

---
## 16. Future Enhancements (Backlog)
- Multi-window support (compare multiple decklists).
- Bulk operations: apply set preference to all ambiguous matches.
- Automatic cheapest set suggestion toggle.
- Dark mode theme.
- Auto-update application (packaged installer).
- Drag & drop decklist file into window.
- Plugin architecture for additional sellers.

---
## 17. Directory & File Layout Plan (Updated for UI)
```
src/commonMain/kotlin/
  model/Models.kt
  match/Normalizer.kt
  match/Levenshtein.kt
  catalog/CatalogParser.kt   // ksoup parsing logic
  catalog/CatalogSerialization.kt
  deck/DecklistParser.kt
  match/Matcher.kt
  util/Price.kt
  util/Logging.kt
src/jvmMain/kotlin/
  app/Main.kt                  // Compose Desktop entry
  ui/StartupScreen.kt
  ui/MainScreen.kt
```

Runtime `data/` folder: catalog.json, overrides.json, preferences.json, app.log.

---
## 18. Build & Dependencies
Gradle Dependencies (Kotlin Multiplatform):
- plugins: kotlin-multiplatform, org.jetbrains.compose, kotlin-serialization.
- commonMain dependencies: kotlinx-serialization-json, ksoup, kotlinx-datetime.
- jvmMain dependencies: compose.desktop.currentOs.

---
## 19. Implementation Phases Checklist (UI Focus)
Phase 1: Gradle setup (Compose) + skeleton packages & data classes
Phase 2: Catalog fetch & parse + caching
Phase 3: Decklist parser + normalization + basic matching (exact)
Phase 4: UI Startup Screen & MainScreen with table of entries (status placeholders)
Phase 5: Integrate automatic matching & display statuses
Phase 6: CandidatePanel with manual selection + fuzzy suggestions
Phase 7: Preferences dialog & variant/set priority logic
Phase 8: Overrides persistence & application
Phase 9: CSV export + summary bar
Phase 10: Polishing (error dialogs, logging, performance tweaks, packaging instructions)

---
## 20. Risks & Mitigations
- HTML structure changes -> robust header mapping & fallback; show diagnostic snippet.
- Large catalog slows fuzzy -> restrict candidate pool via length & token pre-filter.
- User confusion on ambiguous sets -> tooltip with set code explanation; allow cheapest auto-suggestion.
- Missing variants (user chooses Foil but only Regular exists) -> graceful fallback + warning icon.

---
## 21. Success Criteria
- User can load example deck, resolve ambiguous/missing items interactively, and export a CSV identical in format to `example-output.csv`.
- Application handles offline mode with cached catalog.
- UI clearly indicates unresolved entries; no crashes in standard flow.

---
## 22. Next Immediate Actions (Phase 1)
1. Convert build.gradle.kts to multiplatform + compose.
2. Add dependencies (serialization, ksoup, datetime).
3. Create common data model & normalization utilities.
4. Basic Compose Desktop window ("US MTG Proxy Tool" placeholder) using AppState stub.
5. Implement temporary in-memory decklist load (paste area) before full file chooser.

After Phase 1: proceed to catalog fetch & parse integration.

---
This updated plan reflects a shift from CLI + tests to an interactive desktop UI per user request.
