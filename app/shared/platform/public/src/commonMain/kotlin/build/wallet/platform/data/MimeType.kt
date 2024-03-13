package build.wallet.platform.data

// TODO: convert to a value class when supported in ObjC - https://youtrack.jetbrains.com/issue/KT-32352
data class MimeType(
  val name: String,
) {
  companion object {
    val GOOGLE_DRIVE_FOLDER = MimeType(name = "application/vnd.google-apps.folder")
    val JSON = MimeType("application/json")
    val PDF = MimeType("application/pdf")
    val PROTOBUF = MimeType("application/x-protobuf")
    val TEXT_PLAIN = MimeType("text/plain")
  }

  /**
   * File extension for the given [MimeType], if applicable.
   */
  val ext: String?
    // Implemented as an extension instead of a property to make it easier to pass the MimeType from
    // raw type name.
    get() = when (name) {
      JSON.name -> "json"
      PDF.name -> "pdf"
      PROTOBUF.name -> "proto"
      TEXT_PLAIN.name -> "txt"
      else -> null
    }
}
