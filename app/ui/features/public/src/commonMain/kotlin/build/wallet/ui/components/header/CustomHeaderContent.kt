package build.wallet.ui.components.header

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.painter
import kotlinx.coroutines.delay

@Composable
fun CustomHeaderContent(model: FormHeaderModel.CustomContent) {
  when (model) {
    FormHeaderModel.CustomContent.AsteriskWave -> AsteriskWave()
    FormHeaderModel.CustomContent.ScanAnimation -> ScanAnimation()
    is FormHeaderModel.CustomContent.PartnershipTransferAnimation -> PartnershipTransferAnimation(model)
    is FormHeaderModel.PosterImage -> PosterImage(model)
  }
}

@Composable
private fun PosterImage(model: FormHeaderModel.PosterImage) {
  Image(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 20.dp)
      .clip(RoundedCornerShape(16.dp)),
    contentScale = ContentScale.FillWidth,
    painter = model.icon.painter(),
    contentDescription = ""
  )
}

@Composable
private fun PartnershipTransferAnimation(
  model: FormHeaderModel.CustomContent.PartnershipTransferAnimation,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .padding(top = 24.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconImage(model = model.bitkeyIcon)
    Spacer(modifier = Modifier.size(6.dp))
    DotsLoadingIndicator()
    Spacer(modifier = Modifier.size(6.dp))
    IconImage(model = model.partnerIcon)
  }
}

@Composable
private fun DotsLoadingIndicator() {
  // Infinite transition for the animation
  val infiniteTransition = rememberInfiniteTransition()

  // Animating the opacity for each dot
  val dot1Alpha by infiniteTransition.animateFloat(
    initialValue = 0.1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(0)
    )
  )

  val dot2Alpha by infiniteTransition.animateFloat(
    initialValue = 0.1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(200)
    )
  )

  val dot3Alpha by infiniteTransition.animateFloat(
    initialValue = 0.1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(500, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(400)
    )
  )

  // Creating the row with 3 dots
  Row(
    modifier = Modifier
      .wrapContentSize(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Dot(alpha = dot1Alpha)
    Dot(alpha = dot2Alpha)
    Dot(alpha = dot3Alpha)
  }
}

// Single Dot composable that takes in the current alpha
@Composable
private fun Dot(alpha: Float) {
  Box(
    modifier = Modifier
      .size(8.dp)
      .background(WalletTheme.colors.bitkeyPrimary.copy(alpha = alpha), shape = RoundedCornerShape(3.dp))
  )
}

@Composable
private fun AsteriskWave() {
  val theme = LocalTheme.current
  val labelColor = when (theme) {
    Theme.DARK -> Color.White
    else -> Color.Unspecified
  }
  val labelTreatment = when (theme) {
    Theme.DARK -> Unspecified
    else -> Primary
  }
  val verticalOffsets = remember {
    List(ASTERISK_WAVE_COUNT) { Animatable(0f) }
  }
  val asteriskStyle = WalletTheme.labelStyle(
    type = FORM_HEADER_MODEL_ASTERISK_LABEL_TYPE,
    treatment = labelTreatment,
    alignment = TextAlign.Center,
    textColor = labelColor
  ).copy(
    fontSize = 20.sp,
    lineHeight = 30.sp
  )

  LaunchedEffect(Unit) {
    while (true) {
      verticalOffsets.forEach { verticalOffset ->
        verticalOffset.animateTo(
          targetValue = ASTERISK_WAVE_HEIGHT_PX,
          animationSpec = tween(
            durationMillis = ASTERISK_WAVE_RISE_DURATION_MS,
            easing = LinearOutSlowInEasing
          )
        )
        verticalOffset.animateTo(
          targetValue = 0f,
          animationSpec = tween(
            durationMillis = ASTERISK_WAVE_FALL_DURATION_MS,
            easing = LinearOutSlowInEasing
          )
        )
      }
      delay(32)
    }
  }

  Row(
    modifier = Modifier.padding(top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    verticalOffsets.forEach { verticalOffset ->
      Label(
        text = "*",
        modifier = Modifier.graphicsLayer {
          translationY = -verticalOffset.value
        },
        style = asteriskStyle
      )
    }
  }
}

// -- Scan Animation --
// Ported from firmware: nfc_dots_animation.c
// Uses only top arcs (upper semicircle) from the NFC dot pattern.

// SVG coordinate space from NFC.svg
private const val SCAN_SVG_WIDTH = 223f

// Only render the top half — max Y among top arc dots is ~98
private const val SCAN_TOP_ARC_MAX_Y = 100f

// Canvas dimensions in dp
private const val SCAN_CANVAS_WIDTH_DP = 120f
private const val SCAN_CANVAS_HEIGHT_DP = 64f

// Dot sizes in SVG-space pixels (matching fingerprint/firmware constants)
private const val SCAN_DOT_SIZE_RESTING = 4f
private const val SCAN_DOT_SIZE_ACTIVE = 8f

// Animation timing (matching firmware)
private const val SCAN_NUM_RINGS = 7
private const val SCAN_RADIATE_CYCLE_MS = 1304
private const val SCAN_RADIATE_GAP_RINGS = 2
private const val SCAN_RADIATE_TOTAL_RINGS = SCAN_NUM_RINGS + SCAN_RADIATE_GAP_RINGS
private const val SCAN_RADIATE_PEAK_COLOR_T = 1f

// Spoke-based dot selection: fixed angular positions (dx from pattern center x=111)
// that form consistent radial lines across all rings.
private const val SCAN_PATTERN_CENTER_X = 111f
private const val SCAN_SPOKE_MATCH_THRESH = 30f
private val SCAN_SPOKE_DX = floatArrayOf(-107f, -85f, -58f, -30f, 0f, 30f, 59f, 85f, 107f)
// Process order: center spoke first, then symmetric pairs outward.
private val SCAN_SPOKE_ORDER = intArrayOf(4, 3, 5, 2, 6, 1, 7, 0, 8)
private const val SCAN_NUM_SPOKES = 9

// Top arc dot positions from NFC.svg (x, y pairs).
// Ring order: 0, A, 1, B, 2, C, 3 (inner to outer, matching firmware ring_ranges).
private val SCAN_TOP_ARC_DOTS: List<List<Pair<Float, Float>>> = listOf(
  // Ring 0 (innermost, r≈58) — 5 dots
  listOf(82f to 98f, 96f to 92f, 111f to 90f, 126f to 92f, 140f to 98f),
  // Ring A (intermediate, r≈72) — 7 dots
  listOf(70f to 89f, 82f to 82f, 96f to 77f, 111f to 76f, 126f to 77f, 140f to 82f, 152f to 89f),
  // Ring 1 (r≈86) — 9 dots
  listOf(56f to 82f, 68f to 73f, 82f to 67f, 96f to 63f, 111f to 62f, 126f to 63f, 141f to 67f, 154f to 73f, 167f to 82f),
  // Ring B (intermediate, r≈100) — 11 dots
  listOf(43f to 74f, 55f to 65f, 68f to 58f, 82f to 52f, 96f to 49f, 111f to 48f, 126f to 49f, 140f to 52f, 154f to 58f, 167f to 65f, 179f to 74f),
  // Ring 2 (r≈115) — 13 dots
  listOf(30f to 67f, 41f to 57f, 54f to 48f, 67f to 42f, 81f to 37f, 96f to 34f, 111f to 33f, 126f to 34f, 141f to 37f, 155f to 42f, 169f to 48f, 181f to 57f, 193f to 67f),
  // Ring C (intermediate, r≈130) — 15 dots
  listOf(17f to 58f, 28f to 48f, 40f to 39f, 53f to 32f, 67f to 26f, 81f to 21f, 96f to 19f, 111f to 18f, 126f to 19f, 141f to 21f, 155f to 26f, 169f to 32f, 182f to 39f, 194f to 48f, 205f to 58f),
  // Ring 3 (outermost, r≈144) — 17 dots
  listOf(4f to 52f, 15f to 41f, 26f to 32f, 39f to 23f, 53f to 16f, 67f to 11f, 81f to 7f, 96f to 5f, 111f to 4f, 126f to 5f, 141f to 7f, 156f to 11f, 170f to 16f, 183f to 23f, 196f to 32f, 208f to 41f, 218f to 52f)
)

/** Find the nearest unactivated dot to a target dx, or -1 if none within threshold. */
private fun findNearestDot(
  dots: List<Pair<Float, Float>>,
  activated: BooleanArray,
  targetDx: Float,
): Int {
  var bestI = -1
  var bestD = Float.MAX_VALUE
  for (i in dots.indices) {
    if (activated[i]) continue
    val d = abs(dots[i].first - SCAN_PATTERN_CENTER_X - targetDx)
    if (d < bestD) {
      bestD = d
      bestI = i
    }
  }
  return if (bestI >= 0 && bestD <= SCAN_SPOKE_MATCH_THRESH) bestI else -1
}

/** Activate spoke-matched dots in an arc, setting sizes and color intensities. */
private fun activateSpokeDots(
  dots: List<Pair<Float, Float>>,
  proximity: Float,
  activated: BooleanArray,
  dotSizes: FloatArray,
  dotColorTs: FloatArray,
) {
  for (si in 0 until SCAN_NUM_SPOKES) {
    val s = SCAN_SPOKE_ORDER[si]
    val bestI = findNearestDot(dots, activated, SCAN_SPOKE_DX[s])
    if (bestI < 0) continue
    activated[bestI] = true

    // Single continuous lerp: size and color sweep together from resting to
    // full active via proximity.
    dotSizes[bestI] = SCAN_DOT_SIZE_RESTING + (SCAN_DOT_SIZE_ACTIVE - SCAN_DOT_SIZE_RESTING) * proximity
    dotColorTs[bestI] = SCAN_RADIATE_PEAK_COLOR_T * proximity
  }
}

/** Add subtle ghost glow on the center-ward neighbor of each highlighted dot. */
private fun applyGhostTrail(
  count: Int,
  proximity: Float,
  activated: BooleanArray,
  dotColorTs: FloatArray,
) {
  val arcCenter = count / 2
  val ghostColorT = SCAN_RADIATE_PEAK_COLOR_T * 0.30f * proximity
  for (i in 0 until count) {
    if (!activated[i] || i == arcCenter) continue
    val neighbor = if (i < arcCenter) i + 1 else i - 1
    if (neighbor in 0 until count && !activated[neighbor]) {
      dotColorTs[neighbor] = maxOf(dotColorTs[neighbor], ghostColorT)
    }
  }
}

@Composable
private fun ScanAnimation() {
  val theme = LocalTheme.current
  val infiniteTransition = rememberInfiniteTransition()

  val phase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = SCAN_RADIATE_TOTAL_RINGS.toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = SCAN_RADIATE_CYCLE_MS, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    )
  )

  val restingColor = when (theme) {
    Theme.DARK -> Color(red = 0x40 / 255f, green = 0x40 / 255f, blue = 0x40 / 255f, alpha = 0.70f)
    else -> Color(0xFFD5D2CD)
  }
  val highlightColor = when (theme) {
    Theme.DARK -> Color(0xFFD1FB96)
    else -> Color.Black
  }

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Canvas(
      modifier = Modifier
        .width(SCAN_CANVAS_WIDTH_DP.dp)
        .height(SCAN_CANVAS_HEIGHT_DP.dp)
    ) {
      val scaleX = size.width / SCAN_SVG_WIDTH
      val scaleY = size.height / SCAN_TOP_ARC_MAX_Y

      for (ringIndex in 0 until SCAN_NUM_RINGS) {
        val dots = SCAN_TOP_ARC_DOTS[ringIndex]
        val count = dots.size
        val dist = abs(phase - ringIndex.toFloat())
        val proximity = if (dist >= 2f) 0f else (1f - dist / 2f)

        val activated = BooleanArray(count)
        val dotSizes = FloatArray(count) { SCAN_DOT_SIZE_RESTING }
        val dotColorTs = FloatArray(count) { 0f }

        if (proximity > 0f) {
          activateSpokeDots(dots, proximity, activated, dotSizes, dotColorTs)
        }
        applyGhostTrail(count, proximity, activated, dotColorTs)

        for (dotIndex in 0 until count) {
          val (svgX, svgY) = dots[dotIndex]
          drawCircle(
            color = lerp(restingColor, highlightColor, dotColorTs[dotIndex]),
            radius = (dotSizes[dotIndex] / 2f) * scaleX,
            center = Offset(svgX * scaleX, svgY * scaleY)
          )
        }
      }
    }
  }
}

/**
 * Interpolate between two Colors in linear-light space (gamma-corrected) for
 * perceptually cleaner transitions. Lerping sRGB channels directly produces
 * muddy midpoints (e.g. grey → lime passes through olive); linear-light keeps
 * the midpoint closer to a brightened hue of the endpoints.
 */
private fun lerp(from: Color, to: Color, t: Float): Color {
  val clamped = t.coerceIn(0f, 1f)
  val linearFrom = from.convert(ColorSpaces.LinearExtendedSrgb)
  val linearTo = to.convert(ColorSpaces.LinearExtendedSrgb)
  return Color(
    red = linearFrom.red + (linearTo.red - linearFrom.red) * clamped,
    green = linearFrom.green + (linearTo.green - linearFrom.green) * clamped,
    blue = linearFrom.blue + (linearTo.blue - linearFrom.blue) * clamped,
    alpha = linearFrom.alpha + (linearTo.alpha - linearFrom.alpha) * clamped,
    colorSpace = ColorSpaces.LinearExtendedSrgb
  ).convert(ColorSpaces.Srgb)
}

private const val ASTERISK_WAVE_COUNT = 4
private const val ASTERISK_WAVE_HEIGHT_PX = 12f
private const val ASTERISK_WAVE_RISE_DURATION_MS = 220
private const val ASTERISK_WAVE_FALL_DURATION_MS = 220
private val FORM_HEADER_MODEL_ASTERISK_LABEL_TYPE = build.wallet.ui.tokens.LabelType.Body2Mono
