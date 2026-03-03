package ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.ProFeature
import model.ProStatus

/** Small lock icon badge for Pro-gated controls. */
@Composable
fun ProBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    PixelBadge(
        text = "\u2726 PRO",
        color = PixelGold,
        textColorOverride = ProBadgeText,
        modifier = mod,
    )
}

/**
 * Compact upgrade chip for top bars. Gold-bordered pill with sparkle + "PRO"
 * and a subtle pulsing glow to draw attention without being obnoxious.
 */
@Composable
fun UpgradeChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Box(
        modifier = modifier
            .clip(PixelShape(cornerSize = 6.dp))
            .background(PixelGold.copy(alpha = 0.15f), shape = PixelShape(cornerSize = 6.dp))
            .pixelBorder(
                borderWidth = 2.dp,
                cornerSize = 6.dp,
                enabled = true,
                glowAlpha = glowAlpha,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "UPGRADE \u25B8",
            style = MaterialTheme.typography.caption,
            color = PixelGold,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
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
