package build.wallet.statemachine.core

/**
 * High-level category for the flow a user is using when an error occurs.
 */
interface AppSegment {
  val id: String
}

/**
 * Creates a child segment of the current
 */
fun AppSegment.childSegment(name: String) =
  object : AppSegment {
    override val id: String = "${this@childSegment.id}.$name"
  }
