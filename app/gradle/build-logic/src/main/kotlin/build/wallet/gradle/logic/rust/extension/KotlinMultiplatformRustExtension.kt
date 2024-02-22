package build.wallet.gradle.logic.rust.extension

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class KotlinMultiplatformRustExtension
  @Inject
  constructor(objects: ObjectFactory) {
    val packageName: Property<String> = objects.property(String::class.java)

    val cargoPath: RegularFileProperty = objects.fileProperty()

    val rustupPath: RegularFileProperty = objects.fileProperty()

    /**
     * Must include all files that should cause a rebuild of the rust library.
     * (These files are set as an input of the [CompileRustTask].)
     */
    val rustProjectFiles: ConfigurableFileCollection = objects.fileCollection()

    val libraryName: Property<String> = objects.property(String::class.java)

    val android: AndroidRustTargetConfiguration by lazy {
      objects.newInstance(AndroidRustTargetConfiguration::class.java, this)
    }

    val jvm: JvmRustTargetConfiguration by lazy {
      objects.newInstance(JvmRustTargetConfiguration::class.java, this)
    }

    fun android(action: Action<AndroidRustTargetConfiguration>) {
      action.execute(android)
    }

    fun jvm(action: Action<JvmRustTargetConfiguration>) {
      action.execute(jvm)
    }
  }
