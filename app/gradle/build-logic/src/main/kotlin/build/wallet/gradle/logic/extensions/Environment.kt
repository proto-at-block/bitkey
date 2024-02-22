package build.wallet.gradle.logic.extensions

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

internal fun Project.environmentVariable(key: String): Provider<String> =
  providers.environmentVariable(key)

internal fun Project.systemProperty(key: String): Provider<String> = providers.systemProperty(key)

internal fun Project.gradleProperty(key: String): Provider<String> = providers.gradleProperty(key)

internal fun Project.isCi(): Boolean = environmentVariable("CI").isPresent

internal fun Project.gitBranch(): String? = environmentVariable("GIT_BRANCH").orNull

internal fun Project.gitCommitSha(): String? = environmentVariable("GIT_COMMIT").orNull

internal fun Project.isRunningInXcode(): Boolean =
  environmentVariable(
    "XCODE_VERSION_ACTUAL"
  ).isPresent

internal fun ProviderFactory.exec(vararg command: String): Provider<String> {
  @Suppress("UnstableApiUsage")
  return exec {
    commandLine(*command)
  }.standardOutput.asText.map { it.trim() }
}
