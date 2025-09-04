/** Guarantee stable project accessor name instead of deriving from directory name. */
rootProject.name = "bitkey"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  plugins {
    id("build.wallet")
  }

  includeBuild("gradle/build-logic")
  includeBuild("gradle/dependency-locking")
}

buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  // Enable back when KMP issue is resolved: https://youtrack.jetbrains.com/issue/KT-51379.
  // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.github.burrunan.s3-build-cache") version "1.8.4"
}

val isCi = System.getenv().containsKey("CI")

buildCache {
  local {
    isEnabled = !isCi
  }
  remote<com.github.burrunan.s3cache.AwsS3BuildCache> {
    region = "us-west-2"
    bucket = "000000000000-bitkey-gha-build-cache"
    prefix = "gradle/"

    isEnabled = isCi
    isPush = true
    lookupDefaultAwsCredentials = true
  }
}

/**
 * Transforms hierarchical module paths into unique Gradle project names while preserving directory structure.
 *
 * Example: module(":domain:wallet:impl") creates:
 * - Gradle project: ":domain:wallet-impl" (unique name for artifacts)
 * - Directory: /domain/wallet/impl/ (original structure preserved)
 *
 * This solves Gradle's limitation where modules with same names (e.g., multiple ":impl" modules)
 * would conflict during artifact generation and Maven publishing.
 */
fun module(name: String) {
  val nameParts = name.split(":").filter { it.isNotBlank() }
  val projectName =
    if (nameParts.size > 1) {
      val outerName = nameParts.first()
      val innerName = nameParts.drop(1).joinToString("-")
      ":$outerName:$innerName"
    } else {
      name
    }

  include(projectName)
  project(projectName).projectDir =
    nameParts.fold(rootDir) { acc, part ->
      acc.resolve(part)
    }
}

module(":android:app")
module(":android:kotest-paparazzi:public")
module(":android:ui:app:public")
module(":android:ui:core:public")
module(":domain:account:fake")
module(":domain:account:impl")
module(":domain:account:public")
module(":domain:analytics:fake")
module(":domain:analytics:impl")
module(":domain:analytics:public")
module(":domain:auth:fake")
module(":domain:auth:impl")
module(":domain:auth:public")
module(":domain:availability:fake")
module(":domain:availability:impl")
module(":domain:availability:public")
module(":domain:bitkey-primitives:fake")
module(":domain:bitkey-primitives:public")
module(":domain:bootstrap:fake")
module(":domain:bootstrap:impl")
module(":domain:bootstrap:public")
module(":domain:cloud-backup:fake")
module(":domain:cloud-backup:impl")
module(":domain:cloud-backup:public")
module(":domain:coachmark:fake")
module(":domain:coachmark:impl")
module(":domain:coachmark:public")
module(":domain:data-state-machine:fake")
module(":domain:data-state-machine:impl")
module(":domain:data-state-machine:public")
module(":domain:database:public")
module(":domain:debug:fake")
module(":domain:debug:impl")
module(":domain:debug:public")
module(":domain:emergency-exit-kit:fake")
module(":domain:emergency-exit-kit:impl")
module(":domain:emergency-exit-kit:public")
module(":domain:f8e-client:fake")
module(":domain:f8e-client:impl")
module(":domain:f8e-client:public")
module(":domain:feature-flag:fake")
module(":domain:feature-flag:impl")
module(":domain:feature-flag:public")
module(":domain:hardware:fake")
module(":domain:hardware:impl")
module(":domain:hardware:public")
module(":domain:home:fake")
module(":domain:home:impl")
module(":domain:home:public")
module(":domain:in-app-security:fake")
module(":domain:in-app-security:impl")
module(":domain:in-app-security:public")
module(":domain:inheritance:fake")
module(":domain:inheritance:impl")
module(":domain:inheritance:public")
module(":domain:metrics:fake")
module(":domain:metrics:impl")
module(":domain:metrics:public")
module(":domain:mobile-pay:fake")
module(":domain:mobile-pay:impl")
module(":domain:mobile-pay:public")
module(":domain:notifications:fake")
module(":domain:notifications:impl")
module(":domain:notifications:public")
module(":domain:onboarding:fake")
module(":domain:onboarding:impl")
module(":domain:onboarding:public")
module(":domain:partnerships:fake")
module(":domain:partnerships:impl")
module(":domain:partnerships:public")
module(":domain:privileged-actions:fake")
module(":domain:privileged-actions:impl")
module(":domain:privileged-actions:public")
module(":domain:recovery:fake")
module(":domain:recovery:impl")
module(":domain:recovery:public")
module(":domain:relationships:fake")
module(":domain:relationships:impl")
module(":domain:relationships:public")
module(":domain:worker:fake")
module(":domain:worker:impl")
module(":domain:worker:public")
module(":domain:security-center:fake")
module(":domain:security-center:impl")
module(":domain:security-center:public")
module(":domain:support:fake")
module(":domain:support:impl")
module(":domain:support:public")
module(":domain:tx-verification:public")
module(":domain:tx-verification:impl")
module(":domain:tx-verification:fake")
module(":domain:wallet:fake")
module(":domain:wallet:impl")
module(":domain:wallet:public")
module(":domain:wallet:testing")
module(":gradle:di-codegen")
module(":gradle:ksp-util")
module(":gradle:snapshot-generator")
module(":gradle:test-code-eliminator")
module(":libs:amount:impl")
module(":libs:amount:public")
module(":libs:bdk-bindings:fake")
module(":libs:bdk-bindings:impl")
module(":libs:bdk-bindings:public")
module(":libs:bitcoin-primitives:fake")
module(":libs:bitcoin-primitives:public")
module(":libs:bugsnag:impl")
module(":libs:bugsnag:impl")
module(":libs:bugsnag:public")
module(":libs:bugsnag:public")
module(":libs:cloud-store:fake")
module(":libs:cloud-store:impl")
module(":libs:cloud-store:public")
module(":libs:compose-runtime:public")
module(":libs:contact-method:fake")
module(":libs:contact-method:impl")
module(":libs:contact-method:public")
module(":libs:datadog:fake")
module(":libs:datadog:fake")
module(":libs:datadog:impl")
module(":libs:datadog:impl")
module(":libs:datadog:public")
module(":libs:datadog:public")
module(":libs:dev:treasury:public")
module(":libs:di-scopes:public")
module(":libs:encryption:fake")
module(":libs:encryption:impl")
module(":libs:encryption:public")
module(":libs:frost:fake")
module(":libs:frost:impl")
module(":libs:frost:public")
module(":libs:google-sign-in:impl")
module(":libs:google-sign-in:public")
module(":libs:grants:fake")
module(":libs:grants:public")
module(":libs:key-value-store:fake")
module(":libs:key-value-store:impl")
module(":libs:key-value-store:public")
module(":libs:ktor-client:fake")
module(":libs:ktor-client:public")
module(":libs:logging:impl")
module(":libs:logging:public")
module(":libs:logging:testing")
module(":libs:memfault:fake")
module(":libs:memfault:impl")
module(":libs:memfault:public")
module(":libs:money:fake")
module(":libs:money:impl")
module(":libs:money:public")
module(":libs:money:testing")
module(":libs:platform:fake")
module(":libs:platform:impl")
module(":libs:platform:public")
module(":libs:queue-processor:fake")
module(":libs:queue-processor:impl")
module(":libs:queue-processor:public")
module(":libs:queue-processor:testing")
module(":libs:secure-enclave:fake")
module(":libs:secure-enclave:impl")
module(":libs:secure-enclave:public")
module(":libs:sqldelight:fake")
module(":libs:sqldelight:impl")
module(":libs:sqldelight:public")
module(":libs:sqldelight:testing")
module(":libs:state-machine:fake")
module(":libs:state-machine:public")
module(":libs:state-machine:testing")
module(":libs:stdlib:public")
module(":libs:testing:public")
module(":libs:time:fake")
module(":libs:time:impl")
module(":libs:time:public")
module(":rust:core-ffi")
module(":rust:firmware-ffi")
module(":sample:android-app")
module(":sample:shared")
module(":shared:app-component:impl")
module(":shared:app-component:public")
module(":shared:balance-utils:impl")
module(":shared:balance-utils:public")
module(":shared:integration-testing:public")
module(":shared:price-chart:fake")
module(":shared:price-chart:impl")
module(":shared:price-chart:public")
module(":shared:xc-framework")
module(":ui:compose-app-controller:impl")
module(":ui:compose-app-controller:public")
module(":ui:features:public")
module(":ui:features:testing")
module(":ui:framework:impl")
module(":ui:framework:public")
module(":ui:framework:testing")
module(":ui:router:public")
module(":ui:snapshot-generator-api:public")
