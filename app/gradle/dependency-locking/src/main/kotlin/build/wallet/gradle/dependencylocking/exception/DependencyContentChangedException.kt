package build.wallet.gradle.dependencylocking.exception

import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.configuration.LockableVariant
import build.wallet.gradle.dependencylocking.lockfile.LockFile

internal class DependencyContentChangedException(
  lockableVariant: LockableVariant,
  lockFileComponent: LockFile.Component,
  lockableConfiguration: LockableConfiguration,
  projectPath: String,
) : DependencyLockingException(
    """
    Content of component '${lockableVariant.coordinates}' requested by the configuration '${lockableConfiguration.id}' from group '${lockableConfiguration.group}' in project '$projectPath' has changed - no matching variant found.
      Resolved content: '${lockableVariant.artifacts}'
      Variants in the lock file: '${lockFileComponent.variants}'
    If this change is expected, please update the lock file using the `just update-gradle-lockfile` command.
    """.trimIndent()
  )
