
# keep model data classes
-keep class build.wallet.statemachine.** { *; }
-keep class build.wallet.rust.** { *; }

# Needed for Google Drive access
-keep class * extends com.google.api.client.json.GenericJson { *; }
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# keep bdk classes
-keep class com.sun.jna.** { *; }
-keep interface build.wallet.nfc.transaction.NfcTransaction { *; }
-keep class org.bitcoindevkit.** { *; }

# These should hypothetically be kept already by rules added by the serialization lib, but some models were still failing
-keep @kotlinx.serialization.Serializable class *
-keep class kotlin.Unit {*;}

# R8 kept trying to optimize this out.
-keep class bitkey.verification.TxVerificationDaoImpl { *; }

# These had to be added when jna was added to the keep list. When R8 evaluates it's input, it will
# give an error if it comes across existing missing classes. If you aren't missing dependencies, this
# is usually expected and we can tell R8 not to warn for them
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window

# These rules preserve line numbers to assist in stack trace interpreting later while keeping file names obfuscated
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Needed for Emergency Exit Kit PDF generation (by virtue of com.tom_roush.pdfbox dependency)
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
