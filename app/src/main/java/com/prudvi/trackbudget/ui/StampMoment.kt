package com.prudvi.trackbudget.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.prudvi.trackbudget.model.AppAmplitude
import com.prudvi.trackbudget.model.EarnedStamp
import com.prudvi.trackbudget.model.StampDefinition
import com.prudvi.trackbudget.model.StampEngine
import kotlinx.coroutines.delay

@Composable
fun StampMoment(
    earnedStamp: EarnedStamp,
    amplitude: AppAmplitude,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val definition = StampEngine.definitions.firstOrNull { it.id == earnedStamp.id }
    if (definition == null) {
        LaunchedEffect(earnedStamp.id) { onClose() }
        return
    }
    StampMoment(definition, amplitude, onClose, modifier)
}

@Composable
fun StampMoment(
    stamp: StampDefinition,
    amplitude: AppAmplitude,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = rememberMotionEnabled()
    val view = LocalView.current
    val scale = remember(stamp.id) { Animatable(if (motionEnabled) 1.6f else 1f) }

    LaunchedEffect(stamp.id, amplitude, motionEnabled) {
        if (amplitude == AppAmplitude.LOUD) {
            if (motionEnabled) {
                scale.snapTo(1.6f)
                scale.animateTo(0.92f, tween(ReceiptsMotion.SHEET))
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
                scale.animateTo(1f, tween(ReceiptsMotion.STAMP - ReceiptsMotion.SHEET))
                delay(2_000)
            } else {
                scale.snapTo(1f)
            }
        }
        onClose()
    }

    if (amplitude == AppAmplitude.LOUD) {
        Box(
            modifier.fillMaxSize()
                .background(receiptsColors.stampVeil)
                .semantics { contentDescription = "Stamp earned: ${stamp.title}. ${stamp.description}" },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(ReceiptsSpace.screen).widthIn(max = ReceiptsSpace.x16 * 5f).scale(scale.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x4),
            ) {
                ReceiptLabel("Stamp earned", color = receiptsColors.chilli)
                StampFace(stamp, earned = true, modifier = Modifier.fillMaxWidth(), large = true)
            }
        }
    }
}
