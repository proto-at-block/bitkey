package build.wallet.gradle.logic

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

/**
 * A Gradle plugin to automatically include the TestCodeEliminator Compiler
 * plugin to the target gradle module.
 *
 * Plugin activation can be controlled in the Gradle buildscript with:
 *
 * ```
 * testCodeEliminator {
 *   enabled.set(isReleaseBuild)
 * }
 * ```
 */
class TestCodeEliminatorPlugin : KotlinCompilerPluginSupportPlugin {
  override fun apply(target: Project) {
    target.extensions.create<TestCodeEliminatorExtension>("testCodeEliminator")
    super.apply(target)
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    val extension = kotlinCompilation.project.extensions.getByType<TestCodeEliminatorExtension>()
    return extension.enabled.get()
  }

  override fun getCompilerPluginId(): String = "bitkey.test-code-eliminator"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(
      groupId = "bitkey",
      artifactId = "test-code-eliminator"
    )

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    return kotlinCompilation.project.provider { emptyList() }
  }
}

open class TestCodeEliminatorExtension
  @Inject
  constructor(
    objects: ObjectFactory,
  ) {
    val enabled = objects.property<Boolean>().convention(false)
  }
