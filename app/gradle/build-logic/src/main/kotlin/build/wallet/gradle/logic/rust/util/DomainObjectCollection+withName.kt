package build.wallet.gradle.logic.rust.util

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Task

fun <T : Named> DomainObjectCollection<T>.withName(name: String): DomainObjectCollection<T> =
  matching { it.name == name }

@JvmName("withTaskNamed")
fun <T : Task> DomainObjectCollection<T>.withName(name: String): DomainObjectCollection<T> =
  matching { it.name == name }
