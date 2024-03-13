package build.wallet.gradle.dependencylocking.exception

import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.configuration.LockableVariant

internal class DependencyNotLockedException(
  lockableVariant: LockableVariant,
  lockableConfiguration: LockableConfiguration,
  projectPath: String,
) : DependencyLockingException(
    """
    Dependency '${lockableVariant.coordinates}' is not present in the lock file. 
    Requested by '${lockableConfiguration.id}' from group '${lockableConfiguration.group}' in project '$projectPath'.
    If this change is expected, please update the lock file using the `just update-gradle-lockfile` command.
    """.trimIndent()
  )
