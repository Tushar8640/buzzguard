package com.buzzguard.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buzzguard.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Restrained, fast, tactile splash screen matching the adaptive launcher icon.
 * No glowing rings, no marketing badges — settles cleanly in under 1 second.
 */
@Composable
fun BuzzGuardSplashScreen(
    isDark: Boolean,
    onFinished: () -> Unit,
) {
    val scale = remember { Animatable(0.82f) }
    val tilt = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        launch {
            delay(120)
            tilt.animateTo(-9f, tween(90, easing = FastOutSlowInEasing))
            tilt.animateTo(7f, tween(100, easing = FastOutSlowInEasing))
            tilt.animateTo(0f, tween(120, easing = FastOutSlowInEasing))
        }
        launch {
            delay(160)
            textAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        delay(980)
        onFinished()
    }

    val bg = if (isDark) Color(0xFF121210) else Color(0xFFF6F5F1)
    val ink = if (isDark) Color(0xFFF1F0EA) else Color(0xFF181816)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(scale.value)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1B5E48)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "BuzzGuard",
                    modifier = Modifier
                        .size(84.dp)
                        .rotate(tilt.value),
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "BuzzGuard",
                color = ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                modifier = Modifier.alpha(textAlpha.value),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Notification rules",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(textAlpha.value),
            )
        }
    }
}
