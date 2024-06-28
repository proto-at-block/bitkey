package build.wallet.platform.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.VibrationEffect
import android.os.VibrationEffect.EFFECT_DOUBLE_CLICK
import android.os.VibrationEffect.EFFECT_TICK
import android.os.Vibrator
import build.wallet.platform.haptics.HapticsEffect.DoubleClick
import build.wallet.platform.haptics.HapticsEffect.DullOneShot
import build.wallet.platform.haptics.HapticsEffect.MediumClick
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun Context.vibrator(): Vibrator? = getSystemService(Vibrator::class.java)

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
          vibrate(VibrationEffect.createPredefined(EFFECT_TICK))
      }
    } else {
      @Suppress("DEPRECATION")
      vibrate(fallback.inWholeMilliseconds)
    }
  }
}
