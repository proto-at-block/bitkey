package build.wallet.gradle.dependencylocking.exception

import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifier

internal class UnexpectedDependencyException(
  moduleIdentifier: ModuleIdentifier,
  lockableConfiguration: LockableConfiguration,
  projectPath: String,
) : DependencyLockingException(
    "Dependency '$moduleIdentifier' from configuration '${lockableConfiguration.id}' in project '$projectPath' " +
      "is not registered for dependency locking group '${lockableConfiguration.group.name}' to which the configuration belongs.\n" +
      "Either the lock file is out of date or the configuration is assigned to an incorrect dependency locking group.\n" +
      "If the lock file is out of date and this change is expected, please update the lock file using the `just update-gradle-lockfile` command."
  )
