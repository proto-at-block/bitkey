package build.wallet.gradle.logic.gradle

import org.gradle.api.Project

private const val SNAPSHOT_BUILD_PARAM = "snapshotBuild"

/**
 * Checks if the build parameter is set to make this build a snapshot build.
 */
fun Project.isSnapshot(): Boolean {
    return project.hasProperty(SNAPSHOT_BUILD_PARAM)
}

/**
 * Require a snapshot build number parameter and return it.
 */
fun Project.snapshotVersion(): Int {
    require(isSnapshot()) { "Snapshot build ID retrieved in non snapshot build. Check `isSnapshot()` first." }
    return project.property(SNAPSHOT_BUILD_PARAM)!!.toString().toInt()
}
