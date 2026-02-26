package database

import model.SavedImport

/**
 * Extension functions to map between database entities and domain models.
 */

// SavedImportEntity <-> SavedImport
fun SavedImportEntity.toDomain(): SavedImport {
    return SavedImport(
        id = this.id,
        name = this.name,
        deckText = this.deckText,
        timestamp = this.timestamp,
        cardCount = this.cardCount.toInt(),
        includeSideboard = this.includeSideboard != 0L,
        includeCommanders = this.includeCommanders != 0L,
        includeTokens = this.includeTokens != 0L
    )
}

// CardVariantEntity <-> CardVariant
fun CardVariantEntity.toDomain(): model.CardVariant {
    return model.CardVariant(
        nameOriginal = this.nameOriginal,
        nameNormalized = this.nameNormalized,
        setCode = this.setCode,
        sku = this.sku,
        variantType = this.variantType,
        priceInCents = this.priceInCents.toInt(),
        collectorNumber = this.collectorNumber,
        imageUrl = this.imageUrl
    )
}

// PreferencesEntity <-> Preferences
fun PreferencesEntity.toDomain(): model.Preferences {
    return model.Preferences(
        includeSideboard = this.includeSideboard != 0L,
        includeCommanders = this.includeCommanders != 0L,
        includeTokens = this.includeTokens != 0L,
        variantPriority = this.variantPriority.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        setPriority = this.setPriority.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        fuzzyEnabled = this.fuzzyEnabled != 0L,
        cacheMaxAgeHours = this.cacheMaxAgeHours.toInt()
    )
}

// LogEntryEntity <-> LogEntry
fun LogEntryEntity.toDomain(): model.LogEntry {
    return model.LogEntry(
        level = this.level,
        message = this.message,
        timestamp = this.timestamp
    )
}


