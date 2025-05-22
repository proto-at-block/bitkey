/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
// IMPORTANT: We need to suppress this because some of the accessed references and members are internal to Kotlin Gradle Plugin.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "ktlint:standard:no-consecutive-comments")

package org.jetbrains.kotlin.gradle.targets.jvm.tasks

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.testing.Test
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.plugin.UsesVariantImplementationFactories

@DisableCachingByDefault
abstract class KotlinJvmTest : Test(), UsesVariantImplementationFactories {
  // IMPORTANT: This is what we need to add, so that IntelliJ can get the correct compilation name.
  @Input
  @Optional
  var compilation: KotlinCompilationNameContainer? = null
    private set

  // IMPORTANT: We add this, so we can easily provide the compilation name from our build scripts.
  fun setCompilationName(name: String) {
    compilation = KotlinCompilationNameContainer(name)
  }

  @Input
  @Optional
  var targetName: String? = null

  override fun createTestExecuter(): TestExecuter<JvmTestExecutionSpec> =
    targetName?.let { Executor(super.createTestExecuter(), it) }
      ?: super.createTestExecuter()

  class Executor(
    private val delegate: TestExecuter<JvmTestExecutionSpec>,
    private val targetName: String,
  ) : TestExecuter<JvmTestExecutionSpec> by delegate {
    override fun execute(
      testExecutionSpec: JvmTestExecutionSpec,
      testResultProcessor: TestResultProcessor,
    ) {
      delegate.execute(
        testExecutionSpec,
        object : TestResultProcessor by testResultProcessor {
          override fun started(
            test: TestDescriptorInternal,
            event: TestStartEvent,
          ) {
            val myTest = object : TestDescriptorInternal by test {
              override fun getDisplayName(): String = "${test.displayName}[$targetName]"

              override fun getClassName(): String? = test.className?.replace('$', '.')

              override fun getClassDisplayName(): String? = test.classDisplayName?.replace('$', '.')
            }
            testResultProcessor.started(myTest, event)
          }
        }
      )
    }
  }
}
