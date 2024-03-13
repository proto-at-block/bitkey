package build.wallet.gradle.dependencylocking.configuration

import build.wallet.gradle.dependencylocking.util.getId
import org.gradle.api.artifacts.Configuration

internal data class ConfigurationWithOrigin(
  val configuration: Configuration,
  val origin: LockableConfiguration.Origin,
) {
  val id: LockableConfiguration.Id = configuration.getId(origin)
}
