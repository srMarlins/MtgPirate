package match

import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.DeckEntryMatch
import model.MatchCandidate
import model.MatchStatus

object Matcher {
    data class MatchConfig(
        val variantPriority: List<String>,
        val setPriority: List<String>,
        val fuzzyEnabled: Boolean
    )

    fun matchAll(entries: List<DeckEntry>, catalog: Catalog, config: MatchConfig): List<DeckEntryMatch> {
        return entries.map { matchEntry(it, catalog, config) }
    }

    private fun matchEntry(entry: DeckEntry, catalog: Catalog, config: MatchConfig): DeckEntryMatch {
        if (!entry.include) return DeckEntryMatch(entry, MatchStatus.UNRESOLVED, null, emptyList(), "Excluded")
        val normalized = NameNormalizer.normalize(entry.cardName)

        // O(1) lookup in the index (keyed by nameNormalized) to get candidates,
        // then apply set code hint filter. This replaces three O(N) linear scans.
        val indexHits = catalog.indexByName[normalized].orEmpty()
        val poolFromIndex = if (entry.setCodeHint != null) {
            indexHits.filter { it.setCode.equals(entry.setCodeHint, true) }
        } else indexHits

        // Pass 1 – Exact name match (nameOriginal == cardName)
        val exactNameMatches = poolFromIndex.filter { it.nameOriginal == entry.cardName }
        if (exactNameMatches.size == 1) {
            return DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, exactNameMatches.first())
        }
        if (exactNameMatches.size > 1) {
            val selected = selectByPriority(exactNameMatches, config, entry)
            return if (selected != null) {
                DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, selected, exactNameMatches.map { MatchCandidate(it, 0, "exact") })
            } else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, exactNameMatches.map { MatchCandidate(it, 0, "exact") })
        }

        // Pass 2 – Case-insensitive match (nameOriginal equalsIgnoreCase cardName)
        val ci = poolFromIndex.filter { it.nameOriginal.equals(entry.cardName, true) }
        if (ci.size == 1) return DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, ci.first())
        if (ci.size > 1) {
            val selected = selectByPriority(ci, config, entry)
            return if (selected != null) DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, selected, ci.map { MatchCandidate(it, 0, "ci") })
            else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, ci.map { MatchCandidate(it, 0, "ci") })
        }

        // Pass 3 – Normalized match (nameNormalized == normalized)
        // poolFromIndex already contains only variants whose nameNormalized == normalized,
        // so all remaining entries are normalized matches.
        val norm = poolFromIndex
        if (norm.size == 1) return DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, norm.first())
        if (norm.size > 1) {
            val selected = selectByPriority(norm, config, entry)
            return if (selected != null) DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, selected, norm.map { MatchCandidate(it, 0, "normalized") })
            else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, norm.map { MatchCandidate(it, 0, "normalized") })
        }

        // Pass 4 – Fuzzy matching (linear scan required for Levenshtein distance)
        if (config.fuzzyEnabled) {
            val fuzzyPool = if (entry.setCodeHint != null) {
                catalog.variants.filter { it.setCode.equals(entry.setCodeHint, true) }
            } else catalog.variants
            val fuzzyCandidates = fuzzy(normalized, Catalog(fuzzyPool))
            return if (fuzzyCandidates.isEmpty()) DeckEntryMatch(entry, MatchStatus.NOT_FOUND)
            else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, fuzzyCandidates)
        }
        return DeckEntryMatch(entry, MatchStatus.NOT_FOUND)
    }

    private fun selectByPriority(list: List<CardVariant>, config: MatchConfig, entry: DeckEntry): CardVariant? {
        // If set code hint was provided, keep only that set
        val filtered = entry.setCodeHint?.let { code -> list.filter { it.setCode.equals(code, true) } }.orEmpty().ifEmpty { list }
        // Set priority first, then variant priority
        val bySet = if (config.setPriority.isNotEmpty()) {
            filtered.sortedBy { idxOrEnd(config.setPriority, it.setCode) }
        } else filtered
        val byVariant = bySet.sortedBy { idxOrEnd(config.variantPriority, it.variantType.displayName) }
        return byVariant.firstOrNull()
    }

    private fun idxOrEnd(list: List<String>, value: String): Int {
        val idx = list.indexOf(value)
        return if (idx >= 0) idx else list.size
    }

    private fun fuzzy(targetNorm: String, catalog: Catalog): List<MatchCandidate> {
        val results = mutableListOf<MatchCandidate>()
        for (variant in catalog.variants) {
            val dist = Levenshtein.distance(targetNorm, variant.nameNormalized)
            val threshold = if (targetNorm.length <= 15) 2 else 3
            if (dist <= threshold) {
                results += MatchCandidate(variant, dist, "lev:$dist")
            }
        }
        return results.sortedWith(compareBy<MatchCandidate> { it.score }.thenBy { it.variant.priceInCents })
    }
}
