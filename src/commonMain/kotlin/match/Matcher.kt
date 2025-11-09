package match

import model.*

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
        val candidates = catalog.variants.filter { it.nameOriginal == entry.cardName }
        if (candidates.size == 1) {
            return DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, candidates.first())
        }
        if (candidates.size > 1) {
            val selected = selectByPriority(candidates, config)
            return if (selected != null) {
                DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, selected, candidates.map { MatchCandidate(it, 0, "exact") })
            } else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, candidates.map { MatchCandidate(it, 0, "exact") })
        }
        // Case-insensitive
        val ci = catalog.variants.filter { it.nameOriginal.equals(entry.cardName, true) }
        if (ci.size == 1) return DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, ci.first())
        if (ci.size > 1) {
            val selected = selectByPriority(ci, config)
            return if (selected != null) DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, selected, ci.map { MatchCandidate(it, 0, "ci") })
            else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, ci.map { MatchCandidate(it, 0, "ci") })
        }
        // Normalized
        val norm = catalog.indexByName[normalized].orEmpty()
        if (norm.size == 1) return DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, norm.first())
        if (norm.size > 1) {
            val selected = selectByPriority(norm, config)
            return if (selected != null) DeckEntryMatch(entry, MatchStatus.AUTO_MATCHED, selected, norm.map { MatchCandidate(it, 0, "normalized") })
            else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, norm.map { MatchCandidate(it, 0, "normalized") })
        }
        if (config.fuzzyEnabled) {
            val fuzzyCandidates = fuzzy(normalized, catalog)
            return if (fuzzyCandidates.isEmpty()) DeckEntryMatch(entry, MatchStatus.NOT_FOUND)
            else DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, fuzzyCandidates)
        }
        return DeckEntryMatch(entry, MatchStatus.NOT_FOUND)
    }

    private fun selectByPriority(list: List<CardVariant>, config: MatchConfig): CardVariant? {
        // Set priority first, then variant priority
        val bySet = if (config.setPriority.isNotEmpty()) {
            list.sortedBy { idxOrEnd(config.setPriority, it.setCode) }
        } else list
        val byVariant = bySet.sortedBy { idxOrEnd(config.variantPriority, it.variantType) }
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

