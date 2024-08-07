package build.wallet.kotest.paparazzi

import app.cash.paparazzi.*
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import io.kotest.core.TestConfiguration
import java.io.File

// Recent layoutlib versions have minor rendering differences
// between host OS types.  For linux CI machines, the max difference
// is increased to allow for text antialiasing and color blending
// variance.
// https://github.com/cashapp/paparazzi/issues/1465#issuecomment-2187303002
private val defaultMaxPercentDifference by lazy {
  val isHostMac = System.getProperty("os.name") == "Mac OS X"
  if (isHostMac) {
    0.01
  } else {
    0.027
  }
}

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
  maxPercentDifference: Double = defaultMaxPercentDifference,
): PaparazziExtension {
  // Name of the spec without "Snapshots" postfix.
  val componentName = requireNotNull(this::class.simpleName).removeSuffix("Snapshots")
  val paparazzi =
    Paparazzi(
      deviceConfig = deviceConfig,
      renderingMode = renderingMode,
      showSystemUi = showSystemUi,
      snapshotHandler = determineHandler(componentName, maxPercentDifference),
      environment =
        detectEnvironment().copy(
          compileSdkVersion = 34
        )
    )
  return extension(PaparazziExtension(paparazzi = paparazzi))
}

// Copied from Paparazzi's internals, except for custom directory path.
private fun determineHandler(
  componentName: String,
  maxPercentDifference: Double,
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
