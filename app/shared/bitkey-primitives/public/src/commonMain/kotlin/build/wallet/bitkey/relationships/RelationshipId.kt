package build.wallet.bitkey.relationships

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class RelationshipId(val value: String)
