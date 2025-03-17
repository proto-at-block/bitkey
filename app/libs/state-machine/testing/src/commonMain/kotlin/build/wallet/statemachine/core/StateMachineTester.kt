@file:OptIn(ExperimentalUuidApi::class)

package build.wallet.statemachine.core

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.testIn
import build.wallet.withRealTimeout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 *
 * Main purpose of this interface is to provide a way to update a [StateMachine]'s props in tests
 * via [updateProps].
 *
 * Meant to be used implicitly via [StateMachine.testWithVirtualTime] extension.
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
 */
suspend inline fun <PropsT : Any, ModelT> StateMachine<PropsT, ModelT>.test(
  props: PropsT,
  // Default timeout is arbitrary, we don't have any tests that take longer than 10 seconds to run.
  testTimeout: Duration = 10.seconds,
  turbineTimeout: Duration = 3.seconds,
  noinline validate: suspend StateMachineTester<PropsT, ModelT>.() -> Unit,
) {
  withRealTimeout(timeout = testTimeout) {
    val dispatcher = singleThreadedDispatcher()
    val propsStateFlow = MutableStateFlow(props)
    val models: Flow<ModelT> = moleculeFlow(mode = Immediate) {
      val latestProps by propsStateFlow.collectAsState()
      model(latestProps)
    }
      .flowOn(dispatcher)
      .onCompletion { dispatcher.cancel() }
      .distinctUntilChanged()

    models.test(timeout = turbineTimeout) {
      val tester = StateMachineTesterImpl<PropsT, ModelT>(
        delegate = this,
        onUpdateProps = { newProps ->
          propsStateFlow.value = newProps
        }
      )
      tester.validate()
    }
  }
}

/**
 * Deprecated, use [StateMachine.test] instead.
 *
 * Same as [StateMachine.test], except [TestScope] which skips delays and uses test dispatcher.
 *
 * [StateMachine.test] with real but small delays, real dispatcher is preferred over using [TestScope].
 */
@Suppress("NOTHING_TO_INLINE")
@Deprecated("Use #test which uses real coroutine dispatcher.")
inline fun <PropsT : Any, ModelT> StateMachine<PropsT, ModelT>.testWithVirtualTime(
  props: PropsT,
  // Default timeout is arbitrary, we don't have any tests that take longer than 10 seconds to run.
  testTimeout: Duration = 10.seconds,
  turbineTimeout: Duration = 3.seconds,
  noinline validate: suspend StateMachineTester<PropsT, ModelT>.() -> Unit,
) {
  runTest(timeout = testTimeout) {
    val propsStateFlow = MutableStateFlow(props)
    val models: Flow<ModelT> = moleculeFlow(mode = Immediate) {
      val latestProps by propsStateFlow.collectAsState()
      model(latestProps)
    }.distinctUntilChanged()

    models.test(timeout = turbineTimeout) {
      val tester = StateMachineTesterImpl<PropsT, ModelT>(
        delegate = this,
        onUpdateProps = { newProps ->
          propsStateFlow.value = newProps
        }
      )
      tester.validate()
    }
  }
}

inline fun <PropsT : Any, ModelT> StateMachine<PropsT, ModelT>.testIn(
  props: PropsT,
  scope: CoroutineScope,
  turbineTimeout: Duration = 3.seconds,
): StateMachineTester<PropsT, ModelT> {
  val dispatcher = singleThreadedDispatcher()
  val propsStateFlow = MutableStateFlow(props)
  val models: Flow<ModelT> = moleculeFlow(mode = Immediate) {
    val latestProps by propsStateFlow.collectAsState()
    model(latestProps)
  }
    .flowOn(dispatcher)
    .onCompletion { dispatcher.cancel() }
    .distinctUntilChanged()

  val receiveTurbine = models.testIn(scope, turbineTimeout)
  return StateMachineTesterImpl(
    delegate = receiveTurbine,
    onUpdateProps = { newProps ->
      propsStateFlow.value = newProps
    }
  )
}

/**
 * Single-threaded dispatcher used for testing `StateMachine` compositions.
 * Ensures deterministic, sequential execution (mimicking the UI thread used in Android and iOS app),
 * and prevents concurrency issues or flaky behavior from parallel recompositions.
 *
 * Only meant for internal `StateMachineTester` implementation. Public to keep `test` extensions
 * inline.
 */
@DelicateCoroutinesApi
fun singleThreadedDispatcher(): CoroutineDispatcher =
  newSingleThreadContext("StateMachineTester-${Uuid.random()}")

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
