package com.digitalhuman.pipecat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * A Compose Canvas-based 2D anime avatar with audio-driven lip sync.
 *
 * Draws an anime-style face with animated eyes (blinking), mouth (lip sync),
 * and idle animations (breathing / slight head bob). No external Live2D SDK
 * dependency required -- everything is rendered with Canvas primitives.
 *
 * @param isSpeaking     Whether the bot is currently speaking.
 * @param audioAmplitude Audio loudness in [0.0, 1.0]. Drives mouth openness.
 * @param modifier       Standard Compose modifier.
 */
@Composable
fun Live2DAvatarView(
    isSpeaking: Boolean,
    audioAmplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    // ---------- Blink state ----------
    var blinkProgress by remember { mutableFloatStateOf(0f) } // 0 = open, 1 = closed
    var isBlinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            // Random delay between blinks (3-5 seconds)
            val delayMs = Random.nextLong(3000L, 5000L)
            delay(delayMs)
            // Blink close
            isBlinking = true
            for (progress in 0..10) {
                blinkProgress = progress / 10f
                delay(8)
            }
            // Blink open
            for (progress in 10 downTo 0) {
                blinkProgress = progress / 10f
                delay(8)
            }
            isBlinking = false
        }
    }

    // ---------- Idle animation time ----------
    var idleTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanoTime ->
                idleTime = (nanoTime / 1_000_000_000f)
            }
        }
    }

    // ---------- Mouth openness ----------
    val targetMouthOpenness = if (isSpeaking) audioAmplitude else 0f
    val animatedMouthOpenness by animateFloatAsState(
        targetValue = targetMouthOpenness,
        animationSpec = tween(durationMillis = 60),
        label = "mouthOpenness"
    )

    // ---------- Eyebrow raise when speaking ----------
    val eyebrowRaise by animateFloatAsState(
        targetValue = if (isSpeaking) 6f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "eyebrowRaise"
    )

    // ---------- Breathing / head bob ----------
    val breathingOffset = sin(idleTime * 1.8f) * 2f // subtle vertical bob

    // ---------- Colors ----------
    val skinColor = Color(0xFFFFE4C4)
    val skinShadow = Color(0xFFE8C9A0)
    val hairColor = Color(0xFF2C1810)
    val hairHighlight = Color(0xFF5C4033)
    val irisColor = Color(0xFF6B8FBF)
    val irisDark = Color(0xFF3A5F8A)
    val blushColor = Color(0xFFFFB6C1)
    val mouthInner = Color(0xFF8B3A3A)
    val eyebrowColor = Color(0xFF3A2518)

    Canvas(
        modifier = modifier.size(300.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val faceRadius = size.minDimension * 0.36f
        val faceCenterY = center.y + breathingOffset

        // ---- Hair (back layer) ----
        drawHairBack(center.x, faceCenterY, faceRadius, hairColor)

        // ---- Face ----
        drawFace(center.x, faceCenterY, faceRadius, skinColor, skinShadow)

        // ---- Hair bangs (front layer) ----
        drawHairBangs(center.x, faceCenterY, faceRadius, hairColor, hairHighlight)

        // ---- Side hair ----
        drawSideHair(center.x, faceCenterY, faceRadius, hairColor)

        // ---- Eyebrows ----
        drawEyebrows(center.x, faceCenterY, faceRadius, eyebrowColor, eyebrowRaise)

        // ---- Eyes ----
        drawEyes(
            centerX = center.x,
            centerY = faceCenterY,
            faceRadius = faceRadius,
            blinkProgress = blinkProgress,
            irisColor = irisColor,
            irisDark = irisDark
        )

        // ---- Nose ----
        drawNose(center.x, faceCenterY, faceRadius, skinShadow)

        // ---- Blush ----
        drawBlush(center.x, faceCenterY, faceRadius, blushColor)

        // ---- Mouth ----
        drawMouth(
            centerX = center.x,
            centerY = faceCenterY,
            faceRadius = faceRadius,
            openness = animatedMouthOpenness,
            innerColor = mouthInner,
            skinColor = skinColor
        )
    }
}

// ============================================================================
// Drawing helpers
// ============================================================================

private fun DrawScope.drawFace(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    skinColor: Color,
    skinShadow: Color
) {
    // Face oval with slight gradient for depth
    val gradient = Brush.radialGradient(
        colors = listOf(skinColor, skinShadow),
        center = Offset(centerX, centerY - faceRadius * 0.1f),
        radius = faceRadius
    )
    drawCircle(
        brush = gradient,
        radius = faceRadius,
        center = Offset(centerX, centerY)
    )

    // Slight chin emphasis -- draw a subtle oval slightly below center
    val chinPath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                centerX - faceRadius * 0.65f,
                centerY + faceRadius * 0.15f,
                centerX + faceRadius * 0.65f,
                centerY + faceRadius * 0.95f
            )
        )
    }
    drawPath(
        path = chinPath,
        color = skinColor,
        style = Fill
    )
}

private fun DrawScope.drawHairBack(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    hairColor: Color
) {
    // Large hair mass behind the face
    val hairPath = Path().apply {
        // Top dome
        addOval(
            androidx.compose.ui.geometry.Rect(
                centerX - faceRadius * 1.15f,
                centerY - faceRadius * 1.35f,
                centerX + faceRadius * 1.15f,
                centerY + faceRadius * 0.3f
            )
        )
    }
    drawPath(
        path = hairPath,
        color = hairColor,
        style = Fill
    )

    // Side extensions going down
    val leftSide = Path().apply {
        moveTo(centerX - faceRadius * 1.1f, centerY - faceRadius * 0.2f)
        quadraticBezierTo(
            centerX - faceRadius * 1.2f, centerY + faceRadius * 0.6f,
            centerX - faceRadius * 0.85f, centerY + faceRadius * 1.3f
        )
        lineTo(centerX - faceRadius * 0.7f, centerY + faceRadius * 1.3f)
        quadraticBezierTo(
            centerX - faceRadius * 0.85f, centerY + faceRadius * 0.3f,
            centerX - faceRadius * 0.8f, centerY - faceRadius * 0.1f
        )
        close()
    }
    drawPath(leftSide, color = hairColor, style = Fill)

    val rightSide = Path().apply {
        moveTo(centerX + faceRadius * 1.1f, centerY - faceRadius * 0.2f)
        quadraticBezierTo(
            centerX + faceRadius * 1.2f, centerY + faceRadius * 0.6f,
            centerX + faceRadius * 0.85f, centerY + faceRadius * 1.3f
        )
        lineTo(centerX + faceRadius * 0.7f, centerY + faceRadius * 1.3f)
        quadraticBezierTo(
            centerX + faceRadius * 0.85f, centerY + faceRadius * 0.3f,
            centerX + faceRadius * 0.8f, centerY - faceRadius * 0.1f
        )
        close()
    }
    drawPath(rightSide, color = hairColor, style = Fill)
}

private fun DrawScope.drawHairBangs(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    hairColor: Color,
    highlightColor: Color
) {
    // Bangs -- several overlapping curves across the forehead
    val bangY = centerY - faceRadius * 0.85f

    // Central bang strand
    val bang1 = Path().apply {
        moveTo(centerX - faceRadius * 0.8f, bangY - faceRadius * 0.2f)
        quadraticBezierTo(
            centerX - faceRadius * 0.1f, bangY + faceRadius * 0.5f,
            centerX, bangY + faceRadius * 0.35f
        )
        quadraticBezierTo(
            centerX + faceRadius * 0.1f, bangY + faceRadius * 0.5f,
            centerX + faceRadius * 0.8f, bangY - faceRadius * 0.2f
        )
        lineTo(centerX + faceRadius * 0.9f, bangY - faceRadius * 0.35f)
        lineTo(centerX - faceRadius * 0.9f, bangY - faceRadius * 0.35f)
        close()
    }
    drawPath(bang1, color = hairColor, style = Fill)

    // Left bang strand
    val bang2 = Path().apply {
        moveTo(centerX - faceRadius * 1.0f, bangY - faceRadius * 0.15f)
        quadraticBezierTo(
            centerX - faceRadius * 0.6f, bangY + faceRadius * 0.55f,
            centerX - faceRadius * 0.25f, bangY + faceRadius * 0.4f
        )
        lineTo(centerX - faceRadius * 0.15f, bangY + faceRadius * 0.1f)
        quadraticBezierTo(
            centerX - faceRadius * 0.4f, bangY - faceRadius * 0.1f,
            centerX - faceRadius * 0.7f, bangY - faceRadius * 0.3f
        )
        close()
    }
    drawPath(bang2, color = highlightColor, style = Fill)

    // Right bang strand
    val bang3 = Path().apply {
        moveTo(centerX + faceRadius * 1.0f, bangY - faceRadius * 0.15f)
        quadraticBezierTo(
            centerX + faceRadius * 0.6f, bangY + faceRadius * 0.55f,
            centerX + faceRadius * 0.25f, bangY + faceRadius * 0.4f
        )
        lineTo(centerX + faceRadius * 0.15f, bangY + faceRadius * 0.1f)
        quadraticBezierTo(
            centerX + faceRadius * 0.4f, bangY - faceRadius * 0.1f,
            centerX + faceRadius * 0.7f, bangY - faceRadius * 0.3f
        )
        close()
    }
    drawPath(bang3, color = highlightColor, style = Fill)
}

private fun DrawScope.drawSideHair(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    hairColor: Color
) {
    // Small accent strands framing the face on each side
    val leftStrand = Path().apply {
        moveTo(centerX - faceRadius * 0.95f, centerY - faceRadius * 0.3f)
        quadraticBezierTo(
            centerX - faceRadius * 1.1f, centerY + faceRadius * 0.2f,
            centerX - faceRadius * 0.9f, centerY + faceRadius * 0.7f
        )
        lineTo(centerX - faceRadius * 0.75f, centerY + faceRadius * 0.65f)
        quadraticBezierTo(
            centerX - faceRadius * 0.9f, centerY + faceRadius * 0.1f,
            centerX - faceRadius * 0.8f, centerY - faceRadius * 0.2f
        )
        close()
    }
    drawPath(leftStrand, color = hairColor, style = Fill)

    val rightStrand = Path().apply {
        moveTo(centerX + faceRadius * 0.95f, centerY - faceRadius * 0.3f)
        quadraticBezierTo(
            centerX + faceRadius * 1.1f, centerY + faceRadius * 0.2f,
            centerX + faceRadius * 0.9f, centerY + faceRadius * 0.7f
        )
        lineTo(centerX + faceRadius * 0.75f, centerY + faceRadius * 0.65f)
        quadraticBezierTo(
            centerX + faceRadius * 0.9f, centerY + faceRadius * 0.1f,
            centerX + faceRadius * 0.8f, centerY - faceRadius * 0.2f
        )
        close()
    }
    drawPath(rightStrand, color = hairColor, style = Fill)
}

private fun DrawScope.drawEyebrows(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    color: Color,
    raise: Float
) {
    val browY = centerY - faceRadius * 0.38f - raise
    val browWidth = faceRadius * 0.3f
    val browThickness = faceRadius * 0.03f

    // Left eyebrow
    val leftBrow = Path().apply {
        moveTo(centerX - faceRadius * 0.52f, browY + browThickness)
        quadraticBezierTo(
            centerX - faceRadius * 0.35f, browY - browThickness * 2f,
            centerX - faceRadius * 0.12f, browY
        )
        quadraticBezierTo(
            centerX - faceRadius * 0.35f, browY - browThickness,
            centerX - faceRadius * 0.52f, browY + browThickness
        )
        close()
    }
    drawPath(leftBrow, color = color, style = Fill)

    // Right eyebrow (mirrored)
    val rightBrow = Path().apply {
        moveTo(centerX + faceRadius * 0.52f, browY + browThickness)
        quadraticBezierTo(
            centerX + faceRadius * 0.35f, browY - browThickness * 2f,
            centerX + faceRadius * 0.12f, browY
        )
        quadraticBezierTo(
            centerX + faceRadius * 0.35f, browY - browThickness,
            centerX + faceRadius * 0.52f, browY + browThickness
        )
        close()
    }
    drawPath(rightBrow, color = color, style = Fill)
}

private fun DrawScope.drawEyes(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    blinkProgress: Float,
    irisColor: Color,
    irisDark: Color
) {
    val eyeY = centerY - faceRadius * 0.15f
    val eyeSpacing = faceRadius * 0.35f
    val eyeWidth = faceRadius * 0.2f
    val eyeHeight = faceRadius * 0.25f

    // Effective height reduced by blink
    val effectiveHeight = eyeHeight * (1f - blinkProgress)

    drawSingleEye(
        cx = centerX - eyeSpacing,
        cy = eyeY,
        width = eyeWidth,
        height = effectiveHeight,
        maxHeight = eyeHeight,
        irisColor = irisColor,
        irisDark = irisDark
    )
    drawSingleEye(
        cx = centerX + eyeSpacing,
        cy = eyeY,
        width = eyeWidth,
        height = effectiveHeight,
        maxHeight = eyeHeight,
        irisColor = irisColor,
        irisDark = irisDark
    )
}

private fun DrawScope.drawSingleEye(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    maxHeight: Float,
    irisColor: Color,
    irisDark: Color
) {
    if (height < 1f) {
        // Eye fully closed -- draw a line
        drawLine(
            color = Color.Black,
            start = Offset(cx - width, cy),
            end = Offset(cx + width, cy),
            strokeWidth = 2f
        )
        return
    }

    // Sclera (white of the eye)
    val scleraPath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                cx - width, cy - height,
                cx + width, cy + height
            )
        )
    }
    drawPath(scleraPath, color = Color.White, style = Fill)

    // Iris
    val irisRadius = minOf(width, maxHeight) * 0.6f
    val irisGradient = Brush.radialGradient(
        colors = listOf(irisColor, irisDark),
        center = Offset(cx, cy),
        radius = irisRadius
    )
    drawCircle(
        brush = irisGradient,
        radius = irisRadius,
        center = Offset(cx, cy)
    )

    // Pupil
    drawCircle(
        color = Color.Black,
        radius = irisRadius * 0.45f,
        center = Offset(cx, cy)
    )

    // Highlight (white dot in upper-right area of iris)
    drawCircle(
        color = Color.White,
        radius = irisRadius * 0.2f,
        center = Offset(cx + irisRadius * 0.3f, cy - irisRadius * 0.3f)
    )

    // Second smaller highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = irisRadius * 0.1f,
        center = Offset(cx - irisRadius * 0.2f, cy + irisRadius * 0.25f)
    )

    // Upper eyelid line (for partial blink effect)
    if (height < maxHeight) {
        val eyelidPath = Path().apply {
            moveTo(cx - width, cy)
            quadraticBezierTo(cx, cy - height * 0.5f, cx + width, cy)
        }
        drawPath(
            path = eyelidPath,
            color = Color.Black,
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawNose(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    color: Color
) {
    val noseY = centerY + faceRadius * 0.12f
    // Small triangular nose hint
    val nosePath = Path().apply {
        moveTo(centerX, noseY - faceRadius * 0.04f)
        lineTo(centerX - faceRadius * 0.04f, noseY + faceRadius * 0.03f)
        lineTo(centerX + faceRadius * 0.04f, noseY + faceRadius * 0.03f)
        close()
    }
    drawPath(nosePath, color = color, style = Fill)
}

private fun DrawScope.drawBlush(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    color: Color
) {
    val blushY = centerY + faceRadius * 0.15f
    val blushRadius = faceRadius * 0.12f

    // Left cheek blush
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = blushRadius,
        center = Offset(centerX - faceRadius * 0.45f, blushY)
    )
    // Right cheek blush
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = blushRadius,
        center = Offset(centerX + faceRadius * 0.45f, blushY)
    )
}

/**
 * Draws the mouth with variable openness driven by audio amplitude.
 *
 * Openness levels:
 *   0.0        - closed (simple curved line)
 *   0.0-0.3    - slight oval (small opening)
 *   0.3-0.6    - wider oval (medium opening)
 *   0.6-0.85   - full open mouth
 *   0.85-1.0   - maximum opening (wide, for loud sounds)
 */
private fun DrawScope.drawMouth(
    centerX: Float,
    centerY: Float,
    faceRadius: Float,
    openness: Float,
    innerColor: Color,
    skinColor: Color
) {
    val mouthY = centerY + faceRadius * 0.38f
    val maxMouthWidth = faceRadius * 0.22f
    val maxMouthHeight = faceRadius * 0.18f

    val mouthWidth = maxMouthWidth * (0.4f + openness * 0.6f)
    val mouthHeight = maxMouthHeight * openness

    if (openness < 0.05f) {
        // Closed mouth -- simple curved line (slight smile)
        val smilePath = Path().apply {
            moveTo(centerX - mouthWidth, mouthY)
            quadraticBezierTo(
                centerX, mouthY + faceRadius * 0.06f,
                centerX + mouthWidth, mouthY
            )
        }
        drawPath(
            path = smilePath,
            color = Color(0xFF6B3A3A),
            style = Stroke(width = 2.5f)
        )
    } else {
        // Open mouth -- draw filled oval
        val mouthPath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    centerX - mouthWidth,
                    mouthY - mouthHeight,
                    centerX + mouthWidth,
                    mouthY + mouthHeight
                )
            )
        }
        // Inner mouth (dark)
        drawPath(mouthPath, color = innerColor, style = Fill)

        // Tongue hint for medium-to-wide openings
        if (openness > 0.4f) {
            val tongueHeight = mouthHeight * 0.4f
            val tongueWidth = mouthWidth * 0.5f
            drawOval(
                color = Color(0xFFCC6666),
                topLeft = Offset(centerX - tongueWidth, mouthY + mouthHeight * 0.1f),
                size = Size(tongueWidth * 2f, tongueHeight)
            )
        }

        // Lip outline
        val lipPath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    centerX - mouthWidth - 1f,
                    mouthY - mouthHeight - 1f,
                    centerX + mouthWidth + 1f,
                    mouthY + mouthHeight + 1f
                )
            )
        }
        drawPath(
            path = lipPath,
            color = Color(0xFF6B3A3A),
            style = Stroke(width = 2f)
        )
    }
}
