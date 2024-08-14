package build.wallet.platform.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.VibrationEffect
import android.os.VibrationEffect.*
import android.os.Vibrator
import build.wallet.platform.haptics.HapticsEffect.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun Context.vibrator(): Vibrator? = getSystemService(Vibrator::class.java)

private val SELECTION_TIMINGS = longArrayOf(1, 10)
private val SELECTION_AMPLITUDE = intArrayOf(60, 0)

/**
 * If vibrator is the phone supports vibration, vibrate with the given vibration [Effect].
 * Fallback on simple vibration with given [fallback] duration if the Android version
 * doesn't support creating predefined [VibrationEffect].
 *
 * Otherwise, no-op.
 */
@SuppressLint("MissingPermission")
internal fun Vibrator.maybeVibrate(
  effect: HapticsEffect,
  fallback: Duration = 70.milliseconds,
) {
  if (hasVibrator()) {
    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
      when (effect) {
        DoubleClick ->
          vibrate(VibrationEffect.createPredefined(EFFECT_DOUBLE_CLICK))
        DullOneShot ->
          vibrate(VibrationEffect.createOneShot(800, 125))
        MediumClick ->
          vibrate(VibrationEffect.createPredefined(EFFECT_CLICK))
        HeavyClick ->
          vibrate(VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK))
        LightClick ->
          vibrate(VibrationEffect.createPredefined(EFFECT_TICK))
        Selection ->
          vibrate(VibrationEffect.createWaveform(SELECTION_TIMINGS, SELECTION_AMPLITUDE, -1))
      }
    } else {
      @Suppress("DEPRECATION")
      vibrate(fallback.inWholeMilliseconds)
    }
  }
}
