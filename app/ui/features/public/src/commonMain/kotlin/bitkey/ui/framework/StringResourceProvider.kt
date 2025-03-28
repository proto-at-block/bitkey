package bitkey.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Provides a way to get string resources in a [build.wallet.statemachine.core.StateMachine] or [ScreenPresenter].
 * This is useful for getting strings in a way that is testable and doesn't require access to
 * generated resources.
 */
interface StringResourceProvider {
  @Composable
  fun getString(resourceId: StringResource): String
}

@BitkeyInject(ActivityScope::class)
class StringResourceProviderImpl : StringResourceProvider {
  @Composable
  override fun getString(resourceId: StringResource): String {
    return stringResource(resourceId)
  }
}
