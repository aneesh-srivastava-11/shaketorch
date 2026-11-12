package com.shaketorch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shaketorch.ui.theme.DarkSurfaceVariant
import com.shaketorch.ui.theme.TextPrimary
import com.shaketorch.ui.theme.TextSecondary
import com.shaketorch.ui.theme.TorchAmber
import com.shaketorch.ui.theme.TorchGlow
import com.shaketorch.ui.theme.TorchGlowOuter
import com.shaketorch.ui.theme.TorchGold
import com.shaketorch.ui.theme.TorchMuted
import com.shaketorch.ui.theme.TorchMutedIcon

/**
 * Visual centerpiece representing the hardware flashlight.
 * Features smooth color transitions, animated pulsing glow halos, and status typography.
 */
@Composable
fun FlashlightIndicator(
    isFlashlightOn: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation when flashlight is ON
    val infiniteTransition = rememberInfiniteTransition(label = "TorchGlowPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )

    val targetOuterScale = if (isFlashlightOn) pulseScale else 1.0f
    val outerScale by animateFloatAsState(
        targetValue = targetOuterScale,
        animationSpec = tween(400),
        label = "OuterGlowScaleTransition"
    )

    val innerBgColor by animateColorAsState(
        targetValue = if (isFlashlightOn) TorchGold else TorchMuted,
        animationSpec = tween(350),
        label = "InnerBgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isFlashlightOn) Color(0xFF1E1700) else TorchMutedIcon,
        animationSpec = tween(350),
        label = "IconColor"
    )

    val statusText = if (isFlashlightOn) "Flashlight is ON" else "Flashlight is OFF"
    val statusColor by animateColorAsState(
        targetValue = if (isFlashlightOn) TorchGold else TextSecondary,
        animationSpec = tween(300),
        label = "StatusTextColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outermost glowing halo (only when ON)
            if (isFlashlightOn) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(outerScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(TorchGlow, TorchGlowOuter, Color.Transparent)
                            )
                        )
                )
            }

            // Middle decorative ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(if (isFlashlightOn) Color(0x33FFD54F) else DarkSurfaceVariant)
                    .border(
                        width = 2.dp,
                        color = if (isFlashlightOn) TorchAmber.copy(alpha = 0.5f) else Color(0xFF2C2C35),
                        shape = CircleShape
                    )
            )

            // Inner primary circle
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(innerBgColor)
                    .border(
                        width = 3.dp,
                        color = if (isFlashlightOn) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = statusText,
                    tint = iconColor,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // State label
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            ),
            color = statusColor
        )
    }
}

