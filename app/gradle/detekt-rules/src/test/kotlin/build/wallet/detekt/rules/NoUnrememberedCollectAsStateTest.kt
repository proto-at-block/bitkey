package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class NoUnrememberedCollectAsStateTest {
  private val rule = NoUnrememberedCollectAsState(Config.empty)

  // ==========================================================================
  // Safe patterns - should NOT report
  // ==========================================================================

  @Test
  fun `does not report property access`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by service.stateFlow.collectAsState()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report deep property access`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(viewModel: MyViewModel) {
        val state by viewModel.repository.cache.stateFlow.collectAsState()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report remember wrapped flow`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by remember { service.getFlow() }.collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report rememberSaveable wrapped flow`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by rememberSaveable { service.getFlow() }.collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report when collectAsState not imported`() {
    val code = """
      import kotlinx.coroutines.flow.Flow

      fun example(service: MyService) {
        val flow = service.getFlow().collectAsState()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report local variable from remembered flow`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val flow = remember { service.getFlow() }
        val state by flow.collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  // ==========================================================================
  // Unsafe patterns - should report
  // ==========================================================================

  @Test
  fun `reports function call as receiver`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by service.getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertEquals("NoUnrememberedCollectAsState", findings.first().id)
  }

  @Test
  fun `reports operator chain - map`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by service.flow.map { it.name }.collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports operator chain - filter`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by service.flow.filter { it.isValid }.collectAsState(initial = false)
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports multi-operator chain`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by service.flow.filter { it.isValid }.distinctUntilChanged().collectAsState(initial = null)
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports nested function calls`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example() {
        val state by getService().getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports function call deep in chain`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(viewModel: MyViewModel) {
        val state by viewModel.getRepository().cache.stateFlow.collectAsState()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports collectAsStateWithLifecycle with function call`() {
    val code = """
      import androidx.lifecycle.compose.collectAsStateWithLifecycle

      @Composable
      fun Example(service: MyService) {
        val state by service.getFlow().collectAsStateWithLifecycle(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports multiple violations in same file`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state1 by service.getFlow().collectAsState(initial = "")
        val state2 by service.flow.map { it.name }.collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(2, findings.size)
  }

  @Test
  fun `reports direct function call receiver`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example() {
        val state by getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  // ==========================================================================
  // Edge cases
  // ==========================================================================

  @Test
  fun `handles both imports in same file`() {
    val code = """
      import androidx.compose.runtime.collectAsState
      import androidx.lifecycle.compose.collectAsStateWithLifecycle

      @Composable
      fun Example(service: MyService) {
        val state1 by service.getFlow().collectAsState(initial = "")
        val state2 by service.getOtherFlow().collectAsStateWithLifecycle(initial = 0)
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(2, findings.size)
  }

  @Test
  fun `resets import flag between files`() {
    // First file has import
    val code1 = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by service.getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    // Second file does not have import
    val code2 = """
      @Composable
      fun Example(service: MyService) {
        val state by service.getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    val findings1 = rule.lint(code1)
    assertEquals(1, findings1.size)

    val findings2 = rule.lint(code2)
    assertEquals(0, findings2.size)
  }

  @Test
  fun `does not report standalone collectAsState function call`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      fun collectAsState(): String = "test"

      @Composable
      fun Example() {
        val value = collectAsState()
      }
    """.trimIndent()

    // Standalone function call, not a method call
    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  // ==========================================================================
  // Wildcard imports
  // ==========================================================================

  @Test
  fun `reports with wildcard import from compose runtime`() {
    val code = """
      import androidx.compose.runtime.*

      @Composable
      fun Example(service: MyService) {
        val state by service.getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports with wildcard import from lifecycle compose`() {
    val code = """
      import androidx.lifecycle.compose.*

      @Composable
      fun Example(service: MyService) {
        val state by service.getFlow().collectAsStateWithLifecycle(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `does not report property access with wildcard import`() {
    val code = """
      import androidx.compose.runtime.*

      @Composable
      fun Example(service: MyService) {
        val state by service.stateFlow.collectAsState()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  // ==========================================================================
  // Wrapper expressions (parentheses, !!, etc.)
  // ==========================================================================

  @Test
  fun `reports function call wrapped in parentheses`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by (service.getFlow()).collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `reports function call with not-null assertion`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService?) {
        val state by service!!.getFlow().collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `does not report property access with not-null assertion`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService?) {
        val state by service!!.stateFlow.collectAsState()
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `reports nested parentheses with function call`() {
    val code = """
      import androidx.compose.runtime.collectAsState

      @Composable
      fun Example(service: MyService) {
        val state by ((service.getFlow())).collectAsState(initial = "")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }
}
