/** Guarantee stable project accessor name instead of deriving from directory name. */
rootProject.name = "dependency-locking"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }

  versionCatalogs {
    create("libs") {
      from(files("../libs.versions.toml"))
    }
  }
}
