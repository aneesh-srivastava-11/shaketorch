package com.shaketorch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shaketorch.flashlight.FlashlightStatus
import com.shaketorch.ui.components.FlashlightIndicator
import com.shaketorch.ui.components.ShakeStatusBadge
import com.shaketorch.ui.theme.DarkBackground
import com.shaketorch.ui.theme.DarkSurface
import com.shaketorch.ui.theme.DarkSurfaceVariant
import com.shaketorch.ui.theme.StatusRed
import com.shaketorch.ui.theme.TextPrimary
import com.shaketorch.ui.theme.TextSecondary
import com.shaketorch.ui.theme.TextTertiary
import com.shaketorch.ui.theme.TorchGold
import com.shaketorch.ui.theme.TorchMuted
import com.shaketorch.viewmodel.MainViewModel

/**
 * Main application screen featuring real-time flashlight status, glowing indicator,
 * shake detection status, instruction banner, and optional manual toggle backup.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val hasFlashHardware by viewModel.hasFlashHardware.collectAsState()
    val flashlightStatus by viewModel.flashlightStatus.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val userFeedbackMessage by viewModel.userFeedbackMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userFeedbackMessage) {
        userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserFeedbackMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                            .border(1.dp, Color(0xFF33333F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashlightOn,
                            contentDescription = null,
                            tint = TorchGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ShakeTorch",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Hardware warning if accelerometer or flash is missing
            if (!viewModel.hasAccelerometer) {
                HardwareWarningCard(
                    message = "Shake detection is unavailable on this device (Accelerometer not detected)."
                )
            } else if (!hasFlashHardware) {
                HardwareWarningCard(
                    message = "This device does not have camera flash hardware."
                )
            } else if (flashlightStatus is FlashlightStatus.Error) {
                HardwareWarningCard(
                    message = (flashlightStatus as FlashlightStatus.Error).message
                )
            } else {
                // Shake detection status badge
                ShakeStatusBadge(
                    isShakeEnabled = userPreferences.isShakeEnabled,
                    sensitivity = userPreferences.sensitivity,
                    isBackgroundActive = userPreferences.isBackgroundServiceEnabled,
                    shakeDetectedPulse = viewModel.shakeDetectedPulse
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center piece: Large Glowing Flashlight Indicator
            FlashlightIndicator(
                isFlashlightOn = isFlashlightOn
            )

            // Primary instruction text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Shake your phone to toggle the flashlight.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = TextPrimary
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ONE SHAKE alternates between ON and OFF.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextTertiary,
                        fontSize = 13.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Optional Manual Control (Backup)
            val manualButtonColor by animateColorAsState(
                targetValue = if (isFlashlightOn) TorchMuted else TorchGold,
                animationSpec = tween(300),
                label = "ManualButtonColor"
            )
            val manualTextColor = if (isFlashlightOn) TextPrimary else Color(0xFF1E1700)

            Button(
                onClick = { viewModel.toggleFlashlightManual() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = manualButtonColor
                ),
                enabled = hasFlashHardware
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.PowerSettingsNew else Icons.Default.FlashlightOn,
                    contentDescription = null,
                    tint = manualTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isFlashlightOn) "Manual Turn OFF" else "Manual Turn ON",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = manualTextColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun HardwareWarningCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1E1E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = StatusRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFFFCDD2),
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

