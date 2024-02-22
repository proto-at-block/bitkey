package build.wallet.platform.data

// TODO: convert to a value class when supported in ObjC - https://youtrack.jetbrains.com/issue/KT-32352
data class MimeType(val name: String) {
  companion object {
    val GOOGLE_DRIVE_FOLDER = MimeType("application/vnd.google-apps.folder")
    val JSON = MimeType("application/json")
    val PDF = MimeType("application/pdf")
    val PROTOBUF = MimeType("application/x-protobuf")
    val TEXT_PLAIN = MimeType("text/plain")
  }
}
