package model

enum class ProFeature {
    MULTI_SELLER,
    SHOPPING_OPTIMIZER,
    SELLER_OVERRIDE,
    IMPORT_HISTORY,
    THEME_CUSTOMIZATION,
}

sealed class ProStatus {
    object Free : ProStatus()
    object Pro : ProStatus()
    object Loading : ProStatus()

    val isPro: Boolean get() = this is Pro
}

enum class PurchaseResult {
    SUCCESS,
    CANCELLED,
    ERROR,
}
