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
        val fuzzyEnabled: Boolean,
        val proxyFirst: Boolean = true,
    )

    /** Secret Lair and promotional alternate names mapped to their canonical card names. */
    private val ALTERNATE_NAMES = mapOf(
        "Calliope's Song" to "Seething Song",
        "Earth's Mightiest Emblem" to "Arcane Signet",
        "Fangorn Forest" to "Yavimaya, Cradle of Growth",
        "Hope's Aero Magic" to "Cyclonic Rift",
        "La abundancia de Yucahú" to "Sylvan Library",
        "La abundancia de Yucahu" to "Sylvan Library",
        "Power Sneakers" to "Lightning Greaves",
        "Storm's Will" to "Jeska's Will",
    )

    /** Pre-normalized version of [ALTERNATE_NAMES] for case/accent-insensitive lookup. */
    private val ALTERNATE_NAMES_NORMALIZED: Map<String, String> =
        ALTERNATE_NAMES.entries.associate { (k, v) ->
            NameNormalizer.normalize(k) to v
        }

    /**
     * Returns the canonical card name for an alternate name, or null if not an alternate.
     * Used by DeckSearchUseCase to include canonical names when filtering catalog results.
     */
    fun resolveAlternateName(cardName: String): String? {
        return ALTERNATE_NAMES[cardName]
            ?: ALTERNATE_NAMES_NORMALIZED[NameNormalizer.normalize(cardName)]
    }

    /**
     * Pairs of normalized names that are within Levenshtein distance but should
     * never match each other.
     */
    private val FUZZY_BLOCKLIST: Map<String, Set<String>> = mapOf(
        "electrodominance" to setOf("necrodominance"),
        "necrodominance" to setOf("electrodominance"),
    )

    fun matchAll(entries: List<DeckEntry>, catalog: Catalog, config: MatchConfig): List<DeckEntryMatch> {
        val initial = entries.map { matchEntry(it, catalog, config) }
        if (!config.fuzzyEnabled) return initial

        // Recheck pass: retry NOT_FOUND entries with a relaxed Levenshtein threshold
        return initial.map { match ->
            if (match.status == MatchStatus.NOT_FOUND) {
                recheckEntry(match, catalog)
            } else {
                match
            }
        }
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
            if (fuzzyCandidates.isNotEmpty()) {
                return DeckEntryMatch(entry, MatchStatus.AMBIGUOUS, null, fuzzyCandidates)
            }
        }

        // Pass 5 – Alternate name retry: if the card name has a known alternate,
        // re-run matching with the canonical name
        val alternateName = ALTERNATE_NAMES[entry.cardName]
            ?: ALTERNATE_NAMES_NORMALIZED[normalized]
        if (alternateName != null) {
            val aliasEntry = entry.copy(cardName = alternateName)
            val aliasResult = matchEntry(aliasEntry, catalog, config)
            if (aliasResult.status != MatchStatus.NOT_FOUND) {
                return aliasResult.copy(
                    deckEntry = entry,
                    notes = "Matched via alternate name: ${entry.cardName} -> $alternateName"
                )
            }
        }

        return DeckEntryMatch(entry, MatchStatus.NOT_FOUND)
    }

    private fun selectByPriority(list: List<CardVariant>, config: MatchConfig, entry: DeckEntry): CardVariant? {
        // If set code hint was provided, keep only that set
        val filtered = entry.setCodeHint?.let { code -> list.filter { it.setCode.equals(code, true) } }.orEmpty().ifEmpty { list }
        return filtered.sortedWith(
            MatchSorting.variantComparator(
                proxyFirst = config.proxyFirst,
                variantPriority = config.variantPriority,
                setPriority = config.setPriority,
            )
        ).firstOrNull()
    }

    private fun fuzzy(targetNorm: String, catalog: Catalog): List<MatchCandidate> {
        val blocked = FUZZY_BLOCKLIST[targetNorm].orEmpty()
        val threshold = MatchSorting.fuzzyThreshold(targetNorm.length)
        val results = mutableListOf<MatchCandidate>()
        for (variant in catalog.variants) {
            if (variant.nameNormalized in blocked) continue
            val dist = Levenshtein.distance(targetNorm, variant.nameNormalized)
            if (dist <= threshold) {
                results += MatchCandidate(variant, dist, "lev:$dist")
            }
        }
        return results.sortedWith(compareBy<MatchCandidate> { it.score }.thenBy { it.variant.priceInCents })
    }

    /**
     * Re-attempt matching for a NOT_FOUND entry using a relaxed Levenshtein threshold.
     * The relaxed threshold is the standard threshold + 2, giving more room for typos.
     */
    private fun recheckEntry(match: DeckEntryMatch, catalog: Catalog): DeckEntryMatch {
        val entry = match.deckEntry
        val normalized = NameNormalizer.normalize(entry.cardName)
        val fuzzyPool = if (entry.setCodeHint != null) {
            catalog.variants.filter { it.setCode.equals(entry.setCodeHint, true) }
        } else catalog.variants
        val candidates = fuzzyRecheck(normalized, Catalog(fuzzyPool))
        return if (candidates.isEmpty()) {
            match
        } else {
            DeckEntryMatch(entry, MatchStatus.FUZZY_RECHECK, null, candidates)
        }
    }

    /**
     * Fuzzy match with a relaxed threshold (standard + 2) for the recheck pass.
     * Only returns candidates that would NOT have been found by the standard fuzzy pass.
     */
    private fun fuzzyRecheck(targetNorm: String, catalog: Catalog): List<MatchCandidate> {
        val blocked = FUZZY_BLOCKLIST[targetNorm].orEmpty()
        val standardThreshold = MatchSorting.fuzzyThreshold(targetNorm.length)
        val relaxedThreshold = MatchSorting.fuzzyThreshold(targetNorm.length, relaxed = true)
        val results = mutableListOf<MatchCandidate>()
        for (variant in catalog.variants) {
            if (variant.nameNormalized in blocked) continue
            val dist = Levenshtein.distance(targetNorm, variant.nameNormalized)
            if (dist in (standardThreshold + 1)..relaxedThreshold) {
                results += MatchCandidate(variant, dist, "recheck:$dist")
            }
        }
        return results.sortedWith(compareBy<MatchCandidate> { it.score }.thenBy { it.variant.priceInCents })
    }
}
