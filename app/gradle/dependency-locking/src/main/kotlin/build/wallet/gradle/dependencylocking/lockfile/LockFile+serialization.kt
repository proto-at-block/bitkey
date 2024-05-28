package build.wallet.gradle.dependencylocking.lockfile

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import build.wallet.gradle.dependencylocking.configuration.LockableVariant
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File

internal fun LockFile.serializeTo(directory: File) {
  directory.deleteRecursively()

  directory.mkdirs()

  val jsons = serialize()

  jsons.forEach { (fileName, content) ->
    directory.resolve(fileName).writeText(content)
  }
}

internal fun LockFile.serialize(): Map<String, String> =
  lockedModules.associate {
    "${it.moduleIdentifier.fullIdentifier}.json" to it.serialize()
  }

private fun LockFile.LockedModule.serialize(): String {
  val objects = toObjects()

  val json = JsonOutput.toJson(objects)

  return JsonOutput.prettyPrint(json) + "\n"
}

private fun LockFile.LockedModule.toObjects(): Map<String, Any> =
  mapOf(
    "module-identifier" to moduleIdentifier.fullIdentifier,
    "components" to components.sortedBy { it.coordinates.componentVersion.value }.map { it.toObjects() }
  )

private fun LockFile.Component.toObjects(): Map<String, Any?> =
  mapOf(
    "version" to coordinates.componentVersion.value,
    "variants" to variants.sortedBy { it.id }.map { it.toObjects() },
    "dependency-locking-groups" to dependencyLockingGroups.map { it.name }.sorted()
  )

private fun LockFile.Variant.toObjects(): Map<String, Any?> =
  mapOf(
    "artifacts" to artifacts.sortedBy { it.name.value }.associate { it.name.value to it.hash.value }
  )

internal fun LockFile.Companion.deserialize(directory: File): LockFile =
  directory.listLockFileSegments()
    .map { LockFile.LockedModule.deserialize(it) }
    .let(::LockFile)

internal fun LockFile.LockedModule.Companion.deserialize(file: File): LockFile.LockedModule =
  deserialize(file.readText())

@Suppress("UNCHECKED_CAST")
internal fun LockFile.LockedModule.Companion.deserialize(json: String): LockFile.LockedModule {
  val objects = JsonSlurper().parse(json.byteInputStream()) as Map<String, Any>

  return lockedModuleFromObjects(objects)
}

@Suppress("UNCHECKED_CAST")
private fun lockedModuleFromObjects(objects: Map<String, Any>): LockFile.LockedModule {
  val moduleIdentifier = ModuleIdentifier(objects["module-identifier"] as String)

  return LockFile.LockedModule(
    moduleIdentifier = moduleIdentifier,
    components = (objects["components"] as List<*>)
      .map { componentFromObjects(it as Map<String, Any?>, moduleIdentifier) }
  )
}

@Suppress("UNCHECKED_CAST")
private fun componentFromObjects(
  objects: Map<String, Any?>,
  moduleIdentifier: ModuleIdentifier,
): LockFile.Component {
  val componentVersion = ComponentVersion(objects["version"] as String)

  return LockFile.Component(
    coordinates = LockableVariant.Coordinates(moduleIdentifier, componentVersion),
    variants = (objects["variants"] as List<*>).map { variantFromObjects(it as Map<String, Any?>) },
    dependencyLockingGroups = (objects["dependency-locking-groups"] as List<*>).map {
      DependencyLockingGroup.Known(it as String)
    }
  )
}

@Suppress("UNCHECKED_CAST")
private fun variantFromObjects(objects: Map<String, Any?>): LockFile.Variant =
  LockFile.Variant(
    artifacts = (objects["artifacts"] as Map<String, Any?>).map {
      LockFile.Artifact(ArtifactName(it.key), ArtifactHash(it.value as String))
    }
  )

internal fun File.listLockFileSegments(): List<File> =
  listFiles()?.filter { it.isFile && !it.isHidden } ?: emptyList()
