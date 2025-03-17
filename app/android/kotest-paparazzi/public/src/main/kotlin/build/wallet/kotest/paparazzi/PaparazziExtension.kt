package build.wallet.kotest.paparazzi

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme
import com.android.internal.R.attr.theme
import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Custom Kotest extension that integrates [Paparazzi] Junit 4 rule with Kotest.
 *
 * We purely use Kotest for writing tests. Paparazzi is implemented as a Junit 4 rule, which does
 * not integrate easily out of the box with Kotest. This is why we are wrapping it with our own
 * custom extension here. Because of this, this extension is prone to potential breakages after
 * updating Paparazzi.
 * TODO(W-695): use Paparazzi's generic test setup once it's decoupled from Junit 4:
 *  https://github.com/cashapp/paparazzi/issues/282.
 *
 * Warning: This implementation is prone to potential breakages since
 *
 * Recommended to be used with [TestConfiguration.paparazziExtension].
 */
class PaparazziExtension(
  private val paparazzi: Paparazzi,
  private val newStoragePath: Boolean,
) : BeforeTestListener, AfterTestListener {
  /**
   * Captures snapshot of a UI Composable using [PreviewWalletTheme].
   */
  fun snapshot(
    onlyTheme: Theme? = null,
    deviceConfig: DeviceConfig? = null,
    content: @Composable () -> Unit,
  ) {
    deviceConfig?.let { paparazzi.unsafeUpdateConfig(it) }
    val themes = onlyTheme?.let { listOf(it) } ?: Theme.entries.toList()
    themes.forEach { theme ->
      val name = if (newStoragePath) {
        "${theme.name}.Android"
      } else {
        theme.name
      }
      paparazzi.snapshot(name = name) {
        PreviewWalletTheme(theme = theme) {
          content()
        }
      }
    }
  }

  /**
   * Captures gif of a UI Composable using [PreviewWalletTheme].
   */
  fun gif(
    length: Long = 1000,
    onlyTheme: Theme? = null,
    deviceConfig: DeviceConfig? = null,
    content: @Composable () -> Unit,
  ) {
    deviceConfig?.let { paparazzi.unsafeUpdateConfig(it) }

    val themes = onlyTheme?.let { listOf(it) } ?: Theme.entries.toList()
    themes.forEach { theme ->
      val hostView = ComposeView(paparazzi.context).apply {
        setContent {
          PreviewWalletTheme(theme = theme) {
            content()
          }
        }
      }
      paparazzi.gif(
        view = hostView,
        name = theme.name,
        start = 0,
        end = length,
        fps = 60
      )
    }
  }

  override suspend fun beforeTest(testCase: TestCase) {
    val junit4Description = testCase.junit4Description
    paparazzi.apply(
      base = NoopJunitStatement,
      description = junit4Description
    ).evaluate()
    paparazzi.prepare(junit4Description)
  }

  override suspend fun afterTest(
    testCase: TestCase,
    result: TestResult,
  ) {
    paparazzi.close()
  }
}

private val TestCase.junit4Description: Description
  get() = Description.createTestDescription(descriptor.parent.id.value, sanitizedName)

private object NoopJunitStatement : Statement() {
  override fun evaluate() = Unit
}

/**
 * Returns sanitized name of the test to be used for snapshot filename:
 * - removes '-', and ',' chars
 * - replaces whitespace ' ' with '_' char
 */
private val TestCase.sanitizedName: String
  get() = name.testName
    .replace("-", "")
    .replace(",", "")
    .replace(" ", "_")
