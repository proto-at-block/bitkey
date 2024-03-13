package build.wallet.gradle.dependencylocking.util

import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import org.gradle.api.artifacts.Configuration

internal fun Configuration.getId(origin: LockableConfiguration.Origin): LockableConfiguration.Id =
  LockableConfiguration.Id(origin, name)
