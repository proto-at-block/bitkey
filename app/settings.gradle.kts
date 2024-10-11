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
  id("com.gradle.enterprise") version "3.16.2"
  id("com.github.burrunan.s3-build-cache") version "1.8.1"
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
module(":android:debug:impl")
module(":android:debug:public")
module(":android:kotest-paparazzi:public")
module(":android:nfc:impl")
module(":android:nfc:public")
module(":android:platform:impl")
module(":android:ui:app:impl")
module(":android:ui:app:public")
module(":android:ui:core:public")
module(":rust:core-ffi")
module(":rust:firmware-ffi")
module(":sample:android-app")
module(":sample:shared")
module(":shared:account:fake")
module(":shared:account:impl")
module(":shared:account:public")
module(":shared:amount:fake")
module(":shared:amount:impl")
module(":shared:amount:public")
module(":shared:analytics:fake")
module(":shared:analytics:impl")
module(":shared:analytics:public")
module(":shared:app-component:impl")
module(":shared:app-component:public")
module(":shared:auth:fake")
module(":shared:auth:impl")
module(":shared:auth:public")
module(":shared:availability:fake")
module(":shared:availability:impl")
module(":shared:availability:public")
module(":shared:bdk-bindings:fake")
module(":shared:bdk-bindings:impl")
module(":shared:bdk-bindings:public")
module(":shared:bitcoin-primitives:fake")
module(":shared:bitcoin-primitives:public")
module(":shared:bitcoin:fake")
module(":shared:bitcoin:impl")
module(":shared:bitcoin:public")
module(":shared:bitcoin:testing")
module(":shared:bitkey-primitives:fake")
module(":shared:bitkey-primitives:public")
module(":shared:bootstrap:fake")
module(":shared:bootstrap:impl")
module(":shared:bootstrap:public")
module(":shared:bugsnag:impl")
module(":shared:bugsnag:public")
module(":shared:cloud-backup:fake")
module(":shared:cloud-backup:impl")
module(":shared:cloud-backup:public")
module(":shared:cloud-store:fake")
module(":shared:cloud-store:impl")
module(":shared:cloud-store:public")
module(":shared:coachmark:fake")
module(":shared:coachmark:impl")
module(":shared:coachmark:public")
module(":shared:compose-runtime:public")
module(":shared:coroutines:public")
module(":shared:coroutines:testing")
module(":shared:database:public")
module(":shared:datadog:fake")
module(":shared:datadog:impl")
module(":shared:datadog:public")
module(":shared:db-result:public")
module(":shared:debug:fake")
module(":shared:debug:impl")
module(":shared:debug:public")
module(":shared:dev:treasury:public")
module(":shared:email:fake")
module(":shared:email:impl")
module(":shared:email:public")
module(":shared:emergency-access-kit:fake")
module(":shared:emergency-access-kit:impl")
module(":shared:emergency-access-kit:public")
module(":shared:encryption:fake")
module(":shared:encryption:impl")
module(":shared:encryption:public")
module(":shared:f8e-client:fake")
module(":shared:f8e-client:impl")
module(":shared:f8e-client:public")
module(":shared:f8e:fake")
module(":shared:f8e:impl")
module(":shared:f8e:public")
module(":shared:feature-flag:fake")
module(":shared:feature-flag:impl")
module(":shared:feature-flag:public")
module(":shared:firmware:fake")
module(":shared:firmware:impl")
module(":shared:firmware:public")
module(":shared:frost:impl")
module(":shared:frost:public")
module(":shared:fwup:fake")
module(":shared:fwup:impl")
module(":shared:fwup:public")
module(":shared:google-sign-in:impl")
module(":shared:google-sign-in:public")
module(":shared:home:fake")
module(":shared:home:impl")
module(":shared:home:public")
module(":shared:in-app-security:fake")
module(":shared:in-app-security:impl")
module(":shared:in-app-security:public")
module(":shared:inheritance:fake")
module(":shared:inheritance:impl")
module(":shared:inheritance:public")
module(":shared:integration-testing:public")
module(":shared:key-value-store:fake")
module(":shared:key-value-store:impl")
module(":shared:key-value-store:public")
module(":shared:keybox:fake")
module(":shared:keybox:impl")
module(":shared:keybox:public")
module(":shared:kotest:public")
module(":shared:ktor-client:fake")
module(":shared:ktor-client:public")
module(":shared:ldk-bindings:fake")
module(":shared:ldk-bindings:public")
module(":shared:logging:impl")
module(":shared:logging:public")
module(":shared:memfault:fake")
module(":shared:memfault:impl")
module(":shared:memfault:public")
module(":shared:mobile-pay:fake")
module(":shared:mobile-pay:impl")
module(":shared:mobile-pay:public")
module(":shared:money:fake")
module(":shared:money:impl")
module(":shared:money:public")
module(":shared:money:testing")
module(":shared:nfc:fake")
module(":shared:nfc:impl")
module(":shared:nfc:public")
module(":shared:notifications:fake")
module(":shared:notifications:impl")
module(":shared:notifications:public")
module(":shared:onboarding:fake")
module(":shared:onboarding:impl")
module(":shared:onboarding:public")
module(":shared:partnerships:fake")
module(":shared:partnerships:impl")
module(":shared:partnerships:public")
module(":shared:phone-number:fake")
module(":shared:phone-number:impl")
module(":shared:phone-number:public")
module(":shared:platform:fake")
module(":shared:platform:impl")
module(":shared:platform:public")
module(":shared:price-chart:fake")
module(":shared:price-chart:impl")
module(":shared:price-chart:public")
module(":shared:queue-processor:fake")
module(":shared:queue-processor:impl")
module(":shared:queue-processor:public")
module(":shared:queue-processor:testing")
module(":shared:recovery:fake")
module(":shared:recovery:impl")
module(":shared:recovery:public")
module(":shared:relationships:fake")
module(":shared:relationships:impl")
module(":shared:relationships:public")
module(":shared:result:public")
module(":shared:router:public")
module(":shared:serialization:public")
module(":shared:sqldelight:fake")
module(":shared:sqldelight:impl")
module(":shared:sqldelight:public")
module(":shared:sqldelight:testing")
module(":shared:state-machine:data:fake")
module(":shared:state-machine:data:impl")
module(":shared:state-machine:data:public")
module(":shared:state-machine:framework:fake")
module(":shared:state-machine:framework:public")
module(":shared:state-machine:framework:testing")
module(":shared:state-machine:ui:public")
module(":shared:state-machine:ui:testing")
module(":shared:stdlib:public")
module(":shared:support:fake")
module(":shared:support:impl")
module(":shared:support:public")
module(":shared:testing:public")
module(":shared:time:fake")
module(":shared:time:impl")
module(":shared:time:public")
module(":shared:ui:core:impl")
module(":shared:ui:core:public")
module(":shared:ui:core:testing")
module(":shared:worker:fake")
module(":shared:worker:impl")
module(":shared:worker:public")
module(":shared:xc-framework")
module(":gradle:formbodymodel-generator")
