plugins {
  // AGP version matches the main build (gradle/libs.versions.toml android-gradle-plugin) so both
  // run on the same hermit-provided Gradle. Kotlin matches trailblaze's own build — its
  // artifacts carry Kotlin 2.3.x metadata that the main build's older Kotlin cannot consume,
  // which is one of the reasons this build is standalone (see README.md).
  id("com.android.library") version "8.10.1"
  id("org.jetbrains.kotlin.android") version "2.3.20"
}

/**
 * Trailblaze artifacts are vendored into vendor/maven/, built from source at this tag
 * (the tag is the published version string). See vendor/update-vendored-trailblaze.sh for
 * why (upstream's published artifacts are unusable on-device) and how to bump.
 */
val trailblazeVersion = "2026.06.01"

/** Versions mirror trailblaze's own catalog at the pinned build (tag v2026.06.01). */
val koogVersion = "1.0.0"
val ktor3Version = "3.3.3"

android {
  namespace = "build.wallet.trailblaze"
  // 36 is required by trailblaze's quickjs-kt dependency (AAR metadata check).
  compileSdk = 36

  defaultConfig {
    minSdk = 28
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("androidTest") {
      // Bundle trails as test-APK assets so AndroidTrailblazeRule.runFromAsset() can read
      // them. Paths passed to runFromAsset() are relative to this directory.
      assets.srcDirs("trails")
    }
  }

  packaging {
    resources.excludes.add("META-INF/INDEX.LIST")
    resources.excludes.add("META-INF/AL2.0")
    resources.excludes.add("META-INF/LICENSE.md")
    resources.excludes.add("META-INF/LICENSE-notice.md")
    resources.excludes.add("META-INF/LGPL2.1")
    resources.excludes.add("META-INF/io.netty.versions.properties")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    animationsDisabled = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  androidTestImplementation("xyz.block.trailblaze:trailblaze-common:$trailblazeVersion")
  androidTestImplementation("xyz.block.trailblaze:trailblaze-android:$trailblazeVersion")
  androidTestImplementation("junit:junit:4.13.2")

  // LLM client classes are referenced by trailblaze's runner setup even in recordings-only
  // replay (trailblaze.aiEnabled=false). No API key is required at replay time; keys are only
  // needed when authoring trails with AI assistance.
  androidTestImplementation("ai.koog:prompt-executor-clients:$koogVersion")
  androidTestImplementation("ai.koog:prompt-executor-ollama-client:$koogVersion")
  androidTestImplementation("ai.koog:prompt-executor-openai-client:$koogVersion")
  androidTestImplementation("ai.koog:prompt-executor-openrouter-client:$koogVersion")
  androidTestImplementation("ai.koog:prompt-llm:$koogVersion")
  androidTestImplementation("io.ktor:ktor-client-core:$ktor3Version")
  // Compat build providing kotlinx.datetime.Clock alongside kotlin.time.Clock, required by koog.
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
  androidTestImplementation("dev.mobile:maestro-orchestra-models:2.3.0") {
    isTransitive = false
  }

  androidTestRuntimeOnly("androidx.test:runner:1.7.0")
  androidTestRuntimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

val validateTrailRecordings by tasks.registering(Exec::class) {
  description = "Validate committed Trailblaze recordings only use replay-safe tools."
  commandLine(
    "python3",
    layout.projectDirectory.file("scripts/validate-trail-recordings.py").asFile.absolutePath,
    layout.projectDirectory.dir("trails").asFile.absolutePath,
  )
}

tasks.named("preBuild").configure {
  dependsOn(validateTrailRecordings)
}
