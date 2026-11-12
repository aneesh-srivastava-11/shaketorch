package com.shaketorch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shaketorch.data.SensitivityLevel
import com.shaketorch.ui.theme.DarkSurfaceVariant
import com.shaketorch.ui.theme.StatusGreen
import com.shaketorch.ui.theme.StatusRed
import com.shaketorch.ui.theme.TextPrimary
import com.shaketorch.ui.theme.TextSecondary
import com.shaketorch.ui.theme.TorchGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Status indicator for accelerometer shake detection state and live shake trigger feedback.
 */
@Composable
fun ShakeStatusBadge(
    isShakeEnabled: Boolean,
    sensitivity: SensitivityLevel,
    isBackgroundActive: Boolean,
    shakeDetectedPulse: SharedFlow<Unit>,
    modifier: Modifier = Modifier
) {
    var isPulsing by remember { mutableStateOf(false) }

    LaunchedEffect(shakeDetectedPulse) {
        shakeDetectedPulse.collectLatest {
            isPulsing = true
            delay(650)
            isPulsing = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isPulsing) {
            // Flash banner when a shake is actively detected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(TorchGold)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF1E1700),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Shake Triggered!",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1700)
                    )
                )
            }
        } else {
            // Default calm status pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, Color(0xFF33333F), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isShakeEnabled) StatusGreen else StatusRed)
                )

                Spacer(modifier = Modifier.width(8.dp))

                val statusDescription = buildString {
                    if (isShakeEnabled) {
                        append("Shake Active")
                        append(" • ")
                        append(sensitivity.label)
                        if (isBackgroundActive) {
                            append(" (Background)")
                        }
                    } else {
                        append("Shake Disabled")
                    }
                }

                Text(
                    text = statusDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                )
            }
        }
    }
}

