package build.wallet.gradle.logic.structure

import org.gradle.api.Project
import org.jetbrains.kotlin.util.removeSuffixIfPresent

internal fun Project.isPublicModule(): Boolean = name.endsWith("-public")

internal fun Project.isImplModule(): Boolean = name.endsWith("-impl")

internal fun Project.isFakeModule(): Boolean = name.endsWith("-fake")

internal fun Project.isTestingModule(): Boolean = name.endsWith("-testing")

/**
 * Returns module's namespace, examples:
 * - "foo:bar-public" -> "foo.bar"
 * - "foo:bar-baz-impl" -> foo.bar.baz.impl"
 * - "foo-bar-fake" -> foo.bar.fake"
 */
internal val Project.namespace: String
  get() =
    path
      // Remove 'public' from namespaces of public modules.
      // 'public' is a Java keyword, so we can't use it as part of a package name.
      .removeSuffixIfPresent("-public")
      // Remove the leading ':'.
      .removePrefix(":")
      .replace("-", ".")
      .replace(":", ".")
