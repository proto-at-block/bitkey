package build.wallet.gradle.dependencylocking.exception

import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration

internal class UndefinedDependencyLockingGroupException(
  lockableConfiguration: LockableConfiguration,
) : DependencyLockingException(
    "Configuration '${lockableConfiguration.id}' does not belong to any dependency locking group. " +
      "You need to either assign the configuration to some dependency locking group or disable locking for this configuration."
  )
