package build.wallet.gradle.logic

import build.wallet.gradle.logic.extensions.environmentVariable
import build.wallet.gradle.logic.extensions.exec
import build.wallet.gradle.logic.extensions.gitBranch
import build.wallet.gradle.logic.extensions.gitCommitSha
import build.wallet.gradle.logic.extensions.gradleProperty
import build.wallet.gradle.logic.extensions.isCi
import build.wallet.gradle.logic.extensions.isRunningInXcode
import build.wallet.gradle.logic.extensions.systemProperty
import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension
import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.support.serviceOf
import java.net.URLEncoder
import java.nio.charset.Charset.defaultCharset

internal class GradleBuildScansPlugin : Plugin<Project> {
  private lateinit var gradleEnterprise: GradleEnterpriseExtension
  private lateinit var buildScan: BuildScanExtension

  override fun apply(project: Project) =
    project.run {
      gradleEnterprise = extensions.getByType<GradleEnterpriseExtension>()
      buildScan = gradleEnterprise.buildScan

      buildScan.run {
        isUploadInBackground = !isCi()

        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"

        val shouldCreateBuildScan = shouldCreateBuildScans()
        publishAlwaysIf(shouldCreateBuildScan)

        if (shouldCreateBuildScan) {
          tagCiOrLocal()
          tagGradleProfiler()
          tagIde()
          tagBuildEnvironment()
          addGitMetadata()
        }
      }
    }

  private fun Project.shouldCreateBuildScans(): Boolean {
    // Ignore IDE tasks.
    if (gradle.startParameter.taskNames.any {
        it.startsWith("idea")
      } ||
      isRunningInXcode() ||
      isIdeSync()
    ) {
      return false
    }

    if (isCi()) {
      return true
    }

    return shouldCreateBuildScansForLocalBuild()
  }

  private fun Project.shouldCreateBuildScansForLocalBuild(): Boolean {
    val buildsDisabledReason =
      when {
        project.property("build.wallet.scans.local-builds") != "true" -> {
          "Local build scans are disabled. " +
            "To enable local build scans change 'build.wallet.scans.local-builds' property to " +
            "\"true\" in 'gradle.properties', or rerun the task with the " +
            "'-Pbuild.wallet.scans.local-builds=true' parameter."
        }
        // Create build scans for local builds only if connected to VPN.
        !providers.isVpnEnabled().get() -> {
          "Build scans are disabled because the Block VPN is not connected."
        }
        else -> null
      }

    if (buildsDisabledReason != null) {
      project.serviceOf<StyledTextOutputFactory>().create("GradleBuildScansPlugin")
        .style(StyledTextOutput.Style.Info)
        .println(buildsDisabledReason)

      return false
    } else {
      return true
    }
  }

  private fun Project.tagCiOrLocal() {
    buildScan.tag(if (isCi()) "CI" else "LOCAL")
  }

  private fun Project.tagGradleProfiler() {
    // Naive way to determine if Gradle task is being ran by a Gradle Profiler.
    // The `GRADLE_PROFILER` environment variable is set by a just recipe when `gradle-profiler` is
    // called.
    if (project.environmentVariable("GRADLE_PROFILER").isPresent) {
      buildScan.tag("GRADLE_PROFILER")
    }
  }

  private fun Project.tagBuildEnvironment() =
    buildScan.run {
      tag(systemProperty("os.name").get())
      value("OS: Device Total Processors", Runtime.getRuntime().availableProcessors().toString())

      val (maxMemoryBegin, totalMemoryBegin, freeMemoryBegin) = getMemoryParameters()
      value("OS: Device Max Memory [Begin]", maxMemoryBegin)
      value("OS: Device Total Memory [Begin]", totalMemoryBegin)
      value("OS: Device Free Memory [Begin]", freeMemoryBegin)

      buildFinished {
        val (maxMemoryEnd, totalMemoryEnd, freeMemoryEnd) = getMemoryParameters()
        value("OS: Device Max Memory [End]", maxMemoryEnd)
        value("OS: Device Total Memory [End]", totalMemoryEnd)
        value("OS: Device Free Memory [End]", freeMemoryEnd)
      }
    }

  private fun getMemoryParameters(): List<String> {
    val runtime = Runtime.getRuntime()
    val bytesPerMb = 1_024 * 1_024
    val maxMemory = runtime.maxMemory()

    return listOf(
      if (maxMemory == Long.MAX_VALUE) "No limit" else "${(maxMemory / bytesPerMb)} MiB",
      "${(runtime.totalMemory() / bytesPerMb)} MiB",
      "${(runtime.freeMemory() / bytesPerMb)} MiB"
    )
  }

  private fun Project.tagIde() =
    buildScan.run {
      when {
        isAndroidStudio() -> {
          tag("Android Studio")
          value("Android Studio Version", androidStudioVersion())
          value(
            "# Modules",
            rootProject.allprojects.filter { it.subprojects.isEmpty() }.size.toString()
          )
        }

        isIntelliJ() -> tag("IntelliJ IDEA")

        !isCi() -> tag("CLI")
      }

      if (isIdeSync()) {
        tag("IDE sync")
      }
    }

  private fun Project.addGitMetadata() {
    val isCi = isCi()

    val gitCommitSha: Provider<String?> =
      if (isCi) {
        provider { gitCommitSha()?.take(8) }
      } else {
        providers.exec("git", "rev-parse", "--short=8", "--verify", "HEAD").map { it }
      }

    val gitBranchName: Provider<String?> =
      if (isCi) {
        provider { gitBranch() }
      } else {
        providers.exec("git", "name-rev", "--name-only", "HEAD").map { it }
      }
    val gitStatus: Provider<String?> =
      if (isCi) {
        provider { null }
      } else {
        providers.exec("git", "status", "--porcelain").map { it }
      }

    buildScan.buildFinished {
      with(buildScan) {
        gitCommitSha.orNull?.let { sha ->
          val gitCommitShaLabel = "Git: SHA"
          value(gitCommitShaLabel, sha)
          addSearchLink("SHA $sha scans", mapOf(gitCommitShaLabel to sha))
          link("Github commit", "https://github.com/squareup/wallet/commit/$sha")
        }
        gitBranchName.orNull?.let { branch ->
          val branchNameLabel = "Git: Branch"
          value(branchNameLabel, branch)
          addSearchLink("$branch scans", mapOf(branchNameLabel to branch))
        }
        gitStatus.orNull?.let { status ->
          tag("Dirty")
          value("Git: Status", status)
        }
      }
    }
  }

  private fun BuildScanExtension.addSearchLink(
    title: String,
    search: Map<String, String>,
  ) {
    this.server?.let { server ->
      this.link(title, customValueSearchUrl(server, search))
    }
  }

  private fun customValueSearchUrl(
    buildScanServer: String,
    search: Map<String, String>,
  ): String {
    val query =
      search.map { (name, value) ->
        "search.names=${encodeURL(name)}&search.values=${encodeURL(value)}"
      }.joinToString(separator = "&")
    return "${appendIfMissing(buildScanServer, "/")}scans?$query"
  }

  private fun encodeURL(url: String): String {
    return URLEncoder.encode(url, defaultCharset().name())
  }

  private fun appendIfMissing(
    str: String,
    suffix: String,
  ): String = if (str.endsWith(suffix)) str else "$str$suffix"

  private fun Project.isAndroidStudio(): Boolean =
    gradleProperty("android.injected.invoked.from.ide").isPresent

  private fun Project.androidStudioVersion(): String? =
    gradleProperty("android.studio.version").orNull

  private fun Project.isIntelliJ(): Boolean = systemProperty("idea.version").isPresent

  private fun Project.isIdeSync(): Boolean =
    systemProperty("idea.sync.active").orElse("false").map { it.toBoolean() }.get()

  private fun ProviderFactory.isVpnEnabled(): Provider<Boolean> {
    return exec("ifconfig", "-X", "utun.*").map { it.contains("inet 172.30") }
  }
}
