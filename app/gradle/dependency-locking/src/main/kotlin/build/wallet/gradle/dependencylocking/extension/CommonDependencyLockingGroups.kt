package build.wallet.gradle.dependencylocking.extension

class CommonDependencyLockingGroups(
  private val extension: DependencyLockingExtension,
) {
  val gradlePlugins: DependencyLockingGroupConfig
    get() = extension.dependencyLockingGroups.maybeCreate("gradle-plugins[${extension.project.name}]")

  val buildClasspath: DependencyLockingGroupConfig
    get() = extension.dependencyLockingGroups.maybeCreate("build-classpath")

  val kotlinCompiler: DependencyLockingGroupConfig
    get() = extension.dependencyLockingGroups.maybeCreate("kotlin-compiler")

  val buildToolchain: DependencyLockingGroupConfig
    get() = extension.dependencyLockingGroups.maybeCreate("build-toolchain")

  val kotlinScript: DependencyLockingGroupConfig
    get() = extension.dependencyLockingGroups.maybeCreate("kotlin-script")
}

val DependencyLockingExtension.commonDependencyLockingGroups: CommonDependencyLockingGroups
  get() = CommonDependencyLockingGroups(this)
