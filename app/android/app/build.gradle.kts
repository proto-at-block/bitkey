import build.wallet.gradle.dependencylocking.extension.commonDependencyLockingGroups
import build.wallet.gradle.dependencylocking.util.ifMatches
import build.wallet.gradle.logic.reproducible.reproducibleBuildVariables

plugins {
  id("build.wallet.android.app")
  kotlin("android")
  id("build.wallet.android.bugsnag")
  id("com.google.gms.google-services")
  alias(libs.plugins.datadog)
  alias(libs.plugins.licensee)
}

buildLogic {
  app {
    version(
      yyyy = 2026,
      version = 11,
      patch = 2,
      build = 1
    )
  }
  compose {
    composeUi()
  }
}

android {
  namespace = "build.wallet"

  defaultConfig {
    applicationId = "world.bitkey"

    buildFeatures {
      buildConfig = true
    }

    // Default for debug builds — avoids invalidating Gradle config cache on every commit.
    buildConfigField("String", "BUILD_DATE", "\"dev\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    getByName("debug") {
      storeFile = file(project.file("debug.keystore"))
      storePassword = "bitcointime"
      keyAlias = "upload-key"
      keyPassword = "bitcointime"
    }
  }

  buildTypes {
    // Resolve the build date once for non-debug build types.
    @Suppress("UnstableApiUsage")
    val releaseBuildDate by lazy {
      // Published source commits can be created after the internal build commit,
      // so their git commit date may not match the date compiled into the binary.
      // Source publishing writes release-build-date.txt with the shipped date;
      // builds without that file use the current checkout's commit date.
      val buildDate =
        rootProject.file("verifiable-build/android/release-build-date.txt")
          .takeIf { it.isFile }
          ?.readText()
          ?.trim()
          ?.takeIf { it.isNotBlank() }
          ?: providers.exec {
            commandLine("git", "log", "-1", "--format=%cd", "--date=short")
          }.standardOutput.asText.get().trim()

      require(Regex("""\d{4}-\d{2}-\d{2}""").matches(buildDate)) {
        "Build date must use YYYY-MM-DD format."
      }
      buildDate
    }

    // Build Type for development builds.
    // App ID = "world.bitkey.debug"
    debug {
      applicationIdSuffix = ".debug"
      isDebuggable = true
      isMinifyEnabled = false
      isShrinkResources = false
    }

    // Build Type for Production builds
    // App ID = "world.bitkey"
    register("customer") {
      initWith(getByName("release"))
      applicationIdSuffix = ".app"
      matchingFallbacks += listOf("release")
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      buildConfigField("String", "BUILD_DATE", "\"${releaseBuildDate}\"")
      reproducibleBuildVariables(project)
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }

    register("beta") {
      initWith(getByName("release"))
      matchingFallbacks += listOf("release")
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      buildConfigField("String", "BUILD_DATE", "\"${releaseBuildDate}\"")
      reproducibleBuildVariables(project)
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }

    // Build Type for Team builds.
    // App ID = "world.bitkey.team"
    register("team") {
      initWith(getByName("customer"))
      applicationIdSuffix = ".team"
      reproducibleBuildVariables(project)
    }

    register("emergency") {
      initWith(getByName("customer"))
      reproducibleBuildVariables(project)
    }

    // Disable default "release" build type, we use "customer" and "team".
    androidComponents {
      beforeVariants { variant ->
        if (variant.buildType == "release") {
          variant.enable = false
        }
      }
    }
  }
}

/** go/opensource */
licensee {
  allow("Apache-1.1")
  allow("Apache-2.0")
  allow("BSD-2-Clause")
  allow("BSD-3-Clause")
  allow("BSL-1.0")
  allow("EPL-1.0")
  allow("ISC")
  allow("MIT")
  allow("OpenSSL")
  allowUrl("https://developer.android.com/studio/terms.html")
  allowUrl("https://github.com/bitcoindevkit/bdk/blob/master/LICENSE-APACHE")
  allowUrl("http://www.bouncycastle.org/licence.html")
  allowUrl("https://www.zetetic.net/sqlcipher/license/")
  allowUrl("https://golang.org/LICENSE")
  allowUrl("https://opensource.org/licenses/MIT")
  allowUrl("https://developer.android.com/guide/playcore/license")
}

dependencies {
  implementation(libs.android.activity.ktx)
  implementation(libs.android.appcompat)
  implementation(libs.android.compose.ui.activity)
  implementation(libs.android.core.ktx)
  implementation(libs.android.lifecycle.process)
  implementation(libs.kmp.kermit)
  implementation(libs.kmp.molecule.runtime)
  implementation(projects.android.uiAppPublic)
  implementation(projects.android.uiCorePublic)
  implementation(projects.shared.appComponentImpl)
  implementation(projects.libs.bugsnagPublic)
  implementation(libs.android.lifecycle.common)
  implementation(libs.android.firebase.messaging)
  implementation(libs.kmp.kotlin.coroutines)
  implementation(libs.android.splashscreen)

  debugImplementation(libs.android.leakcanary)

  testImplementation(libs.jvm.test.junit)

  androidTestImplementation(libs.android.test.runner)
  androidTestImplementation(libs.android.test.junit)
}

customDependencyLocking {
  android.buildTypes.configureEach {
    val buildTypeName = name

    configurations.configureEach {
      ifMatches {
        nameIs("${buildTypeName}WearBundling", "${buildTypeName}ReverseMetadataValues")
      } then {
        dependencyLockingGroup = commonDependencyLockingGroups.buildClasspath
      }
    }
  }
}
