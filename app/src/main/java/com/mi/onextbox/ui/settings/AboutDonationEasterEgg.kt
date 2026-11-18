package com.mi.onextbox.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.common.CouiConfirmDialog
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun AboutDonationEntry(onOpenDonation: () -> Unit) {
    var show by rememberSaveable { mutableStateOf(false) }
    SettingsCardRow(
        title = stringResource(R.string.about_donate_title),
        summary = "",
        showArrow = true,
        hasDividerAbove = true,
        hasDividerBelow = true,
        onClick = { show = true },
        leadingContent = {
            Icon(AppIcons.Heart, contentDescription = null, tint = COUITheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp))
        },
    )
    DonationGreetingDialog(show, onDismiss = { show = false }, onDonate = onOpenDonation)
}

@Composable
internal fun DonationGreetingDialog(show: Boolean, onDismiss: () -> Unit, onDonate: () -> Unit) {
    var openDonationAfterDismiss by remember { mutableStateOf(false) }
    CouiConfirmDialog(
        show = show,
        title = stringResource(R.string.about_donation_greeting_title),
        summary = stringResource(R.string.about_donation_greeting_body),
        negativeText = stringResource(R.string.about_donation_continue),
        positiveText = stringResource(R.string.about_donation_keep_money),
        onDismissRequest = onDismiss,
        onNegative = { openDonationAfterDismiss = true; onDismiss() },
        onPositive = onDismiss,
        onDismissFinished = {
            if (openDonationAfterDismiss) {
                openDonationAfterDismiss = false
                onDonate()
            }
        },
        content = { GreetingFireworks(show) },
    )
}

private data class GreetingSpark(val x: Float, val y: Float, val weight: Float)
private data class GreetingBurst(val x: Float, val y: Float, val start: Float, val color: Color, val sparks: List<GreetingSpark>)
private const val FireworksDurationMillis = 6000

private fun greetingBursts(): List<GreetingBurst> {
    val random = Random(0x4F4E42)
    val colors = listOf(Color(0xFFFFC16E), Color(0xFFBBA6FF), Color(0xFF7DE8D3),
        Color(0xFFFF9DC4), Color(0xFF8DCFFF), Color(0xFFFFD987))
    val locations = listOf(.24f to .43f, .74f to .36f, .48f to .25f,
        .81f to .57f, .25f to .59f, .58f to .43f)
    val starts = listOf(0f, 300f, 650f, 1600f, 2500f, 3400f)
    return locations.mapIndexed { index, location ->
        GreetingBurst(location.first, location.second, starts[index], colors[index], List(36) { spark ->
            val angle = 2.0 * PI * spark / 36 + random.nextDouble(-.045, .045)
            val radius = random.nextDouble(.55, 1.0).toFloat()
            GreetingSpark(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius, random.nextFloat())
        })
    }
}

@Composable
internal fun GreetingFireworks(visible: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val bursts = remember { greetingBursts() }
    val sky = remember { Brush.verticalGradient(listOf(Color(0xFF121A35), Color(0xFF20203C))) }
    val description = stringResource(R.string.about_donation_fireworks)
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
            progress.snapTo(.35f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(FireworksDurationMillis, easing = LinearEasing))
        }
    }
    Canvas(modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)).background(sky)
        .testTag("donationFireworks").semantics { contentDescription = description }) {
        val elapsed = progress.value * FireworksDurationMillis
        val radius = minOf(size.width * .23f, size.height * .38f)
        val stroke = 1.1.dp.toPx()
        repeat(26) { index ->
            val x = ((index * 47 + 13) % 101) / 101f * size.width
            val y = ((index * 29 + 7) % 97) / 97f * size.height
            drawCircle(Color(0xFFCFDAFF).copy(alpha = if (index % 4 == 0) .65f else .3f),
                if (index % 4 == 0) .9.dp.toPx() else .55.dp.toPx(), Offset(x, y))
        }
        bursts.forEach { burst ->
            val age = elapsed - burst.start
            val origin = Offset(burst.x * size.width, burst.y * size.height)
            if (age in 0f..600f) {
                val t = age / 600f
                val travel = 1f - (1f - t).pow(2)
                val head = Offset(origin.x, size.height * 1.05f + (origin.y - size.height * 1.05f) * travel)
                val tail = head + Offset(0f, 15.dp.toPx() * (1f - t))
                drawLine(burst.color.copy(alpha = .75f), tail, head, stroke, StrokeCap.Round)
                drawCircle(Color.White, 1.4.dp.toPx(), head)
            } else if (age > 600f && age < 2200f) {
                val t = (age - 600f) / 1600f
                val spread = 1f - exp(-3.8f * t)
                val alpha = (1f - t).pow(.9f)
                val tailT = (t - .1f).coerceAtLeast(0f)
                val tailSpread = 1f - exp(-3.8f * tailT)
                burst.sparks.forEach { spark ->
                    val center = origin + Offset(spark.x * radius * spread,
                        spark.y * radius * spread + t * t * size.height * .14f)
                    val tail = origin + Offset(spark.x * radius * tailSpread,
                        spark.y * radius * tailSpread + tailT * tailT * size.height * .14f)
                    drawLine(burst.color.copy(alpha = alpha * .75f), tail, center, stroke, StrokeCap.Round)
                    drawCircle(burst.color.copy(alpha = alpha * .4f), 1.6.dp.toPx(), center)
                    drawCircle(Color.White.copy(alpha = alpha), (.55f + spark.weight * .35f).dp.toPx(), center)
                }
            }
        }
    }
}
