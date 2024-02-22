package build.wallet.statemachine

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.Model

/**
 * Compat API for consuming [StateMachine] on Android.
 *
 * Effectively exposes [StateMachine]'s [model] function to be used by external Android modules.
 *
 * This is needed because [https://github.com/JetBrains/compose-jb/issues/2346] prevents us from
 * compiling public `@Composable` functions on native for the time being.
 */
@SuppressLint("ComposableNaming")
@Composable
fun <PropsT : Any, ModelT : Model?> StateMachine<PropsT, ModelT>.model(props: PropsT) = model(props)
