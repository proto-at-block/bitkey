/** Guarantee stable project accessor name instead of deriving from directory name. */
rootProject.name = "build-logic"

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  versionCatalogs {
    create("libs") {
      from(files("../libs.versions.toml"))
    }
  }
}

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  includeBuild("../dependency-locking")
}

includeBuild("../dependency-locking")
