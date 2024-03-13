package build.wallet.gradle.logic.rust.extension

import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.rust.task.BaseCompileRustTask
import build.wallet.gradle.logic.rust.task.CompileRustForJvmTask
import build.wallet.gradle.logic.rust.task.GenerateKotlinRustBindingsTask
import build.wallet.gradle.logic.rust.util.RustCompilationProfile
import build.wallet.gradle.logic.rust.util.RustTarget
import build.wallet.gradle.logic.rust.util.withName
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject

abstract class JvmRustTargetConfiguration
  @Inject
  constructor(
    project: Project,
    extension: KotlinMultiplatformRustExtension,
  ) : BaseTargetConfiguration(project, extension) {
    fun host() {
      val hostTarget =
        RustTarget.host ?: run {
          project.logger.warn("Cannot configure Rust plugin for JVM - Host OS is not supported.")
          return
        }

      configureTarget(hostTarget)
    }

    override val kmpTargetName: String = "jvm"

    override val supportedProfiles: List<RustCompilationProfile> =
      listOf(
        RustCompilationProfile.Debug
      )

    override val compileRustTaskClass: Class<out BaseCompileRustTask> =
      CompileRustForJvmTask::class.java

    override fun onFirstTarget() {
      configureJvmMainSourceSet {
        dependencies {
          api(project.libs.jvm.jna.asProvider().get())
        }
      }
    }

    override fun configureCompileRustTask(
      profile: RustCompilationProfile,
      task: TaskProvider<out BaseCompileRustTask>,
    ) {
      configureJvmMainSourceSet {
        resources.srcDir(task.map { it.outputDirectory })
      }
    }

    override fun configureGenerateKotlinRustBindingsTask(
      task: TaskProvider<GenerateKotlinRustBindingsTask>,
    ) {
      configureJvmMainSourceSet {
        kotlin.srcDir(task.map { it.outputDirectory })
      }
    }

    override fun getOutputLibraryFile(
      compileRustTask: BaseCompileRustTask,
      target: RustTarget,
    ): Provider<RegularFile> =
      compileRustTask.outputDirectory.file(
        project.provider {
          BaseCompileRustTask.getLibraryFileName(extension.libraryName.get(), target)
        }
      )

    private fun configureJvmMainSourceSet(action: KotlinSourceSet.() -> Unit) {
      project.extensions.configure(KotlinMultiplatformExtension::class.java) {
        sourceSets.withName("jvmMain").configureEach {
          action()
        }
      }
    }
  }
