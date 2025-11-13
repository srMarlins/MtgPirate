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

fun SavedImport.toEntity(): SavedImportEntity {
    return SavedImportEntity(
        id = this.id,
        name = this.name,
        deckText = this.deckText,
        timestamp = this.timestamp,
        cardCount = this.cardCount.toLong(),
        includeSideboard = if (this.includeSideboard) 1L else 0L,
        includeCommanders = if (this.includeCommanders) 1L else 0L,
        includeTokens = if (this.includeTokens) 1L else 0L
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

fun model.CardVariant.toEntity(id: Long = 0): CardVariantEntity {
    return CardVariantEntity(
        id = id,
        nameOriginal = this.nameOriginal,
        nameNormalized = this.nameNormalized,
        setCode = this.setCode,
        sku = this.sku,
        variantType = this.variantType,
        priceInCents = this.priceInCents.toLong(),
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

fun model.Preferences.toEntity(id: Long = 0): PreferencesEntity {
    return PreferencesEntity(
        id = id,
        includeSideboard = if (this.includeSideboard) 1L else 0L,
        includeCommanders = if (this.includeCommanders) 1L else 0L,
        includeTokens = if (this.includeTokens) 1L else 0L,
        variantPriority = this.variantPriority.joinToString(","),
        setPriority = this.setPriority.joinToString(","),
        fuzzyEnabled = if (this.fuzzyEnabled) 1L else 0L,
        cacheMaxAgeHours = this.cacheMaxAgeHours.toLong()
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

fun model.LogEntry.toEntity(id: Long = 0): LogEntryEntity {
    return LogEntryEntity(
        id = id,
        level = this.level,
        message = this.message,
        timestamp = this.timestamp
    )
}

