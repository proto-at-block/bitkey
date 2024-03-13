package build.wallet.gradle.dependencylocking.exception

import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.configuration.LockableVariant
import build.wallet.gradle.dependencylocking.lockfile.LockFile

internal class DependencyVersionChangedException(
  lockableVariant: LockableVariant,
  lockFileComponent: LockFile.Component,
  lockableConfiguration: LockableConfiguration,
  projectPath: String,
) : DependencyLockingException(
    """
    Version of dependency '${lockableVariant.moduleIdentifier}' has changed in configuration '${lockableConfiguration.id}' from group '${lockableConfiguration.group}' in project '$projectPath'. 
      Requested version: '${lockableVariant.version}'
      Version in the lock file: '${lockFileComponent.coordinates.componentVersion}'
    If this change is expected, please update the lock file using the `just update-gradle-lockfile` command.
    """.trimIndent()
  )
