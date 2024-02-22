package org.jetbrains.kotlin.gradle.targets.jvm.tasks

import java.io.Serializable

data class KotlinCompilationNameContainer(
  val compilationName: String,
) : Serializable
