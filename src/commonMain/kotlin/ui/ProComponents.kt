package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.ProFeature
import model.ProStatus

/** Gold color for Pro branding elements. */
val PixelGold = Color(0xFFFBBF24)

/** Small lock icon badge for Pro-gated controls. */
@Composable
fun ProBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    PixelBadge(
        text = "\uD83D\uDD12 PRO",
        color = PixelGold,
        modifier = mod,
    )
}

/** Returns a user-facing title for a Pro feature. */
private fun featureTitle(feature: ProFeature): String = when (feature) {
    ProFeature.MULTI_SELLER -> "Multi-Seller Search"
    ProFeature.SHOPPING_OPTIMIZER -> "Shopping Optimizer"
    ProFeature.SELLER_OVERRIDE -> "Seller Override"
    ProFeature.IMPORT_HISTORY -> "Import History"
    ProFeature.THEME_CUSTOMIZATION -> "Theme Customization"
}

/** Returns a user-facing description for a Pro feature. */
private fun featureDescription(feature: ProFeature): String = when (feature) {
    ProFeature.MULTI_SELLER -> "Search across multiple sellers at once to find the best prices."
    ProFeature.SHOPPING_OPTIMIZER -> "Optimize your shopping cart across sellers to minimize total cost."
    ProFeature.SELLER_OVERRIDE -> "Override per-card seller assignments for full control."
    ProFeature.IMPORT_HISTORY -> "Save and reload previous imports so you never lose a list."
    ProFeature.THEME_CUSTOMIZATION -> "Switch between light and dark themes."
}

/**
 * Full-screen upgrade dialog shown when a free user taps a Pro-gated feature.
 * Uses [PixelCard] with a glowing border for visual emphasis.
 */
@Composable
fun UpgradeDialog(
    feature: ProFeature,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background.copy(alpha = 0.9f))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(
            glowing = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}), // block click-through
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Text(
                    text = "\u25B8 UNLOCK PRO",
                    style = MaterialTheme.typography.h5,
                    color = PixelGold,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(4.dp))

                // Feature title
                Text(
                    text = featureTitle(feature),
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                // Feature description
                Text(
                    text = featureDescription(feature),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // Purchase button
                PixelButton(
                    text = "Unlock Pro \u2014 \$4.99",
                    onClick = onPurchase,
                    variant = PixelButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Restore button
                PixelButton(
                    text = "Restore Purchase",
                    onClick = onRestore,
                    variant = PixelButtonVariant.SURFACE,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Caption
                Text(
                    text = "One-time purchase. No subscription.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Wraps [content] with a semi-transparent overlay and a [ProBadge] when the user
 * is not on Pro. Tapping the overlay triggers [onUpgradeClick].
 */
@Composable
fun ProGate(
    proStatus: ProStatus,
    feature: ProFeature,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()

        if (!proStatus.isPro) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(PixelShape(cornerSize = 9.dp))
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.6f))
                    .clickable(onClick = onUpgradeClick),
                contentAlignment = Alignment.Center,
            ) {
                ProBadge(onClick = onUpgradeClick)
            }
        }
    }
}
