package build.wallet.kotest.paparazzi

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.HtmlReportWriter
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.SnapshotVerifier
import app.cash.paparazzi.detectEnvironment
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import io.kotest.core.TestConfiguration
import java.io.File

/**
 * Registers [PaparazziExtension].
 *
 * Usage:
 * ```kotlin
 * class SomeSnapshots : FunSpec({
 *   val paparazzi = paparazziExtension()
 *
 *   test("cool button") {
 *     paparazzi.snapshot {
 *       CoolButton(text = "hi!")
 *     }
 *   }
 * })
 * ```
 */
fun TestConfiguration.paparazziExtension(
  deviceConfig: DeviceConfig = DeviceConfig.PIXEL_6,
  renderingMode: RenderingMode = RenderingMode.SHRINK,
  showSystemUi: Boolean = false,
): PaparazziExtension {
  // Name of the spec without "Snapshots" postfix.
  val componentName = requireNotNull(this::class.simpleName).removeSuffix("Snapshots")
  val paparazzi =
    Paparazzi(
      deviceConfig = deviceConfig,
      renderingMode = renderingMode,
      showSystemUi = showSystemUi,
      snapshotHandler = determineHandler(componentName),
      // Paparazzi doesn't work with 34.
      // Workaround for https://github.com/cashapp/paparazzi/issues/1025.
      // TODO: remove this workaround once Paparazzi supports SDK 34.
      //       Also make sure to remove SDK 33 from `../.github/actions/android-sdk/action.yml`.
      environment =
        detectEnvironment().let {
          it.copy(
            compileSdkVersion = 33,
            platformDir = it.platformDir.replace("34", "33")
          )
        }
    )
  return extension(PaparazziExtension(paparazzi = paparazzi))
}

// Copied from Paparazzi's internals, except for custom directory path.
private fun determineHandler(
  componentName: String,
  maxPercentDifference: Double = 0.01,
): SnapshotHandler {
  val rootDirectory = File("src/test/snapshots/$componentName")
  return if (isVerifying) {
    SnapshotVerifier(maxPercentDifference, rootDirectory = rootDirectory)
  } else {
    HtmlReportWriter(snapshotRootDirectory = rootDirectory)
  }
}

private val isVerifying: Boolean =
  System.getProperty("paparazzi.test.verify")?.toBoolean() == true
