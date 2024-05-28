package build.wallet.statemachine.core

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.testIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 *
 * Main purpose of this interface is to provide a way to update a [StateMachine]'s props in tests
 * via [updateProps].
 *
 * Meant to be used implicitly via [StateMachine.test] extension.
 */
interface StateMachineTester<PropsT, ModelT> : ReceiveTurbine<ModelT> {
  fun updateProps(props: PropsT)
}

/**
 * Testing API for [StateMachine].
 *
 * Collects and validates models produced by [StateMachine]. Props can be later updated in tests
 * by calling [StateMachineTester.updateProps].
 *
 * Example usage:
 * ```
 * someStateMachine.test(initialProps) {
 *   awaitItem() // do some validations
 *
 *   updateProps(newProps)
 *
 *   awaitItem() // do some validations
 * }
 * ```
 *
 * @param [props] initial props to use for the state machine.
 * @param [testTimeout] test timeout duration.
 * @param [useVirtualTime] if true, the code under test will be executed in a coroutine [TestScope]
 * which uses virtual time. Otherwise, the code under test will be executed with real time - delays
 * won't be skipped.
 */
suspend inline fun <PropsT : Any, ModelT> StateMachine<PropsT, ModelT>.test(
  props: PropsT,
  useVirtualTime: Boolean = true,
  // Default timeout is arbitrary, we don't have any tests that take longer than 10 seconds to run.
  testTimeout: Duration = 10.seconds,
  turbineTimeout: Duration = 3.seconds,
  noinline validate: suspend StateMachineTester<PropsT, ModelT>.() -> Unit,
) {
  if (useVirtualTime) {
    runTest(timeout = testTimeout) {
      testInternal(props, turbineTimeout, validate)
    }
  } else {
    withTimeout(testTimeout) {
      // It's possible that `useVirtualTime` is false but the test is still running in a TestScope,
      // in which case the delays will be still skipped. To avoid this, we run the test in a
      // different dispatcher which cuts the Test dispatcher and prevents delays from being skipped.
      // Based on https://github.com/Kotlin/kotlinx.coroutines/issues/3179#issuecomment-1132961347.
      withContext(Dispatchers.Default.limitedParallelism(1)) {
        testInternal(props, turbineTimeout, validate)
      }
    }
  }
}

suspend fun <PropsT : Any, ModelT> StateMachine<PropsT, ModelT>.testInternal(
  props: PropsT,
  turbineTimeout: Duration = 3.seconds,
  validate: suspend StateMachineTester<PropsT, ModelT>.() -> Unit,
) {
  val propsStateFlow = MutableStateFlow(props)
  val models: Flow<ModelT> =
    moleculeFlow(mode = RecompositionMode.Immediate) {
      val latestProps by propsStateFlow.collectAsState()
      model(latestProps)
    }.distinctUntilChanged()

  models.test(timeout = turbineTimeout) {
    val tester =
      StateMachineTesterImpl<PropsT, ModelT>(
        delegate = this,
        onUpdateProps = { newProps ->
          propsStateFlow.value = newProps
        }
      )
    tester.validate()
  }
}

inline fun <PropsT : Any, ModelT> StateMachine<PropsT, ModelT>.testIn(
  props: PropsT,
  scope: CoroutineScope,
  turbineTimeout: Duration = 3.seconds,
): StateMachineTester<PropsT, ModelT> {
  val propsStateFlow = MutableStateFlow(props)
  val models: Flow<ModelT> =
    moleculeFlow(mode = RecompositionMode.Immediate) {
      val latestProps by propsStateFlow.collectAsState()
      model(latestProps)
    }.distinctUntilChanged()

  val receiveTurbine = models.testIn(scope, turbineTimeout)
  return StateMachineTesterImpl(
    delegate = receiveTurbine,
    onUpdateProps = { newProps ->
      propsStateFlow.value = newProps
    }
  )
}

/**
 * Implementation of [StateMachineTester] that delegates to a specific [ReceiveTurbine] instance
 * and exposes a callback for updating props.
 */
class StateMachineTesterImpl<PropsT, ModelT>(
  val delegate: ReceiveTurbine<ModelT>,
  val onUpdateProps: (PropsT) -> Unit,
) : StateMachineTester<PropsT, ModelT>, ReceiveTurbine<ModelT> by delegate {
  override fun updateProps(props: PropsT) = onUpdateProps(props)
}
