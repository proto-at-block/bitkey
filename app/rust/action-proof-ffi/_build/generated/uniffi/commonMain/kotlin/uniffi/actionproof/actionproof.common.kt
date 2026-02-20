

@file:Suppress("RemoveRedundantBackticks")

package uniffi.actionproof

// Common helper code.
//
// Ideally this would live in a separate .kt file where it can be unittested etc
// in isolation, and perhaps even published as a re-useable package.
//
// However, it's important that the details of how this helper code works (e.g. the
// way that different builtin types are passed across the FFI) exactly match what's
// expected by the Rust code on the other side of the interface. In practice right
// now that means coming from the exact some version of `uniffi` that was used to
// compile the Rust component. The easiest way to ensure this is to bundle the Kotlin
// helpers directly inline like we're doing here.

public class InternalException(message: String) : kotlin.Exception(message)

// Public interface members begin here.


// Interface implemented by anything that can contain an object reference.
//
// Such types expose a `destroy()` method that must be called to cleanly
// dispose of the contained objects. Failure to call this method may result
// in memory leaks.
//
// The easiest way to ensure this method is called is to use the `.use`
// helper method to execute a block and destroy the object at the end.
@OptIn(ExperimentalStdlibApi::class)
public interface Disposable : AutoCloseable {
    public fun destroy()
    override fun close(): Unit = destroy()
    public companion object {
        internal fun destroy(vararg args: Any?) {
            for (arg in args) {
                when (arg) {
                    is Disposable -> arg.destroy()
                    is ArrayList<*> -> {
                        for (idx in arg.indices) {
                            val element = arg[idx]
                            if (element is Disposable) {
                                element.destroy()
                            }
                        }
                    }
                    is Map<*, *> -> {
                        for (element in arg.values) {
                            if (element is Disposable) {
                                element.destroy()
                            }
                        }
                    }
                    is Array<*> -> {
                        for (element in arg) {
                            if (element is Disposable) {
                                element.destroy()
                            }
                        }
                    }
                    is Iterable<*> -> {
                        for (element in arg) {
                            if (element is Disposable) {
                                element.destroy()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(kotlin.contracts.ExperimentalContracts::class)
public inline fun <T : Disposable?, R> T.use(block: (T) -> R): R {
    kotlin.contracts.contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block(this)
    } finally {
        try {
            // N.B. our implementation is on the nullable type `Disposable?`.
            this?.destroy()
        } catch (e: Throwable) {
            // swallow
        }
    }
}

/** Used to instantiate an interface without an actual pointer, for fakes in tests, mostly. */
public object NoPointer







/**
 * Key-value pair for context bindings.
 */

public data class ContextBindingPair (
    var `key`: kotlin.String, 
    var `value`: kotlin.String
) {
    public companion object
}




/**
 * Action types for privileged operations.
 */


public enum class Action {
    
    ADD,
    REMOVE,
    SET,
    DISABLE,
    ACCEPT;
    public companion object
}







/**
 * Unified error type for all action-proof operations.
 */
public sealed class ActionProofException(message: String): kotlin.Exception(message) {
    
    /**
     * Value exceeds maximum length (128 bytes UTF-8).
     */
    public class TooLong(message: String) : ActionProofException(message)
    
    /**
     * Value is empty when a value is required.
     */
    public class Empty(message: String) : ActionProofException(message)
    
    /**
     * Value contains control characters (0x00-0x1E, 0x7F).
     */
    public class ControlCharacter(message: String) : ActionProofException(message)
    
    /**
     * Value contains unit separator (0x1F) which is reserved.
     */
    public class ContainsDelimiter(message: String) : ActionProofException(message)
    
    /**
     * Value contains dangerous Unicode (zero-width, directional, BOM).
     */
    public class DangerousCharacter(message: String) : ActionProofException(message)
    
    /**
     * Value mixes Latin with confusable scripts (homoglyph risk).
     */
    public class MixedScripts(message: String) : ActionProofException(message)
    
    /**
     * Binding key is empty.
     */
    public class EmptyBindingKey(message: String) : ActionProofException(message)
    
    /**
     * Binding key contains reserved characters.
     */
    public class InvalidBindingKey(message: String) : ActionProofException(message)
    
    /**
     * Binding value contains reserved characters.
     */
    public class InvalidBindingValue(message: String) : ActionProofException(message)
    
    /**
     * Duplicate binding key provided.
     */
    public class DuplicateBindingKey(message: String) : ActionProofException(message)
    
    /**
     * Action is not valid for the specified field.
     */
    public class InvalidActionForField(message: String) : ActionProofException(message)
    
    /**
     * Bindings not sorted.
     */
    public class BindingsNotSorted(message: String) : ActionProofException(message)
    
}




/**
 * Context binding types for action proofs.
 */


public enum class ContextBinding {
    
    TOKEN_BINDING,
    ENTITY_ID,
    NONCE;
    public companion object
}






/**
 * Fields that can be modified by actions.
 */


public enum class Field {
    
    SPEND_WITHOUT_HARDWARE,
    VERIFICATION_THRESHOLD,
    RECOVERY_EMAIL,
    RECOVERY_PHONE,
    RECOVERY_PUSH_NOTIFICATIONS,
    RECOVERY_CONTACTS,
    BENEFICIARIES,
    RECOVERY_CONTACTS_INVITE,
    BENEFICIARIES_INVITE;
    public companion object
}









/**
 * Builds a canonical action payload.
 * Bindings are automatically sorted alphabetically by key.
 * Returns binary payload as Vec<u8>.
 */
@Throws(ActionProofException::class)
public expect fun `buildPayload`(`action`: Action, `field`: Field, `value`: kotlin.String?, `current`: kotlin.String?, `bindings`: List<ContextBindingPair>): List<kotlin.UByte>

/**
 * Computes token binding as SHA256("ActionProof tb v1" || JWT) hex-encoded.
 * Returns 64-character lowercase hex string.
 */
public expect fun `computeTokenBinding`(`jwt`: kotlin.String): kotlin.String

/**
 * Returns the string key for a context binding (e.g., TokenBinding -> "tb").
 */
public expect fun `contextBindingKey`(`binding`: ContextBinding): kotlin.String

/**
 * Validates a value against all security rules.
 * Throws ActionProofError on validation failure.
 */
@Throws(ActionProofException::class)
public expect fun `validateValue`(`value`: kotlin.String)

