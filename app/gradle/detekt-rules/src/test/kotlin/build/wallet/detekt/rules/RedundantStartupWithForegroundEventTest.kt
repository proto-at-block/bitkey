package build.wallet.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class RedundantStartupWithForegroundEventTest {
  private val rule = RedundantStartupWithForegroundEvent(Config.empty)

  @Test
  fun `reports Startup combined with OnEvent FOREGROUND`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.Startup(),
          RunStrategy.OnEvent(
            observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
          )
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertEquals("RedundantStartupWithForegroundEvent", findings.first().id)
  }

  @Test
  fun `reports Startup with backgroundStrategy combined with OnEvent FOREGROUND`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.Startup(backgroundStrategy = BackgroundStrategy.Skip),
          RunStrategy.OnEvent(
            observer = appSessionManager.appSessionState
              .filter { it == AppSessionState.FOREGROUND },
            backgroundStrategy = BackgroundStrategy.Skip
          )
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
  }

  @Test
  fun `does not report Startup without FOREGROUND OnEvent`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.Startup(),
          RunStrategy.OnEvent(keysetRepairFeatureFlag.flagValue())
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report OnEvent FOREGROUND without Startup`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.OnEvent(
            observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
          )
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report Startup with Periodic`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.Startup(),
          RunStrategy.Periodic(interval = 1.minutes)
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `reports Startup combined with OnEvent FOREGROUND via custom getter`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> get() = setOf(
          RunStrategy.Startup(),
          RunStrategy.OnEvent(
            observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
          )
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertEquals("RedundantStartupWithForegroundEvent", findings.first().id)
  }

  @Test
  fun `does not report Startup with FOREGROUND but without OnEvent`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.Startup(),
          RunStrategy.SomeOther(AppSessionState.FOREGROUND)
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report Startup with OnEvent and FOREGROUND in separate strategies`() {
    val code = """
      class MyWorker : AppWorker {
        override val runStrategy: Set<RunStrategy> = setOf(
          RunStrategy.Startup(),
          RunStrategy.OnEvent(keysetRepairFeatureFlag.flagValue()),
          RunStrategy.SomeOther(AppSessionState.FOREGROUND)
        )
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }

  @Test
  fun `does not report unrelated property`() {
    val code = """
      class MyClass {
        val someProperty: Set<String> = setOf("Startup", "FOREGROUND")
      }
    """.trimIndent()

    val findings = rule.lint(code)
    assertEquals(0, findings.size)
  }
}
