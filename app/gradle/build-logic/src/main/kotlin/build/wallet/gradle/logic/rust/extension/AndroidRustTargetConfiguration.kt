package build.wallet.gradle.logic.rust.extension

import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.rust.task.BaseCompileRustTask
import build.wallet.gradle.logic.rust.task.CompileRustForAndroidTask
import build.wallet.gradle.logic.rust.task.GenerateKotlinRustBindingsTask
import build.wallet.gradle.logic.rust.util.RustCompilationProfile
import build.wallet.gradle.logic.rust.util.RustTarget
import build.wallet.gradle.logic.rust.util.withName
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import javax.inject.Inject

abstract class AndroidRustTargetConfiguration
  @Inject
  constructor(
    project: Project,
    extension: KotlinMultiplatformRustExtension,
    objects: ObjectFactory,
  ) : BaseTargetConfiguration(project, extension) {
    val apiLevel: Property<Int> = objects.property(Int::class.java)

    fun arm32() {
      configureTarget(RustTarget.AndroidArm32)
    }

    fun arm64() {
      configureTarget(RustTarget.AndroidArm64)
    }

    fun x64() {
      configureTarget(RustTarget.AndroidX64)
    }

    override val kmpTargetName: String = "android"

    override val compileRustTaskClass: Class<out BaseCompileRustTask> =
      CompileRustForAndroidTask::class.java

    override val supportedProfiles: List<RustCompilationProfile> =
      listOf(
        RustCompilationProfile.Debug,
        RustCompilationProfile.Release
      )

    override fun onFirstTarget() {
      project.extensions.configure(KotlinMultiplatformExtension::class.java) {
        sourceSets.withName("androidMain").configureEach {
          dependencies {
            api(project.libs.jvm.jna.asProvider().get()) {
              artifact {
                name = "jna"
                type = "aar"
              }
            }
          }
        }
      }
    }

    override fun configureCompileRustTask(
      profile: RustCompilationProfile,
      task: TaskProvider<out BaseCompileRustTask>,
    ) {
      task.configure {
        this as CompileRustForAndroidTask

        apiLevel.set(this@AndroidRustTargetConfiguration.apiLevel)
        ndkDirectory.set(
          project.provider {
            project.extensions.getByType<LibraryExtension>().ndkDirectory
          }
        )
      }

      addToJniSrcDir(profile, task.map { it.outputDirectory })
    }

    private fun addToJniSrcDir(
      profile: RustCompilationProfile,
      directory: Provider<DirectoryProperty>,
    ) {
      configureAndroidSourceSet(profile) {
        jniLibs.srcDir(directory.map { it.asFile.get() })
      }

      project.tasks.withName(profile.mergeJniLibFolderTaskName).configureEach {
        inputs.files(directory.map { it.asFile.get() })
      }
    }

    override fun configureGenerateKotlinRustBindingsTask(
      task: TaskProvider<GenerateKotlinRustBindingsTask>,
    ) {
      supportedProfiles.forEach { profile ->
        addToKotlinSrcDir(profile, task.map { it.outputDirectory })
      }
    }

    private fun addToKotlinSrcDir(
      profile: RustCompilationProfile,
      directory: Provider<DirectoryProperty>,
    ) {
      configureAndroidSourceSet(profile) {
        // Cannot use the Kotlin source set because it is overwritten later
        java.srcDir(directory.map { it.asFile.get() })
      }

      project.tasks.withName(profile.compileKotlinTaskName).configureEach {
        inputs.files(directory.map { it.asFile.get() })
      }
    }

    private fun configureAndroidSourceSet(
      profile: RustCompilationProfile,
      action: AndroidSourceSet.() -> Unit,
    ) {
      project.extensions.configure(LibraryExtension::class.java) {
        sourceSets.withName(profile.androidSourceSetName).configureEach {
          action()
        }
      }
    }

    private val RustCompilationProfile.androidSourceSetName: String
      get() =
        when (this) {
          RustCompilationProfile.Debug -> "debug"
          RustCompilationProfile.Release -> "release"
        }

    private val RustCompilationProfile.compileKotlinTaskName: String
      get() =
        when (this) {
          RustCompilationProfile.Debug -> "compileDebugKotlinAndroid"
          RustCompilationProfile.Release -> "compileReleaseKotlinAndroid"
        }

    private val RustCompilationProfile.mergeJniLibFolderTaskName: String
      get() =
        when (this) {
          RustCompilationProfile.Debug -> "mergeDebugJniLibFolders"
          RustCompilationProfile.Release -> "mergeReleaseJniLibFolders"
        }

    override fun getOutputLibraryFile(
      compileRustTask: BaseCompileRustTask,
      target: RustTarget,
    ): Provider<RegularFile> =
      compileRustTask.outputDirectory
        .dir(CompileRustForAndroidTask.getOutputLibraryDirectory(target))
        .map {
          it.file(
            BaseCompileRustTask.getLibraryFileName(extension.libraryName.get(), target)
          )
        }
  }
