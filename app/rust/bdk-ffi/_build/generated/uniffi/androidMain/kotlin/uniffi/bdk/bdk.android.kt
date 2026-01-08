

@file:Suppress("RemoveRedundantBackticks")

package uniffi.bdk

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

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext


internal typealias Pointer = com.sun.jna.Pointer
internal val NullPointer: Pointer? = com.sun.jna.Pointer.NULL
internal fun Pointer.toLong(): Long = Pointer.nativeValue(this)
internal fun kotlin.Long.toPointer() = com.sun.jna.Pointer(this)


@kotlin.jvm.JvmInline
public value class ByteBuffer(private val inner: java.nio.ByteBuffer) {
    init {
        inner.order(java.nio.ByteOrder.BIG_ENDIAN)
    }

    public fun internal(): java.nio.ByteBuffer = inner

    public fun limit(): Int = inner.limit()

    public fun position(): Int = inner.position()

    public fun hasRemaining(): Boolean = inner.hasRemaining()

    public fun get(): Byte = inner.get()

    public fun get(bytesToRead: Int): ByteArray = ByteArray(bytesToRead).apply(inner::get)

    public fun getShort(): Short = inner.getShort()

    public fun getInt(): Int = inner.getInt()

    public fun getLong(): Long = inner.getLong()

    public fun getFloat(): Float = inner.getFloat()

    public fun getDouble(): Double = inner.getDouble()

    public fun put(value: Byte) {
        inner.put(value)
    }

    public fun put(src: ByteArray) {
        inner.put(src)
    }

    public fun putShort(value: Short) {
        inner.putShort(value)
    }

    public fun putInt(value: Int) {
        inner.putInt(value)
    }

    public fun putLong(value: Long) {
        inner.putLong(value)
    }

    public fun putFloat(value: Float) {
        inner.putFloat(value)
    }

    public fun putDouble(value: Double) {
        inner.putDouble(value)
    }
}
public fun RustBuffer.setValue(array: RustBufferByValue) {
    this.data = array.data
    this.len = array.len
    this.capacity = array.capacity
}

internal object RustBufferHelper {
    internal fun allocValue(size: ULong = 0UL): RustBufferByValue = uniffiRustCall { status ->
        // Note: need to convert the size to a `Long` value to make this work with JVM.
        UniffiLib.ffi_bdk_rustbuffer_alloc(size.toLong(), status)
    }.also {
        if(it.data == null) {
            throw RuntimeException("RustBuffer.alloc() returned null data pointer (size=${size})")
        }
    }

    internal fun free(buf: RustBufferByValue) = uniffiRustCall { status ->
        UniffiLib.ffi_bdk_rustbuffer_free(buf, status)
    }
}

@Structure.FieldOrder("capacity", "len", "data")
public open class RustBufferStruct(
    // Note: `capacity` and `len` are actually `ULong` values, but JVM only supports signed values.
    // When dealing with these fields, make sure to call `toULong()`.
    @JvmField public var capacity: Long,
    @JvmField public var len: Long,
    @JvmField public var data: Pointer?,
) : Structure() {
    public constructor(): this(0.toLong(), 0.toLong(), null)

    public class ByValue(
        capacity: Long,
        len: Long,
        data: Pointer?,
    ): RustBuffer(capacity, len, data), Structure.ByValue {
        public constructor(): this(0.toLong(), 0.toLong(), null)
    }

    /**
     * The equivalent of the `*mut RustBuffer` type.
     * Required for callbacks taking in an out pointer.
     *
     * Size is the sum of all values in the struct.
     */
    public class ByReference(
        capacity: Long,
        len: Long,
        data: Pointer?,
    ): RustBuffer(capacity, len, data), Structure.ByReference {
        public constructor(): this(0.toLong(), 0.toLong(), null)
    }
}

public typealias RustBuffer = RustBufferStruct
public typealias RustBufferByValue = RustBufferStruct.ByValue

internal fun RustBuffer.asByteBuffer(): ByteBuffer? {
    require(this.len <= Int.MAX_VALUE) {
        val length = this.len
        "cannot handle RustBuffer longer than Int.MAX_VALUE bytes: length is $length"
    }
    return ByteBuffer(data?.getByteBuffer(0L, this.len) ?: return null)
}

internal fun RustBufferByValue.asByteBuffer(): ByteBuffer? {
    require(this.len <= Int.MAX_VALUE) {
        val length = this.len
        "cannot handle RustBuffer longer than Int.MAX_VALUE bytes: length is $length"
    }
    return ByteBuffer(data?.getByteBuffer(0L, this.len) ?: return null)
}

// This is a helper for safely passing byte references into the rust code.
// It's not actually used at the moment, because there aren't many things that you
// can take a direct pointer to in the JVM, and if we're going to copy something
// then we might as well copy it into a `RustBuffer`. But it's here for API
// completeness.

@Structure.FieldOrder("len", "data")
internal open class ForeignBytesStruct : Structure() {
    @JvmField var len: Int = 0
    @JvmField var data: Pointer? = null

    internal class ByValue : ForeignBytes(), Structure.ByValue
}
internal typealias ForeignBytes = ForeignBytesStruct
internal typealias ForeignBytesByValue = ForeignBytesStruct.ByValue

public interface FfiConverter<KotlinType, FfiType> {
    // Convert an FFI type to a Kotlin type
    public fun lift(value: FfiType): KotlinType

    // Convert an Kotlin type to an FFI type
    public fun lower(value: KotlinType): FfiType

    // Read a Kotlin type from a `ByteBuffer`
    public fun read(buf: ByteBuffer): KotlinType

    // Calculate bytes to allocate when creating a `RustBuffer`
    //
    // This must return at least as many bytes as the write() function will
    // write. It can return more bytes than needed, for example when writing
    // Strings we can't know the exact bytes needed until we the UTF-8
    // encoding, so we pessimistically allocate the largest size possible (3
    // bytes per codepoint).  Allocating extra bytes is not really a big deal
    // because the `RustBuffer` is short-lived.
    public fun allocationSize(value: KotlinType): ULong

    // Write a Kotlin type to a `ByteBuffer`
    public fun write(value: KotlinType, buf: ByteBuffer)

    // Lower a value into a `RustBuffer`
    //
    // This method lowers a value into a `RustBuffer` rather than the normal
    // FfiType.  It's used by the callback interface code.  Callback interface
    // returns are always serialized into a `RustBuffer` regardless of their
    // normal FFI type.
    public fun lowerIntoRustBuffer(value: KotlinType): RustBufferByValue {
        val rbuf = RustBufferHelper.allocValue(allocationSize(value))
        val bbuf = rbuf.asByteBuffer()!!
        write(value, bbuf)
        return RustBufferByValue(
            capacity = rbuf.capacity,
            len = bbuf.position().toLong(),
            data = rbuf.data,
        )
    }

    // Lift a value from a `RustBuffer`.
    //
    // This here mostly because of the symmetry with `lowerIntoRustBuffer()`.
    // It's currently only used by the `FfiConverterRustBuffer` class below.
    public fun liftFromRustBuffer(rbuf: RustBufferByValue): KotlinType {
        val byteBuf = rbuf.asByteBuffer()!!
        try {
           val item = read(byteBuf)
           if (byteBuf.hasRemaining()) {
               throw RuntimeException("junk remaining in buffer after lifting, something is very wrong!!")
           }
           return item
        } finally {
            RustBufferHelper.free(rbuf)
        }
    }
}

// FfiConverter that uses `RustBuffer` as the FfiType
public interface FfiConverterRustBuffer<KotlinType>: FfiConverter<KotlinType, RustBufferByValue> {
    override fun lift(value: RustBufferByValue): KotlinType = liftFromRustBuffer(value)
    override fun lower(value: KotlinType): RustBufferByValue = lowerIntoRustBuffer(value)
}

internal const val UNIFFI_CALL_SUCCESS = 0.toByte()
internal const val UNIFFI_CALL_ERROR = 1.toByte()
internal const val UNIFFI_CALL_UNEXPECTED_ERROR = 2.toByte()

// Default Implementations
internal fun UniffiRustCallStatus.isSuccess(): Boolean
    = code == UNIFFI_CALL_SUCCESS

internal fun UniffiRustCallStatus.isError(): Boolean
    = code == UNIFFI_CALL_ERROR

internal fun UniffiRustCallStatus.isPanic(): Boolean
    = code == UNIFFI_CALL_UNEXPECTED_ERROR

internal fun UniffiRustCallStatusByValue.isSuccess(): Boolean
    = code == UNIFFI_CALL_SUCCESS

internal fun UniffiRustCallStatusByValue.isError(): Boolean
    = code == UNIFFI_CALL_ERROR

internal fun UniffiRustCallStatusByValue.isPanic(): Boolean
    = code == UNIFFI_CALL_UNEXPECTED_ERROR

// Each top-level error class has a companion object that can lift the error from the call status's rust buffer
public interface UniffiRustCallStatusErrorHandler<E> {
    public fun lift(errorBuf: RustBufferByValue): E
}

// Helpers for calling Rust
// In practice we usually need to be synchronized to call this safely, so it doesn't
// synchronize itself

// Call a rust function that returns a Result<>.  Pass in the Error class companion that corresponds to the Err
internal inline fun <U, E: kotlin.Exception> uniffiRustCallWithError(errorHandler: UniffiRustCallStatusErrorHandler<E>, crossinline callback: (UniffiRustCallStatus) -> U): U {
    return UniffiRustCallStatusHelper.withReference() { status ->
        val returnValue = callback(status)
        uniffiCheckCallStatus(errorHandler, status)
        returnValue
    }
}

// Check `status` and throw an error if the call wasn't successful
internal fun<E: kotlin.Exception> uniffiCheckCallStatus(errorHandler: UniffiRustCallStatusErrorHandler<E>, status: UniffiRustCallStatus) {
    if (status.isSuccess()) {
        return
    } else if (status.isError()) {
        throw errorHandler.lift(status.errorBuf)
    } else if (status.isPanic()) {
        // when the rust code sees a panic, it tries to construct a rustbuffer
        // with the message.  but if that code panics, then it just sends back
        // an empty buffer.
        if (status.errorBuf.len > 0) {
            throw InternalException(FfiConverterString.lift(status.errorBuf))
        } else {
            throw InternalException("Rust panic")
        }
    } else {
        throw InternalException("Unknown rust call status: $status.code")
    }
}

// UniffiRustCallStatusErrorHandler implementation for times when we don't expect a CALL_ERROR
public object UniffiNullRustCallStatusErrorHandler: UniffiRustCallStatusErrorHandler<InternalException> {
    override fun lift(errorBuf: RustBufferByValue): InternalException {
        RustBufferHelper.free(errorBuf)
        return InternalException("Unexpected CALL_ERROR")
    }
}

// Call a rust function that returns a plain value
internal inline fun <U> uniffiRustCall(crossinline callback: (UniffiRustCallStatus) -> U): U {
    return uniffiRustCallWithError(UniffiNullRustCallStatusErrorHandler, callback)
}

internal inline fun<T> uniffiTraitInterfaceCall(
    callStatus: UniffiRustCallStatus,
    makeCall: () -> T,
    writeReturn: (T) -> Unit,
) {
    try {
        writeReturn(makeCall())
    } catch(e: kotlin.Exception) {
        callStatus.code = UNIFFI_CALL_UNEXPECTED_ERROR
        callStatus.errorBuf = FfiConverterString.lower(e.toString())
    }
}

internal inline fun<T, reified E: Throwable> uniffiTraitInterfaceCallWithError(
    callStatus: UniffiRustCallStatus,
    makeCall: () -> T,
    writeReturn: (T) -> Unit,
    lowerError: (E) -> RustBufferByValue
) {
    try {
        writeReturn(makeCall())
    } catch(e: kotlin.Exception) {
        if (e is E) {
            callStatus.code = UNIFFI_CALL_ERROR
            callStatus.errorBuf = lowerError(e)
        } else {
            callStatus.code = UNIFFI_CALL_UNEXPECTED_ERROR
            callStatus.errorBuf = FfiConverterString.lower(e.toString())
        }
    }
}

@Structure.FieldOrder("code", "errorBuf")
internal open class UniffiRustCallStatusStruct(
    @JvmField public var code: Byte,
    @JvmField public var errorBuf: RustBufferByValue,
) : Structure() {
    internal constructor(): this(0.toByte(), RustBufferByValue())

    internal class ByValue(
        code: Byte,
        errorBuf: RustBufferByValue,
    ): UniffiRustCallStatusStruct(code, errorBuf), Structure.ByValue {
        internal constructor(): this(0.toByte(), RustBufferByValue())
    }
    internal class ByReference(
        code: Byte,
        errorBuf: RustBufferByValue,
    ): UniffiRustCallStatusStruct(code, errorBuf), Structure.ByReference {
        internal constructor(): this(0.toByte(), RustBufferByValue())
    }
}

internal typealias UniffiRustCallStatus = UniffiRustCallStatusStruct.ByReference
internal typealias UniffiRustCallStatusByValue = UniffiRustCallStatusStruct.ByValue

internal object UniffiRustCallStatusHelper {
    internal fun allocValue() = UniffiRustCallStatusByValue()
    internal fun <U> withReference(block: (UniffiRustCallStatus) -> U): U {
        val status = UniffiRustCallStatus()
        return block(status)
    }
}

internal class UniffiHandleMap<T: Any> {
    private val map = java.util.concurrent.ConcurrentHashMap<Long, T>()
    private val counter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    internal val size: Int
        get() = map.size

    // Insert a new object into the handle map and get a handle for it
    internal fun insert(obj: T): Long {
        val handle = counter.getAndAdd(1)
        map[handle] = obj
        return handle
    }

    // Get an object from the handle map
    internal fun get(handle: Long): T {
        return map[handle] ?: throw InternalException("UniffiHandleMap.get: Invalid handle")
    }

    // Remove an entry from the handlemap and get the Kotlin object back
    internal fun remove(handle: Long): T {
        return map.remove(handle) ?: throw InternalException("UniffiHandleMap.remove: Invalid handle")
    }
}

internal typealias ByteByReference = com.sun.jna.ptr.ByteByReference
internal typealias DoubleByReference = com.sun.jna.ptr.DoubleByReference
internal typealias FloatByReference = com.sun.jna.ptr.FloatByReference
internal typealias IntByReference = com.sun.jna.ptr.IntByReference
internal typealias LongByReference = com.sun.jna.ptr.LongByReference
internal typealias PointerByReference = com.sun.jna.ptr.PointerByReference
internal typealias ShortByReference = com.sun.jna.ptr.ShortByReference

// Contains loading, initialization code,
// and the FFI Function declarations in a com.sun.jna.Library.

// Define FFI callback types
internal interface UniffiRustFutureContinuationCallback: com.sun.jna.Callback {
    public fun callback(`data`: Long,`pollResult`: Byte,)
}
internal interface UniffiForeignFutureFree: com.sun.jna.Callback {
    public fun callback(`handle`: Long,)
}
internal interface UniffiCallbackInterfaceFree: com.sun.jna.Callback {
    public fun callback(`handle`: Long,)
}
@Structure.FieldOrder("handle", "free")
internal open class UniffiForeignFutureStruct(
    @JvmField public var `handle`: Long,
    @JvmField public var `free`: UniffiForeignFutureFree?,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `handle` = 0.toLong(),
        
        `free` = null,
        
    )

    internal class UniffiByValue(
        `handle`: Long,
        `free`: UniffiForeignFutureFree?,
    ): UniffiForeignFuture(`handle`,`free`,), Structure.ByValue
}

internal typealias UniffiForeignFuture = UniffiForeignFutureStruct

internal fun UniffiForeignFuture.uniffiSetValue(other: UniffiForeignFuture) {
    `handle` = other.`handle`
    `free` = other.`free`
}
internal fun UniffiForeignFuture.uniffiSetValue(other: UniffiForeignFutureUniffiByValue) {
    `handle` = other.`handle`
    `free` = other.`free`
}

internal typealias UniffiForeignFutureUniffiByValue = UniffiForeignFutureStruct.UniffiByValue
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructU8Struct(
    @JvmField public var `returnValue`: Byte,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.toByte(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Byte,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructU8(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructU8 = UniffiForeignFutureStructU8Struct

internal fun UniffiForeignFutureStructU8.uniffiSetValue(other: UniffiForeignFutureStructU8) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructU8.uniffiSetValue(other: UniffiForeignFutureStructU8UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructU8UniffiByValue = UniffiForeignFutureStructU8Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteU8: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructU8UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructI8Struct(
    @JvmField public var `returnValue`: Byte,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.toByte(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Byte,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructI8(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructI8 = UniffiForeignFutureStructI8Struct

internal fun UniffiForeignFutureStructI8.uniffiSetValue(other: UniffiForeignFutureStructI8) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructI8.uniffiSetValue(other: UniffiForeignFutureStructI8UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructI8UniffiByValue = UniffiForeignFutureStructI8Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteI8: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructI8UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructU16Struct(
    @JvmField public var `returnValue`: Short,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.toShort(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Short,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructU16(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructU16 = UniffiForeignFutureStructU16Struct

internal fun UniffiForeignFutureStructU16.uniffiSetValue(other: UniffiForeignFutureStructU16) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructU16.uniffiSetValue(other: UniffiForeignFutureStructU16UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructU16UniffiByValue = UniffiForeignFutureStructU16Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteU16: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructU16UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructI16Struct(
    @JvmField public var `returnValue`: Short,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.toShort(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Short,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructI16(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructI16 = UniffiForeignFutureStructI16Struct

internal fun UniffiForeignFutureStructI16.uniffiSetValue(other: UniffiForeignFutureStructI16) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructI16.uniffiSetValue(other: UniffiForeignFutureStructI16UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructI16UniffiByValue = UniffiForeignFutureStructI16Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteI16: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructI16UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructU32Struct(
    @JvmField public var `returnValue`: Int,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0,
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Int,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructU32(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructU32 = UniffiForeignFutureStructU32Struct

internal fun UniffiForeignFutureStructU32.uniffiSetValue(other: UniffiForeignFutureStructU32) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructU32.uniffiSetValue(other: UniffiForeignFutureStructU32UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructU32UniffiByValue = UniffiForeignFutureStructU32Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteU32: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructU32UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructI32Struct(
    @JvmField public var `returnValue`: Int,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0,
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Int,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructI32(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructI32 = UniffiForeignFutureStructI32Struct

internal fun UniffiForeignFutureStructI32.uniffiSetValue(other: UniffiForeignFutureStructI32) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructI32.uniffiSetValue(other: UniffiForeignFutureStructI32UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructI32UniffiByValue = UniffiForeignFutureStructI32Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteI32: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructI32UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructU64Struct(
    @JvmField public var `returnValue`: Long,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.toLong(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Long,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructU64(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructU64 = UniffiForeignFutureStructU64Struct

internal fun UniffiForeignFutureStructU64.uniffiSetValue(other: UniffiForeignFutureStructU64) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructU64.uniffiSetValue(other: UniffiForeignFutureStructU64UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructU64UniffiByValue = UniffiForeignFutureStructU64Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteU64: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructU64UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructI64Struct(
    @JvmField public var `returnValue`: Long,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.toLong(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Long,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructI64(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructI64 = UniffiForeignFutureStructI64Struct

internal fun UniffiForeignFutureStructI64.uniffiSetValue(other: UniffiForeignFutureStructI64) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructI64.uniffiSetValue(other: UniffiForeignFutureStructI64UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructI64UniffiByValue = UniffiForeignFutureStructI64Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteI64: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructI64UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructF32Struct(
    @JvmField public var `returnValue`: Float,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.0f,
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Float,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructF32(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructF32 = UniffiForeignFutureStructF32Struct

internal fun UniffiForeignFutureStructF32.uniffiSetValue(other: UniffiForeignFutureStructF32) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructF32.uniffiSetValue(other: UniffiForeignFutureStructF32UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructF32UniffiByValue = UniffiForeignFutureStructF32Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteF32: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructF32UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructF64Struct(
    @JvmField public var `returnValue`: Double,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = 0.0,
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Double,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructF64(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructF64 = UniffiForeignFutureStructF64Struct

internal fun UniffiForeignFutureStructF64.uniffiSetValue(other: UniffiForeignFutureStructF64) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructF64.uniffiSetValue(other: UniffiForeignFutureStructF64UniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructF64UniffiByValue = UniffiForeignFutureStructF64Struct.UniffiByValue
internal interface UniffiForeignFutureCompleteF64: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructF64UniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructPointerStruct(
    @JvmField public var `returnValue`: Pointer?,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = NullPointer,
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: Pointer?,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructPointer(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructPointer = UniffiForeignFutureStructPointerStruct

internal fun UniffiForeignFutureStructPointer.uniffiSetValue(other: UniffiForeignFutureStructPointer) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructPointer.uniffiSetValue(other: UniffiForeignFutureStructPointerUniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructPointerUniffiByValue = UniffiForeignFutureStructPointerStruct.UniffiByValue
internal interface UniffiForeignFutureCompletePointer: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructPointerUniffiByValue,)
}
@Structure.FieldOrder("returnValue", "callStatus")
internal open class UniffiForeignFutureStructRustBufferStruct(
    @JvmField public var `returnValue`: RustBufferByValue,
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `returnValue` = RustBufferHelper.allocValue(),
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `returnValue`: RustBufferByValue,
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructRustBuffer(`returnValue`,`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructRustBuffer = UniffiForeignFutureStructRustBufferStruct

internal fun UniffiForeignFutureStructRustBuffer.uniffiSetValue(other: UniffiForeignFutureStructRustBuffer) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructRustBuffer.uniffiSetValue(other: UniffiForeignFutureStructRustBufferUniffiByValue) {
    `returnValue` = other.`returnValue`
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructRustBufferUniffiByValue = UniffiForeignFutureStructRustBufferStruct.UniffiByValue
internal interface UniffiForeignFutureCompleteRustBuffer: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructRustBufferUniffiByValue,)
}
@Structure.FieldOrder("callStatus")
internal open class UniffiForeignFutureStructVoidStruct(
    @JvmField public var `callStatus`: UniffiRustCallStatusByValue,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `callStatus` = UniffiRustCallStatusHelper.allocValue(),
        
    )

    internal class UniffiByValue(
        `callStatus`: UniffiRustCallStatusByValue,
    ): UniffiForeignFutureStructVoid(`callStatus`,), Structure.ByValue
}

internal typealias UniffiForeignFutureStructVoid = UniffiForeignFutureStructVoidStruct

internal fun UniffiForeignFutureStructVoid.uniffiSetValue(other: UniffiForeignFutureStructVoid) {
    `callStatus` = other.`callStatus`
}
internal fun UniffiForeignFutureStructVoid.uniffiSetValue(other: UniffiForeignFutureStructVoidUniffiByValue) {
    `callStatus` = other.`callStatus`
}

internal typealias UniffiForeignFutureStructVoidUniffiByValue = UniffiForeignFutureStructVoidStruct.UniffiByValue
internal interface UniffiForeignFutureCompleteVoid: com.sun.jna.Callback {
    public fun callback(`callbackData`: Long,`result`: UniffiForeignFutureStructVoidUniffiByValue,)
}
internal interface UniffiCallbackInterfaceFullScanScriptInspectorMethod0: com.sun.jna.Callback {
    public fun callback(`uniffiHandle`: Long,`keychain`: RustBufferByValue,`index`: Int,`script`: Pointer?,`uniffiOutReturn`: Pointer,uniffiCallStatus: UniffiRustCallStatus,)
}
internal interface UniffiCallbackInterfacePersistenceMethod0: com.sun.jna.Callback {
    public fun callback(`uniffiHandle`: Long,`uniffiOutReturn`: PointerByReference,uniffiCallStatus: UniffiRustCallStatus,)
}
internal interface UniffiCallbackInterfacePersistenceMethod1: com.sun.jna.Callback {
    public fun callback(`uniffiHandle`: Long,`changeset`: Pointer?,`uniffiOutReturn`: Pointer,uniffiCallStatus: UniffiRustCallStatus,)
}
internal interface UniffiCallbackInterfaceSyncScriptInspectorMethod0: com.sun.jna.Callback {
    public fun callback(`uniffiHandle`: Long,`script`: Pointer?,`total`: Long,`uniffiOutReturn`: Pointer,uniffiCallStatus: UniffiRustCallStatus,)
}
@Structure.FieldOrder("inspect", "uniffiFree")
internal open class UniffiVTableCallbackInterfaceFullScanScriptInspectorStruct(
    @JvmField public var `inspect`: UniffiCallbackInterfaceFullScanScriptInspectorMethod0?,
    @JvmField public var `uniffiFree`: UniffiCallbackInterfaceFree?,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `inspect` = null,
        
        `uniffiFree` = null,
        
    )

    internal class UniffiByValue(
        `inspect`: UniffiCallbackInterfaceFullScanScriptInspectorMethod0?,
        `uniffiFree`: UniffiCallbackInterfaceFree?,
    ): UniffiVTableCallbackInterfaceFullScanScriptInspector(`inspect`,`uniffiFree`,), Structure.ByValue
}

internal typealias UniffiVTableCallbackInterfaceFullScanScriptInspector = UniffiVTableCallbackInterfaceFullScanScriptInspectorStruct

internal fun UniffiVTableCallbackInterfaceFullScanScriptInspector.uniffiSetValue(other: UniffiVTableCallbackInterfaceFullScanScriptInspector) {
    `inspect` = other.`inspect`
    `uniffiFree` = other.`uniffiFree`
}
internal fun UniffiVTableCallbackInterfaceFullScanScriptInspector.uniffiSetValue(other: UniffiVTableCallbackInterfaceFullScanScriptInspectorUniffiByValue) {
    `inspect` = other.`inspect`
    `uniffiFree` = other.`uniffiFree`
}

internal typealias UniffiVTableCallbackInterfaceFullScanScriptInspectorUniffiByValue = UniffiVTableCallbackInterfaceFullScanScriptInspectorStruct.UniffiByValue
@Structure.FieldOrder("initialize", "persist", "uniffiFree")
internal open class UniffiVTableCallbackInterfacePersistenceStruct(
    @JvmField public var `initialize`: UniffiCallbackInterfacePersistenceMethod0?,
    @JvmField public var `persist`: UniffiCallbackInterfacePersistenceMethod1?,
    @JvmField public var `uniffiFree`: UniffiCallbackInterfaceFree?,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `initialize` = null,
        
        `persist` = null,
        
        `uniffiFree` = null,
        
    )

    internal class UniffiByValue(
        `initialize`: UniffiCallbackInterfacePersistenceMethod0?,
        `persist`: UniffiCallbackInterfacePersistenceMethod1?,
        `uniffiFree`: UniffiCallbackInterfaceFree?,
    ): UniffiVTableCallbackInterfacePersistence(`initialize`,`persist`,`uniffiFree`,), Structure.ByValue
}

internal typealias UniffiVTableCallbackInterfacePersistence = UniffiVTableCallbackInterfacePersistenceStruct

internal fun UniffiVTableCallbackInterfacePersistence.uniffiSetValue(other: UniffiVTableCallbackInterfacePersistence) {
    `initialize` = other.`initialize`
    `persist` = other.`persist`
    `uniffiFree` = other.`uniffiFree`
}
internal fun UniffiVTableCallbackInterfacePersistence.uniffiSetValue(other: UniffiVTableCallbackInterfacePersistenceUniffiByValue) {
    `initialize` = other.`initialize`
    `persist` = other.`persist`
    `uniffiFree` = other.`uniffiFree`
}

internal typealias UniffiVTableCallbackInterfacePersistenceUniffiByValue = UniffiVTableCallbackInterfacePersistenceStruct.UniffiByValue
@Structure.FieldOrder("inspect", "uniffiFree")
internal open class UniffiVTableCallbackInterfaceSyncScriptInspectorStruct(
    @JvmField public var `inspect`: UniffiCallbackInterfaceSyncScriptInspectorMethod0?,
    @JvmField public var `uniffiFree`: UniffiCallbackInterfaceFree?,
) : com.sun.jna.Structure() {
    internal constructor(): this(
        
        `inspect` = null,
        
        `uniffiFree` = null,
        
    )

    internal class UniffiByValue(
        `inspect`: UniffiCallbackInterfaceSyncScriptInspectorMethod0?,
        `uniffiFree`: UniffiCallbackInterfaceFree?,
    ): UniffiVTableCallbackInterfaceSyncScriptInspector(`inspect`,`uniffiFree`,), Structure.ByValue
}

internal typealias UniffiVTableCallbackInterfaceSyncScriptInspector = UniffiVTableCallbackInterfaceSyncScriptInspectorStruct

internal fun UniffiVTableCallbackInterfaceSyncScriptInspector.uniffiSetValue(other: UniffiVTableCallbackInterfaceSyncScriptInspector) {
    `inspect` = other.`inspect`
    `uniffiFree` = other.`uniffiFree`
}
internal fun UniffiVTableCallbackInterfaceSyncScriptInspector.uniffiSetValue(other: UniffiVTableCallbackInterfaceSyncScriptInspectorUniffiByValue) {
    `inspect` = other.`inspect`
    `uniffiFree` = other.`uniffiFree`
}

internal typealias UniffiVTableCallbackInterfaceSyncScriptInspectorUniffiByValue = UniffiVTableCallbackInterfaceSyncScriptInspectorStruct.UniffiByValue












































































































































































































































































































































































































































































































































































































































































@Synchronized
private fun findLibraryName(componentName: String): String {
    val libOverride = System.getProperty("uniffi.component.$componentName.libraryOverride")
    if (libOverride != null) {
        return libOverride
    }
    return "bdk"
}

// For large crates we prevent `MethodTooLargeException` (see #2340)
// N.B. the name of the extension is very misleading, since it is 
// rather `InterfaceTooLargeException`, caused by too many methods 
// in the interface for large crates.
//
// By splitting the otherwise huge interface into two parts
// * UniffiLib 
// * IntegrityCheckingUniffiLib (this)
// we allow for ~2x as many methods in the UniffiLib interface.
// 
// The `ffi_uniffi_contract_version` method and all checksum methods are put 
// into `IntegrityCheckingUniffiLib` and these methods are called only once,
// when the library is loaded.
internal object IntegrityCheckingUniffiLib : Library {
    init {
        Native.register(IntegrityCheckingUniffiLib::class.java, findLibraryName("bdk"))
        uniffiCheckContractApiVersion()
        uniffiCheckApiChecksums()
    }

    private fun uniffiCheckContractApiVersion() {
        // Get the bindings contract version from our ComponentInterface
        val bindingsContractVersion = 29
        // Get the scaffolding contract version by calling the into the dylib
        val scaffoldingContractVersion = ffi_bdk_uniffi_contract_version()
        if (bindingsContractVersion != scaffoldingContractVersion) {
            throw RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project")
        }
    }
    private fun uniffiCheckApiChecksums() {
        if (uniffi_bdk_checksum_method_address_is_valid_for_network() != 59447.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_address_script_pubkey() != 121.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_address_to_address_data() != 37013.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_address_to_qr_uri() != 480.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_amount_to_btc() != 55517.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_amount_to_sat() != 11436.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_blockhash_serialize() != 13690.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_bumpfeetxbuilder_allow_dust() != 52832.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_bumpfeetxbuilder_current_height() != 27010.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_bumpfeetxbuilder_finish() != 13999.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_bumpfeetxbuilder_nlocktime() != 34054.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_bumpfeetxbuilder_set_exact_sequence() != 39212.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_bumpfeetxbuilder_version() != 26230.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_build() != 37588.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_configure_timeout_millis() != 19144.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_connections() != 56129.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_data_dir() != 3602.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_peers() != 42138.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_scan_type() != 38744.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfbuilder_socks5_proxy() != 39360.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_average_fee_rate() != 40544.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_broadcast() != 55772.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_connect() != 59771.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_is_running() != 2508.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_lookup_host() != 49566.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_min_broadcast_feerate() != 4829.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_next_info() != 46453.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_next_warning() != 31984.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_shutdown() != 5606.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfclient_update() != 18318.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_cbfnode_run() != 27277.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_changeset_change_descriptor() != 56229.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_changeset_descriptor() != 59205.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_changeset_indexer_changeset() != 46695.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_changeset_localchain_changeset() != 29451.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_changeset_network() != 26245.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_changeset_tx_graph_changeset() != 42945.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_derivationpath_is_empty() != 58330.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_derivationpath_is_master() != 16195.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_derivationpath_len() != 58350.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptor_desc_type() != 7096.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptor_descriptor_id() != 16690.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptor_is_multipath() != 12060.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptor_max_weight_to_satisfy() != 38273.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptor_to_single_descriptors() != 23470.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptor_to_string_with_secret() != 6721.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorid_serialize() != 37093.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorpublickey_derive() != 24560.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorpublickey_extend() != 54912.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorpublickey_is_multipath() != 10746.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorpublickey_master_fingerprint() != 30571.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorsecretkey_as_public() != 5636.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorsecretkey_derive() != 62147.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorsecretkey_extend() != 31136.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_descriptorsecretkey_secret_bytes() != 56962.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_block_headers_subscribe() != 63126.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_estimate_fee() != 1396.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_full_scan() != 42950.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_ping() != 28476.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_server_features() != 8929.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_sync() != 35663.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_electrumclient_transaction_broadcast() != 17718.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_broadcast() != 63160.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_full_scan() != 16744.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_get_block_hash() != 23636.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_get_fee_estimates() != 22724.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_get_height() != 41363.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_get_tx() != 2805.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_get_tx_info() != 12516.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_get_tx_status() != 61556.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_esploraclient_sync() != 16200.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_feerate_to_sat_per_kwu() != 42333.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_feerate_to_sat_per_vb_ceil() != 2605.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_feerate_to_sat_per_vb_floor() != 36496.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_fullscanrequestbuilder_build() != 25636.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_fullscanrequestbuilder_inspect_spks_for_all_keychains() != 2318.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_fullscanscriptinspector_inspect() != 14933.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_hashableoutpoint_outpoint() != 55826.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_persistence_initialize() != 4559.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_persistence_persist() != 19214.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_policy_as_string() != 47206.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_policy_contribution() != 2647.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_policy_id() != 21412.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_policy_item() != 64348.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_policy_requires_path() != 51219.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_policy_satisfaction() != 37599.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_combine() != 10366.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_extract_tx() != 57032.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_fee() != 30668.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_finalize() != 63789.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_input() != 38419.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_json_serialize() != 20780.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_serialize() != 17281.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_spend_utxo() != 1095.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_psbt_write_to_file() != 34373.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_script_to_bytes() != 23896.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_syncrequestbuilder_build() != 57853.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_syncrequestbuilder_inspect_spks() != 22249.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_syncscriptinspector_inspect() != 51612.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_compute_txid() != 60748.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_compute_wtxid() != 62832.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_input() != 19405.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_is_coinbase() != 4784.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_is_explicitly_rbf() != 65056.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_is_lock_time_enabled() != 61785.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_lock_time() != 36141.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_output() != 19623.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_serialize() != 1121.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_total_size() != 65113.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_version() != 10306.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_vsize() != 1155.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_transaction_weight() != 41027.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_add_data() != 60041.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_add_global_xpubs() != 28938.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_add_recipient() != 45753.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_add_unspendable() != 16236.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_add_utxo() != 21886.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_add_utxos() != 34920.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_allow_dust() != 37198.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_change_policy() != 41574.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_current_height() != 55014.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_do_not_spend_change() != 46703.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_drain_to() != 64834.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_drain_wallet() != 64806.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_exclude_below_confirmations() != 4438.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_exclude_unconfirmed() != 34020.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_fee_absolute() != 28920.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_fee_rate() != 44684.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_finish() != 18533.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_manually_selected_only() != 17541.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_nlocktime() != 9689.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_only_spend_change() != 44017.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_policy_path() != 7992.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_set_exact_sequence() != 26799.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_set_recipients() != 54539.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_unspendable() != 51678.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txbuilder_version() != 5633.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txmerklenode_serialize() != 20317.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_txid_serialize() != 25336.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_apply_evicted_txs() != 5302.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_apply_unconfirmed_txs() != 41342.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_apply_update() != 52248.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_balance() != 55430.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_calculate_fee() != 2864.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_calculate_fee_rate() != 1180.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_cancel_tx() != 27288.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_derivation_index() != 33468.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_derivation_of_spk() != 37936.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_descriptor_checksum() != 48928.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_finalize_psbt() != 33787.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_get_tx() != 51339.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_get_utxo() != 19754.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_insert_txout() != 14543.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_is_mine() != 44751.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_latest_checkpoint() != 28933.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_list_output() != 35875.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_list_unspent() != 45713.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_list_unused_addresses() != 22552.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_mark_used() != 8695.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_network() != 15524.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_next_derivation_index() != 50247.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_next_unused_address() != 61942.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_peek_address() != 25484.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_persist() != 30303.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_policies() != 17681.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_public_descriptor() != 59687.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_reveal_addresses_to() != 9874.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_reveal_next_address() != 42985.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_sent_and_received() != 64478.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_sign() != 39997.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_staged() != 37823.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_start_full_scan() != 49027.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_start_sync_with_revealed_spks() != 27480.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_take_staged() != 10903.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_transactions() != 48879.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_tx_details() != 4018.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wallet_unmark_used() != 29630.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_method_wtxid_serialize() != 51054.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_address_from_script() != 4367.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_address_new() != 44368.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_amount_from_btc() != 13081.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_amount_from_sat() != 52254.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_blockhash_from_bytes() != 65276.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_blockhash_from_string() != 33459.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_bumpfeetxbuilder_new() != 28425.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_cbfbuilder_new() != 24513.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_from_aggregate() != 33691.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_from_descriptor_and_network() != 53366.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_from_indexer_changeset() != 52939.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_from_local_chain_changes() != 62309.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_from_merge() != 11020.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_from_tx_graph_changeset() != 13579.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_changeset_new() != 33803.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_derivationpath_master() != 56076.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_derivationpath_new() != 48050.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new() != 34732.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip44() != 13357.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip44_public() != 53913.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip49() != 48761.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip49_public() != 54016.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip84() != 58557.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip84_public() != 12238.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip86() != 63928.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptor_new_bip86_public() != 42810.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptorid_from_bytes() != 28147.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptorid_from_string() != 49555.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptorpublickey_from_string() != 30829.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptorsecretkey_from_string() != 28122.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_descriptorsecretkey_new() != 41957.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_electrumclient_new() != 24489.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_esploraclient_new() != 35071.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_feerate_from_sat_per_kwu() != 63059.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_feerate_from_sat_per_vb() != 32134.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_hashableoutpoint_new() != 28412.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_ipaddress_from_ipv4() != 20811.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_ipaddress_from_ipv6() != 62840.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_mnemonic_from_entropy() != 57892.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_mnemonic_from_string() != 21275.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_mnemonic_new() != 6812.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_persister_custom() != 21325.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_persister_new_in_memory() != 2974.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_persister_new_sqlite() != 36115.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_psbt_from_file() != 65274.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_psbt_from_unsigned_tx() != 23831.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_psbt_new() != 41151.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_script_new() != 43177.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_transaction_new() != 58871.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_txbuilder_new() != 56399.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_txmerklenode_from_bytes() != 5185.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_txmerklenode_from_string() != 17688.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_txid_from_bytes() != 23136.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_txid_from_string() != 22089.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wallet_create_from_two_path_descriptor() != 53150.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wallet_create_single() != 31480.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wallet_load() != 445.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wallet_load_single() != 61618.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wallet_new() != 13144.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wtxid_from_bytes() != 23488.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
        if (uniffi_bdk_checksum_constructor_wtxid_from_string() != 45144.toShort()) {
            throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project")
        }
    }

    // Integrity check functions only
    @JvmStatic
    external fun uniffi_bdk_checksum_method_address_is_valid_for_network(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_address_script_pubkey(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_address_to_address_data(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_address_to_qr_uri(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_amount_to_btc(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_amount_to_sat(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_blockhash_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_bumpfeetxbuilder_allow_dust(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_bumpfeetxbuilder_current_height(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_bumpfeetxbuilder_finish(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_bumpfeetxbuilder_nlocktime(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_bumpfeetxbuilder_set_exact_sequence(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_bumpfeetxbuilder_version(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_build(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_configure_timeout_millis(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_connections(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_data_dir(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_peers(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_scan_type(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfbuilder_socks5_proxy(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_average_fee_rate(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_broadcast(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_connect(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_is_running(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_lookup_host(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_min_broadcast_feerate(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_next_info(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_next_warning(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_shutdown(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfclient_update(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_cbfnode_run(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_changeset_change_descriptor(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_changeset_descriptor(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_changeset_indexer_changeset(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_changeset_localchain_changeset(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_changeset_network(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_changeset_tx_graph_changeset(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_derivationpath_is_empty(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_derivationpath_is_master(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_derivationpath_len(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptor_desc_type(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptor_descriptor_id(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptor_is_multipath(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptor_max_weight_to_satisfy(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptor_to_single_descriptors(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptor_to_string_with_secret(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorid_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorpublickey_derive(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorpublickey_extend(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorpublickey_is_multipath(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorpublickey_master_fingerprint(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorsecretkey_as_public(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorsecretkey_derive(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorsecretkey_extend(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_descriptorsecretkey_secret_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_block_headers_subscribe(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_estimate_fee(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_full_scan(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_ping(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_server_features(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_sync(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_electrumclient_transaction_broadcast(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_broadcast(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_full_scan(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_get_block_hash(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_get_fee_estimates(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_get_height(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_get_tx(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_get_tx_info(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_get_tx_status(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_esploraclient_sync(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_feerate_to_sat_per_kwu(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_feerate_to_sat_per_vb_ceil(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_feerate_to_sat_per_vb_floor(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_fullscanrequestbuilder_build(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_fullscanrequestbuilder_inspect_spks_for_all_keychains(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_fullscanscriptinspector_inspect(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_hashableoutpoint_outpoint(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_persistence_initialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_persistence_persist(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_policy_as_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_policy_contribution(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_policy_id(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_policy_item(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_policy_requires_path(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_policy_satisfaction(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_combine(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_extract_tx(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_fee(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_finalize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_input(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_json_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_spend_utxo(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_psbt_write_to_file(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_script_to_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_syncrequestbuilder_build(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_syncrequestbuilder_inspect_spks(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_syncscriptinspector_inspect(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_compute_txid(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_compute_wtxid(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_input(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_is_coinbase(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_is_explicitly_rbf(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_is_lock_time_enabled(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_lock_time(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_output(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_total_size(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_version(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_vsize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_transaction_weight(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_add_data(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_add_global_xpubs(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_add_recipient(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_add_unspendable(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_add_utxo(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_add_utxos(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_allow_dust(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_change_policy(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_current_height(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_do_not_spend_change(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_drain_to(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_drain_wallet(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_exclude_below_confirmations(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_exclude_unconfirmed(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_fee_absolute(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_fee_rate(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_finish(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_manually_selected_only(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_nlocktime(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_only_spend_change(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_policy_path(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_set_exact_sequence(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_set_recipients(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_unspendable(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txbuilder_version(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txmerklenode_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_txid_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_apply_evicted_txs(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_apply_unconfirmed_txs(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_apply_update(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_balance(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_calculate_fee(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_calculate_fee_rate(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_cancel_tx(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_derivation_index(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_derivation_of_spk(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_descriptor_checksum(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_finalize_psbt(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_get_tx(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_get_utxo(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_insert_txout(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_is_mine(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_latest_checkpoint(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_list_output(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_list_unspent(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_list_unused_addresses(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_mark_used(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_network(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_next_derivation_index(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_next_unused_address(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_peek_address(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_persist(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_policies(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_public_descriptor(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_reveal_addresses_to(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_reveal_next_address(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_sent_and_received(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_sign(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_staged(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_start_full_scan(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_start_sync_with_revealed_spks(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_take_staged(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_transactions(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_tx_details(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wallet_unmark_used(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_method_wtxid_serialize(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_address_from_script(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_address_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_amount_from_btc(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_amount_from_sat(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_blockhash_from_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_blockhash_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_bumpfeetxbuilder_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_cbfbuilder_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_from_aggregate(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_from_descriptor_and_network(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_from_indexer_changeset(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_from_local_chain_changes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_from_merge(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_from_tx_graph_changeset(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_changeset_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_derivationpath_master(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_derivationpath_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip44(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip44_public(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip49(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip49_public(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip84(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip84_public(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip86(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptor_new_bip86_public(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptorid_from_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptorid_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptorpublickey_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptorsecretkey_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_descriptorsecretkey_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_electrumclient_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_esploraclient_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_feerate_from_sat_per_kwu(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_feerate_from_sat_per_vb(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_hashableoutpoint_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_ipaddress_from_ipv4(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_ipaddress_from_ipv6(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_mnemonic_from_entropy(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_mnemonic_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_mnemonic_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_persister_custom(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_persister_new_in_memory(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_persister_new_sqlite(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_psbt_from_file(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_psbt_from_unsigned_tx(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_psbt_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_script_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_transaction_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_txbuilder_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_txmerklenode_from_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_txmerklenode_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_txid_from_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_txid_from_string(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wallet_create_from_two_path_descriptor(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wallet_create_single(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wallet_load(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wallet_load_single(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wallet_new(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wtxid_from_bytes(
    ): Short
    @JvmStatic
    external fun uniffi_bdk_checksum_constructor_wtxid_from_string(
    ): Short
    @JvmStatic
    external fun ffi_bdk_uniffi_contract_version(
    ): Int
}

// A JNA Library to expose the extern-C FFI definitions.
// This is an implementation detail which will be called internally by the public API.
internal object UniffiLib : Library {

    init {
        IntegrityCheckingUniffiLib
        Native.register(UniffiLib::class.java, findLibraryName("bdk"))
        // No need to check the contract version and checksums, since 
        // we already did that with `IntegrityCheckingUniffiLib` above.
        uniffiCallbackInterfaceFullScanScriptInspector.register(this)
        uniffiCallbackInterfacePersistence.register(this)
        uniffiCallbackInterfaceSyncScriptInspector.register(this)
    }
    // The Cleaner for the whole library
    internal val CLEANER: UniffiCleaner by lazy {
        UniffiCleaner.create()
    }
    @JvmStatic
    external fun uniffi_bdk_fn_clone_address(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_address(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_address_from_script(
        `script`: Pointer?,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_address_new(
        `address`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_is_valid_for_network(
        `ptr`: Pointer?,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_script_pubkey(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_to_address_data(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_to_qr_uri(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_address_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_clone_amount(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_amount(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_amount_from_btc(
        `btc`: Double,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_amount_from_sat(
        `satoshi`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_amount_to_btc(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Double
    @JvmStatic
    external fun uniffi_bdk_fn_method_amount_to_sat(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_blockhash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_blockhash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_blockhash_from_bytes(
        `bytes`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_blockhash_from_string(
        `hex`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_blockhash_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_blockhash_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_blockhash_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_blockhash_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_blockhash_uniffi_trait_hash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_bumpfeetxbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_bumpfeetxbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_bumpfeetxbuilder_new(
        `txid`: Pointer?,
        `feeRate`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_bumpfeetxbuilder_allow_dust(
        `ptr`: Pointer?,
        `allowDust`: Byte,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_bumpfeetxbuilder_current_height(
        `ptr`: Pointer?,
        `height`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_bumpfeetxbuilder_finish(
        `ptr`: Pointer?,
        `wallet`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_bumpfeetxbuilder_nlocktime(
        `ptr`: Pointer?,
        `locktime`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_bumpfeetxbuilder_set_exact_sequence(
        `ptr`: Pointer?,
        `nsequence`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_bumpfeetxbuilder_version(
        `ptr`: Pointer?,
        `version`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_cbfbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_cbfbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_cbfbuilder_new(
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_build(
        `ptr`: Pointer?,
        `wallet`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_configure_timeout_millis(
        `ptr`: Pointer?,
        `handshake`: Long,
        `response`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_connections(
        `ptr`: Pointer?,
        `connections`: Byte,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_data_dir(
        `ptr`: Pointer?,
        `dataDir`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_peers(
        `ptr`: Pointer?,
        `peers`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_scan_type(
        `ptr`: Pointer?,
        `scanType`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfbuilder_socks5_proxy(
        `ptr`: Pointer?,
        `proxy`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_cbfclient(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_cbfclient(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_average_fee_rate(
        `ptr`: Pointer?,
        `blockhash`: Pointer?,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_broadcast(
        `ptr`: Pointer?,
        `transaction`: Pointer?,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_connect(
        `ptr`: Pointer?,
        `peer`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_is_running(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_lookup_host(
        `ptr`: Pointer?,
        `hostname`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_min_broadcast_feerate(
        `ptr`: Pointer?,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_next_info(
        `ptr`: Pointer?,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_next_warning(
        `ptr`: Pointer?,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_shutdown(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfclient_update(
        `ptr`: Pointer?,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_cbfnode(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_cbfnode(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_cbfnode_run(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_changeset(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_changeset(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_from_aggregate(
        `descriptor`: RustBufferByValue,
        `changeDescriptor`: RustBufferByValue,
        `network`: RustBufferByValue,
        `localChain`: RustBufferByValue,
        `txGraph`: RustBufferByValue,
        `indexer`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_from_descriptor_and_network(
        `descriptor`: RustBufferByValue,
        `changeDescriptor`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_from_indexer_changeset(
        `indexerChanges`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_from_local_chain_changes(
        `localChainChanges`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_from_merge(
        `left`: Pointer?,
        `right`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_from_tx_graph_changeset(
        `txGraphChangeset`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_changeset_new(
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_changeset_change_descriptor(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_changeset_descriptor(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_changeset_indexer_changeset(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_changeset_localchain_changeset(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_changeset_network(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_changeset_tx_graph_changeset(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_derivationpath(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_derivationpath(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_derivationpath_master(
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_derivationpath_new(
        `path`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_derivationpath_is_empty(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_derivationpath_is_master(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_derivationpath_len(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_derivationpath_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_descriptor(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_descriptor(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new(
        `descriptor`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip44(
        `secretKey`: Pointer?,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip44_public(
        `publicKey`: Pointer?,
        `fingerprint`: RustBufferByValue,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip49(
        `secretKey`: Pointer?,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip49_public(
        `publicKey`: Pointer?,
        `fingerprint`: RustBufferByValue,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip84(
        `secretKey`: Pointer?,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip84_public(
        `publicKey`: Pointer?,
        `fingerprint`: RustBufferByValue,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip86(
        `secretKey`: Pointer?,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptor_new_bip86_public(
        `publicKey`: Pointer?,
        `fingerprint`: RustBufferByValue,
        `keychainKind`: RustBufferByValue,
        `network`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_desc_type(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_descriptor_id(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_is_multipath(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_max_weight_to_satisfy(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_to_single_descriptors(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_to_string_with_secret(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_uniffi_trait_debug(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptor_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_descriptorid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_descriptorid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptorid_from_bytes(
        `bytes`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptorid_from_string(
        `hex`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorid_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorid_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorid_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorid_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorid_uniffi_trait_hash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_descriptorpublickey(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_descriptorpublickey(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptorpublickey_from_string(
        `publicKey`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorpublickey_derive(
        `ptr`: Pointer?,
        `path`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorpublickey_extend(
        `ptr`: Pointer?,
        `path`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorpublickey_is_multipath(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorpublickey_master_fingerprint(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorpublickey_uniffi_trait_debug(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorpublickey_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_descriptorsecretkey(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_descriptorsecretkey(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptorsecretkey_from_string(
        `privateKey`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_descriptorsecretkey_new(
        `network`: RustBufferByValue,
        `mnemonic`: Pointer?,
        `password`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorsecretkey_as_public(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorsecretkey_derive(
        `ptr`: Pointer?,
        `path`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorsecretkey_extend(
        `ptr`: Pointer?,
        `path`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorsecretkey_secret_bytes(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorsecretkey_uniffi_trait_debug(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_descriptorsecretkey_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_electrumclient(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_electrumclient(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_electrumclient_new(
        `url`: RustBufferByValue,
        `socks5`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_block_headers_subscribe(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_estimate_fee(
        `ptr`: Pointer?,
        `number`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Double
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_full_scan(
        `ptr`: Pointer?,
        `request`: Pointer?,
        `stopGap`: Long,
        `batchSize`: Long,
        `fetchPrevTxouts`: Byte,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_ping(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_server_features(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_sync(
        `ptr`: Pointer?,
        `request`: Pointer?,
        `batchSize`: Long,
        `fetchPrevTxouts`: Byte,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_electrumclient_transaction_broadcast(
        `ptr`: Pointer?,
        `tx`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_esploraclient(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_esploraclient(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_esploraclient_new(
        `url`: RustBufferByValue,
        `proxy`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_broadcast(
        `ptr`: Pointer?,
        `transaction`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_full_scan(
        `ptr`: Pointer?,
        `request`: Pointer?,
        `stopGap`: Long,
        `parallelRequests`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_get_block_hash(
        `ptr`: Pointer?,
        `blockHeight`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_get_fee_estimates(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_get_height(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Int
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_get_tx(
        `ptr`: Pointer?,
        `txid`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_get_tx_info(
        `ptr`: Pointer?,
        `txid`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_get_tx_status(
        `ptr`: Pointer?,
        `txid`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_esploraclient_sync(
        `ptr`: Pointer?,
        `request`: Pointer?,
        `parallelRequests`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_feerate(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_feerate(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_feerate_from_sat_per_kwu(
        `satKwu`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_feerate_from_sat_per_vb(
        `satVb`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_feerate_to_sat_per_kwu(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_feerate_to_sat_per_vb_ceil(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_feerate_to_sat_per_vb_floor(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_fullscanrequest(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_fullscanrequest(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_fullscanrequestbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_fullscanrequestbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_fullscanrequestbuilder_build(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_fullscanrequestbuilder_inspect_spks_for_all_keychains(
        `ptr`: Pointer?,
        `inspector`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_fullscanscriptinspector(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_fullscanscriptinspector(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_init_callback_vtable_fullscanscriptinspector(
        `vtable`: UniffiVTableCallbackInterfaceFullScanScriptInspector,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_fullscanscriptinspector_inspect(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        `index`: Int,
        `script`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_hashableoutpoint(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_hashableoutpoint(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_hashableoutpoint_new(
        `outpoint`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_hashableoutpoint_outpoint(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_debug(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_hash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_ipaddress(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_ipaddress(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_ipaddress_from_ipv4(
        `q1`: Byte,
        `q2`: Byte,
        `q3`: Byte,
        `q4`: Byte,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_ipaddress_from_ipv6(
        `a`: Short,
        `b`: Short,
        `c`: Short,
        `d`: Short,
        `e`: Short,
        `f`: Short,
        `g`: Short,
        `h`: Short,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_mnemonic(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_mnemonic(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_mnemonic_from_entropy(
        `entropy`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_mnemonic_from_string(
        `mnemonic`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_mnemonic_new(
        `wordCount`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_mnemonic_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_persistence(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_persistence(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_init_callback_vtable_persistence(
        `vtable`: UniffiVTableCallbackInterfacePersistence,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_persistence_initialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_persistence_persist(
        `ptr`: Pointer?,
        `changeset`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_persister(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_persister(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_persister_custom(
        `persistence`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_persister_new_in_memory(
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_persister_new_sqlite(
        `path`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_policy(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_policy(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_policy_as_string(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_policy_contribution(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_policy_id(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_policy_item(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_policy_requires_path(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_policy_satisfaction(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_psbt(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_psbt(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_psbt_from_file(
        `path`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_psbt_from_unsigned_tx(
        `tx`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_psbt_new(
        `psbtBase64`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_combine(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_extract_tx(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_fee(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_finalize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_input(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_json_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_spend_utxo(
        `ptr`: Pointer?,
        `inputIndex`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_psbt_write_to_file(
        `ptr`: Pointer?,
        `path`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_script(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_script(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_script_new(
        `rawOutputScript`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_script_to_bytes(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_script_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_clone_syncrequest(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_syncrequest(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_syncrequestbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_syncrequestbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_syncrequestbuilder_build(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_syncrequestbuilder_inspect_spks(
        `ptr`: Pointer?,
        `inspector`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_syncscriptinspector(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_syncscriptinspector(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_init_callback_vtable_syncscriptinspector(
        `vtable`: UniffiVTableCallbackInterfaceSyncScriptInspector,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_syncscriptinspector_inspect(
        `ptr`: Pointer?,
        `script`: Pointer?,
        `total`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_transaction(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_transaction(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_transaction_new(
        `transactionBytes`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_compute_txid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_compute_wtxid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_input(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_is_coinbase(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_is_explicitly_rbf(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_is_lock_time_enabled(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_lock_time(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Int
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_output(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_total_size(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_version(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Int
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_vsize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_weight(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_transaction_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_clone_txbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_txbuilder(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_txbuilder_new(
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_add_data(
        `ptr`: Pointer?,
        `data`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_add_global_xpubs(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_add_recipient(
        `ptr`: Pointer?,
        `script`: Pointer?,
        `amount`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_add_unspendable(
        `ptr`: Pointer?,
        `unspendable`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_add_utxo(
        `ptr`: Pointer?,
        `outpoint`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_add_utxos(
        `ptr`: Pointer?,
        `outpoints`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_allow_dust(
        `ptr`: Pointer?,
        `allowDust`: Byte,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_change_policy(
        `ptr`: Pointer?,
        `changePolicy`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_current_height(
        `ptr`: Pointer?,
        `height`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_do_not_spend_change(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_drain_to(
        `ptr`: Pointer?,
        `script`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_drain_wallet(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_exclude_below_confirmations(
        `ptr`: Pointer?,
        `minConfirms`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_exclude_unconfirmed(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_fee_absolute(
        `ptr`: Pointer?,
        `feeAmount`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_fee_rate(
        `ptr`: Pointer?,
        `feeRate`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_finish(
        `ptr`: Pointer?,
        `wallet`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_manually_selected_only(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_nlocktime(
        `ptr`: Pointer?,
        `locktime`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_only_spend_change(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_policy_path(
        `ptr`: Pointer?,
        `policyPath`: RustBufferByValue,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_set_exact_sequence(
        `ptr`: Pointer?,
        `nsequence`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_set_recipients(
        `ptr`: Pointer?,
        `recipients`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_unspendable(
        `ptr`: Pointer?,
        `unspendable`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txbuilder_version(
        `ptr`: Pointer?,
        `version`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_clone_txmerklenode(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_txmerklenode(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_txmerklenode_from_bytes(
        `bytes`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_txmerklenode_from_string(
        `hex`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txmerklenode_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_txmerklenode_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_txmerklenode_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_txmerklenode_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_txmerklenode_uniffi_trait_hash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_txid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_txid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_txid_from_bytes(
        `bytes`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_txid_from_string(
        `hex`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_txid_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_txid_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_txid_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_txid_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_txid_uniffi_trait_hash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun uniffi_bdk_fn_clone_update(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_update(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_clone_wallet(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_wallet(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wallet_create_from_two_path_descriptor(
        `twoPathDescriptor`: Pointer?,
        `network`: RustBufferByValue,
        `persister`: Pointer?,
        `lookahead`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wallet_create_single(
        `descriptor`: Pointer?,
        `network`: RustBufferByValue,
        `persister`: Pointer?,
        `lookahead`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wallet_load(
        `descriptor`: Pointer?,
        `changeDescriptor`: Pointer?,
        `persister`: Pointer?,
        `lookahead`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wallet_load_single(
        `descriptor`: Pointer?,
        `persister`: Pointer?,
        `lookahead`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wallet_new(
        `descriptor`: Pointer?,
        `changeDescriptor`: Pointer?,
        `network`: RustBufferByValue,
        `persister`: Pointer?,
        `lookahead`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_apply_evicted_txs(
        `ptr`: Pointer?,
        `evictedTxs`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_apply_unconfirmed_txs(
        `ptr`: Pointer?,
        `unconfirmedTxs`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_apply_update(
        `ptr`: Pointer?,
        `update`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_balance(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_calculate_fee(
        `ptr`: Pointer?,
        `tx`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_calculate_fee_rate(
        `ptr`: Pointer?,
        `tx`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_cancel_tx(
        `ptr`: Pointer?,
        `tx`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_derivation_index(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_derivation_of_spk(
        `ptr`: Pointer?,
        `spk`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_descriptor_checksum(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_finalize_psbt(
        `ptr`: Pointer?,
        `psbt`: Pointer?,
        `signOptions`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_get_tx(
        `ptr`: Pointer?,
        `txid`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_get_utxo(
        `ptr`: Pointer?,
        `op`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_insert_txout(
        `ptr`: Pointer?,
        `outpoint`: RustBufferByValue,
        `txout`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_is_mine(
        `ptr`: Pointer?,
        `script`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_latest_checkpoint(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_list_output(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_list_unspent(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_list_unused_addresses(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_mark_used(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        `index`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_network(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_next_derivation_index(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Int
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_next_unused_address(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_peek_address(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        `index`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_persist(
        `ptr`: Pointer?,
        `persister`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_policies(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_public_descriptor(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_reveal_addresses_to(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        `index`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_reveal_next_address(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_sent_and_received(
        `ptr`: Pointer?,
        `tx`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_sign(
        `ptr`: Pointer?,
        `psbt`: Pointer?,
        `signOptions`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_staged(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_start_full_scan(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_start_sync_with_revealed_spks(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_take_staged(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_transactions(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_tx_details(
        `ptr`: Pointer?,
        `txid`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wallet_unmark_used(
        `ptr`: Pointer?,
        `keychain`: RustBufferByValue,
        `index`: Int,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_clone_wtxid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_free_wtxid(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wtxid_from_bytes(
        `bytes`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_constructor_wtxid_from_string(
        `hex`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun uniffi_bdk_fn_method_wtxid_serialize(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wtxid_uniffi_trait_display(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun uniffi_bdk_fn_method_wtxid_uniffi_trait_eq_eq(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wtxid_uniffi_trait_eq_ne(
        `ptr`: Pointer?,
        `other`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun uniffi_bdk_fn_method_wtxid_uniffi_trait_hash(
        `ptr`: Pointer?,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun ffi_bdk_rustbuffer_alloc(
        `size`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun ffi_bdk_rustbuffer_from_bytes(
        `bytes`: ForeignBytesByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun ffi_bdk_rustbuffer_free(
        `buf`: RustBufferByValue,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rustbuffer_reserve(
        `buf`: RustBufferByValue,
        `additional`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_u8(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_u8(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_u8(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_u8(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_i8(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_i8(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_i8(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_i8(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Byte
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_u16(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_u16(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_u16(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_u16(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Short
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_i16(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_i16(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_i16(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_i16(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Short
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_u32(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_u32(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_u32(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_u32(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Int
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_i32(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_i32(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_i32(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_i32(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Int
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_u64(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_u64(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_u64(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_u64(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_i64(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_i64(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_i64(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_i64(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Long
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_f32(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_f32(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_f32(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_f32(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Float
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_f64(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_f64(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_f64(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_f64(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Double
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_pointer(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_pointer(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_pointer(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_pointer(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Pointer?
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_rust_buffer(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_rust_buffer(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_rust_buffer(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_rust_buffer(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): RustBufferByValue
    @JvmStatic
    external fun ffi_bdk_rust_future_poll_void(
        `handle`: Long,
        `callback`: UniffiRustFutureContinuationCallback,
        `callbackData`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_cancel_void(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_free_void(
        `handle`: Long,
    ): Unit
    @JvmStatic
    external fun ffi_bdk_rust_future_complete_void(
        `handle`: Long,
        uniffiCallStatus: UniffiRustCallStatus,
    ): Unit
}

public fun uniffiEnsureInitialized() {
    UniffiLib
}

// Public interface members begin here.

internal const val IDX_CALLBACK_FREE = 0
// Callback return codes
internal const val UNIFFI_CALLBACK_SUCCESS = 0
internal const val UNIFFI_CALLBACK_ERROR = 1
internal const val UNIFFI_CALLBACK_UNEXPECTED_ERROR = 2

public abstract class FfiConverterCallbackInterface<CallbackInterface: Any>: FfiConverter<CallbackInterface, Long> {
    internal val handleMap = UniffiHandleMap<CallbackInterface>()

    internal fun drop(handle: Long) {
        handleMap.remove(handle)
    }

    override fun lift(value: Long): CallbackInterface {
        return handleMap.get(value)
    }

    override fun read(buf: ByteBuffer): CallbackInterface = lift(buf.getLong())

    override fun lower(value: CallbackInterface): Long = handleMap.insert(value)

    override fun allocationSize(value: CallbackInterface): ULong = 8UL

    override fun write(value: CallbackInterface, buf: ByteBuffer) {
        buf.putLong(lower(value))
    }
}
// The cleaner interface for Object finalization code to run.
// This is the entry point to any implementation that we're using.
//
// The cleaner registers disposables and returns cleanables, so now we are
// defining a `UniffiCleaner` with a `UniffiClenaer.Cleanable` to abstract the
// different implementations available at compile time.
public interface UniffiCleaner {
    public interface Cleanable {
        public fun clean()
    }

    public fun register(resource: Any, disposable: Disposable): UniffiCleaner.Cleanable

    public companion object
}
// The fallback Jna cleaner, which is available for both Android, and the JVM.
private class UniffiJnaCleaner : UniffiCleaner {
    private val cleaner = com.sun.jna.internal.Cleaner.getCleaner()

    override fun register(resource: Any, disposable: Disposable): UniffiCleaner.Cleanable =
        UniffiJnaCleanable(cleaner.register(resource, UniffiCleanerAction(disposable)))
}

private class UniffiJnaCleanable(
    private val cleanable: com.sun.jna.internal.Cleaner.Cleanable,
) : UniffiCleaner.Cleanable {
    override fun clean() = cleanable.clean()
}

private class UniffiCleanerAction(private val disposable: Disposable): Runnable {
    override fun run() {
        disposable.destroy()
    }
}

// The SystemCleaner, available from API Level 33.
// Some API Level 33 OSes do not support using it, so we require API Level 34.
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class AndroidSystemCleaner : UniffiCleaner {
    private val cleaner = android.system.SystemCleaner.cleaner()

    override fun register(resource: Any, disposable: Disposable): UniffiCleaner.Cleanable =
        AndroidSystemCleanable(cleaner.register(resource, UniffiCleanerAction(disposable)))
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class AndroidSystemCleanable(
    private val cleanable: java.lang.ref.Cleaner.Cleanable,
) : UniffiCleaner.Cleanable {
    override fun clean() = cleanable.clean()
}

private fun UniffiCleaner.Companion.create(): UniffiCleaner {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        try {
            return AndroidSystemCleaner()
        } catch (_: IllegalAccessError) {
            // (For Compose preview) Fallback to UniffiJnaCleaner if AndroidSystemCleaner is
            // unavailable, even for API level 34 or higher.
        }
    }
    return UniffiJnaCleaner()
}


public object FfiConverterUByte: FfiConverter<UByte, Byte> {
    override fun lift(value: Byte): UByte {
        return value.toUByte()
    }

    override fun read(buf: ByteBuffer): UByte {
        return lift(buf.get())
    }

    override fun lower(value: UByte): Byte {
        return value.toByte()
    }

    override fun allocationSize(value: UByte): ULong = 1UL

    override fun write(value: UByte, buf: ByteBuffer) {
        buf.put(value.toByte())
    }
}


public object FfiConverterUShort: FfiConverter<UShort, Short> {
    override fun lift(value: Short): UShort {
        return value.toUShort()
    }

    override fun read(buf: ByteBuffer): UShort {
        return lift(buf.getShort())
    }

    override fun lower(value: UShort): Short {
        return value.toShort()
    }

    override fun allocationSize(value: UShort): ULong = 2UL

    override fun write(value: UShort, buf: ByteBuffer) {
        buf.putShort(value.toShort())
    }
}


public object FfiConverterUInt: FfiConverter<UInt, Int> {
    override fun lift(value: Int): UInt {
        return value.toUInt()
    }

    override fun read(buf: ByteBuffer): UInt {
        return lift(buf.getInt())
    }

    override fun lower(value: UInt): Int {
        return value.toInt()
    }

    override fun allocationSize(value: UInt): ULong = 4UL

    override fun write(value: UInt, buf: ByteBuffer) {
        buf.putInt(value.toInt())
    }
}


public object FfiConverterInt: FfiConverter<Int, Int> {
    override fun lift(value: Int): Int {
        return value
    }

    override fun read(buf: ByteBuffer): Int {
        return buf.getInt()
    }

    override fun lower(value: Int): Int {
        return value
    }

    override fun allocationSize(value: Int): ULong = 4UL

    override fun write(value: Int, buf: ByteBuffer) {
        buf.putInt(value)
    }
}


public object FfiConverterULong: FfiConverter<ULong, Long> {
    override fun lift(value: Long): ULong {
        return value.toULong()
    }

    override fun read(buf: ByteBuffer): ULong {
        return lift(buf.getLong())
    }

    override fun lower(value: ULong): Long {
        return value.toLong()
    }

    override fun allocationSize(value: ULong): ULong = 8UL

    override fun write(value: ULong, buf: ByteBuffer) {
        buf.putLong(value.toLong())
    }
}


public object FfiConverterLong: FfiConverter<Long, Long> {
    override fun lift(value: Long): Long {
        return value
    }

    override fun read(buf: ByteBuffer): Long {
        return buf.getLong()
    }

    override fun lower(value: Long): Long {
        return value
    }

    override fun allocationSize(value: Long): ULong = 8UL

    override fun write(value: Long, buf: ByteBuffer) {
        buf.putLong(value)
    }
}


public object FfiConverterFloat: FfiConverter<Float, Float> {
    override fun lift(value: Float): Float {
        return value
    }

    override fun read(buf: ByteBuffer): Float {
        return buf.getFloat()
    }

    override fun lower(value: Float): Float {
        return value
    }

    override fun allocationSize(value: Float): ULong = 4UL

    override fun write(value: Float, buf: ByteBuffer) {
        buf.putFloat(value)
    }
}


public object FfiConverterDouble: FfiConverter<Double, Double> {
    override fun lift(value: Double): Double {
        return value
    }

    override fun read(buf: ByteBuffer): Double {
        return buf.getDouble()
    }

    override fun lower(value: Double): Double {
        return value
    }

    override fun allocationSize(value: Double): ULong = 8UL

    override fun write(value: Double, buf: ByteBuffer) {
        buf.putDouble(value)
    }
}


public object FfiConverterBoolean: FfiConverter<Boolean, Byte> {
    override fun lift(value: Byte): Boolean {
        return value.toInt() != 0
    }

    override fun read(buf: ByteBuffer): Boolean {
        return lift(buf.get())
    }

    override fun lower(value: Boolean): Byte {
        return if (value) 1.toByte() else 0.toByte()
    }

    override fun allocationSize(value: Boolean): ULong = 1UL

    override fun write(value: Boolean, buf: ByteBuffer) {
        buf.put(lower(value))
    }
}


public object FfiConverterString: FfiConverter<String, RustBufferByValue> {
    // Note: we don't inherit from FfiConverterRustBuffer, because we use a
    // special encoding when lowering/lifting.  We can use `RustBuffer.len` to
    // store our length and avoid writing it out to the buffer.
    override fun lift(value: RustBufferByValue): String {
        try {
            require(value.len <= Int.MAX_VALUE) {
        val length = value.len
        "cannot handle RustBuffer longer than Int.MAX_VALUE bytes: length is $length"
    }
            val byteArr =  value.asByteBuffer()!!.get(value.len.toInt())
            return byteArr.decodeToString()
        } finally {
            RustBufferHelper.free(value)
        }
    }

    override fun read(buf: ByteBuffer): String {
        val len = buf.getInt()
        val byteArr = buf.get(len)
        return byteArr.decodeToString()
    }

    override fun lower(value: String): RustBufferByValue {
        // TODO: prevent allocating a new byte array here
        val encoded = value.encodeToByteArray(throwOnInvalidSequence = true)
        return RustBufferHelper.allocValue(encoded.size.toULong()).apply {
            asByteBuffer()!!.put(encoded)
        }
    }

    // We aren't sure exactly how many bytes our string will be once it's UTF-8
    // encoded.  Allocate 3 bytes per UTF-16 code unit which will always be
    // enough.
    override fun allocationSize(value: String): ULong {
        val sizeForLength = 4UL
        val sizeForString = value.length.toULong() * 3UL
        return sizeForLength + sizeForString
    }

    override fun write(value: String, buf: ByteBuffer) {
        // TODO: prevent allocating a new byte array here
        val encoded = value.encodeToByteArray(throwOnInvalidSequence = true)
        buf.putInt(encoded.size)
        buf.put(encoded)
    }
}


public object FfiConverterByteArray: FfiConverterRustBuffer<ByteArray> {
    override fun read(buf: ByteBuffer): ByteArray {
        val len = buf.getInt()
        val byteArr = buf.get(len)
        return byteArr
    }
    override fun allocationSize(value: ByteArray): ULong {
        return 4UL + value.size.toULong()
    }
    override fun write(value: ByteArray, buf: ByteBuffer) {
        buf.putInt(value.size)
        buf.put(value)
    }
}



/**
 * A bitcoin address
 */
public actual open class Address: Disposable, AddressInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Parse a string as an address for the given network.
     */
    public actual constructor(`address`: kotlin.String, `network`: Network) : this(
        uniffiRustCallWithError(AddressParseExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_address_new(
                FfiConverterString.lower(`address`),
                FfiConverterTypeNetwork.lower(`network`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_address(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_address(pointer!!, status)
        }!!
    }

    
    /**
     * Is the address valid for the provided network
     */
    public actual override fun `isValidForNetwork`(`network`: Network): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_address_is_valid_for_network(
                    it,
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Return the `scriptPubKey` underlying an address.
     */
    public actual override fun `scriptPubkey`(): Script {
        return FfiConverterTypeScript.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_address_script_pubkey(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Return the data for the address.
     */
    public actual override fun `toAddressData`(): AddressData {
        return FfiConverterTypeAddressData.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_address_to_address_data(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Return a BIP-21 URI string for this address.
     */
    public actual override fun `toQrUri`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_address_to_qr_uri(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_address_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Address) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_address_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeAddress.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    public actual companion object {
        
        /**
         * Parse a script as an address for the given network
         */
        @Throws(FromScriptException::class)
        public actual fun `fromScript`(`script`: Script, `network`: Network): Address {
            return FfiConverterTypeAddress.lift(uniffiRustCallWithError(FromScriptExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_address_from_script(
                    FfiConverterTypeScript.lower(`script`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeAddress: FfiConverter<Address, Pointer> {

    override fun lower(value: Address): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Address {
        return Address(value)
    }

    override fun read(buf: ByteBuffer): Address {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Address): ULong = 8UL

    override fun write(value: Address, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * The Amount type can be used to express Bitcoin amounts that support arithmetic and conversion
 * to various denominations. The operations that Amount implements will panic when overflow or
 * underflow occurs. Also note that since the internal representation of amounts is unsigned,
 * subtracting below zero is considered an underflow and will cause a panic.
 */
public actual open class Amount: Disposable, AmountInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_amount(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_amount(pointer!!, status)
        }!!
    }

    
    /**
     * Express this Amount as a floating-point value in Bitcoin. Please be aware of the risk of
     * using floating-point numbers.
     */
    public actual override fun `toBtc`(): kotlin.Double {
        return FfiConverterDouble.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_amount_to_btc(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the number of satoshis in this Amount.
     */
    public actual override fun `toSat`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_amount_to_sat(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }


    
    public actual companion object {
        
        /**
         * Convert from a value expressing bitcoins to an Amount.
         */
        @Throws(ParseAmountException::class)
        public actual fun `fromBtc`(`btc`: kotlin.Double): Amount {
            return FfiConverterTypeAmount.lift(uniffiRustCallWithError(ParseAmountExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_amount_from_btc(
                    FfiConverterDouble.lower(`btc`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Create an Amount with satoshi precision and the given number of satoshis.
         */
        public actual fun `fromSat`(`satoshi`: kotlin.ULong): Amount {
            return FfiConverterTypeAmount.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_amount_from_sat(
                    FfiConverterULong.lower(`satoshi`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeAmount: FfiConverter<Amount, Pointer> {

    override fun lower(value: Amount): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Amount {
        return Amount(value)
    }

    override fun read(buf: ByteBuffer): Amount {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Amount): ULong = 8UL

    override fun write(value: Amount, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A bitcoin Block hash
 */
public actual open class BlockHash: Disposable, BlockHashInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_blockhash(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_blockhash(pointer!!, status)
        }!!
    }

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public actual override fun `serialize`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_blockhash_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_blockhash_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockHash) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_blockhash_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeBlockHash.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    actual override fun hashCode(): Int {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_blockhash_uniffi_trait_hash(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }).toInt()
    }

    
    public actual companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public actual fun `fromBytes`(`bytes`: kotlin.ByteArray): BlockHash {
            return FfiConverterTypeBlockHash.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_blockhash_from_bytes(
                    FfiConverterByteArray.lower(`bytes`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public actual fun `fromString`(`hex`: kotlin.String): BlockHash {
            return FfiConverterTypeBlockHash.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_blockhash_from_string(
                    FfiConverterString.lower(`hex`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeBlockHash: FfiConverter<BlockHash, Pointer> {

    override fun lower(value: BlockHash): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): BlockHash {
        return BlockHash(value)
    }

    override fun read(buf: ByteBuffer): BlockHash {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: BlockHash): ULong = 8UL

    override fun write(value: BlockHash, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A `BumpFeeTxBuilder` is created by calling `build_fee_bump` on a wallet. After assigning it, you set options on it
 * until finally calling `finish` to consume the builder and generate the transaction.
 */
public actual open class BumpFeeTxBuilder: Disposable, BumpFeeTxBuilderInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    public actual constructor(`txid`: Txid, `feeRate`: FeeRate) : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_bumpfeetxbuilder_new(
                FfiConverterTypeTxid.lower(`txid`),
                FfiConverterTypeFeeRate.lower(`feeRate`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_bumpfeetxbuilder(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_bumpfeetxbuilder(pointer!!, status)
        }!!
    }

    
    /**
     * Set whether the dust limit is checked.
     *
     * Note: by avoiding a dust limit check you may end up with a transaction that is non-standard.
     */
    public actual override fun `allowDust`(`allowDust`: kotlin.Boolean): BumpFeeTxBuilder {
        return FfiConverterTypeBumpFeeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_bumpfeetxbuilder_allow_dust(
                    it,
                    FfiConverterBoolean.lower(`allowDust`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set the current blockchain height.
     *
     * This will be used to:
     *
     * 1. Set the `nLockTime` for preventing fee sniping. Note: This will be ignored if you manually specify a
     * `nlocktime` using `TxBuilder::nlocktime`.
     *
     * 2. Decide whether coinbase outputs are mature or not. If the coinbase outputs are not mature at `current_height`,
     * we ignore them in the coin selection. If you want to create a transaction that spends immature coinbase inputs,
     * manually add them using `TxBuilder::add_utxos`.
     * In both cases, if you don’t provide a current height, we use the last sync height.
     */
    public actual override fun `currentHeight`(`height`: kotlin.UInt): BumpFeeTxBuilder {
        return FfiConverterTypeBumpFeeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_bumpfeetxbuilder_current_height(
                    it,
                    FfiConverterUInt.lower(`height`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Finish building the transaction.
     *
     * Uses the thread-local random number generator (rng).
     *
     * Returns a new `Psbt` per BIP174.
     *
     * WARNING: To avoid change address reuse you must persist the changes resulting from one or more calls to this
     * method before closing the wallet. See `Wallet::reveal_next_address`.
     */
    @Throws(CreateTxException::class)
    public actual override fun `finish`(`wallet`: Wallet): Psbt {
        return FfiConverterTypePsbt.lift(callWithPointer {
            uniffiRustCallWithError(CreateTxExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_bumpfeetxbuilder_finish(
                    it,
                    FfiConverterTypeWallet.lower(`wallet`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Use a specific nLockTime while creating the transaction.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "after" (`OP_CLTV`) operator.
     */
    public actual override fun `nlocktime`(`locktime`: LockTime): BumpFeeTxBuilder {
        return FfiConverterTypeBumpFeeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_bumpfeetxbuilder_nlocktime(
                    it,
                    FfiConverterTypeLockTime.lower(`locktime`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set an exact `nSequence` value.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "older" (`OP_CSV`) operator and the given
     * `nsequence` is lower than the CSV value.
     */
    public actual override fun `setExactSequence`(`nsequence`: kotlin.UInt): BumpFeeTxBuilder {
        return FfiConverterTypeBumpFeeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_bumpfeetxbuilder_set_exact_sequence(
                    it,
                    FfiConverterUInt.lower(`nsequence`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Build a transaction with a specific version.
     *
     * The version should always be greater than 0 and greater than 1 if the wallet’s descriptors contain an "older"
     * (`OP_CSV`) operator.
     */
    public actual override fun `version`(`version`: kotlin.Int): BumpFeeTxBuilder {
        return FfiConverterTypeBumpFeeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_bumpfeetxbuilder_version(
                    it,
                    FfiConverterInt.lower(`version`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeBumpFeeTxBuilder: FfiConverter<BumpFeeTxBuilder, Pointer> {

    override fun lower(value: BumpFeeTxBuilder): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): BumpFeeTxBuilder {
        return BumpFeeTxBuilder(value)
    }

    override fun read(buf: ByteBuffer): BumpFeeTxBuilder {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: BumpFeeTxBuilder): ULong = 8UL

    override fun write(value: BumpFeeTxBuilder, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * Build a BIP 157/158 light client to fetch transactions for a `Wallet`.
 *
 * Options:
 * * List of `Peer`: Bitcoin full-nodes for the light client to connect to. May be empty.
 * * `connections`: The number of connections for the light client to maintain.
 * * `scan_type`: Sync, recover, or start a new wallet. For more information see [`ScanType`].
 * * `data_dir`: Optional directory to store block headers and peers.
 *
 * A note on recovering wallets. Developers should allow users to provide an
 * approximate recovery height and an estimated number of transactions for the
 * wallet. When determining how many scripts to check filters for, the `Wallet`
 * `lookahead` value will be used. To ensure all transactions are recovered, the
 * `lookahead` should be roughly the number of transactions in the wallet history.
 */
public actual open class CbfBuilder: Disposable, CbfBuilderInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Start a new [`CbfBuilder`]
     */
    public actual constructor() : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_cbfbuilder_new(
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_cbfbuilder(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_cbfbuilder(pointer!!, status)
        }!!
    }

    
    /**
     * Construct a [`CbfComponents`] for a [`Wallet`].
     */
    public actual override fun `build`(`wallet`: Wallet): CbfComponents {
        return FfiConverterTypeCbfComponents.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_build(
                    it,
                    FfiConverterTypeWallet.lower(`wallet`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Configure the time in milliseconds that a node has to:
     * 1. Respond to the initial connection
     * 2. Respond to a request
     */
    public actual override fun `configureTimeoutMillis`(`handshake`: kotlin.ULong, `response`: kotlin.ULong): CbfBuilder {
        return FfiConverterTypeCbfBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_configure_timeout_millis(
                    it,
                    FfiConverterULong.lower(`handshake`),
                    FfiConverterULong.lower(`response`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * The number of connections for the light client to maintain. Default is two.
     */
    public actual override fun `connections`(`connections`: kotlin.UByte): CbfBuilder {
        return FfiConverterTypeCbfBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_connections(
                    it,
                    FfiConverterUByte.lower(`connections`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Directory to store block headers and peers. If none is provided, the current
     * working directory will be used.
     */
    public actual override fun `dataDir`(`dataDir`: kotlin.String): CbfBuilder {
        return FfiConverterTypeCbfBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_data_dir(
                    it,
                    FfiConverterString.lower(`dataDir`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Bitcoin full-nodes to attempt a connection with.
     */
    public actual override fun `peers`(`peers`: List<Peer>): CbfBuilder {
        return FfiConverterTypeCbfBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_peers(
                    it,
                    FfiConverterSequenceTypePeer.lower(`peers`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Select between syncing, recovering, or scanning for new wallets.
     */
    public actual override fun `scanType`(`scanType`: ScanType): CbfBuilder {
        return FfiConverterTypeCbfBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_scan_type(
                    it,
                    FfiConverterTypeScanType.lower(`scanType`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Configure connections to be established through a `Socks5 proxy. The vast majority of the
     * time, the connection is to a local Tor daemon, which is typically exposed at
     * `127.0.0.1:9050`.
     */
    public actual override fun `socks5Proxy`(`proxy`: Socks5Proxy): CbfBuilder {
        return FfiConverterTypeCbfBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfbuilder_socks5_proxy(
                    it,
                    FfiConverterTypeSocks5Proxy.lower(`proxy`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeCbfBuilder: FfiConverter<CbfBuilder, Pointer> {

    override fun lower(value: CbfBuilder): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): CbfBuilder {
        return CbfBuilder(value)
    }

    override fun read(buf: ByteBuffer): CbfBuilder {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: CbfBuilder): ULong = 8UL

    override fun write(value: CbfBuilder, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A [`CbfClient`] handles wallet updates from a [`CbfNode`].
 */
public actual open class CbfClient: Disposable, CbfClientInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_cbfclient(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_cbfclient(pointer!!, status)
        }!!
    }

    
    /**
     * Fetch the average fee rate for a block by requesting it from a peer. Not recommend for
     * resource-limited devices.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public actual override suspend fun `averageFeeRate`(`blockhash`: BlockHash): FeeRate {
        return uniffiRustCallAsync(
            callWithPointer { thisPtr ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_average_fee_rate(
                    thisPtr,
                    FfiConverterTypeBlockHash.lower(`blockhash`),
                )
            },
            { future, callback, continuation -> UniffiLib.ffi_bdk_rust_future_poll_pointer(future, callback, continuation) },
            { future, continuation -> UniffiLib.ffi_bdk_rust_future_complete_pointer(future, continuation) },
            { future -> UniffiLib.ffi_bdk_rust_future_free_pointer(future) },
            { future -> UniffiLib.ffi_bdk_rust_future_cancel_pointer(future) },
            // lift function
            { FfiConverterTypeFeeRate.lift(it!!) },
            // Error FFI converter
            CbfExceptionErrorHandler,
        )
    }

    /**
     * Broadcast a transaction to the network, erroring if the node has stopped running.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public actual override suspend fun `broadcast`(`transaction`: Transaction): Wtxid {
        return uniffiRustCallAsync(
            callWithPointer { thisPtr ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_broadcast(
                    thisPtr,
                    FfiConverterTypeTransaction.lower(`transaction`),
                )
            },
            { future, callback, continuation -> UniffiLib.ffi_bdk_rust_future_poll_pointer(future, callback, continuation) },
            { future, continuation -> UniffiLib.ffi_bdk_rust_future_complete_pointer(future, continuation) },
            { future -> UniffiLib.ffi_bdk_rust_future_free_pointer(future) },
            { future -> UniffiLib.ffi_bdk_rust_future_cancel_pointer(future) },
            // lift function
            { FfiConverterTypeWtxid.lift(it!!) },
            // Error FFI converter
            CbfExceptionErrorHandler,
        )
    }

    /**
     * Add another [`Peer`] to attempt a connection with.
     */
    @Throws(CbfException::class)
    public actual override fun `connect`(`peer`: Peer) {
        callWithPointer {
            uniffiRustCallWithError(CbfExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_connect(
                    it,
                    FfiConverterTypePeer.lower(`peer`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Check if the node is still running in the background.
     */
    public actual override fun `isRunning`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_is_running(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Query a Bitcoin DNS seeder using the configured resolver.
     *
     * This is **not** a generic DNS implementation. Host names are prefixed with a `x849` to filter
     * for compact block filter nodes from the seeder. For example `dns.myseeder.com` will be queried
     * as `x849.dns.myseeder.com`. This has no guarantee to return any `IpAddr`.
     */
    public actual override fun `lookupHost`(`hostname`: kotlin.String): List<IpAddress> {
        return FfiConverterSequenceTypeIpAddress.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_lookup_host(
                    it,
                    FfiConverterString.lower(`hostname`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * The minimum fee rate required to broadcast a transcation to all connected peers.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public actual override suspend fun `minBroadcastFeerate`(): FeeRate {
        return uniffiRustCallAsync(
            callWithPointer { thisPtr ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_min_broadcast_feerate(
                    thisPtr,
                )
            },
            { future, callback, continuation -> UniffiLib.ffi_bdk_rust_future_poll_pointer(future, callback, continuation) },
            { future, continuation -> UniffiLib.ffi_bdk_rust_future_complete_pointer(future, continuation) },
            { future -> UniffiLib.ffi_bdk_rust_future_free_pointer(future) },
            { future -> UniffiLib.ffi_bdk_rust_future_cancel_pointer(future) },
            // lift function
            { FfiConverterTypeFeeRate.lift(it!!) },
            // Error FFI converter
            CbfExceptionErrorHandler,
        )
    }

    /**
     * Return the next available info message from a node. If none is returned, the node has stopped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public actual override suspend fun `nextInfo`(): Info {
        return uniffiRustCallAsync(
            callWithPointer { thisPtr ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_next_info(
                    thisPtr,
                )
            },
            { future, callback, continuation -> UniffiLib.ffi_bdk_rust_future_poll_rust_buffer(future, callback, continuation) },
            { future, continuation -> UniffiLib.ffi_bdk_rust_future_complete_rust_buffer(future, continuation) },
            { future -> UniffiLib.ffi_bdk_rust_future_free_rust_buffer(future) },
            { future -> UniffiLib.ffi_bdk_rust_future_cancel_rust_buffer(future) },
            // lift function
            { FfiConverterTypeInfo.lift(it) },
            // Error FFI converter
            CbfExceptionErrorHandler,
        )
    }

    /**
     * Return the next available warning message from a node. If none is returned, the node has stopped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public actual override suspend fun `nextWarning`(): Warning {
        return uniffiRustCallAsync(
            callWithPointer { thisPtr ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_next_warning(
                    thisPtr,
                )
            },
            { future, callback, continuation -> UniffiLib.ffi_bdk_rust_future_poll_rust_buffer(future, callback, continuation) },
            { future, continuation -> UniffiLib.ffi_bdk_rust_future_complete_rust_buffer(future, continuation) },
            { future -> UniffiLib.ffi_bdk_rust_future_free_rust_buffer(future) },
            { future -> UniffiLib.ffi_bdk_rust_future_cancel_rust_buffer(future) },
            // lift function
            { FfiConverterTypeWarning.lift(it) },
            // Error FFI converter
            CbfExceptionErrorHandler,
        )
    }

    /**
     * Stop the [`CbfNode`]. Errors if the node is already stopped.
     */
    @Throws(CbfException::class)
    public actual override fun `shutdown`() {
        callWithPointer {
            uniffiRustCallWithError(CbfExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_shutdown(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Return an [`Update`]. This is method returns once the node syncs to the rest of
     * the network or a new block has been gossiped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public actual override suspend fun `update`(): Update {
        return uniffiRustCallAsync(
            callWithPointer { thisPtr ->
                UniffiLib.uniffi_bdk_fn_method_cbfclient_update(
                    thisPtr,
                )
            },
            { future, callback, continuation -> UniffiLib.ffi_bdk_rust_future_poll_pointer(future, callback, continuation) },
            { future, continuation -> UniffiLib.ffi_bdk_rust_future_complete_pointer(future, continuation) },
            { future -> UniffiLib.ffi_bdk_rust_future_free_pointer(future) },
            { future -> UniffiLib.ffi_bdk_rust_future_cancel_pointer(future) },
            // lift function
            { FfiConverterTypeUpdate.lift(it!!) },
            // Error FFI converter
            CbfExceptionErrorHandler,
        )
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeCbfClient: FfiConverter<CbfClient, Pointer> {

    override fun lower(value: CbfClient): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): CbfClient {
        return CbfClient(value)
    }

    override fun read(buf: ByteBuffer): CbfClient {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: CbfClient): ULong = 8UL

    override fun write(value: CbfClient, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A [`CbfNode`] gathers transactions for a [`Wallet`].
 * To receive [`Update`] for [`Wallet`], refer to the
 * [`CbfClient`]. The [`CbfNode`] will run until instructed
 * to stop.
 */
public actual open class CbfNode: Disposable, CbfNodeInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_cbfnode(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_cbfnode(pointer!!, status)
        }!!
    }

    
    /**
     * Start the node on a detached OS thread and immediately return.
     */
    public actual override fun `run`() {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_cbfnode_run(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeCbfNode: FfiConverter<CbfNode, Pointer> {

    override fun lower(value: CbfNode): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): CbfNode {
        return CbfNode(value)
    }

    override fun read(buf: ByteBuffer): CbfNode {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: CbfNode): ULong = 8UL

    override fun write(value: CbfNode, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class ChangeSet: Disposable, ChangeSetInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Create an empty `ChangeSet`.
     */
    public actual constructor() : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_changeset_new(
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_changeset(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_changeset(pointer!!, status)
        }!!
    }

    
    /**
     * Get the change `Descriptor`
     */
    public actual override fun `changeDescriptor`(): Descriptor? {
        return FfiConverterOptionalTypeDescriptor.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_changeset_change_descriptor(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the receiving `Descriptor`.
     */
    public actual override fun `descriptor`(): Descriptor? {
        return FfiConverterOptionalTypeDescriptor.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_changeset_descriptor(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the changes to the indexer.
     */
    public actual override fun `indexerChangeset`(): IndexerChangeSet {
        return FfiConverterTypeIndexerChangeSet.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_changeset_indexer_changeset(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the changes to the local chain.
     */
    public actual override fun `localchainChangeset`(): LocalChainChangeSet {
        return FfiConverterTypeLocalChainChangeSet.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_changeset_localchain_changeset(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the `Network`
     */
    public actual override fun `network`(): Network? {
        return FfiConverterOptionalTypeNetwork.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_changeset_network(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the changes to the transaction graph.
     */
    public actual override fun `txGraphChangeset`(): TxGraphChangeSet {
        return FfiConverterTypeTxGraphChangeSet.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_changeset_tx_graph_changeset(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }


    
    public actual companion object {
        
        public actual fun `fromAggregate`(`descriptor`: Descriptor?, `changeDescriptor`: Descriptor?, `network`: Network?, `localChain`: LocalChainChangeSet, `txGraph`: TxGraphChangeSet, `indexer`: IndexerChangeSet): ChangeSet {
            return FfiConverterTypeChangeSet.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_changeset_from_aggregate(
                    FfiConverterOptionalTypeDescriptor.lower(`descriptor`),
                    FfiConverterOptionalTypeDescriptor.lower(`changeDescriptor`),
                    FfiConverterOptionalTypeNetwork.lower(`network`),
                    FfiConverterTypeLocalChainChangeSet.lower(`localChain`),
                    FfiConverterTypeTxGraphChangeSet.lower(`txGraph`),
                    FfiConverterTypeIndexerChangeSet.lower(`indexer`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        public actual fun `fromDescriptorAndNetwork`(`descriptor`: Descriptor?, `changeDescriptor`: Descriptor?, `network`: Network?): ChangeSet {
            return FfiConverterTypeChangeSet.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_changeset_from_descriptor_and_network(
                    FfiConverterOptionalTypeDescriptor.lower(`descriptor`),
                    FfiConverterOptionalTypeDescriptor.lower(`changeDescriptor`),
                    FfiConverterOptionalTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Start a wallet `ChangeSet` from indexer changes.
         */
        public actual fun `fromIndexerChangeset`(`indexerChanges`: IndexerChangeSet): ChangeSet {
            return FfiConverterTypeChangeSet.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_changeset_from_indexer_changeset(
                    FfiConverterTypeIndexerChangeSet.lower(`indexerChanges`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Start a wallet `ChangeSet` from local chain changes.
         */
        public actual fun `fromLocalChainChanges`(`localChainChanges`: LocalChainChangeSet): ChangeSet {
            return FfiConverterTypeChangeSet.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_changeset_from_local_chain_changes(
                    FfiConverterTypeLocalChainChangeSet.lower(`localChainChanges`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Build a `ChangeSet` by merging together two `ChangeSet`.
         */
        public actual fun `fromMerge`(`left`: ChangeSet, `right`: ChangeSet): ChangeSet {
            return FfiConverterTypeChangeSet.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_changeset_from_merge(
                    FfiConverterTypeChangeSet.lower(`left`),
                    FfiConverterTypeChangeSet.lower(`right`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Start a wallet `ChangeSet` from transaction graph changes.
         */
        public actual fun `fromTxGraphChangeset`(`txGraphChangeset`: TxGraphChangeSet): ChangeSet {
            return FfiConverterTypeChangeSet.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_changeset_from_tx_graph_changeset(
                    FfiConverterTypeTxGraphChangeSet.lower(`txGraphChangeset`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeChangeSet: FfiConverter<ChangeSet, Pointer> {

    override fun lower(value: ChangeSet): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): ChangeSet {
        return ChangeSet(value)
    }

    override fun read(buf: ByteBuffer): ChangeSet {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: ChangeSet): ULong = 8UL

    override fun write(value: ChangeSet, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A BIP-32 derivation path.
 */
public actual open class DerivationPath: Disposable, DerivationPathInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Parse a string as a BIP-32 derivation path.
     */
    public actual constructor(`path`: kotlin.String) : this(
        uniffiRustCallWithError(Bip32ExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_derivationpath_new(
                FfiConverterString.lower(`path`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_derivationpath(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_derivationpath(pointer!!, status)
        }!!
    }

    
    /**
     * Returns `true` if the derivation path is empty
     */
    public actual override fun `isEmpty`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_derivationpath_is_empty(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns whether derivation path represents master key (i.e. it's length
     * is empty). True for `m` path.
     */
    public actual override fun `isMaster`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_derivationpath_is_master(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns length of the derivation path
     */
    public actual override fun `len`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_derivationpath_len(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_derivationpath_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    public actual companion object {
        
        /**
         * Returns derivation path for a master key (i.e. empty derivation path)
         */
        public actual fun `master`(): DerivationPath {
            return FfiConverterTypeDerivationPath.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_derivationpath_master(
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeDerivationPath: FfiConverter<DerivationPath, Pointer> {

    override fun lower(value: DerivationPath): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): DerivationPath {
        return DerivationPath(value)
    }

    override fun read(buf: ByteBuffer): DerivationPath {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: DerivationPath): ULong = 8UL

    override fun write(value: DerivationPath, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * An expression of how to derive output scripts: https://github.com/bitcoin/bitcoin/blob/master/doc/descriptors.md
 */
public actual open class Descriptor: Disposable, DescriptorInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Parse a string as a descriptor for the given network.
     */
    public actual constructor(`descriptor`: kotlin.String, `network`: Network) : this(
        uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_descriptor_new(
                FfiConverterString.lower(`descriptor`),
                FfiConverterTypeNetwork.lower(`network`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_descriptor(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_descriptor(pointer!!, status)
        }!!
    }

    
    public actual override fun `descType`(): DescriptorType {
        return FfiConverterTypeDescriptorType.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_desc_type(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * A unique identifier for the descriptor.
     */
    public actual override fun `descriptorId`(): DescriptorId {
        return FfiConverterTypeDescriptorId.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_descriptor_id(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Does this descriptor contain paths: https://github.com/bitcoin/bips/blob/master/bip-0389.mediawiki
     */
    public actual override fun `isMultipath`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_is_multipath(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Computes an upper bound on the difference between a non-satisfied `TxIn`'s
     * `segwit_weight` and a satisfied `TxIn`'s `segwit_weight`.
     */
    @Throws(DescriptorException::class)
    public actual override fun `maxWeightToSatisfy`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_max_weight_to_satisfy(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Return descriptors for all valid paths.
     */
    @Throws(MiniscriptException::class)
    public actual override fun `toSingleDescriptors`(): List<Descriptor> {
        return FfiConverterSequenceTypeDescriptor.lift(callWithPointer {
            uniffiRustCallWithError(MiniscriptExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_to_single_descriptors(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Dangerously convert the descriptor to a string.
     */
    public actual override fun `toStringWithSecret`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_to_string_with_secret(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptor_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    public actual companion object {
        
        /**
         * Multi-account hierarchy descriptor: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
         */
        public actual fun `newBip44`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip44(
                    FfiConverterTypeDescriptorSecretKey.lower(`secretKey`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Multi-account hierarchy descriptor: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
         */
        @Throws(DescriptorException::class)
        public actual fun `newBip44Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip44_public(
                    FfiConverterTypeDescriptorPublicKey.lower(`publicKey`),
                    FfiConverterString.lower(`fingerprint`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * P2SH nested P2WSH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki
         */
        public actual fun `newBip49`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip49(
                    FfiConverterTypeDescriptorSecretKey.lower(`secretKey`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * P2SH nested P2WSH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki
         */
        @Throws(DescriptorException::class)
        public actual fun `newBip49Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip49_public(
                    FfiConverterTypeDescriptorPublicKey.lower(`publicKey`),
                    FfiConverterString.lower(`fingerprint`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Pay to witness PKH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
         */
        public actual fun `newBip84`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip84(
                    FfiConverterTypeDescriptorSecretKey.lower(`secretKey`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Pay to witness PKH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
         */
        @Throws(DescriptorException::class)
        public actual fun `newBip84Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip84_public(
                    FfiConverterTypeDescriptorPublicKey.lower(`publicKey`),
                    FfiConverterString.lower(`fingerprint`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Single key P2TR descriptor: https://github.com/bitcoin/bips/blob/master/bip-0086.mediawiki
         */
        public actual fun `newBip86`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip86(
                    FfiConverterTypeDescriptorSecretKey.lower(`secretKey`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Single key P2TR descriptor: https://github.com/bitcoin/bips/blob/master/bip-0086.mediawiki
         */
        @Throws(DescriptorException::class)
        public actual fun `newBip86Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor {
            return FfiConverterTypeDescriptor.lift(uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptor_new_bip86_public(
                    FfiConverterTypeDescriptorPublicKey.lower(`publicKey`),
                    FfiConverterString.lower(`fingerprint`),
                    FfiConverterTypeKeychainKind.lower(`keychainKind`),
                    FfiConverterTypeNetwork.lower(`network`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeDescriptor: FfiConverter<Descriptor, Pointer> {

    override fun lower(value: Descriptor): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Descriptor {
        return Descriptor(value)
    }

    override fun read(buf: ByteBuffer): Descriptor {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Descriptor): ULong = 8UL

    override fun write(value: Descriptor, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A collision-proof unique identifier for a descriptor.
 */
public actual open class DescriptorId: Disposable, DescriptorIdInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_descriptorid(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_descriptorid(pointer!!, status)
        }!!
    }

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public actual override fun `serialize`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorid_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorid_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DescriptorId) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorid_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeDescriptorId.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    actual override fun hashCode(): Int {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorid_uniffi_trait_hash(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }).toInt()
    }

    
    public actual companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public actual fun `fromBytes`(`bytes`: kotlin.ByteArray): DescriptorId {
            return FfiConverterTypeDescriptorId.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptorid_from_bytes(
                    FfiConverterByteArray.lower(`bytes`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public actual fun `fromString`(`hex`: kotlin.String): DescriptorId {
            return FfiConverterTypeDescriptorId.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptorid_from_string(
                    FfiConverterString.lower(`hex`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeDescriptorId: FfiConverter<DescriptorId, Pointer> {

    override fun lower(value: DescriptorId): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): DescriptorId {
        return DescriptorId(value)
    }

    override fun read(buf: ByteBuffer): DescriptorId {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: DescriptorId): ULong = 8UL

    override fun write(value: DescriptorId, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A descriptor public key.
 */
public actual open class DescriptorPublicKey: Disposable, DescriptorPublicKeyInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_descriptorpublickey(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_descriptorpublickey(pointer!!, status)
        }!!
    }

    
    /**
     * Derive the descriptor public key at the given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public actual override fun `derive`(`path`: DerivationPath): DescriptorPublicKey {
        return FfiConverterTypeDescriptorPublicKey.lift(callWithPointer {
            uniffiRustCallWithError(DescriptorKeyExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorpublickey_derive(
                    it,
                    FfiConverterTypeDerivationPath.lower(`path`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Extend the descriptor public key by the given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public actual override fun `extend`(`path`: DerivationPath): DescriptorPublicKey {
        return FfiConverterTypeDescriptorPublicKey.lift(callWithPointer {
            uniffiRustCallWithError(DescriptorKeyExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorpublickey_extend(
                    it,
                    FfiConverterTypeDerivationPath.lower(`path`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Whether or not this key has multiple derivation paths.
     */
    public actual override fun `isMultipath`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorpublickey_is_multipath(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * The fingerprint of the master key associated with this key, `0x00000000` if none.
     */
    public actual override fun `masterFingerprint`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorpublickey_master_fingerprint(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorpublickey_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    public actual companion object {
        
        /**
         * Attempt to parse a string as a descriptor public key.
         */
        @Throws(DescriptorKeyException::class)
        public actual fun `fromString`(`publicKey`: kotlin.String): DescriptorPublicKey {
            return FfiConverterTypeDescriptorPublicKey.lift(uniffiRustCallWithError(DescriptorKeyExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptorpublickey_from_string(
                    FfiConverterString.lower(`publicKey`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeDescriptorPublicKey: FfiConverter<DescriptorPublicKey, Pointer> {

    override fun lower(value: DescriptorPublicKey): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): DescriptorPublicKey {
        return DescriptorPublicKey(value)
    }

    override fun read(buf: ByteBuffer): DescriptorPublicKey {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: DescriptorPublicKey): ULong = 8UL

    override fun write(value: DescriptorPublicKey, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A descriptor containing secret data.
 */
public actual open class DescriptorSecretKey: Disposable, DescriptorSecretKeyInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Construct a secret descriptor using a mnemonic.
     */
    public actual constructor(`network`: Network, `mnemonic`: Mnemonic, `password`: kotlin.String?) : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_descriptorsecretkey_new(
                FfiConverterTypeNetwork.lower(`network`),
                FfiConverterTypeMnemonic.lower(`mnemonic`),
                FfiConverterOptionalString.lower(`password`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_descriptorsecretkey(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_descriptorsecretkey(pointer!!, status)
        }!!
    }

    
    /**
     * Return the descriptor public key corresponding to this secret.
     */
    public actual override fun `asPublic`(): DescriptorPublicKey {
        return FfiConverterTypeDescriptorPublicKey.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorsecretkey_as_public(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Derive a descriptor secret key at a given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public actual override fun `derive`(`path`: DerivationPath): DescriptorSecretKey {
        return FfiConverterTypeDescriptorSecretKey.lift(callWithPointer {
            uniffiRustCallWithError(DescriptorKeyExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorsecretkey_derive(
                    it,
                    FfiConverterTypeDerivationPath.lower(`path`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Extend the descriptor secret key by the derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public actual override fun `extend`(`path`: DerivationPath): DescriptorSecretKey {
        return FfiConverterTypeDescriptorSecretKey.lift(callWithPointer {
            uniffiRustCallWithError(DescriptorKeyExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorsecretkey_extend(
                    it,
                    FfiConverterTypeDerivationPath.lower(`path`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Return the bytes of this descriptor secret key.
     */
    public actual override fun `secretBytes`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorsecretkey_secret_bytes(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_descriptorsecretkey_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    public actual companion object {
        
        /**
         * Attempt to parse a string as a descriptor secret key.
         */
        @Throws(DescriptorKeyException::class)
        public actual fun `fromString`(`privateKey`: kotlin.String): DescriptorSecretKey {
            return FfiConverterTypeDescriptorSecretKey.lift(uniffiRustCallWithError(DescriptorKeyExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_descriptorsecretkey_from_string(
                    FfiConverterString.lower(`privateKey`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeDescriptorSecretKey: FfiConverter<DescriptorSecretKey, Pointer> {

    override fun lower(value: DescriptorSecretKey): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): DescriptorSecretKey {
        return DescriptorSecretKey(value)
    }

    override fun read(buf: ByteBuffer): DescriptorSecretKey {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: DescriptorSecretKey): ULong = 8UL

    override fun write(value: DescriptorSecretKey, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * Wrapper around an electrum_client::ElectrumApi which includes an internal in-memory transaction
 * cache to avoid re-fetching already downloaded transactions.
 */
public actual open class ElectrumClient: Disposable, ElectrumClientInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Creates a new bdk client from a electrum_client::ElectrumApi
     * Optional: Set the proxy of the builder
     */
    public actual constructor(`url`: kotlin.String, `socks5`: kotlin.String?) : this(
        uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_electrumclient_new(
                FfiConverterString.lower(`url`),
                FfiConverterOptionalString.lower(`socks5`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_electrumclient(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_electrumclient(pointer!!, status)
        }!!
    }

    
    /**
     * Subscribes to notifications for new block headers, by sending a blockchain.headers.subscribe call.
     */
    @Throws(ElectrumException::class)
    public actual override fun `blockHeadersSubscribe`(): HeaderNotification {
        return FfiConverterTypeHeaderNotification.lift(callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_block_headers_subscribe(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Estimates the fee required in bitcoin per kilobyte to confirm a transaction in `number` blocks.
     */
    @Throws(ElectrumException::class)
    public actual override fun `estimateFee`(`number`: kotlin.ULong): kotlin.Double {
        return FfiConverterDouble.lift(callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_estimate_fee(
                    it,
                    FfiConverterULong.lower(`number`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Full scan the keychain scripts specified with the blockchain (via an Electrum client) and
     * returns updates for bdk_chain data structures.
     *
     * - `request`: struct with data required to perform a spk-based blockchain client
     * full scan, see `FullScanRequest`.
     * - `stop_gap`: the full scan for each keychain stops after a gap of script pubkeys with no
     * associated transactions.
     * - `batch_size`: specifies the max number of script pubkeys to request for in a single batch
     * request.
     * - `fetch_prev_txouts`: specifies whether we want previous `TxOuts` for fee calculation. Note
     * that this requires additional calls to the Electrum server, but is necessary for
     * calculating the fee on a transaction if your wallet does not own the inputs. Methods like
     * `Wallet.calculate_fee` and `Wallet.calculate_fee_rate` will return a
     * `CalculateFeeError::MissingTxOut` error if those TxOuts are not present in the transaction
     * graph.
     */
    @Throws(ElectrumException::class)
    public actual override fun `fullScan`(`request`: FullScanRequest, `stopGap`: kotlin.ULong, `batchSize`: kotlin.ULong, `fetchPrevTxouts`: kotlin.Boolean): Update {
        return FfiConverterTypeUpdate.lift(callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_full_scan(
                    it,
                    FfiConverterTypeFullScanRequest.lower(`request`),
                    FfiConverterULong.lower(`stopGap`),
                    FfiConverterULong.lower(`batchSize`),
                    FfiConverterBoolean.lower(`fetchPrevTxouts`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Pings the server.
     */
    @Throws(ElectrumException::class)
    public actual override fun `ping`() {
        callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_ping(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Returns the capabilities of the server.
     */
    @Throws(ElectrumException::class)
    public actual override fun `serverFeatures`(): ServerFeaturesRes {
        return FfiConverterTypeServerFeaturesRes.lift(callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_server_features(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Sync a set of scripts with the blockchain (via an Electrum client) for the data specified and returns updates for bdk_chain data structures.
     *
     * - `request`: struct with data required to perform a spk-based blockchain client
     * sync, see `SyncRequest`.
     * - `batch_size`: specifies the max number of script pubkeys to request for in a single batch
     * request.
     * - `fetch_prev_txouts`: specifies whether we want previous `TxOuts` for fee calculation. Note
     * that this requires additional calls to the Electrum server, but is necessary for
     * calculating the fee on a transaction if your wallet does not own the inputs. Methods like
     * `Wallet.calculate_fee` and `Wallet.calculate_fee_rate` will return a
     * `CalculateFeeError::MissingTxOut` error if those TxOuts are not present in the transaction
     * graph.
     *
     * If the scripts to sync are unknown, such as when restoring or importing a keychain that may
     * include scripts that have been used, use full_scan with the keychain.
     */
    @Throws(ElectrumException::class)
    public actual override fun `sync`(`request`: SyncRequest, `batchSize`: kotlin.ULong, `fetchPrevTxouts`: kotlin.Boolean): Update {
        return FfiConverterTypeUpdate.lift(callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_sync(
                    it,
                    FfiConverterTypeSyncRequest.lower(`request`),
                    FfiConverterULong.lower(`batchSize`),
                    FfiConverterBoolean.lower(`fetchPrevTxouts`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Broadcasts a transaction to the network.
     */
    @Throws(ElectrumException::class)
    public actual override fun `transactionBroadcast`(`tx`: Transaction): Txid {
        return FfiConverterTypeTxid.lift(callWithPointer {
            uniffiRustCallWithError(ElectrumExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_electrumclient_transaction_broadcast(
                    it,
                    FfiConverterTypeTransaction.lower(`tx`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeElectrumClient: FfiConverter<ElectrumClient, Pointer> {

    override fun lower(value: ElectrumClient): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): ElectrumClient {
        return ElectrumClient(value)
    }

    override fun read(buf: ByteBuffer): ElectrumClient {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: ElectrumClient): ULong = 8UL

    override fun write(value: ElectrumClient, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * Wrapper around an esplora_client::BlockingClient which includes an internal in-memory transaction
 * cache to avoid re-fetching already downloaded transactions.
 */
public actual open class EsploraClient: Disposable, EsploraClientInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Creates a new bdk client from an esplora_client::BlockingClient.
     * Optional: Set the proxy of the builder.
     */
    public actual constructor(`url`: kotlin.String, `proxy`: kotlin.String?) : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_esploraclient_new(
                FfiConverterString.lower(`url`),
                FfiConverterOptionalString.lower(`proxy`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_esploraclient(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_esploraclient(pointer!!, status)
        }!!
    }

    
    /**
     * Broadcast a [`Transaction`] to Esplora.
     */
    @Throws(EsploraException::class)
    public actual override fun `broadcast`(`transaction`: Transaction) {
        callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_broadcast(
                    it,
                    FfiConverterTypeTransaction.lower(`transaction`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Scan keychain scripts for transactions against Esplora, returning an update that can be
     * applied to the receiving structures.
     *
     * `request` provides the data required to perform a script-pubkey-based full scan
     * (see [`FullScanRequest`]). The full scan for each keychain (`K`) stops after a gap of
     * `stop_gap` script pubkeys with no associated transactions. `parallel_requests` specifies
     * the maximum number of HTTP requests to make in parallel.
     */
    @Throws(EsploraException::class)
    public actual override fun `fullScan`(`request`: FullScanRequest, `stopGap`: kotlin.ULong, `parallelRequests`: kotlin.ULong): Update {
        return FfiConverterTypeUpdate.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_full_scan(
                    it,
                    FfiConverterTypeFullScanRequest.lower(`request`),
                    FfiConverterULong.lower(`stopGap`),
                    FfiConverterULong.lower(`parallelRequests`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Get the [`BlockHash`] of a specific block height.
     */
    @Throws(EsploraException::class)
    public actual override fun `getBlockHash`(`blockHeight`: kotlin.UInt): BlockHash {
        return FfiConverterTypeBlockHash.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_get_block_hash(
                    it,
                    FfiConverterUInt.lower(`blockHeight`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Get a map where the key is the confirmation target (in number of
     * blocks) and the value is the estimated feerate (in sat/vB).
     */
    @Throws(EsploraException::class)
    public actual override fun `getFeeEstimates`(): Map<kotlin.UShort, kotlin.Double> {
        return FfiConverterMapUShortDouble.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_get_fee_estimates(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the height of the current blockchain tip.
     */
    @Throws(EsploraException::class)
    public actual override fun `getHeight`(): kotlin.UInt {
        return FfiConverterUInt.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_get_height(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get a [`Transaction`] option given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public actual override fun `getTx`(`txid`: Txid): Transaction? {
        return FfiConverterOptionalTypeTransaction.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_get_tx(
                    it,
                    FfiConverterTypeTxid.lower(`txid`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get transaction info given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public actual override fun `getTxInfo`(`txid`: Txid): Tx? {
        return FfiConverterOptionalTypeTx.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_get_tx_info(
                    it,
                    FfiConverterTypeTxid.lower(`txid`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the status of a [`Transaction`] given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public actual override fun `getTxStatus`(`txid`: Txid): TxStatus {
        return FfiConverterTypeTxStatus.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_get_tx_status(
                    it,
                    FfiConverterTypeTxid.lower(`txid`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Sync a set of scripts, txids, and/or outpoints against Esplora.
     *
     * `request` provides the data required to perform a script-pubkey-based sync (see
     * [`SyncRequest`]). `parallel_requests` specifies the maximum number of HTTP requests to make
     * in parallel.
     */
    @Throws(EsploraException::class)
    public actual override fun `sync`(`request`: SyncRequest, `parallelRequests`: kotlin.ULong): Update {
        return FfiConverterTypeUpdate.lift(callWithPointer {
            uniffiRustCallWithError(EsploraExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_esploraclient_sync(
                    it,
                    FfiConverterTypeSyncRequest.lower(`request`),
                    FfiConverterULong.lower(`parallelRequests`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeEsploraClient: FfiConverter<EsploraClient, Pointer> {

    override fun lower(value: EsploraClient): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): EsploraClient {
        return EsploraClient(value)
    }

    override fun read(buf: ByteBuffer): EsploraClient {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: EsploraClient): ULong = 8UL

    override fun write(value: EsploraClient, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * Represents fee rate.
 *
 * This is an integer type representing fee rate in sat/kwu. It provides protection against mixing
 * up the types as well as basic formatting features.
 */
public actual open class FeeRate: Disposable, FeeRateInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_feerate(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_feerate(pointer!!, status)
        }!!
    }

    
    public actual override fun `toSatPerKwu`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_feerate_to_sat_per_kwu(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `toSatPerVbCeil`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_feerate_to_sat_per_vb_ceil(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `toSatPerVbFloor`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_feerate_to_sat_per_vb_floor(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }


    
    public actual companion object {
        
        public actual fun `fromSatPerKwu`(`satKwu`: kotlin.ULong): FeeRate {
            return FfiConverterTypeFeeRate.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_feerate_from_sat_per_kwu(
                    FfiConverterULong.lower(`satKwu`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        @Throws(FeeRateException::class)
        public actual fun `fromSatPerVb`(`satVb`: kotlin.ULong): FeeRate {
            return FfiConverterTypeFeeRate.lift(uniffiRustCallWithError(FeeRateExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_feerate_from_sat_per_vb(
                    FfiConverterULong.lower(`satVb`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeFeeRate: FfiConverter<FeeRate, Pointer> {

    override fun lower(value: FeeRate): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): FeeRate {
        return FeeRate(value)
    }

    override fun read(buf: ByteBuffer): FeeRate {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: FeeRate): ULong = 8UL

    override fun write(value: FeeRate, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class FullScanRequest: Disposable, FullScanRequestInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_fullscanrequest(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_fullscanrequest(pointer!!, status)
        }!!
    }

    

    
    
    public actual companion object
    
}





public object FfiConverterTypeFullScanRequest: FfiConverter<FullScanRequest, Pointer> {

    override fun lower(value: FullScanRequest): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): FullScanRequest {
        return FullScanRequest(value)
    }

    override fun read(buf: ByteBuffer): FullScanRequest {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: FullScanRequest): ULong = 8UL

    override fun write(value: FullScanRequest, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class FullScanRequestBuilder: Disposable, FullScanRequestBuilderInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_fullscanrequestbuilder(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_fullscanrequestbuilder(pointer!!, status)
        }!!
    }

    
    @Throws(RequestBuilderException::class)
    public actual override fun `build`(): FullScanRequest {
        return FfiConverterTypeFullScanRequest.lift(callWithPointer {
            uniffiRustCallWithError(RequestBuilderExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_fullscanrequestbuilder_build(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    @Throws(RequestBuilderException::class)
    public actual override fun `inspectSpksForAllKeychains`(`inspector`: FullScanScriptInspector): FullScanRequestBuilder {
        return FfiConverterTypeFullScanRequestBuilder.lift(callWithPointer {
            uniffiRustCallWithError(RequestBuilderExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_fullscanrequestbuilder_inspect_spks_for_all_keychains(
                    it,
                    FfiConverterTypeFullScanScriptInspector.lower(`inspector`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeFullScanRequestBuilder: FfiConverter<FullScanRequestBuilder, Pointer> {

    override fun lower(value: FullScanRequestBuilder): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): FullScanRequestBuilder {
        return FullScanRequestBuilder(value)
    }

    override fun read(buf: ByteBuffer): FullScanRequestBuilder {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: FullScanRequestBuilder): ULong = 8UL

    override fun write(value: FullScanRequestBuilder, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class FullScanScriptInspectorImpl: Disposable, FullScanScriptInspector {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_fullscanscriptinspector(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_fullscanscriptinspector(pointer!!, status)
        }!!
    }

    
    public actual override fun `inspect`(`keychain`: KeychainKind, `index`: kotlin.UInt, `script`: Script) {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_fullscanscriptinspector_inspect(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    FfiConverterUInt.lower(`index`),
                    FfiConverterTypeScript.lower(`script`),
                    uniffiRustCallStatus,
                )
            }
        }
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeFullScanScriptInspector: FfiConverter<FullScanScriptInspector, Pointer> {
    internal val handleMap = UniffiHandleMap<FullScanScriptInspector>()

    override fun lower(value: FullScanScriptInspector): Pointer {
        return handleMap.insert(value).toPointer()
    }

    override fun lift(value: Pointer): FullScanScriptInspector {
        return FullScanScriptInspectorImpl(value)
    }

    override fun read(buf: ByteBuffer): FullScanScriptInspector {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: FullScanScriptInspector): ULong = 8UL

    override fun write(value: FullScanScriptInspector, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}


// Put the implementation in an object so we don't pollute the top-level namespace
internal object uniffiCallbackInterfaceFullScanScriptInspector {
    internal object `inspect`: UniffiCallbackInterfaceFullScanScriptInspectorMethod0 {
        override fun callback (
            `uniffiHandle`: Long,
            `keychain`: RustBufferByValue,
            `index`: Int,
            `script`: Pointer?,
            `uniffiOutReturn`: Pointer,
            uniffiCallStatus: UniffiRustCallStatus,
        ) {
            val uniffiObj = FfiConverterTypeFullScanScriptInspector.handleMap.get(uniffiHandle)
            val makeCall = { ->
                uniffiObj.`inspect`(
                    FfiConverterTypeKeychainKind.lift(`keychain`),
                    FfiConverterUInt.lift(`index`),
                    FfiConverterTypeScript.lift(`script`!!),
                )
            }
            val writeReturn = { _: Unit ->
                @Suppress("UNUSED_EXPRESSION")
                uniffiOutReturn
                Unit
            }
            uniffiTraitInterfaceCall(uniffiCallStatus, makeCall, writeReturn)
        }
    }
    internal object uniffiFree: UniffiCallbackInterfaceFree {
        override fun callback(handle: Long) {
            FfiConverterTypeFullScanScriptInspector.handleMap.remove(handle)
        }
    }

    internal val vtable = UniffiVTableCallbackInterfaceFullScanScriptInspector(
        `inspect`,
        uniffiFree,
    )

    internal fun register(lib: UniffiLib) {
        lib.uniffi_bdk_fn_init_callback_vtable_fullscanscriptinspector(vtable)
    }
}



/**
 * An [`OutPoint`] used as a key in a hash map.
 *
 * Due to limitations in generating the foreign language bindings, we cannot use [`OutPoint`] as a
 * key for hash maps.
 */
public actual open class HashableOutPoint: Disposable, HashableOutPointInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Create a key for a key-value store from an [`OutPoint`]
     */
    public actual constructor(`outpoint`: OutPoint) : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_hashableoutpoint_new(
                FfiConverterTypeOutPoint.lower(`outpoint`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_hashableoutpoint(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_hashableoutpoint(pointer!!, status)
        }!!
    }

    
    /**
     * Get the internal [`OutPoint`]
     */
    public actual override fun `outpoint`(): OutPoint {
        return FfiConverterTypeOutPoint.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_hashableoutpoint_outpoint(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HashableOutPoint) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeHashableOutPoint.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    actual override fun hashCode(): Int {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_hash(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }).toInt()
    }

    
    
    public actual companion object
    
}





public object FfiConverterTypeHashableOutPoint: FfiConverter<HashableOutPoint, Pointer> {

    override fun lower(value: HashableOutPoint): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): HashableOutPoint {
        return HashableOutPoint(value)
    }

    override fun read(buf: ByteBuffer): HashableOutPoint {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: HashableOutPoint): ULong = 8UL

    override fun write(value: HashableOutPoint, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * An IP address to connect to over TCP.
 */
public actual open class IpAddress: Disposable, IpAddressInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_ipaddress(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_ipaddress(pointer!!, status)
        }!!
    }

    

    
    public actual companion object {
        
        /**
         * Build an IPv4 address.
         */
        public actual fun `fromIpv4`(`q1`: kotlin.UByte, `q2`: kotlin.UByte, `q3`: kotlin.UByte, `q4`: kotlin.UByte): IpAddress {
            return FfiConverterTypeIpAddress.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_ipaddress_from_ipv4(
                    FfiConverterUByte.lower(`q1`),
                    FfiConverterUByte.lower(`q2`),
                    FfiConverterUByte.lower(`q3`),
                    FfiConverterUByte.lower(`q4`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Build an IPv6 address.
         */
        public actual fun `fromIpv6`(`a`: kotlin.UShort, `b`: kotlin.UShort, `c`: kotlin.UShort, `d`: kotlin.UShort, `e`: kotlin.UShort, `f`: kotlin.UShort, `g`: kotlin.UShort, `h`: kotlin.UShort): IpAddress {
            return FfiConverterTypeIpAddress.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_ipaddress_from_ipv6(
                    FfiConverterUShort.lower(`a`),
                    FfiConverterUShort.lower(`b`),
                    FfiConverterUShort.lower(`c`),
                    FfiConverterUShort.lower(`d`),
                    FfiConverterUShort.lower(`e`),
                    FfiConverterUShort.lower(`f`),
                    FfiConverterUShort.lower(`g`),
                    FfiConverterUShort.lower(`h`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeIpAddress: FfiConverter<IpAddress, Pointer> {

    override fun lower(value: IpAddress): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): IpAddress {
        return IpAddress(value)
    }

    override fun read(buf: ByteBuffer): IpAddress {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: IpAddress): ULong = 8UL

    override fun write(value: IpAddress, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A mnemonic seed phrase to recover a BIP-32 wallet.
 */
public actual open class Mnemonic: Disposable, MnemonicInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Generate a mnemonic given a word count.
     */
    public actual constructor(`wordCount`: WordCount) : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_mnemonic_new(
                FfiConverterTypeWordCount.lower(`wordCount`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_mnemonic(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_mnemonic(pointer!!, status)
        }!!
    }

    
    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_mnemonic_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    public actual companion object {
        
        /**
         * Construct a mnemonic given an array of bytes. Note that using weak entropy will result in a loss
         * of funds. To ensure the entropy is generated properly, read about your operating
         * system specific ways to generate secure random numbers.
         */
        @Throws(Bip39Exception::class)
        public actual fun `fromEntropy`(`entropy`: kotlin.ByteArray): Mnemonic {
            return FfiConverterTypeMnemonic.lift(uniffiRustCallWithError(Bip39ExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_mnemonic_from_entropy(
                    FfiConverterByteArray.lower(`entropy`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Parse a string as a mnemonic seed phrase.
         */
        @Throws(Bip39Exception::class)
        public actual fun `fromString`(`mnemonic`: kotlin.String): Mnemonic {
            return FfiConverterTypeMnemonic.lift(uniffiRustCallWithError(Bip39ExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_mnemonic_from_string(
                    FfiConverterString.lower(`mnemonic`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeMnemonic: FfiConverter<Mnemonic, Pointer> {

    override fun lower(value: Mnemonic): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Mnemonic {
        return Mnemonic(value)
    }

    override fun read(buf: ByteBuffer): Mnemonic {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Mnemonic): ULong = 8UL

    override fun write(value: Mnemonic, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * Definition of a wallet persistence implementation.
 */
public actual open class PersistenceImpl: Disposable, Persistence {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_persistence(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_persistence(pointer!!, status)
        }!!
    }

    
    /**
     * Initialize the total aggregate `ChangeSet` for the underlying wallet.
     */
    @Throws(PersistenceException::class)
    public actual override fun `initialize`(): ChangeSet {
        return FfiConverterTypeChangeSet.lift(callWithPointer {
            uniffiRustCallWithError(PersistenceExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_persistence_initialize(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Persist a `ChangeSet` to the total aggregate changeset of the wallet.
     */
    @Throws(PersistenceException::class)
    public actual override fun `persist`(`changeset`: ChangeSet) {
        callWithPointer {
            uniffiRustCallWithError(PersistenceExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_persistence_persist(
                    it,
                    FfiConverterTypeChangeSet.lower(`changeset`),
                    uniffiRustCallStatus,
                )
            }
        }
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypePersistence: FfiConverter<Persistence, Pointer> {
    internal val handleMap = UniffiHandleMap<Persistence>()

    override fun lower(value: Persistence): Pointer {
        return handleMap.insert(value).toPointer()
    }

    override fun lift(value: Pointer): Persistence {
        return PersistenceImpl(value)
    }

    override fun read(buf: ByteBuffer): Persistence {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Persistence): ULong = 8UL

    override fun write(value: Persistence, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}


// Put the implementation in an object so we don't pollute the top-level namespace
internal object uniffiCallbackInterfacePersistence {
    internal object `initialize`: UniffiCallbackInterfacePersistenceMethod0 {
        override fun callback (
            `uniffiHandle`: Long,
            `uniffiOutReturn`: PointerByReference,
            uniffiCallStatus: UniffiRustCallStatus,
        ) {
            val uniffiObj = FfiConverterTypePersistence.handleMap.get(uniffiHandle)
            val makeCall = { ->
                uniffiObj.`initialize`(
                )
            }
            val writeReturn = { uniffiResultValue: ChangeSet ->
                uniffiOutReturn.setValue(FfiConverterTypeChangeSet.lower(uniffiResultValue))
            }
            uniffiTraitInterfaceCallWithError(
                uniffiCallStatus,
                makeCall,
                writeReturn,
            ) { e: PersistenceException -> FfiConverterTypePersistenceError.lower(e) }
        }
    }
    internal object `persist`: UniffiCallbackInterfacePersistenceMethod1 {
        override fun callback (
            `uniffiHandle`: Long,
            `changeset`: Pointer?,
            `uniffiOutReturn`: Pointer,
            uniffiCallStatus: UniffiRustCallStatus,
        ) {
            val uniffiObj = FfiConverterTypePersistence.handleMap.get(uniffiHandle)
            val makeCall = { ->
                uniffiObj.`persist`(
                    FfiConverterTypeChangeSet.lift(`changeset`!!),
                )
            }
            val writeReturn = { _: Unit ->
                @Suppress("UNUSED_EXPRESSION")
                uniffiOutReturn
                Unit
            }
            uniffiTraitInterfaceCallWithError(
                uniffiCallStatus,
                makeCall,
                writeReturn,
            ) { e: PersistenceException -> FfiConverterTypePersistenceError.lower(e) }
        }
    }
    internal object uniffiFree: UniffiCallbackInterfaceFree {
        override fun callback(handle: Long) {
            FfiConverterTypePersistence.handleMap.remove(handle)
        }
    }

    internal val vtable = UniffiVTableCallbackInterfacePersistence(
        `initialize`,
        `persist`,
        uniffiFree,
    )

    internal fun register(lib: UniffiLib) {
        lib.uniffi_bdk_fn_init_callback_vtable_persistence(vtable)
    }
}



/**
 * Wallet backend implementations.
 */
public actual open class Persister: Disposable, PersisterInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_persister(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_persister(pointer!!, status)
        }!!
    }

    

    
    public actual companion object {
        
        /**
         * Use a native persistence layer.
         */
        public actual fun `custom`(`persistence`: Persistence): Persister {
            return FfiConverterTypePersister.lift(uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_persister_custom(
                    FfiConverterTypePersistence.lower(`persistence`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Create a new connection in memory.
         */
        @Throws(PersistenceException::class)
        public actual fun `newInMemory`(): Persister {
            return FfiConverterTypePersister.lift(uniffiRustCallWithError(PersistenceExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_persister_new_in_memory(
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Create a new Sqlite connection at the specified file path.
         */
        @Throws(PersistenceException::class)
        public actual fun `newSqlite`(`path`: kotlin.String): Persister {
            return FfiConverterTypePersister.lift(uniffiRustCallWithError(PersistenceExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_persister_new_sqlite(
                    FfiConverterString.lower(`path`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypePersister: FfiConverter<Persister, Pointer> {

    override fun lower(value: Persister): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Persister {
        return Persister(value)
    }

    override fun read(buf: ByteBuffer): Persister {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Persister): ULong = 8UL

    override fun write(value: Persister, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * Descriptor spending policy
 */
public actual open class Policy: Disposable, PolicyInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_policy(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_policy(pointer!!, status)
        }!!
    }

    
    public actual override fun `asString`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_policy_as_string(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `contribution`(): Satisfaction {
        return FfiConverterTypeSatisfaction.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_policy_contribution(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `id`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_policy_id(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `item`(): SatisfiableItem {
        return FfiConverterTypeSatisfiableItem.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_policy_item(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `requiresPath`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_policy_requires_path(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    public actual override fun `satisfaction`(): Satisfaction {
        return FfiConverterTypeSatisfaction.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_policy_satisfaction(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypePolicy: FfiConverter<Policy, Pointer> {

    override fun lower(value: Policy): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Policy {
        return Policy(value)
    }

    override fun read(buf: ByteBuffer): Policy {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Policy): ULong = 8UL

    override fun write(value: Policy, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A Partially Signed Transaction.
 */
public actual open class Psbt: Disposable, PsbtInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Creates a new `Psbt` instance from a base64-encoded string.
     */
    public actual constructor(`psbtBase64`: kotlin.String) : this(
        uniffiRustCallWithError(PsbtParseExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_psbt_new(
                FfiConverterString.lower(`psbtBase64`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_psbt(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_psbt(pointer!!, status)
        }!!
    }

    
    /**
     * Combines this `Psbt` with `other` PSBT as described by BIP 174.
     *
     * In accordance with BIP 174 this function is commutative i.e., `A.combine(B) == B.combine(A)`
     */
    @Throws(PsbtException::class)
    public actual override fun `combine`(`other`: Psbt): Psbt {
        return FfiConverterTypePsbt.lift(callWithPointer {
            uniffiRustCallWithError(PsbtExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_combine(
                    it,
                    FfiConverterTypePsbt.lower(`other`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Extracts the `Transaction` from a `Psbt` by filling in the available signature information.
     *
     * #### Errors
     *
     * `ExtractTxError` variants will contain either the `Psbt` itself or the `Transaction`
     * that was extracted. These can be extracted from the Errors in order to recover.
     * See the error documentation for info on the variants. In general, it covers large fees.
     */
    @Throws(ExtractTxException::class)
    public actual override fun `extractTx`(): Transaction {
        return FfiConverterTypeTransaction.lift(callWithPointer {
            uniffiRustCallWithError(ExtractTxExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_extract_tx(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Calculates transaction fee.
     *
     * 'Fee' being the amount that will be paid for mining a transaction with the current inputs
     * and outputs i.e., the difference in value of the total inputs and the total outputs.
     *
     * #### Errors
     *
     * - `MissingUtxo` when UTXO information for any input is not present or is invalid.
     * - `NegativeFee` if calculated value is negative.
     * - `FeeOverflow` if an integer overflow occurs.
     */
    @Throws(PsbtException::class)
    public actual override fun `fee`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCallWithError(PsbtExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_fee(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Finalizes the current PSBT and produces a result indicating
     *
     * whether the finalization was successful or not.
     */
    public actual override fun `finalize`(): FinalizedPsbtResult {
        return FfiConverterTypeFinalizedPsbtResult.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_finalize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * The corresponding key-value map for each input in the unsigned transaction.
     */
    public actual override fun `input`(): List<Input> {
        return FfiConverterSequenceTypeInput.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_input(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Serializes the PSBT into a JSON string representation.
     */
    public actual override fun `jsonSerialize`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_json_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Serialize the PSBT into a base64-encoded string.
     */
    public actual override fun `serialize`(): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the spending utxo for this PSBT's input at `input_index`.
     */
    public actual override fun `spendUtxo`(`inputIndex`: kotlin.ULong): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_spend_utxo(
                    it,
                    FfiConverterULong.lower(`inputIndex`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Write the `Psbt` to a file. Note that the file must not yet exist.
     */
    @Throws(PsbtException::class)
    public actual override fun `writeToFile`(`path`: kotlin.String) {
        callWithPointer {
            uniffiRustCallWithError(PsbtExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_psbt_write_to_file(
                    it,
                    FfiConverterString.lower(`path`),
                    uniffiRustCallStatus,
                )
            }
        }
    }


    
    public actual companion object {
        
        /**
         * Create a new `Psbt` from a `.psbt` file.
         */
        @Throws(PsbtException::class)
        public actual fun `fromFile`(`path`: kotlin.String): Psbt {
            return FfiConverterTypePsbt.lift(uniffiRustCallWithError(PsbtExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_psbt_from_file(
                    FfiConverterString.lower(`path`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Creates a PSBT from an unsigned transaction.
         *
         * # Errors
         *
         * If transactions is not unsigned.
         */
        @Throws(PsbtException::class)
        public actual fun `fromUnsignedTx`(`tx`: Transaction): Psbt {
            return FfiConverterTypePsbt.lift(uniffiRustCallWithError(PsbtExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_psbt_from_unsigned_tx(
                    FfiConverterTypeTransaction.lower(`tx`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypePsbt: FfiConverter<Psbt, Pointer> {

    override fun lower(value: Psbt): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Psbt {
        return Psbt(value)
    }

    override fun read(buf: ByteBuffer): Psbt {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Psbt): ULong = 8UL

    override fun write(value: Psbt, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A bitcoin script: https://en.bitcoin.it/wiki/Script
 */
public actual open class Script: Disposable, ScriptInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Interpret an array of bytes as a bitcoin script.
     */
    public actual constructor(`rawOutputScript`: kotlin.ByteArray) : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_script_new(
                FfiConverterByteArray.lower(`rawOutputScript`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_script(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_script(pointer!!, status)
        }!!
    }

    
    /**
     * Convert a script into an array of bytes.
     */
    public actual override fun `toBytes`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_script_to_bytes(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_script_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    
    public actual companion object
    
}





public object FfiConverterTypeScript: FfiConverter<Script, Pointer> {

    override fun lower(value: Script): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Script {
        return Script(value)
    }

    override fun read(buf: ByteBuffer): Script {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Script): ULong = 8UL

    override fun write(value: Script, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class SyncRequest: Disposable, SyncRequestInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_syncrequest(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_syncrequest(pointer!!, status)
        }!!
    }

    

    
    
    public actual companion object
    
}





public object FfiConverterTypeSyncRequest: FfiConverter<SyncRequest, Pointer> {

    override fun lower(value: SyncRequest): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): SyncRequest {
        return SyncRequest(value)
    }

    override fun read(buf: ByteBuffer): SyncRequest {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: SyncRequest): ULong = 8UL

    override fun write(value: SyncRequest, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class SyncRequestBuilder: Disposable, SyncRequestBuilderInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_syncrequestbuilder(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_syncrequestbuilder(pointer!!, status)
        }!!
    }

    
    @Throws(RequestBuilderException::class)
    public actual override fun `build`(): SyncRequest {
        return FfiConverterTypeSyncRequest.lift(callWithPointer {
            uniffiRustCallWithError(RequestBuilderExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_syncrequestbuilder_build(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    @Throws(RequestBuilderException::class)
    public actual override fun `inspectSpks`(`inspector`: SyncScriptInspector): SyncRequestBuilder {
        return FfiConverterTypeSyncRequestBuilder.lift(callWithPointer {
            uniffiRustCallWithError(RequestBuilderExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_syncrequestbuilder_inspect_spks(
                    it,
                    FfiConverterTypeSyncScriptInspector.lower(`inspector`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeSyncRequestBuilder: FfiConverter<SyncRequestBuilder, Pointer> {

    override fun lower(value: SyncRequestBuilder): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): SyncRequestBuilder {
        return SyncRequestBuilder(value)
    }

    override fun read(buf: ByteBuffer): SyncRequestBuilder {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: SyncRequestBuilder): ULong = 8UL

    override fun write(value: SyncRequestBuilder, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



public actual open class SyncScriptInspectorImpl: Disposable, SyncScriptInspector {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_syncscriptinspector(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_syncscriptinspector(pointer!!, status)
        }!!
    }

    
    public actual override fun `inspect`(`script`: Script, `total`: kotlin.ULong) {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_syncscriptinspector_inspect(
                    it,
                    FfiConverterTypeScript.lower(`script`),
                    FfiConverterULong.lower(`total`),
                    uniffiRustCallStatus,
                )
            }
        }
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeSyncScriptInspector: FfiConverter<SyncScriptInspector, Pointer> {
    internal val handleMap = UniffiHandleMap<SyncScriptInspector>()

    override fun lower(value: SyncScriptInspector): Pointer {
        return handleMap.insert(value).toPointer()
    }

    override fun lift(value: Pointer): SyncScriptInspector {
        return SyncScriptInspectorImpl(value)
    }

    override fun read(buf: ByteBuffer): SyncScriptInspector {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: SyncScriptInspector): ULong = 8UL

    override fun write(value: SyncScriptInspector, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}


// Put the implementation in an object so we don't pollute the top-level namespace
internal object uniffiCallbackInterfaceSyncScriptInspector {
    internal object `inspect`: UniffiCallbackInterfaceSyncScriptInspectorMethod0 {
        override fun callback (
            `uniffiHandle`: Long,
            `script`: Pointer?,
            `total`: Long,
            `uniffiOutReturn`: Pointer,
            uniffiCallStatus: UniffiRustCallStatus,
        ) {
            val uniffiObj = FfiConverterTypeSyncScriptInspector.handleMap.get(uniffiHandle)
            val makeCall = { ->
                uniffiObj.`inspect`(
                    FfiConverterTypeScript.lift(`script`!!),
                    FfiConverterULong.lift(`total`),
                )
            }
            val writeReturn = { _: Unit ->
                @Suppress("UNUSED_EXPRESSION")
                uniffiOutReturn
                Unit
            }
            uniffiTraitInterfaceCall(uniffiCallStatus, makeCall, writeReturn)
        }
    }
    internal object uniffiFree: UniffiCallbackInterfaceFree {
        override fun callback(handle: Long) {
            FfiConverterTypeSyncScriptInspector.handleMap.remove(handle)
        }
    }

    internal val vtable = UniffiVTableCallbackInterfaceSyncScriptInspector(
        `inspect`,
        uniffiFree,
    )

    internal fun register(lib: UniffiLib) {
        lib.uniffi_bdk_fn_init_callback_vtable_syncscriptinspector(vtable)
    }
}



/**
 * Bitcoin transaction.
 * An authenticated movement of coins.
 */
public actual open class Transaction: Disposable, TransactionInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Creates a new `Transaction` instance from serialized transaction bytes.
     */
    public actual constructor(`transactionBytes`: kotlin.ByteArray) : this(
        uniffiRustCallWithError(TransactionExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_transaction_new(
                FfiConverterByteArray.lower(`transactionBytes`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_transaction(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_transaction(pointer!!, status)
        }!!
    }

    
    /**
     * Computes the Txid.
     * Hashes the transaction excluding the segwit data (i.e. the marker, flag bytes, and the witness fields themselves).
     */
    public actual override fun `computeTxid`(): Txid {
        return FfiConverterTypeTxid.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_compute_txid(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Compute the Wtxid, which includes the witness in the transaction hash.
     */
    public actual override fun `computeWtxid`(): Wtxid {
        return FfiConverterTypeWtxid.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_compute_wtxid(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * List of transaction inputs.
     */
    public actual override fun `input`(): List<TxIn> {
        return FfiConverterSequenceTypeTxIn.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_input(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Checks if this is a coinbase transaction.
     * The first transaction in the block distributes the mining reward and is called the coinbase transaction.
     * It is impossible to check if the transaction is first in the block, so this function checks the structure
     * of the transaction instead - the previous output must be all-zeros (creates satoshis “out of thin air”).
     */
    public actual override fun `isCoinbase`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_is_coinbase(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns `true` if the transaction itself opted in to be BIP-125-replaceable (RBF).
     *
     * # Warning
     *
     * **Incorrectly relying on RBF may lead to monetary loss!**
     *
     * This **does not** cover the case where a transaction becomes replaceable due to ancestors
     * being RBF. Please note that transactions **may be replaced** even if they **do not** include
     * the RBF signal: <https://bitcoinops.org/en/newsletters/2022/10/19/#transaction-replacement-option>.
     */
    public actual override fun `isExplicitlyRbf`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_is_explicitly_rbf(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns `true` if this transactions nLockTime is enabled ([BIP-65]).
     *
     * [BIP-65]: https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
     */
    public actual override fun `isLockTimeEnabled`(): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_is_lock_time_enabled(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Block height or timestamp. Transaction cannot be included in a block until this height/time.
     *
     * /// ### Relevant BIPs
     *
     * * [BIP-65 OP_CHECKLOCKTIMEVERIFY](https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki)
     * * [BIP-113 Median time-past as endpoint for lock-time calculations](https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki)
     */
    public actual override fun `lockTime`(): kotlin.UInt {
        return FfiConverterUInt.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_lock_time(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * List of transaction outputs.
     */
    public actual override fun `output`(): List<TxOut> {
        return FfiConverterSequenceTypeTxOut.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_output(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Serialize transaction into consensus-valid format. See https://docs.rs/bitcoin/latest/bitcoin/struct.Transaction.html#serialization-notes for more notes on transaction serialization.
     */
    public actual override fun `serialize`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the total transaction size
     *
     * Total transaction size is the transaction size in bytes serialized as described in BIP144,
     * including base data and witness data.
     */
    public actual override fun `totalSize`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_total_size(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * The protocol version, is currently expected to be 1 or 2 (BIP 68).
     */
    public actual override fun `version`(): kotlin.Int {
        return FfiConverterInt.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_version(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the "virtual size" (vsize) of this transaction.
     *
     * Will be `ceil(weight / 4.0)`. Note this implements the virtual size as per [`BIP141`], which
     * is different to what is implemented in Bitcoin Core.
     * > Virtual transaction size is defined as Transaction weight / 4 (rounded up to the next integer).
     *
     * [`BIP141`]: https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki
     */
    public actual override fun `vsize`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_vsize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the weight of this transaction, as defined by BIP-141.
     *
     * > Transaction weight is defined as Base transaction size * 3 + Total transaction size (ie.
     * > the same method as calculating Block weight from Base size and Total size).
     *
     * For transactions with an empty witness, this is simply the consensus-serialized size times
     * four. For transactions with a witness, this is the non-witness consensus-serialized size
     * multiplied by three plus the with-witness consensus-serialized size.
     *
     * For transactions with no inputs, this function will return a value 2 less than the actual
     * weight of the serialized transaction. The reason is that zero-input transactions, post-segwit,
     * cannot be unambiguously serialized; we make a choice that adds two extra bytes. For more
     * details see [BIP 141](https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki)
     * which uses a "input count" of `0x00` as a `marker` for a Segwit-encoded transaction.
     *
     * If you need to use 0-input transactions, we strongly recommend you do so using the PSBT
     * API. The unsigned transaction encoded within PSBT is always a non-segwit transaction
     * and can therefore avoid this ambiguity.
     */
    public actual override fun `weight`(): kotlin.ULong {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_weight(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transaction) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_transaction_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeTransaction.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    

    
    
    public actual companion object
    
}





public object FfiConverterTypeTransaction: FfiConverter<Transaction, Pointer> {

    override fun lower(value: Transaction): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Transaction {
        return Transaction(value)
    }

    override fun read(buf: ByteBuffer): Transaction {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Transaction): ULong = 8UL

    override fun write(value: Transaction, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A `TxBuilder` is created by calling `build_tx` on a wallet. After assigning it, you set options on it until finally
 * calling `finish` to consume the builder and generate the transaction.
 */
public actual open class TxBuilder: Disposable, TxBuilderInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    public actual constructor() : this(
        uniffiRustCall { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_txbuilder_new(
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_txbuilder(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_txbuilder(pointer!!, status)
        }!!
    }

    
    /**
     * Add data as an output using `OP_RETURN`.
     */
    public actual override fun `addData`(`data`: kotlin.ByteArray): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_add_data(
                    it,
                    FfiConverterByteArray.lower(`data`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Fill-in the `PSBT_GLOBAL_XPUB` field with the extended keys contained in both the external and internal
     * descriptors.
     *
     * This is useful for offline signers that take part to a multisig. Some hardware wallets like BitBox and ColdCard
     * are known to require this.
     */
    public actual override fun `addGlobalXpubs`(): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_add_global_xpubs(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Add a recipient to the internal list of recipients.
     */
    public actual override fun `addRecipient`(`script`: Script, `amount`: Amount): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_add_recipient(
                    it,
                    FfiConverterTypeScript.lower(`script`),
                    FfiConverterTypeAmount.lower(`amount`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Add a utxo to the internal list of unspendable utxos.
     *
     * It’s important to note that the "must-be-spent" utxos added with `TxBuilder::add_utxo` have priority over this.
     */
    public actual override fun `addUnspendable`(`unspendable`: OutPoint): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_add_unspendable(
                    it,
                    FfiConverterTypeOutPoint.lower(`unspendable`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Add a utxo to the internal list of utxos that must be spent.
     *
     * These have priority over the "unspendable" utxos, meaning that if a utxo is present both in the "utxos" and the
     * "unspendable" list, it will be spent.
     */
    public actual override fun `addUtxo`(`outpoint`: OutPoint): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_add_utxo(
                    it,
                    FfiConverterTypeOutPoint.lower(`outpoint`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Add the list of outpoints to the internal list of UTXOs that must be spent.
     */
    public actual override fun `addUtxos`(`outpoints`: List<OutPoint>): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_add_utxos(
                    it,
                    FfiConverterSequenceTypeOutPoint.lower(`outpoints`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set whether or not the dust limit is checked.
     *
     * Note: by avoiding a dust limit check you may end up with a transaction that is non-standard.
     */
    public actual override fun `allowDust`(`allowDust`: kotlin.Boolean): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_allow_dust(
                    it,
                    FfiConverterBoolean.lower(`allowDust`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set a specific `ChangeSpendPolicy`. See `TxBuilder::do_not_spend_change` and `TxBuilder::only_spend_change` for
     * some shortcuts. This method assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public actual override fun `changePolicy`(`changePolicy`: ChangeSpendPolicy): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_change_policy(
                    it,
                    FfiConverterTypeChangeSpendPolicy.lower(`changePolicy`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set the current blockchain height.
     *
     * This will be used to:
     *
     * 1. Set the `nLockTime` for preventing fee sniping. Note: This will be ignored if you manually specify a
     * `nlocktime` using `TxBuilder::nlocktime`.
     *
     * 2. Decide whether coinbase outputs are mature or not. If the coinbase outputs are not mature at `current_height`,
     * we ignore them in the coin selection. If you want to create a transaction that spends immature coinbase inputs,
     * manually add them using `TxBuilder::add_utxos`.
     * In both cases, if you don’t provide a current height, we use the last sync height.
     */
    public actual override fun `currentHeight`(`height`: kotlin.UInt): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_current_height(
                    it,
                    FfiConverterUInt.lower(`height`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Do not spend change outputs.
     *
     * This effectively adds all the change outputs to the "unspendable" list. See `TxBuilder::unspendable`. This method
     * assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public actual override fun `doNotSpendChange`(): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_do_not_spend_change(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Sets the address to drain excess coins to.
     *
     * Usually, when there are excess coins they are sent to a change address generated by the wallet. This option
     * replaces the usual change address with an arbitrary script_pubkey of your choosing. Just as with a change output,
     * if the drain output is not needed (the excess coins are too small) it will not be included in the resulting
     * transaction. The only difference is that it is valid to use `drain_to` without setting any ordinary recipients
     * with `add_recipient` (but it is perfectly fine to add recipients as well).
     *
     * If you choose not to set any recipients, you should provide the utxos that the transaction should spend via
     * `add_utxos`. `drain_to` is very useful for draining all the coins in a wallet with `drain_wallet` to a single
     * address.
     */
    public actual override fun `drainTo`(`script`: Script): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_drain_to(
                    it,
                    FfiConverterTypeScript.lower(`script`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Spend all the available inputs. This respects filters like `TxBuilder::unspendable` and the change policy.
     */
    public actual override fun `drainWallet`(): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_drain_wallet(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Excludes any outpoints whose enclosing transaction has fewer than `min_confirms`
     * confirmations.
     *
     * `min_confirms` is the minimum number of confirmations a transaction must have in order for
     * its outpoints to remain spendable.
     * - Passing `0` will include all transactions (no filtering).
     * - Passing `1` will exclude all unconfirmed transactions (equivalent to
     * `exclude_unconfirmed`).
     * - Passing `6` will only allow outpoints from transactions with at least 6 confirmations.
     *
     * If you chain this with other filtering methods, the final set of unspendable outpoints will
     * be the union of all filters.
     */
    public actual override fun `excludeBelowConfirmations`(`minConfirms`: kotlin.UInt): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_exclude_below_confirmations(
                    it,
                    FfiConverterUInt.lower(`minConfirms`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Exclude outpoints whose enclosing transaction is unconfirmed.
     * This is a shorthand for exclude_below_confirmations(1).
     */
    public actual override fun `excludeUnconfirmed`(): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_exclude_unconfirmed(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set an absolute fee The `fee_absolute` method refers to the absolute transaction fee in `Amount`. If anyone sets
     * both the `fee_absolute` method and the `fee_rate` method, the `FeePolicy` enum will be set by whichever method was
     * called last, as the `FeeRate` and `FeeAmount` are mutually exclusive.
     *
     * Note that this is really a minimum absolute fee – it’s possible to overshoot it slightly since adding a change output to drain the remaining excess might not be viable.
     */
    public actual override fun `feeAbsolute`(`feeAmount`: Amount): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_fee_absolute(
                    it,
                    FfiConverterTypeAmount.lower(`feeAmount`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set a custom fee rate.
     *
     * This method sets the mining fee paid by the transaction as a rate on its size. This means that the total fee paid
     * is equal to fee_rate times the size of the transaction. Default is 1 sat/vB in accordance with Bitcoin Core’s
     * default relay policy.
     *
     * Note that this is really a minimum feerate – it’s possible to overshoot it slightly since adding a change output
     * to drain the remaining excess might not be viable.
     */
    public actual override fun `feeRate`(`feeRate`: FeeRate): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_fee_rate(
                    it,
                    FfiConverterTypeFeeRate.lower(`feeRate`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Finish building the transaction.
     *
     * Uses the thread-local random number generator (rng).
     *
     * Returns a new `Psbt` per BIP174.
     *
     * WARNING: To avoid change address reuse you must persist the changes resulting from one or more calls to this
     * method before closing the wallet. See `Wallet::reveal_next_address`.
     */
    @Throws(CreateTxException::class)
    public actual override fun `finish`(`wallet`: Wallet): Psbt {
        return FfiConverterTypePsbt.lift(callWithPointer {
            uniffiRustCallWithError(CreateTxExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_finish(
                    it,
                    FfiConverterTypeWallet.lower(`wallet`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Only spend utxos added by `TxBuilder::add_utxo`.
     *
     * The wallet will not add additional utxos to the transaction even if they are needed to make the transaction valid.
     */
    public actual override fun `manuallySelectedOnly`(): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_manually_selected_only(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Use a specific nLockTime while creating the transaction.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "after" (`OP_CLTV`) operator.
     */
    public actual override fun `nlocktime`(`locktime`: LockTime): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_nlocktime(
                    it,
                    FfiConverterTypeLockTime.lower(`locktime`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Only spend change outputs.
     *
     * This effectively adds all the non-change outputs to the "unspendable" list. See `TxBuilder::unspendable`. This
     * method assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public actual override fun `onlySpendChange`(): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_only_spend_change(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * The TxBuilder::policy_path is a complex API. See the Rust docs for complete       information: https://docs.rs/bdk_wallet/latest/bdk_wallet/struct.TxBuilder.html#method.policy_path
     */
    public actual override fun `policyPath`(`policyPath`: Map<kotlin.String, List<kotlin.ULong>>, `keychain`: KeychainKind): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_policy_path(
                    it,
                    FfiConverterMapStringSequenceULong.lower(`policyPath`),
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Set an exact `nSequence` value.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "older" (`OP_CSV`) operator and the given
     * `nsequence` is lower than the CSV value.
     */
    public actual override fun `setExactSequence`(`nsequence`: kotlin.UInt): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_set_exact_sequence(
                    it,
                    FfiConverterUInt.lower(`nsequence`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Replace the recipients already added with a new list of recipients.
     */
    public actual override fun `setRecipients`(`recipients`: List<ScriptAmount>): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_set_recipients(
                    it,
                    FfiConverterSequenceTypeScriptAmount.lower(`recipients`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Replace the internal list of unspendable utxos with a new list.
     *
     * It’s important to note that the "must-be-spent" utxos added with `TxBuilder::add_utxo` have priority over these.
     */
    public actual override fun `unspendable`(`unspendable`: List<OutPoint>): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_unspendable(
                    it,
                    FfiConverterSequenceTypeOutPoint.lower(`unspendable`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Build a transaction with a specific version.
     *
     * The version should always be greater than 0 and greater than 1 if the wallet’s descriptors contain an "older"
     * (`OP_CSV`) operator.
     */
    public actual override fun `version`(`version`: kotlin.Int): TxBuilder {
        return FfiConverterTypeTxBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txbuilder_version(
                    it,
                    FfiConverterInt.lower(`version`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }


    
    
    public actual companion object
    
}





public object FfiConverterTypeTxBuilder: FfiConverter<TxBuilder, Pointer> {

    override fun lower(value: TxBuilder): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): TxBuilder {
        return TxBuilder(value)
    }

    override fun read(buf: ByteBuffer): TxBuilder {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: TxBuilder): ULong = 8UL

    override fun write(value: TxBuilder, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * The merkle root of the merkle tree corresponding to a block's transactions.
 */
public actual open class TxMerkleNode: Disposable, TxMerkleNodeInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_txmerklenode(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_txmerklenode(pointer!!, status)
        }!!
    }

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public actual override fun `serialize`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txmerklenode_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txmerklenode_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TxMerkleNode) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txmerklenode_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeTxMerkleNode.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    actual override fun hashCode(): Int {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txmerklenode_uniffi_trait_hash(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }).toInt()
    }

    
    public actual companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public actual fun `fromBytes`(`bytes`: kotlin.ByteArray): TxMerkleNode {
            return FfiConverterTypeTxMerkleNode.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_txmerklenode_from_bytes(
                    FfiConverterByteArray.lower(`bytes`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public actual fun `fromString`(`hex`: kotlin.String): TxMerkleNode {
            return FfiConverterTypeTxMerkleNode.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_txmerklenode_from_string(
                    FfiConverterString.lower(`hex`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeTxMerkleNode: FfiConverter<TxMerkleNode, Pointer> {

    override fun lower(value: TxMerkleNode): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): TxMerkleNode {
        return TxMerkleNode(value)
    }

    override fun read(buf: ByteBuffer): TxMerkleNode {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: TxMerkleNode): ULong = 8UL

    override fun write(value: TxMerkleNode, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A bitcoin transaction identifier
 */
public actual open class Txid: Disposable, TxidInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_txid(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_txid(pointer!!, status)
        }!!
    }

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public actual override fun `serialize`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txid_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txid_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Txid) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txid_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeTxid.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    actual override fun hashCode(): Int {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_txid_uniffi_trait_hash(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }).toInt()
    }

    
    public actual companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public actual fun `fromBytes`(`bytes`: kotlin.ByteArray): Txid {
            return FfiConverterTypeTxid.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_txid_from_bytes(
                    FfiConverterByteArray.lower(`bytes`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public actual fun `fromString`(`hex`: kotlin.String): Txid {
            return FfiConverterTypeTxid.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_txid_from_string(
                    FfiConverterString.lower(`hex`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeTxid: FfiConverter<Txid, Pointer> {

    override fun lower(value: Txid): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Txid {
        return Txid(value)
    }

    override fun read(buf: ByteBuffer): Txid {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Txid): ULong = 8UL

    override fun write(value: Txid, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * An update for a wallet containing chain, descriptor index, and transaction data.
 */
public actual open class Update: Disposable, UpdateInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_update(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_update(pointer!!, status)
        }!!
    }

    

    
    
    public actual companion object
    
}





public object FfiConverterTypeUpdate: FfiConverter<Update, Pointer> {

    override fun lower(value: Update): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Update {
        return Update(value)
    }

    override fun read(buf: ByteBuffer): Update {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Update): ULong = 8UL

    override fun write(value: Update, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A Bitcoin wallet.
 *
 * The Wallet acts as a way of coherently interfacing with output descriptors and related transactions. Its main components are:
 * 1. output descriptors from which it can derive addresses.
 * 2. signers that can contribute signatures to addresses instantiated from the descriptors.
 *
 * The user is responsible for loading and writing wallet changes which are represented as
 * ChangeSets (see take_staged). Also see individual functions and example for instructions on when
 * Wallet state needs to be persisted.
 *
 * The Wallet descriptor (external) and change descriptor (internal) must not derive the same
 * script pubkeys. See KeychainTxOutIndex::insert_descriptor() for more details.
 */
public actual open class Wallet: Disposable, WalletInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }
    /**
     * Build a new Wallet.
     *
     * If you have previously created a wallet, use load instead.
     */
    public actual constructor(`descriptor`: Descriptor, `changeDescriptor`: Descriptor, `network`: Network, `persister`: Persister, `lookahead`: kotlin.UInt) : this(
        uniffiRustCallWithError(CreateWithPersistExceptionErrorHandler) { uniffiRustCallStatus ->
            UniffiLib.uniffi_bdk_fn_constructor_wallet_new(
                FfiConverterTypeDescriptor.lower(`descriptor`),
                FfiConverterTypeDescriptor.lower(`changeDescriptor`),
                FfiConverterTypeNetwork.lower(`network`),
                FfiConverterTypePersister.lower(`persister`),
                FfiConverterUInt.lower(`lookahead`),
                uniffiRustCallStatus,
            )
        }!!
    )

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_wallet(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_wallet(pointer!!, status)
        }!!
    }

    
    /**
     * Apply transactions that have been evicted from the mempool.
     * Transactions may be evicted for paying too-low fee, or for being malformed.
     * Irrelevant transactions are ignored.
     *
     * For more information: https://docs.rs/bdk_wallet/latest/bdk_wallet/struct.Wallet.html#method.apply_evicted_txs
     */
    public actual override fun `applyEvictedTxs`(`evictedTxs`: List<EvictedTx>) {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_apply_evicted_txs(
                    it,
                    FfiConverterSequenceTypeEvictedTx.lower(`evictedTxs`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Apply relevant unconfirmed transactions to the wallet.
     * Transactions that are not relevant are filtered out.
     */
    public actual override fun `applyUnconfirmedTxs`(`unconfirmedTxs`: List<UnconfirmedTx>) {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_apply_unconfirmed_txs(
                    it,
                    FfiConverterSequenceTypeUnconfirmedTx.lower(`unconfirmedTxs`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Applies an update to the wallet and stages the changes (but does not persist them).
     *
     * Usually you create an `update` by interacting with some blockchain data source and inserting
     * transactions related to your wallet into it.
     *
     * After applying updates you should persist the staged wallet changes. For an example of how
     * to persist staged wallet changes see [`Wallet::reveal_next_address`].
     */
    @Throws(CannotConnectException::class)
    public actual override fun `applyUpdate`(`update`: Update) {
        callWithPointer {
            uniffiRustCallWithError(CannotConnectExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_apply_update(
                    it,
                    FfiConverterTypeUpdate.lower(`update`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Return the balance, separated into available, trusted-pending, untrusted-pending and
     * immature values.
     */
    public actual override fun `balance`(): Balance {
        return FfiConverterTypeBalance.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_balance(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Calculates the fee of a given transaction. Returns [`Amount::ZERO`] if `tx` is a coinbase transaction.
     *
     * To calculate the fee for a [`Transaction`] with inputs not owned by this wallet you must
     * manually insert the TxOut(s) into the tx graph using the [`insert_txout`] function.
     *
     * Note `tx` does not have to be in the graph for this to work.
     */
    @Throws(CalculateFeeException::class)
    public actual override fun `calculateFee`(`tx`: Transaction): Amount {
        return FfiConverterTypeAmount.lift(callWithPointer {
            uniffiRustCallWithError(CalculateFeeExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_calculate_fee(
                    it,
                    FfiConverterTypeTransaction.lower(`tx`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Calculate the [`FeeRate`] for a given transaction.
     *
     * To calculate the fee rate for a [`Transaction`] with inputs not owned by this wallet you must
     * manually insert the TxOut(s) into the tx graph using the [`insert_txout`] function.
     *
     * Note `tx` does not have to be in the graph for this to work.
     */
    @Throws(CalculateFeeException::class)
    public actual override fun `calculateFeeRate`(`tx`: Transaction): FeeRate {
        return FfiConverterTypeFeeRate.lift(callWithPointer {
            uniffiRustCallWithError(CalculateFeeExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_calculate_fee_rate(
                    it,
                    FfiConverterTypeTransaction.lower(`tx`),
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Informs the wallet that you no longer intend to broadcast a tx that was built from it.
     *
     * This frees up the change address used when creating the tx for use in future transactions.
     */
    public actual override fun `cancelTx`(`tx`: Transaction) {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_cancel_tx(
                    it,
                    FfiConverterTypeTransaction.lower(`tx`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * The derivation index of this wallet. It will return `None` if it has not derived any addresses.
     * Otherwise, it will return the index of the highest address it has derived.
     */
    public actual override fun `derivationIndex`(`keychain`: KeychainKind): kotlin.UInt? {
        return FfiConverterOptionalUInt.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_derivation_index(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Finds how the wallet derived the script pubkey `spk`.
     *
     * Will only return `Some(_)` if the wallet has given out the spk.
     */
    public actual override fun `derivationOfSpk`(`spk`: Script): KeychainAndIndex? {
        return FfiConverterOptionalTypeKeychainAndIndex.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_derivation_of_spk(
                    it,
                    FfiConverterTypeScript.lower(`spk`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Return the checksum of the public descriptor associated to `keychain`.
     *
     * Internally calls [`Self::public_descriptor`] to fetch the right descriptor.
     */
    public actual override fun `descriptorChecksum`(`keychain`: KeychainKind): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_descriptor_checksum(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Finalize a PSBT, i.e., for each input determine if sufficient data is available to pass
     * validation and construct the respective `scriptSig` or `scriptWitness`. Please refer to
     * [BIP174](https://github.com/bitcoin/bips/blob/master/bip-0174.mediawiki#Input_Finalizer),
     * and [BIP371](https://github.com/bitcoin/bips/blob/master/bip-0371.mediawiki)
     * for further information.
     *
     * Returns `true` if the PSBT could be finalized, and `false` otherwise.
     *
     * The [`SignOptions`] can be used to tweak the behavior of the finalizer.
     */
    @Throws(SignerException::class)
    public actual override fun `finalizePsbt`(`psbt`: Psbt, `signOptions`: SignOptions?): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCallWithError(SignerExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_finalize_psbt(
                    it,
                    FfiConverterTypePsbt.lower(`psbt`),
                    FfiConverterOptionalTypeSignOptions.lower(`signOptions`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get a single transaction from the wallet as a [`WalletTx`] (if the transaction exists).
     *
     * `WalletTx` contains the full transaction alongside meta-data such as:
     * * Blocks that the transaction is [`Anchor`]ed in. These may or may not be blocks that exist
     * in the best chain.
     * * The [`ChainPosition`] of the transaction in the best chain - whether the transaction is
     * confirmed or unconfirmed. If the transaction is confirmed, the anchor which proves the
     * confirmation is provided. If the transaction is unconfirmed, the unix timestamp of when
     * the transaction was last seen in the mempool is provided.
     */
    @Throws(TxidParseException::class)
    public actual override fun `getTx`(`txid`: Txid): CanonicalTx? {
        return FfiConverterOptionalTypeCanonicalTx.lift(callWithPointer {
            uniffiRustCallWithError(TxidParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_get_tx(
                    it,
                    FfiConverterTypeTxid.lower(`txid`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the utxo owned by this wallet corresponding to `outpoint` if it exists in the
     * wallet's database.
     */
    public actual override fun `getUtxo`(`op`: OutPoint): LocalOutput? {
        return FfiConverterOptionalTypeLocalOutput.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_get_utxo(
                    it,
                    FfiConverterTypeOutPoint.lower(`op`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Inserts a [`TxOut`] at [`OutPoint`] into the wallet's transaction graph.
     *
     * This is used for providing a previous output's value so that we can use [`calculate_fee`]
     * or [`calculate_fee_rate`] on a given transaction. Outputs inserted with this method will
     * not be returned in [`list_unspent`] or [`list_output`].
     *
     * **WARNINGS:** This should only be used to add `TxOut`s that the wallet does not own. Only
     * insert `TxOut`s that you trust the values for!
     *
     * You must persist the changes resulting from one or more calls to this method if you need
     * the inserted `TxOut` data to be reloaded after closing the wallet.
     * See [`Wallet::reveal_next_address`].
     *
     * [`calculate_fee`]: Self::calculate_fee
     * [`calculate_fee_rate`]: Self::calculate_fee_rate
     * [`list_unspent`]: Self::list_unspent
     * [`list_output`]: Self::list_output
     */
    public actual override fun `insertTxout`(`outpoint`: OutPoint, `txout`: TxOut) {
        callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_insert_txout(
                    it,
                    FfiConverterTypeOutPoint.lower(`outpoint`),
                    FfiConverterTypeTxOut.lower(`txout`),
                    uniffiRustCallStatus,
                )
            }
        }
    }

    /**
     * Return whether or not a `script` is part of this wallet (either internal or external).
     */
    public actual override fun `isMine`(`script`: Script): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_is_mine(
                    it,
                    FfiConverterTypeScript.lower(`script`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the latest checkpoint.
     */
    public actual override fun `latestCheckpoint`(): BlockId {
        return FfiConverterTypeBlockId.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_latest_checkpoint(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * List all relevant outputs (includes both spent and unspent, confirmed and unconfirmed).
     *
     * To list only unspent outputs (UTXOs), use [`Wallet::list_unspent`] instead.
     */
    public actual override fun `listOutput`(): List<LocalOutput> {
        return FfiConverterSequenceTypeLocalOutput.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_list_output(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Return the list of unspent outputs of this wallet.
     */
    public actual override fun `listUnspent`(): List<LocalOutput> {
        return FfiConverterSequenceTypeLocalOutput.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_list_unspent(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * List addresses that are revealed but unused.
     *
     * Note if the returned iterator is empty you can reveal more addresses
     * by using [`reveal_next_address`](Self::reveal_next_address) or
     * [`reveal_addresses_to`](Self::reveal_addresses_to).
     */
    public actual override fun `listUnusedAddresses`(`keychain`: KeychainKind): List<AddressInfo> {
        return FfiConverterSequenceTypeAddressInfo.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_list_unused_addresses(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Marks an address used of the given `keychain` at `index`.
     *
     * Returns whether the given index was present and then removed from the unused set.
     */
    public actual override fun `markUsed`(`keychain`: KeychainKind, `index`: kotlin.UInt): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_mark_used(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    FfiConverterUInt.lower(`index`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the Bitcoin network the wallet is using.
     */
    public actual override fun `network`(): Network {
        return FfiConverterTypeNetwork.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_network(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * The index of the next address that you would get if you were to ask the wallet for a new
     * address.
     */
    public actual override fun `nextDerivationIndex`(`keychain`: KeychainKind): kotlin.UInt {
        return FfiConverterUInt.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_next_derivation_index(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the next unused address for the given `keychain`, i.e. the address with the lowest
     * derivation index that hasn't been used in a transaction.
     *
     * This will attempt to reveal a new address if all previously revealed addresses have
     * been used, in which case the returned address will be the same as calling [`Wallet::reveal_next_address`].
     *
     * **WARNING**: To avoid address reuse you must persist the changes resulting from one or more
     * calls to this method before closing the wallet. See [`Wallet::reveal_next_address`].
     */
    public actual override fun `nextUnusedAddress`(`keychain`: KeychainKind): AddressInfo {
        return FfiConverterTypeAddressInfo.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_next_unused_address(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Peek an address of the given `keychain` at `index` without revealing it.
     *
     * For non-wildcard descriptors this returns the same address at every provided index.
     *
     * # Panics
     *
     * This panics when the caller requests for an address of derivation index greater than the
     * [BIP32](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki) max index.
     */
    public actual override fun `peekAddress`(`keychain`: KeychainKind, `index`: kotlin.UInt): AddressInfo {
        return FfiConverterTypeAddressInfo.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_peek_address(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    FfiConverterUInt.lower(`index`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Persist staged changes of wallet into persister.
     *
     * Returns whether any new changes were persisted.
     *
     * If the persister errors, the staged changes will not be cleared.
     */
    @Throws(PersistenceException::class)
    public actual override fun `persist`(`persister`: Persister): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCallWithError(PersistenceExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_persist(
                    it,
                    FfiConverterTypePersister.lower(`persister`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Return the spending policies for the wallet’s descriptor.
     */
    @Throws(DescriptorException::class)
    public actual override fun `policies`(`keychain`: KeychainKind): Policy? {
        return FfiConverterOptionalTypePolicy.lift(callWithPointer {
            uniffiRustCallWithError(DescriptorExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_policies(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Returns the descriptor used to create addresses for a particular `keychain`.
     *
     * It's the "public" version of the wallet's descriptor, meaning a new descriptor that has
     * the same structure but with the all secret keys replaced by their corresponding public key.
     * This can be used to build a watch-only version of a wallet.
     */
    public actual override fun `publicDescriptor`(`keychain`: KeychainKind): kotlin.String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_public_descriptor(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Reveal addresses up to and including the target `index` and return an iterator
     * of newly revealed addresses.
     *
     * If the target `index` is unreachable, we make a best effort to reveal up to the last
     * possible index. If all addresses up to the given `index` are already revealed, then
     * no new addresses are returned.
     *
     * **WARNING**: To avoid address reuse you must persist the changes resulting from one or more
     * calls to this method before closing the wallet. See [`Wallet::reveal_next_address`].
     */
    public actual override fun `revealAddressesTo`(`keychain`: KeychainKind, `index`: kotlin.UInt): List<AddressInfo> {
        return FfiConverterSequenceTypeAddressInfo.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_reveal_addresses_to(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    FfiConverterUInt.lower(`index`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Attempt to reveal the next address of the given `keychain`.
     *
     * This will increment the keychain's derivation index. If the keychain's descriptor doesn't
     * contain a wildcard or every address is already revealed up to the maximum derivation
     * index defined in [BIP32](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki),
     * then the last revealed address will be returned.
     */
    public actual override fun `revealNextAddress`(`keychain`: KeychainKind): AddressInfo {
        return FfiConverterTypeAddressInfo.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_reveal_next_address(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Compute the `tx`'s sent and received [`Amount`]s.
     *
     * This method returns a tuple `(sent, received)`. Sent is the sum of the txin amounts
     * that spend from previous txouts tracked by this wallet. Received is the summation
     * of this tx's outputs that send to script pubkeys tracked by this wallet.
     */
    public actual override fun `sentAndReceived`(`tx`: Transaction): SentAndReceivedValues {
        return FfiConverterTypeSentAndReceivedValues.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_sent_and_received(
                    it,
                    FfiConverterTypeTransaction.lower(`tx`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Sign a transaction with all the wallet's signers, in the order specified by every signer's
     * [`SignerOrdering`]. This function returns the `Result` type with an encapsulated `bool` that
     * has the value true if the PSBT was finalized, or false otherwise.
     *
     * The [`SignOptions`] can be used to tweak the behavior of the software signers, and the way
     * the transaction is finalized at the end. Note that it can't be guaranteed that *every*
     * signers will follow the options, but the "software signers" (WIF keys and `xprv`) defined
     * in this library will.
     */
    @Throws(SignerException::class)
    public actual override fun `sign`(`psbt`: Psbt, `signOptions`: SignOptions?): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCallWithError(SignerExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_sign(
                    it,
                    FfiConverterTypePsbt.lower(`psbt`),
                    FfiConverterOptionalTypeSignOptions.lower(`signOptions`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get a reference of the staged [`ChangeSet`] that is yet to be committed (if any).
     */
    public actual override fun `staged`(): ChangeSet? {
        return FfiConverterOptionalTypeChangeSet.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_staged(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Create a [`FullScanRequest] for this wallet.
     *
     * This is the first step when performing a spk-based wallet full scan, the returned
     * [`FullScanRequest] collects iterators for the wallet's keychain script pub keys needed to
     * start a blockchain full scan with a spk based blockchain client.
     *
     * This operation is generally only used when importing or restoring a previously used wallet
     * in which the list of used scripts is not known.
     */
    public actual override fun `startFullScan`(): FullScanRequestBuilder {
        return FfiConverterTypeFullScanRequestBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_start_full_scan(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Create a partial [`SyncRequest`] for this wallet for all revealed spks.
     *
     * This is the first step when performing a spk-based wallet partial sync, the returned
     * [`SyncRequest`] collects all revealed script pubkeys from the wallet keychain needed to
     * start a blockchain sync with a spk based blockchain client.
     */
    public actual override fun `startSyncWithRevealedSpks`(): SyncRequestBuilder {
        return FfiConverterTypeSyncRequestBuilder.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_start_sync_with_revealed_spks(
                    it,
                    uniffiRustCallStatus,
                )
            }!!
        })
    }

    /**
     * Take the staged [`ChangeSet`] to be persisted now (if any).
     */
    public actual override fun `takeStaged`(): ChangeSet? {
        return FfiConverterOptionalTypeChangeSet.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_take_staged(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Iterate over the transactions in the wallet.
     */
    public actual override fun `transactions`(): List<CanonicalTx> {
        return FfiConverterSequenceTypeCanonicalTx.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_transactions(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Get the [`TxDetails`] of a wallet transaction.
     */
    public actual override fun `txDetails`(`txid`: Txid): TxDetails? {
        return FfiConverterOptionalTypeTxDetails.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_tx_details(
                    it,
                    FfiConverterTypeTxid.lower(`txid`),
                    uniffiRustCallStatus,
                )
            }
        })
    }

    /**
     * Undoes the effect of [`mark_used`] and returns whether the `index` was inserted
     * back into the unused set.
     *
     * Since this is only a superficial marker, it will have no effect if the address at the given
     * `index` was actually used, i.e. the wallet has previously indexed a tx output for the
     * derived spk.
     *
     * [`mark_used`]: Self::mark_used
     */
    public actual override fun `unmarkUsed`(`keychain`: KeychainKind, `index`: kotlin.UInt): kotlin.Boolean {
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wallet_unmark_used(
                    it,
                    FfiConverterTypeKeychainKind.lower(`keychain`),
                    FfiConverterUInt.lower(`index`),
                    uniffiRustCallStatus,
                )
            }
        })
    }


    
    public actual companion object {
        
        /**
         * Build a new `Wallet` from a two-path descriptor.
         *
         * This function parses a multipath descriptor with exactly 2 paths and creates a wallet using the existing receive and change wallet creation logic.
         *
         * Multipath descriptors follow [BIP-389](https://github.com/bitcoin/bips/blob/master/bip-0389.mediawiki) and allow defining both receive and change derivation paths in a single descriptor using the <0;1> syntax.
         *
         * If you have previously created a wallet, use load instead.
         *
         * Returns an error if the descriptor is invalid or not a 2-path multipath descriptor.
         */
        @Throws(CreateWithPersistException::class)
        public actual fun `createFromTwoPathDescriptor`(`twoPathDescriptor`: Descriptor, `network`: Network, `persister`: Persister, `lookahead`: kotlin.UInt): Wallet {
            return FfiConverterTypeWallet.lift(uniffiRustCallWithError(CreateWithPersistExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_wallet_create_from_two_path_descriptor(
                    FfiConverterTypeDescriptor.lower(`twoPathDescriptor`),
                    FfiConverterTypeNetwork.lower(`network`),
                    FfiConverterTypePersister.lower(`persister`),
                    FfiConverterUInt.lower(`lookahead`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Build a new single descriptor `Wallet`.
         *
         * If you have previously created a wallet, use `Wallet::load` instead.
         *
         * # Note
         *
         * Only use this method when creating a wallet designed to be used with a single
         * descriptor and keychain. Otherwise the recommended way to construct a new wallet is
         * by using `Wallet::new`. It's worth noting that not all features are available
         * with single descriptor wallets, for example setting a `change_policy` on `TxBuilder`
         * and related methods such as `do_not_spend_change`. This is because all payments are
         * received on the external keychain (including change), and without a change keychain
         * BDK lacks enough information to distinguish between change and outside payments.
         *
         * Additionally because this wallet has no internal (change) keychain, all methods that
         * require a `KeychainKind` as input, e.g. `reveal_next_address` should only be called
         * using the `External` variant. In most cases passing `Internal` is treated as the
         * equivalent of `External` but this behavior must not be relied on.
         */
        @Throws(CreateWithPersistException::class)
        public actual fun `createSingle`(`descriptor`: Descriptor, `network`: Network, `persister`: Persister, `lookahead`: kotlin.UInt): Wallet {
            return FfiConverterTypeWallet.lift(uniffiRustCallWithError(CreateWithPersistExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_wallet_create_single(
                    FfiConverterTypeDescriptor.lower(`descriptor`),
                    FfiConverterTypeNetwork.lower(`network`),
                    FfiConverterTypePersister.lower(`persister`),
                    FfiConverterUInt.lower(`lookahead`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Build Wallet by loading from persistence.
         *
         * Note that the descriptor secret keys are not persisted to the db.
         */
        @Throws(LoadWithPersistException::class)
        public actual fun `load`(`descriptor`: Descriptor, `changeDescriptor`: Descriptor, `persister`: Persister, `lookahead`: kotlin.UInt): Wallet {
            return FfiConverterTypeWallet.lift(uniffiRustCallWithError(LoadWithPersistExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_wallet_load(
                    FfiConverterTypeDescriptor.lower(`descriptor`),
                    FfiConverterTypeDescriptor.lower(`changeDescriptor`),
                    FfiConverterTypePersister.lower(`persister`),
                    FfiConverterUInt.lower(`lookahead`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Build a single-descriptor Wallet by loading from persistence.
         *
         * Note that the descriptor secret keys are not persisted to the db.
         */
        @Throws(LoadWithPersistException::class)
        public actual fun `loadSingle`(`descriptor`: Descriptor, `persister`: Persister, `lookahead`: kotlin.UInt): Wallet {
            return FfiConverterTypeWallet.lift(uniffiRustCallWithError(LoadWithPersistExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_wallet_load_single(
                    FfiConverterTypeDescriptor.lower(`descriptor`),
                    FfiConverterTypePersister.lower(`persister`),
                    FfiConverterUInt.lower(`lookahead`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeWallet: FfiConverter<Wallet, Pointer> {

    override fun lower(value: Wallet): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Wallet {
        return Wallet(value)
    }

    override fun read(buf: ByteBuffer): Wallet {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Wallet): ULong = 8UL

    override fun write(value: Wallet, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}



/**
 * A bitcoin transaction identifier, including witness data.
 * For transactions with no SegWit inputs, the `txid` will be equivalent to `wtxid`.
 */
public actual open class Wtxid: Disposable, WtxidInterface {

    public constructor(pointer: Pointer) {
        this.pointer = pointer
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(pointer))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public actual constructor(noPointer: NoPointer) {
        this.pointer = null
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiPointerDestroyer(null))
    }

    protected val pointer: Pointer?
    protected val cleanable: UniffiCleaner.Cleanable

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    actual override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    actual override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithPointer(block: (ptr: Pointer) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the pointer being freed concurrently.
        try {
            return block(this.uniffiClonePointer())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiPointerDestroyer(private val pointer: Pointer?) : Disposable {
        override fun destroy() {
            pointer?.let { ptr ->
                uniffiRustCall { status ->
                    UniffiLib.uniffi_bdk_fn_free_wtxid(ptr, status)
                }
            }
        }
    }

    public fun uniffiClonePointer(): Pointer {
        return uniffiRustCall { status ->
            UniffiLib.uniffi_bdk_fn_clone_wtxid(pointer!!, status)
        }!!
    }

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public actual override fun `serialize`(): kotlin.ByteArray {
        return FfiConverterByteArray.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wtxid_serialize(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }

    actual override fun toString(): String {
        return FfiConverterString.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wtxid_uniffi_trait_display(
                    it,
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    
    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Wtxid) return false
        return FfiConverterBoolean.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wtxid_uniffi_trait_eq_eq(
                    it,
                    FfiConverterTypeWtxid.lower(`other`),
                    uniffiRustCallStatus,
                )
            }
        })
    }
    
    actual override fun hashCode(): Int {
        return FfiConverterULong.lift(callWithPointer {
            uniffiRustCall { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_method_wtxid_uniffi_trait_hash(
                    it,
                    uniffiRustCallStatus,
                )
            }
        }).toInt()
    }

    
    public actual companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public actual fun `fromBytes`(`bytes`: kotlin.ByteArray): Wtxid {
            return FfiConverterTypeWtxid.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_wtxid_from_bytes(
                    FfiConverterByteArray.lower(`bytes`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public actual fun `fromString`(`hex`: kotlin.String): Wtxid {
            return FfiConverterTypeWtxid.lift(uniffiRustCallWithError(HashParseExceptionErrorHandler) { uniffiRustCallStatus ->
                UniffiLib.uniffi_bdk_fn_constructor_wtxid_from_string(
                    FfiConverterString.lower(`hex`),
                    uniffiRustCallStatus,
                )
            }!!)
        }

        
    }
    
}





public object FfiConverterTypeWtxid: FfiConverter<Wtxid, Pointer> {

    override fun lower(value: Wtxid): Pointer {
        return value.uniffiClonePointer()
    }

    override fun lift(value: Pointer): Wtxid {
        return Wtxid(value)
    }

    override fun read(buf: ByteBuffer): Wtxid {
        // The Rust code always writes pointers as 8 bytes, and will
        // fail to compile if they don't fit.
        return lift(buf.getLong().toPointer())
    }

    override fun allocationSize(value: Wtxid): ULong = 8UL

    override fun write(value: Wtxid, buf: ByteBuffer) {
        // The Rust code always expects pointers written as 8 bytes,
        // and will fail to compile if they don't fit.
        buf.putLong(lower(value).toLong())
    }
}




public object FfiConverterTypeAddressInfo: FfiConverterRustBuffer<AddressInfo> {
    override fun read(buf: ByteBuffer): AddressInfo {
        return AddressInfo(
            FfiConverterUInt.read(buf),
            FfiConverterTypeAddress.read(buf),
            FfiConverterTypeKeychainKind.read(buf),
        )
    }

    override fun allocationSize(value: AddressInfo): ULong = (
            FfiConverterUInt.allocationSize(value.`index`) +
            FfiConverterTypeAddress.allocationSize(value.`address`) +
            FfiConverterTypeKeychainKind.allocationSize(value.`keychain`)
    )

    override fun write(value: AddressInfo, buf: ByteBuffer) {
        FfiConverterUInt.write(value.`index`, buf)
        FfiConverterTypeAddress.write(value.`address`, buf)
        FfiConverterTypeKeychainKind.write(value.`keychain`, buf)
    }
}




public object FfiConverterTypeAnchor: FfiConverterRustBuffer<Anchor> {
    override fun read(buf: ByteBuffer): Anchor {
        return Anchor(
            FfiConverterTypeConfirmationBlockTime.read(buf),
            FfiConverterTypeTxid.read(buf),
        )
    }

    override fun allocationSize(value: Anchor): ULong = (
            FfiConverterTypeConfirmationBlockTime.allocationSize(value.`confirmationBlockTime`) +
            FfiConverterTypeTxid.allocationSize(value.`txid`)
    )

    override fun write(value: Anchor, buf: ByteBuffer) {
        FfiConverterTypeConfirmationBlockTime.write(value.`confirmationBlockTime`, buf)
        FfiConverterTypeTxid.write(value.`txid`, buf)
    }
}




public object FfiConverterTypeBalance: FfiConverterRustBuffer<Balance> {
    override fun read(buf: ByteBuffer): Balance {
        return Balance(
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
        )
    }

    override fun allocationSize(value: Balance): ULong = (
            FfiConverterTypeAmount.allocationSize(value.`immature`) +
            FfiConverterTypeAmount.allocationSize(value.`trustedPending`) +
            FfiConverterTypeAmount.allocationSize(value.`untrustedPending`) +
            FfiConverterTypeAmount.allocationSize(value.`confirmed`) +
            FfiConverterTypeAmount.allocationSize(value.`trustedSpendable`) +
            FfiConverterTypeAmount.allocationSize(value.`total`)
    )

    override fun write(value: Balance, buf: ByteBuffer) {
        FfiConverterTypeAmount.write(value.`immature`, buf)
        FfiConverterTypeAmount.write(value.`trustedPending`, buf)
        FfiConverterTypeAmount.write(value.`untrustedPending`, buf)
        FfiConverterTypeAmount.write(value.`confirmed`, buf)
        FfiConverterTypeAmount.write(value.`trustedSpendable`, buf)
        FfiConverterTypeAmount.write(value.`total`, buf)
    }
}




public object FfiConverterTypeBlockId: FfiConverterRustBuffer<BlockId> {
    override fun read(buf: ByteBuffer): BlockId {
        return BlockId(
            FfiConverterUInt.read(buf),
            FfiConverterTypeBlockHash.read(buf),
        )
    }

    override fun allocationSize(value: BlockId): ULong = (
            FfiConverterUInt.allocationSize(value.`height`) +
            FfiConverterTypeBlockHash.allocationSize(value.`hash`)
    )

    override fun write(value: BlockId, buf: ByteBuffer) {
        FfiConverterUInt.write(value.`height`, buf)
        FfiConverterTypeBlockHash.write(value.`hash`, buf)
    }
}




public object FfiConverterTypeCanonicalTx: FfiConverterRustBuffer<CanonicalTx> {
    override fun read(buf: ByteBuffer): CanonicalTx {
        return CanonicalTx(
            FfiConverterTypeTransaction.read(buf),
            FfiConverterTypeChainPosition.read(buf),
        )
    }

    override fun allocationSize(value: CanonicalTx): ULong = (
            FfiConverterTypeTransaction.allocationSize(value.`transaction`) +
            FfiConverterTypeChainPosition.allocationSize(value.`chainPosition`)
    )

    override fun write(value: CanonicalTx, buf: ByteBuffer) {
        FfiConverterTypeTransaction.write(value.`transaction`, buf)
        FfiConverterTypeChainPosition.write(value.`chainPosition`, buf)
    }
}




public object FfiConverterTypeCbfComponents: FfiConverterRustBuffer<CbfComponents> {
    override fun read(buf: ByteBuffer): CbfComponents {
        return CbfComponents(
            FfiConverterTypeCbfClient.read(buf),
            FfiConverterTypeCbfNode.read(buf),
        )
    }

    override fun allocationSize(value: CbfComponents): ULong = (
            FfiConverterTypeCbfClient.allocationSize(value.`client`) +
            FfiConverterTypeCbfNode.allocationSize(value.`node`)
    )

    override fun write(value: CbfComponents, buf: ByteBuffer) {
        FfiConverterTypeCbfClient.write(value.`client`, buf)
        FfiConverterTypeCbfNode.write(value.`node`, buf)
    }
}




public object FfiConverterTypeChainChange: FfiConverterRustBuffer<ChainChange> {
    override fun read(buf: ByteBuffer): ChainChange {
        return ChainChange(
            FfiConverterUInt.read(buf),
            FfiConverterOptionalTypeBlockHash.read(buf),
        )
    }

    override fun allocationSize(value: ChainChange): ULong = (
            FfiConverterUInt.allocationSize(value.`height`) +
            FfiConverterOptionalTypeBlockHash.allocationSize(value.`hash`)
    )

    override fun write(value: ChainChange, buf: ByteBuffer) {
        FfiConverterUInt.write(value.`height`, buf)
        FfiConverterOptionalTypeBlockHash.write(value.`hash`, buf)
    }
}




public object FfiConverterTypeCondition: FfiConverterRustBuffer<Condition> {
    override fun read(buf: ByteBuffer): Condition {
        return Condition(
            FfiConverterOptionalUInt.read(buf),
            FfiConverterOptionalTypeLockTime.read(buf),
        )
    }

    override fun allocationSize(value: Condition): ULong = (
            FfiConverterOptionalUInt.allocationSize(value.`csv`) +
            FfiConverterOptionalTypeLockTime.allocationSize(value.`timelock`)
    )

    override fun write(value: Condition, buf: ByteBuffer) {
        FfiConverterOptionalUInt.write(value.`csv`, buf)
        FfiConverterOptionalTypeLockTime.write(value.`timelock`, buf)
    }
}




public object FfiConverterTypeConfirmationBlockTime: FfiConverterRustBuffer<ConfirmationBlockTime> {
    override fun read(buf: ByteBuffer): ConfirmationBlockTime {
        return ConfirmationBlockTime(
            FfiConverterTypeBlockId.read(buf),
            FfiConverterULong.read(buf),
        )
    }

    override fun allocationSize(value: ConfirmationBlockTime): ULong = (
            FfiConverterTypeBlockId.allocationSize(value.`blockId`) +
            FfiConverterULong.allocationSize(value.`confirmationTime`)
    )

    override fun write(value: ConfirmationBlockTime, buf: ByteBuffer) {
        FfiConverterTypeBlockId.write(value.`blockId`, buf)
        FfiConverterULong.write(value.`confirmationTime`, buf)
    }
}




public object FfiConverterTypeControlBlock: FfiConverterRustBuffer<ControlBlock> {
    override fun read(buf: ByteBuffer): ControlBlock {
        return ControlBlock(
            FfiConverterByteArray.read(buf),
            FfiConverterSequenceString.read(buf),
            FfiConverterUByte.read(buf),
            FfiConverterUByte.read(buf),
        )
    }

    override fun allocationSize(value: ControlBlock): ULong = (
            FfiConverterByteArray.allocationSize(value.`internalKey`) +
            FfiConverterSequenceString.allocationSize(value.`merkleBranch`) +
            FfiConverterUByte.allocationSize(value.`outputKeyParity`) +
            FfiConverterUByte.allocationSize(value.`leafVersion`)
    )

    override fun write(value: ControlBlock, buf: ByteBuffer) {
        FfiConverterByteArray.write(value.`internalKey`, buf)
        FfiConverterSequenceString.write(value.`merkleBranch`, buf)
        FfiConverterUByte.write(value.`outputKeyParity`, buf)
        FfiConverterUByte.write(value.`leafVersion`, buf)
    }
}




public object FfiConverterTypeEvictedTx: FfiConverterRustBuffer<EvictedTx> {
    override fun read(buf: ByteBuffer): EvictedTx {
        return EvictedTx(
            FfiConverterTypeTxid.read(buf),
            FfiConverterULong.read(buf),
        )
    }

    override fun allocationSize(value: EvictedTx): ULong = (
            FfiConverterTypeTxid.allocationSize(value.`txid`) +
            FfiConverterULong.allocationSize(value.`evictedAt`)
    )

    override fun write(value: EvictedTx, buf: ByteBuffer) {
        FfiConverterTypeTxid.write(value.`txid`, buf)
        FfiConverterULong.write(value.`evictedAt`, buf)
    }
}




public object FfiConverterTypeFinalizedPsbtResult: FfiConverterRustBuffer<FinalizedPsbtResult> {
    override fun read(buf: ByteBuffer): FinalizedPsbtResult {
        return FinalizedPsbtResult(
            FfiConverterTypePsbt.read(buf),
            FfiConverterBoolean.read(buf),
            FfiConverterOptionalSequenceTypePsbtFinalizeError.read(buf),
        )
    }

    override fun allocationSize(value: FinalizedPsbtResult): ULong = (
            FfiConverterTypePsbt.allocationSize(value.`psbt`) +
            FfiConverterBoolean.allocationSize(value.`couldFinalize`) +
            FfiConverterOptionalSequenceTypePsbtFinalizeError.allocationSize(value.`errors`)
    )

    override fun write(value: FinalizedPsbtResult, buf: ByteBuffer) {
        FfiConverterTypePsbt.write(value.`psbt`, buf)
        FfiConverterBoolean.write(value.`couldFinalize`, buf)
        FfiConverterOptionalSequenceTypePsbtFinalizeError.write(value.`errors`, buf)
    }
}




public object FfiConverterTypeHeader: FfiConverterRustBuffer<Header> {
    override fun read(buf: ByteBuffer): Header {
        return Header(
            FfiConverterInt.read(buf),
            FfiConverterTypeBlockHash.read(buf),
            FfiConverterTypeTxMerkleNode.read(buf),
            FfiConverterUInt.read(buf),
            FfiConverterUInt.read(buf),
            FfiConverterUInt.read(buf),
        )
    }

    override fun allocationSize(value: Header): ULong = (
            FfiConverterInt.allocationSize(value.`version`) +
            FfiConverterTypeBlockHash.allocationSize(value.`prevBlockhash`) +
            FfiConverterTypeTxMerkleNode.allocationSize(value.`merkleRoot`) +
            FfiConverterUInt.allocationSize(value.`time`) +
            FfiConverterUInt.allocationSize(value.`bits`) +
            FfiConverterUInt.allocationSize(value.`nonce`)
    )

    override fun write(value: Header, buf: ByteBuffer) {
        FfiConverterInt.write(value.`version`, buf)
        FfiConverterTypeBlockHash.write(value.`prevBlockhash`, buf)
        FfiConverterTypeTxMerkleNode.write(value.`merkleRoot`, buf)
        FfiConverterUInt.write(value.`time`, buf)
        FfiConverterUInt.write(value.`bits`, buf)
        FfiConverterUInt.write(value.`nonce`, buf)
    }
}




public object FfiConverterTypeHeaderNotification: FfiConverterRustBuffer<HeaderNotification> {
    override fun read(buf: ByteBuffer): HeaderNotification {
        return HeaderNotification(
            FfiConverterULong.read(buf),
            FfiConverterTypeHeader.read(buf),
        )
    }

    override fun allocationSize(value: HeaderNotification): ULong = (
            FfiConverterULong.allocationSize(value.`height`) +
            FfiConverterTypeHeader.allocationSize(value.`header`)
    )

    override fun write(value: HeaderNotification, buf: ByteBuffer) {
        FfiConverterULong.write(value.`height`, buf)
        FfiConverterTypeHeader.write(value.`header`, buf)
    }
}




public object FfiConverterTypeIndexerChangeSet: FfiConverterRustBuffer<IndexerChangeSet> {
    override fun read(buf: ByteBuffer): IndexerChangeSet {
        return IndexerChangeSet(
            FfiConverterMapTypeDescriptorIdUInt.read(buf),
        )
    }

    override fun allocationSize(value: IndexerChangeSet): ULong = (
            FfiConverterMapTypeDescriptorIdUInt.allocationSize(value.`lastRevealed`)
    )

    override fun write(value: IndexerChangeSet, buf: ByteBuffer) {
        FfiConverterMapTypeDescriptorIdUInt.write(value.`lastRevealed`, buf)
    }
}




public object FfiConverterTypeInput: FfiConverterRustBuffer<Input> {
    override fun read(buf: ByteBuffer): Input {
        return Input(
            FfiConverterOptionalTypeTransaction.read(buf),
            FfiConverterOptionalTypeTxOut.read(buf),
            FfiConverterMapStringByteArray.read(buf),
            FfiConverterOptionalString.read(buf),
            FfiConverterOptionalTypeScript.read(buf),
            FfiConverterOptionalTypeScript.read(buf),
            FfiConverterMapStringTypeKeySource.read(buf),
            FfiConverterOptionalTypeScript.read(buf),
            FfiConverterOptionalSequenceByteArray.read(buf),
            FfiConverterMapStringByteArray.read(buf),
            FfiConverterMapStringByteArray.read(buf),
            FfiConverterMapStringByteArray.read(buf),
            FfiConverterMapStringByteArray.read(buf),
            FfiConverterOptionalByteArray.read(buf),
            FfiConverterMapTypeTapScriptSigKeyByteArray.read(buf),
            FfiConverterMapTypeControlBlockTypeTapScriptEntry.read(buf),
            FfiConverterMapStringTypeTapKeyOrigin.read(buf),
            FfiConverterOptionalString.read(buf),
            FfiConverterOptionalString.read(buf),
            FfiConverterMapTypeProprietaryKeyByteArray.read(buf),
            FfiConverterMapTypeKeyByteArray.read(buf),
        )
    }

    override fun allocationSize(value: Input): ULong = (
            FfiConverterOptionalTypeTransaction.allocationSize(value.`nonWitnessUtxo`) +
            FfiConverterOptionalTypeTxOut.allocationSize(value.`witnessUtxo`) +
            FfiConverterMapStringByteArray.allocationSize(value.`partialSigs`) +
            FfiConverterOptionalString.allocationSize(value.`sighashType`) +
            FfiConverterOptionalTypeScript.allocationSize(value.`redeemScript`) +
            FfiConverterOptionalTypeScript.allocationSize(value.`witnessScript`) +
            FfiConverterMapStringTypeKeySource.allocationSize(value.`bip32Derivation`) +
            FfiConverterOptionalTypeScript.allocationSize(value.`finalScriptSig`) +
            FfiConverterOptionalSequenceByteArray.allocationSize(value.`finalScriptWitness`) +
            FfiConverterMapStringByteArray.allocationSize(value.`ripemd160Preimages`) +
            FfiConverterMapStringByteArray.allocationSize(value.`sha256Preimages`) +
            FfiConverterMapStringByteArray.allocationSize(value.`hash160Preimages`) +
            FfiConverterMapStringByteArray.allocationSize(value.`hash256Preimages`) +
            FfiConverterOptionalByteArray.allocationSize(value.`tapKeySig`) +
            FfiConverterMapTypeTapScriptSigKeyByteArray.allocationSize(value.`tapScriptSigs`) +
            FfiConverterMapTypeControlBlockTypeTapScriptEntry.allocationSize(value.`tapScripts`) +
            FfiConverterMapStringTypeTapKeyOrigin.allocationSize(value.`tapKeyOrigins`) +
            FfiConverterOptionalString.allocationSize(value.`tapInternalKey`) +
            FfiConverterOptionalString.allocationSize(value.`tapMerkleRoot`) +
            FfiConverterMapTypeProprietaryKeyByteArray.allocationSize(value.`proprietary`) +
            FfiConverterMapTypeKeyByteArray.allocationSize(value.`unknown`)
    )

    override fun write(value: Input, buf: ByteBuffer) {
        FfiConverterOptionalTypeTransaction.write(value.`nonWitnessUtxo`, buf)
        FfiConverterOptionalTypeTxOut.write(value.`witnessUtxo`, buf)
        FfiConverterMapStringByteArray.write(value.`partialSigs`, buf)
        FfiConverterOptionalString.write(value.`sighashType`, buf)
        FfiConverterOptionalTypeScript.write(value.`redeemScript`, buf)
        FfiConverterOptionalTypeScript.write(value.`witnessScript`, buf)
        FfiConverterMapStringTypeKeySource.write(value.`bip32Derivation`, buf)
        FfiConverterOptionalTypeScript.write(value.`finalScriptSig`, buf)
        FfiConverterOptionalSequenceByteArray.write(value.`finalScriptWitness`, buf)
        FfiConverterMapStringByteArray.write(value.`ripemd160Preimages`, buf)
        FfiConverterMapStringByteArray.write(value.`sha256Preimages`, buf)
        FfiConverterMapStringByteArray.write(value.`hash160Preimages`, buf)
        FfiConverterMapStringByteArray.write(value.`hash256Preimages`, buf)
        FfiConverterOptionalByteArray.write(value.`tapKeySig`, buf)
        FfiConverterMapTypeTapScriptSigKeyByteArray.write(value.`tapScriptSigs`, buf)
        FfiConverterMapTypeControlBlockTypeTapScriptEntry.write(value.`tapScripts`, buf)
        FfiConverterMapStringTypeTapKeyOrigin.write(value.`tapKeyOrigins`, buf)
        FfiConverterOptionalString.write(value.`tapInternalKey`, buf)
        FfiConverterOptionalString.write(value.`tapMerkleRoot`, buf)
        FfiConverterMapTypeProprietaryKeyByteArray.write(value.`proprietary`, buf)
        FfiConverterMapTypeKeyByteArray.write(value.`unknown`, buf)
    }
}




public object FfiConverterTypeKey: FfiConverterRustBuffer<Key> {
    override fun read(buf: ByteBuffer): Key {
        return Key(
            FfiConverterUByte.read(buf),
            FfiConverterByteArray.read(buf),
        )
    }

    override fun allocationSize(value: Key): ULong = (
            FfiConverterUByte.allocationSize(value.`typeValue`) +
            FfiConverterByteArray.allocationSize(value.`key`)
    )

    override fun write(value: Key, buf: ByteBuffer) {
        FfiConverterUByte.write(value.`typeValue`, buf)
        FfiConverterByteArray.write(value.`key`, buf)
    }
}




public object FfiConverterTypeKeySource: FfiConverterRustBuffer<KeySource> {
    override fun read(buf: ByteBuffer): KeySource {
        return KeySource(
            FfiConverterString.read(buf),
            FfiConverterTypeDerivationPath.read(buf),
        )
    }

    override fun allocationSize(value: KeySource): ULong = (
            FfiConverterString.allocationSize(value.`fingerprint`) +
            FfiConverterTypeDerivationPath.allocationSize(value.`path`)
    )

    override fun write(value: KeySource, buf: ByteBuffer) {
        FfiConverterString.write(value.`fingerprint`, buf)
        FfiConverterTypeDerivationPath.write(value.`path`, buf)
    }
}




public object FfiConverterTypeKeychainAndIndex: FfiConverterRustBuffer<KeychainAndIndex> {
    override fun read(buf: ByteBuffer): KeychainAndIndex {
        return KeychainAndIndex(
            FfiConverterTypeKeychainKind.read(buf),
            FfiConverterUInt.read(buf),
        )
    }

    override fun allocationSize(value: KeychainAndIndex): ULong = (
            FfiConverterTypeKeychainKind.allocationSize(value.`keychain`) +
            FfiConverterUInt.allocationSize(value.`index`)
    )

    override fun write(value: KeychainAndIndex, buf: ByteBuffer) {
        FfiConverterTypeKeychainKind.write(value.`keychain`, buf)
        FfiConverterUInt.write(value.`index`, buf)
    }
}




public object FfiConverterTypeLocalChainChangeSet: FfiConverterRustBuffer<LocalChainChangeSet> {
    override fun read(buf: ByteBuffer): LocalChainChangeSet {
        return LocalChainChangeSet(
            FfiConverterSequenceTypeChainChange.read(buf),
        )
    }

    override fun allocationSize(value: LocalChainChangeSet): ULong = (
            FfiConverterSequenceTypeChainChange.allocationSize(value.`changes`)
    )

    override fun write(value: LocalChainChangeSet, buf: ByteBuffer) {
        FfiConverterSequenceTypeChainChange.write(value.`changes`, buf)
    }
}




public object FfiConverterTypeLocalOutput: FfiConverterRustBuffer<LocalOutput> {
    override fun read(buf: ByteBuffer): LocalOutput {
        return LocalOutput(
            FfiConverterTypeOutPoint.read(buf),
            FfiConverterTypeTxOut.read(buf),
            FfiConverterTypeKeychainKind.read(buf),
            FfiConverterBoolean.read(buf),
            FfiConverterUInt.read(buf),
            FfiConverterTypeChainPosition.read(buf),
        )
    }

    override fun allocationSize(value: LocalOutput): ULong = (
            FfiConverterTypeOutPoint.allocationSize(value.`outpoint`) +
            FfiConverterTypeTxOut.allocationSize(value.`txout`) +
            FfiConverterTypeKeychainKind.allocationSize(value.`keychain`) +
            FfiConverterBoolean.allocationSize(value.`isSpent`) +
            FfiConverterUInt.allocationSize(value.`derivationIndex`) +
            FfiConverterTypeChainPosition.allocationSize(value.`chainPosition`)
    )

    override fun write(value: LocalOutput, buf: ByteBuffer) {
        FfiConverterTypeOutPoint.write(value.`outpoint`, buf)
        FfiConverterTypeTxOut.write(value.`txout`, buf)
        FfiConverterTypeKeychainKind.write(value.`keychain`, buf)
        FfiConverterBoolean.write(value.`isSpent`, buf)
        FfiConverterUInt.write(value.`derivationIndex`, buf)
        FfiConverterTypeChainPosition.write(value.`chainPosition`, buf)
    }
}




public object FfiConverterTypeOutPoint: FfiConverterRustBuffer<OutPoint> {
    override fun read(buf: ByteBuffer): OutPoint {
        return OutPoint(
            FfiConverterTypeTxid.read(buf),
            FfiConverterUInt.read(buf),
        )
    }

    override fun allocationSize(value: OutPoint): ULong = (
            FfiConverterTypeTxid.allocationSize(value.`txid`) +
            FfiConverterUInt.allocationSize(value.`vout`)
    )

    override fun write(value: OutPoint, buf: ByteBuffer) {
        FfiConverterTypeTxid.write(value.`txid`, buf)
        FfiConverterUInt.write(value.`vout`, buf)
    }
}




public object FfiConverterTypePeer: FfiConverterRustBuffer<Peer> {
    override fun read(buf: ByteBuffer): Peer {
        return Peer(
            FfiConverterTypeIpAddress.read(buf),
            FfiConverterOptionalUShort.read(buf),
            FfiConverterBoolean.read(buf),
        )
    }

    override fun allocationSize(value: Peer): ULong = (
            FfiConverterTypeIpAddress.allocationSize(value.`address`) +
            FfiConverterOptionalUShort.allocationSize(value.`port`) +
            FfiConverterBoolean.allocationSize(value.`v2Transport`)
    )

    override fun write(value: Peer, buf: ByteBuffer) {
        FfiConverterTypeIpAddress.write(value.`address`, buf)
        FfiConverterOptionalUShort.write(value.`port`, buf)
        FfiConverterBoolean.write(value.`v2Transport`, buf)
    }
}




public object FfiConverterTypeProprietaryKey: FfiConverterRustBuffer<ProprietaryKey> {
    override fun read(buf: ByteBuffer): ProprietaryKey {
        return ProprietaryKey(
            FfiConverterByteArray.read(buf),
            FfiConverterUByte.read(buf),
            FfiConverterByteArray.read(buf),
        )
    }

    override fun allocationSize(value: ProprietaryKey): ULong = (
            FfiConverterByteArray.allocationSize(value.`prefix`) +
            FfiConverterUByte.allocationSize(value.`subtype`) +
            FfiConverterByteArray.allocationSize(value.`key`)
    )

    override fun write(value: ProprietaryKey, buf: ByteBuffer) {
        FfiConverterByteArray.write(value.`prefix`, buf)
        FfiConverterUByte.write(value.`subtype`, buf)
        FfiConverterByteArray.write(value.`key`, buf)
    }
}




public object FfiConverterTypeScriptAmount: FfiConverterRustBuffer<ScriptAmount> {
    override fun read(buf: ByteBuffer): ScriptAmount {
        return ScriptAmount(
            FfiConverterTypeScript.read(buf),
            FfiConverterTypeAmount.read(buf),
        )
    }

    override fun allocationSize(value: ScriptAmount): ULong = (
            FfiConverterTypeScript.allocationSize(value.`script`) +
            FfiConverterTypeAmount.allocationSize(value.`amount`)
    )

    override fun write(value: ScriptAmount, buf: ByteBuffer) {
        FfiConverterTypeScript.write(value.`script`, buf)
        FfiConverterTypeAmount.write(value.`amount`, buf)
    }
}




public object FfiConverterTypeSentAndReceivedValues: FfiConverterRustBuffer<SentAndReceivedValues> {
    override fun read(buf: ByteBuffer): SentAndReceivedValues {
        return SentAndReceivedValues(
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
        )
    }

    override fun allocationSize(value: SentAndReceivedValues): ULong = (
            FfiConverterTypeAmount.allocationSize(value.`sent`) +
            FfiConverterTypeAmount.allocationSize(value.`received`)
    )

    override fun write(value: SentAndReceivedValues, buf: ByteBuffer) {
        FfiConverterTypeAmount.write(value.`sent`, buf)
        FfiConverterTypeAmount.write(value.`received`, buf)
    }
}




public object FfiConverterTypeServerFeaturesRes: FfiConverterRustBuffer<ServerFeaturesRes> {
    override fun read(buf: ByteBuffer): ServerFeaturesRes {
        return ServerFeaturesRes(
            FfiConverterString.read(buf),
            FfiConverterTypeBlockHash.read(buf),
            FfiConverterString.read(buf),
            FfiConverterString.read(buf),
            FfiConverterOptionalString.read(buf),
            FfiConverterOptionalLong.read(buf),
        )
    }

    override fun allocationSize(value: ServerFeaturesRes): ULong = (
            FfiConverterString.allocationSize(value.`serverVersion`) +
            FfiConverterTypeBlockHash.allocationSize(value.`genesisHash`) +
            FfiConverterString.allocationSize(value.`protocolMin`) +
            FfiConverterString.allocationSize(value.`protocolMax`) +
            FfiConverterOptionalString.allocationSize(value.`hashFunction`) +
            FfiConverterOptionalLong.allocationSize(value.`pruning`)
    )

    override fun write(value: ServerFeaturesRes, buf: ByteBuffer) {
        FfiConverterString.write(value.`serverVersion`, buf)
        FfiConverterTypeBlockHash.write(value.`genesisHash`, buf)
        FfiConverterString.write(value.`protocolMin`, buf)
        FfiConverterString.write(value.`protocolMax`, buf)
        FfiConverterOptionalString.write(value.`hashFunction`, buf)
        FfiConverterOptionalLong.write(value.`pruning`, buf)
    }
}




public object FfiConverterTypeSignOptions: FfiConverterRustBuffer<SignOptions> {
    override fun read(buf: ByteBuffer): SignOptions {
        return SignOptions(
            FfiConverterBoolean.read(buf),
            FfiConverterOptionalUInt.read(buf),
            FfiConverterBoolean.read(buf),
            FfiConverterBoolean.read(buf),
            FfiConverterBoolean.read(buf),
            FfiConverterBoolean.read(buf),
        )
    }

    override fun allocationSize(value: SignOptions): ULong = (
            FfiConverterBoolean.allocationSize(value.`trustWitnessUtxo`) +
            FfiConverterOptionalUInt.allocationSize(value.`assumeHeight`) +
            FfiConverterBoolean.allocationSize(value.`allowAllSighashes`) +
            FfiConverterBoolean.allocationSize(value.`tryFinalize`) +
            FfiConverterBoolean.allocationSize(value.`signWithTapInternalKey`) +
            FfiConverterBoolean.allocationSize(value.`allowGrinding`)
    )

    override fun write(value: SignOptions, buf: ByteBuffer) {
        FfiConverterBoolean.write(value.`trustWitnessUtxo`, buf)
        FfiConverterOptionalUInt.write(value.`assumeHeight`, buf)
        FfiConverterBoolean.write(value.`allowAllSighashes`, buf)
        FfiConverterBoolean.write(value.`tryFinalize`, buf)
        FfiConverterBoolean.write(value.`signWithTapInternalKey`, buf)
        FfiConverterBoolean.write(value.`allowGrinding`, buf)
    }
}




public object FfiConverterTypeSocks5Proxy: FfiConverterRustBuffer<Socks5Proxy> {
    override fun read(buf: ByteBuffer): Socks5Proxy {
        return Socks5Proxy(
            FfiConverterTypeIpAddress.read(buf),
            FfiConverterUShort.read(buf),
        )
    }

    override fun allocationSize(value: Socks5Proxy): ULong = (
            FfiConverterTypeIpAddress.allocationSize(value.`address`) +
            FfiConverterUShort.allocationSize(value.`port`)
    )

    override fun write(value: Socks5Proxy, buf: ByteBuffer) {
        FfiConverterTypeIpAddress.write(value.`address`, buf)
        FfiConverterUShort.write(value.`port`, buf)
    }
}




public object FfiConverterTypeTapKeyOrigin: FfiConverterRustBuffer<TapKeyOrigin> {
    override fun read(buf: ByteBuffer): TapKeyOrigin {
        return TapKeyOrigin(
            FfiConverterSequenceString.read(buf),
            FfiConverterTypeKeySource.read(buf),
        )
    }

    override fun allocationSize(value: TapKeyOrigin): ULong = (
            FfiConverterSequenceString.allocationSize(value.`tapLeafHashes`) +
            FfiConverterTypeKeySource.allocationSize(value.`keySource`)
    )

    override fun write(value: TapKeyOrigin, buf: ByteBuffer) {
        FfiConverterSequenceString.write(value.`tapLeafHashes`, buf)
        FfiConverterTypeKeySource.write(value.`keySource`, buf)
    }
}




public object FfiConverterTypeTapScriptEntry: FfiConverterRustBuffer<TapScriptEntry> {
    override fun read(buf: ByteBuffer): TapScriptEntry {
        return TapScriptEntry(
            FfiConverterTypeScript.read(buf),
            FfiConverterUByte.read(buf),
        )
    }

    override fun allocationSize(value: TapScriptEntry): ULong = (
            FfiConverterTypeScript.allocationSize(value.`script`) +
            FfiConverterUByte.allocationSize(value.`leafVersion`)
    )

    override fun write(value: TapScriptEntry, buf: ByteBuffer) {
        FfiConverterTypeScript.write(value.`script`, buf)
        FfiConverterUByte.write(value.`leafVersion`, buf)
    }
}




public object FfiConverterTypeTapScriptSigKey: FfiConverterRustBuffer<TapScriptSigKey> {
    override fun read(buf: ByteBuffer): TapScriptSigKey {
        return TapScriptSigKey(
            FfiConverterString.read(buf),
            FfiConverterString.read(buf),
        )
    }

    override fun allocationSize(value: TapScriptSigKey): ULong = (
            FfiConverterString.allocationSize(value.`xonlyPubkey`) +
            FfiConverterString.allocationSize(value.`tapLeafHash`)
    )

    override fun write(value: TapScriptSigKey, buf: ByteBuffer) {
        FfiConverterString.write(value.`xonlyPubkey`, buf)
        FfiConverterString.write(value.`tapLeafHash`, buf)
    }
}




public object FfiConverterTypeTx: FfiConverterRustBuffer<Tx> {
    override fun read(buf: ByteBuffer): Tx {
        return Tx(
            FfiConverterTypeTxid.read(buf),
            FfiConverterInt.read(buf),
            FfiConverterUInt.read(buf),
            FfiConverterULong.read(buf),
            FfiConverterULong.read(buf),
            FfiConverterULong.read(buf),
            FfiConverterTypeTxStatus.read(buf),
        )
    }

    override fun allocationSize(value: Tx): ULong = (
            FfiConverterTypeTxid.allocationSize(value.`txid`) +
            FfiConverterInt.allocationSize(value.`version`) +
            FfiConverterUInt.allocationSize(value.`locktime`) +
            FfiConverterULong.allocationSize(value.`size`) +
            FfiConverterULong.allocationSize(value.`weight`) +
            FfiConverterULong.allocationSize(value.`fee`) +
            FfiConverterTypeTxStatus.allocationSize(value.`status`)
    )

    override fun write(value: Tx, buf: ByteBuffer) {
        FfiConverterTypeTxid.write(value.`txid`, buf)
        FfiConverterInt.write(value.`version`, buf)
        FfiConverterUInt.write(value.`locktime`, buf)
        FfiConverterULong.write(value.`size`, buf)
        FfiConverterULong.write(value.`weight`, buf)
        FfiConverterULong.write(value.`fee`, buf)
        FfiConverterTypeTxStatus.write(value.`status`, buf)
    }
}




public object FfiConverterTypeTxDetails: FfiConverterRustBuffer<TxDetails> {
    override fun read(buf: ByteBuffer): TxDetails {
        return TxDetails(
            FfiConverterTypeTxid.read(buf),
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeAmount.read(buf),
            FfiConverterOptionalTypeAmount.read(buf),
            FfiConverterOptionalFloat.read(buf),
            FfiConverterLong.read(buf),
            FfiConverterTypeChainPosition.read(buf),
            FfiConverterTypeTransaction.read(buf),
        )
    }

    override fun allocationSize(value: TxDetails): ULong = (
            FfiConverterTypeTxid.allocationSize(value.`txid`) +
            FfiConverterTypeAmount.allocationSize(value.`sent`) +
            FfiConverterTypeAmount.allocationSize(value.`received`) +
            FfiConverterOptionalTypeAmount.allocationSize(value.`fee`) +
            FfiConverterOptionalFloat.allocationSize(value.`feeRate`) +
            FfiConverterLong.allocationSize(value.`balanceDelta`) +
            FfiConverterTypeChainPosition.allocationSize(value.`chainPosition`) +
            FfiConverterTypeTransaction.allocationSize(value.`tx`)
    )

    override fun write(value: TxDetails, buf: ByteBuffer) {
        FfiConverterTypeTxid.write(value.`txid`, buf)
        FfiConverterTypeAmount.write(value.`sent`, buf)
        FfiConverterTypeAmount.write(value.`received`, buf)
        FfiConverterOptionalTypeAmount.write(value.`fee`, buf)
        FfiConverterOptionalFloat.write(value.`feeRate`, buf)
        FfiConverterLong.write(value.`balanceDelta`, buf)
        FfiConverterTypeChainPosition.write(value.`chainPosition`, buf)
        FfiConverterTypeTransaction.write(value.`tx`, buf)
    }
}




public object FfiConverterTypeTxGraphChangeSet: FfiConverterRustBuffer<TxGraphChangeSet> {
    override fun read(buf: ByteBuffer): TxGraphChangeSet {
        return TxGraphChangeSet(
            FfiConverterSequenceTypeTransaction.read(buf),
            FfiConverterMapTypeHashableOutPointTypeTxOut.read(buf),
            FfiConverterSequenceTypeAnchor.read(buf),
            FfiConverterMapTypeTxidULong.read(buf),
            FfiConverterMapTypeTxidULong.read(buf),
            FfiConverterMapTypeTxidULong.read(buf),
        )
    }

    override fun allocationSize(value: TxGraphChangeSet): ULong = (
            FfiConverterSequenceTypeTransaction.allocationSize(value.`txs`) +
            FfiConverterMapTypeHashableOutPointTypeTxOut.allocationSize(value.`txouts`) +
            FfiConverterSequenceTypeAnchor.allocationSize(value.`anchors`) +
            FfiConverterMapTypeTxidULong.allocationSize(value.`lastSeen`) +
            FfiConverterMapTypeTxidULong.allocationSize(value.`firstSeen`) +
            FfiConverterMapTypeTxidULong.allocationSize(value.`lastEvicted`)
    )

    override fun write(value: TxGraphChangeSet, buf: ByteBuffer) {
        FfiConverterSequenceTypeTransaction.write(value.`txs`, buf)
        FfiConverterMapTypeHashableOutPointTypeTxOut.write(value.`txouts`, buf)
        FfiConverterSequenceTypeAnchor.write(value.`anchors`, buf)
        FfiConverterMapTypeTxidULong.write(value.`lastSeen`, buf)
        FfiConverterMapTypeTxidULong.write(value.`firstSeen`, buf)
        FfiConverterMapTypeTxidULong.write(value.`lastEvicted`, buf)
    }
}




public object FfiConverterTypeTxIn: FfiConverterRustBuffer<TxIn> {
    override fun read(buf: ByteBuffer): TxIn {
        return TxIn(
            FfiConverterTypeOutPoint.read(buf),
            FfiConverterTypeScript.read(buf),
            FfiConverterUInt.read(buf),
            FfiConverterSequenceByteArray.read(buf),
        )
    }

    override fun allocationSize(value: TxIn): ULong = (
            FfiConverterTypeOutPoint.allocationSize(value.`previousOutput`) +
            FfiConverterTypeScript.allocationSize(value.`scriptSig`) +
            FfiConverterUInt.allocationSize(value.`sequence`) +
            FfiConverterSequenceByteArray.allocationSize(value.`witness`)
    )

    override fun write(value: TxIn, buf: ByteBuffer) {
        FfiConverterTypeOutPoint.write(value.`previousOutput`, buf)
        FfiConverterTypeScript.write(value.`scriptSig`, buf)
        FfiConverterUInt.write(value.`sequence`, buf)
        FfiConverterSequenceByteArray.write(value.`witness`, buf)
    }
}




public object FfiConverterTypeTxOut: FfiConverterRustBuffer<TxOut> {
    override fun read(buf: ByteBuffer): TxOut {
        return TxOut(
            FfiConverterTypeAmount.read(buf),
            FfiConverterTypeScript.read(buf),
        )
    }

    override fun allocationSize(value: TxOut): ULong = (
            FfiConverterTypeAmount.allocationSize(value.`value`) +
            FfiConverterTypeScript.allocationSize(value.`scriptPubkey`)
    )

    override fun write(value: TxOut, buf: ByteBuffer) {
        FfiConverterTypeAmount.write(value.`value`, buf)
        FfiConverterTypeScript.write(value.`scriptPubkey`, buf)
    }
}




public object FfiConverterTypeTxStatus: FfiConverterRustBuffer<TxStatus> {
    override fun read(buf: ByteBuffer): TxStatus {
        return TxStatus(
            FfiConverterBoolean.read(buf),
            FfiConverterOptionalUInt.read(buf),
            FfiConverterOptionalTypeBlockHash.read(buf),
            FfiConverterOptionalULong.read(buf),
        )
    }

    override fun allocationSize(value: TxStatus): ULong = (
            FfiConverterBoolean.allocationSize(value.`confirmed`) +
            FfiConverterOptionalUInt.allocationSize(value.`blockHeight`) +
            FfiConverterOptionalTypeBlockHash.allocationSize(value.`blockHash`) +
            FfiConverterOptionalULong.allocationSize(value.`blockTime`)
    )

    override fun write(value: TxStatus, buf: ByteBuffer) {
        FfiConverterBoolean.write(value.`confirmed`, buf)
        FfiConverterOptionalUInt.write(value.`blockHeight`, buf)
        FfiConverterOptionalTypeBlockHash.write(value.`blockHash`, buf)
        FfiConverterOptionalULong.write(value.`blockTime`, buf)
    }
}




public object FfiConverterTypeUnconfirmedTx: FfiConverterRustBuffer<UnconfirmedTx> {
    override fun read(buf: ByteBuffer): UnconfirmedTx {
        return UnconfirmedTx(
            FfiConverterTypeTransaction.read(buf),
            FfiConverterULong.read(buf),
        )
    }

    override fun allocationSize(value: UnconfirmedTx): ULong = (
            FfiConverterTypeTransaction.allocationSize(value.`tx`) +
            FfiConverterULong.allocationSize(value.`lastSeen`)
    )

    override fun write(value: UnconfirmedTx, buf: ByteBuffer) {
        FfiConverterTypeTransaction.write(value.`tx`, buf)
        FfiConverterULong.write(value.`lastSeen`, buf)
    }
}




public object FfiConverterTypeWitnessProgram: FfiConverterRustBuffer<WitnessProgram> {
    override fun read(buf: ByteBuffer): WitnessProgram {
        return WitnessProgram(
            FfiConverterUByte.read(buf),
            FfiConverterByteArray.read(buf),
        )
    }

    override fun allocationSize(value: WitnessProgram): ULong = (
            FfiConverterUByte.allocationSize(value.`version`) +
            FfiConverterByteArray.allocationSize(value.`program`)
    )

    override fun write(value: WitnessProgram, buf: ByteBuffer) {
        FfiConverterUByte.write(value.`version`, buf)
        FfiConverterByteArray.write(value.`program`, buf)
    }
}





public object FfiConverterTypeAddressData : FfiConverterRustBuffer<AddressData>{
    override fun read(buf: ByteBuffer): AddressData {
        return when(buf.getInt()) {
            1 -> AddressData.P2pkh(
                FfiConverterString.read(buf),
                )
            2 -> AddressData.P2sh(
                FfiConverterString.read(buf),
                )
            3 -> AddressData.Segwit(
                FfiConverterTypeWitnessProgram.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: AddressData): ULong = when(value) {
        is AddressData.P2pkh -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`pubkeyHash`)
            )
        }
        is AddressData.P2sh -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`scriptHash`)
            )
        }
        is AddressData.Segwit -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterTypeWitnessProgram.allocationSize(value.`witnessProgram`)
            )
        }
    }

    override fun write(value: AddressData, buf: ByteBuffer) {
        when(value) {
            is AddressData.P2pkh -> {
                buf.putInt(1)
                FfiConverterString.write(value.`pubkeyHash`, buf)
                Unit
            }
            is AddressData.P2sh -> {
                buf.putInt(2)
                FfiConverterString.write(value.`scriptHash`, buf)
                Unit
            }
            is AddressData.Segwit -> {
                buf.putInt(3)
                FfiConverterTypeWitnessProgram.write(value.`witnessProgram`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object AddressParseExceptionErrorHandler : UniffiRustCallStatusErrorHandler<AddressParseException> {
    override fun lift(errorBuf: RustBufferByValue): AddressParseException = FfiConverterTypeAddressParseError.lift(errorBuf)
}

public object FfiConverterTypeAddressParseError : FfiConverterRustBuffer<AddressParseException> {
    override fun read(buf: ByteBuffer): AddressParseException {
        return when (buf.getInt()) {
            1 -> AddressParseException.Base58()
            2 -> AddressParseException.Bech32()
            3 -> AddressParseException.WitnessVersion(
                FfiConverterString.read(buf),
                )
            4 -> AddressParseException.WitnessProgram(
                FfiConverterString.read(buf),
                )
            5 -> AddressParseException.UnknownHrp()
            6 -> AddressParseException.LegacyAddressTooLong()
            7 -> AddressParseException.InvalidBase58PayloadLength()
            8 -> AddressParseException.InvalidLegacyPrefix()
            9 -> AddressParseException.NetworkValidation()
            10 -> AddressParseException.OtherAddressParseErr()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: AddressParseException): ULong {
        return when (value) {
            is AddressParseException.Base58 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.Bech32 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.WitnessVersion -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is AddressParseException.WitnessProgram -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is AddressParseException.UnknownHrp -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.LegacyAddressTooLong -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.InvalidBase58PayloadLength -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.InvalidLegacyPrefix -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.NetworkValidation -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is AddressParseException.OtherAddressParseErr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: AddressParseException, buf: ByteBuffer) {
        when (value) {
            is AddressParseException.Base58 -> {
                buf.putInt(1)
                Unit
            }
            is AddressParseException.Bech32 -> {
                buf.putInt(2)
                Unit
            }
            is AddressParseException.WitnessVersion -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is AddressParseException.WitnessProgram -> {
                buf.putInt(4)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is AddressParseException.UnknownHrp -> {
                buf.putInt(5)
                Unit
            }
            is AddressParseException.LegacyAddressTooLong -> {
                buf.putInt(6)
                Unit
            }
            is AddressParseException.InvalidBase58PayloadLength -> {
                buf.putInt(7)
                Unit
            }
            is AddressParseException.InvalidLegacyPrefix -> {
                buf.putInt(8)
                Unit
            }
            is AddressParseException.NetworkValidation -> {
                buf.putInt(9)
                Unit
            }
            is AddressParseException.OtherAddressParseErr -> {
                buf.putInt(10)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object Bip32ExceptionErrorHandler : UniffiRustCallStatusErrorHandler<Bip32Exception> {
    override fun lift(errorBuf: RustBufferByValue): Bip32Exception = FfiConverterTypeBip32Error.lift(errorBuf)
}

public object FfiConverterTypeBip32Error : FfiConverterRustBuffer<Bip32Exception> {
    override fun read(buf: ByteBuffer): Bip32Exception {
        return when (buf.getInt()) {
            1 -> Bip32Exception.CannotDeriveFromHardenedKey()
            2 -> Bip32Exception.Secp256k1(
                FfiConverterString.read(buf),
                )
            3 -> Bip32Exception.InvalidChildNumber(
                FfiConverterUInt.read(buf),
                )
            4 -> Bip32Exception.InvalidChildNumberFormat()
            5 -> Bip32Exception.InvalidDerivationPathFormat()
            6 -> Bip32Exception.UnknownVersion(
                FfiConverterString.read(buf),
                )
            7 -> Bip32Exception.WrongExtendedKeyLength(
                FfiConverterUInt.read(buf),
                )
            8 -> Bip32Exception.Base58(
                FfiConverterString.read(buf),
                )
            9 -> Bip32Exception.Hex(
                FfiConverterString.read(buf),
                )
            10 -> Bip32Exception.InvalidPublicKeyHexLength(
                FfiConverterUInt.read(buf),
                )
            11 -> Bip32Exception.UnknownException(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: Bip32Exception): ULong {
        return when (value) {
            is Bip32Exception.CannotDeriveFromHardenedKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is Bip32Exception.Secp256k1 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is Bip32Exception.InvalidChildNumber -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`childNumber`)
            )
            is Bip32Exception.InvalidChildNumberFormat -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is Bip32Exception.InvalidDerivationPathFormat -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is Bip32Exception.UnknownVersion -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`version`)
            )
            is Bip32Exception.WrongExtendedKeyLength -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`length`)
            )
            is Bip32Exception.Base58 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is Bip32Exception.Hex -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is Bip32Exception.InvalidPublicKeyHexLength -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`length`)
            )
            is Bip32Exception.UnknownException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
        }
    }

    override fun write(value: Bip32Exception, buf: ByteBuffer) {
        when (value) {
            is Bip32Exception.CannotDeriveFromHardenedKey -> {
                buf.putInt(1)
                Unit
            }
            is Bip32Exception.Secp256k1 -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is Bip32Exception.InvalidChildNumber -> {
                buf.putInt(3)
                FfiConverterUInt.write(value.`childNumber`, buf)
                Unit
            }
            is Bip32Exception.InvalidChildNumberFormat -> {
                buf.putInt(4)
                Unit
            }
            is Bip32Exception.InvalidDerivationPathFormat -> {
                buf.putInt(5)
                Unit
            }
            is Bip32Exception.UnknownVersion -> {
                buf.putInt(6)
                FfiConverterString.write(value.`version`, buf)
                Unit
            }
            is Bip32Exception.WrongExtendedKeyLength -> {
                buf.putInt(7)
                FfiConverterUInt.write(value.`length`, buf)
                Unit
            }
            is Bip32Exception.Base58 -> {
                buf.putInt(8)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is Bip32Exception.Hex -> {
                buf.putInt(9)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is Bip32Exception.InvalidPublicKeyHexLength -> {
                buf.putInt(10)
                FfiConverterUInt.write(value.`length`, buf)
                Unit
            }
            is Bip32Exception.UnknownException -> {
                buf.putInt(11)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object Bip39ExceptionErrorHandler : UniffiRustCallStatusErrorHandler<Bip39Exception> {
    override fun lift(errorBuf: RustBufferByValue): Bip39Exception = FfiConverterTypeBip39Error.lift(errorBuf)
}

public object FfiConverterTypeBip39Error : FfiConverterRustBuffer<Bip39Exception> {
    override fun read(buf: ByteBuffer): Bip39Exception {
        return when (buf.getInt()) {
            1 -> Bip39Exception.BadWordCount(
                FfiConverterULong.read(buf),
                )
            2 -> Bip39Exception.UnknownWord(
                FfiConverterULong.read(buf),
                )
            3 -> Bip39Exception.BadEntropyBitCount(
                FfiConverterULong.read(buf),
                )
            4 -> Bip39Exception.InvalidChecksum()
            5 -> Bip39Exception.AmbiguousLanguages(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: Bip39Exception): ULong {
        return when (value) {
            is Bip39Exception.BadWordCount -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`wordCount`)
            )
            is Bip39Exception.UnknownWord -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`index`)
            )
            is Bip39Exception.BadEntropyBitCount -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`bitCount`)
            )
            is Bip39Exception.InvalidChecksum -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is Bip39Exception.AmbiguousLanguages -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`languages`)
            )
        }
    }

    override fun write(value: Bip39Exception, buf: ByteBuffer) {
        when (value) {
            is Bip39Exception.BadWordCount -> {
                buf.putInt(1)
                FfiConverterULong.write(value.`wordCount`, buf)
                Unit
            }
            is Bip39Exception.UnknownWord -> {
                buf.putInt(2)
                FfiConverterULong.write(value.`index`, buf)
                Unit
            }
            is Bip39Exception.BadEntropyBitCount -> {
                buf.putInt(3)
                FfiConverterULong.write(value.`bitCount`, buf)
                Unit
            }
            is Bip39Exception.InvalidChecksum -> {
                buf.putInt(4)
                Unit
            }
            is Bip39Exception.AmbiguousLanguages -> {
                buf.putInt(5)
                FfiConverterString.write(value.`languages`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object CalculateFeeExceptionErrorHandler : UniffiRustCallStatusErrorHandler<CalculateFeeException> {
    override fun lift(errorBuf: RustBufferByValue): CalculateFeeException = FfiConverterTypeCalculateFeeError.lift(errorBuf)
}

public object FfiConverterTypeCalculateFeeError : FfiConverterRustBuffer<CalculateFeeException> {
    override fun read(buf: ByteBuffer): CalculateFeeException {
        return when (buf.getInt()) {
            1 -> CalculateFeeException.MissingTxOut(
                FfiConverterSequenceTypeOutPoint.read(buf),
                )
            2 -> CalculateFeeException.NegativeFee(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: CalculateFeeException): ULong {
        return when (value) {
            is CalculateFeeException.MissingTxOut -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterSequenceTypeOutPoint.allocationSize(value.`outPoints`)
            )
            is CalculateFeeException.NegativeFee -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`amount`)
            )
        }
    }

    override fun write(value: CalculateFeeException, buf: ByteBuffer) {
        when (value) {
            is CalculateFeeException.MissingTxOut -> {
                buf.putInt(1)
                FfiConverterSequenceTypeOutPoint.write(value.`outPoints`, buf)
                Unit
            }
            is CalculateFeeException.NegativeFee -> {
                buf.putInt(2)
                FfiConverterString.write(value.`amount`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object CannotConnectExceptionErrorHandler : UniffiRustCallStatusErrorHandler<CannotConnectException> {
    override fun lift(errorBuf: RustBufferByValue): CannotConnectException = FfiConverterTypeCannotConnectError.lift(errorBuf)
}

public object FfiConverterTypeCannotConnectError : FfiConverterRustBuffer<CannotConnectException> {
    override fun read(buf: ByteBuffer): CannotConnectException {
        return when (buf.getInt()) {
            1 -> CannotConnectException.Include(
                FfiConverterUInt.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: CannotConnectException): ULong {
        return when (value) {
            is CannotConnectException.Include -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`height`)
            )
        }
    }

    override fun write(value: CannotConnectException, buf: ByteBuffer) {
        when (value) {
            is CannotConnectException.Include -> {
                buf.putInt(1)
                FfiConverterUInt.write(value.`height`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object CbfExceptionErrorHandler : UniffiRustCallStatusErrorHandler<CbfException> {
    override fun lift(errorBuf: RustBufferByValue): CbfException = FfiConverterTypeCbfError.lift(errorBuf)
}

public object FfiConverterTypeCbfError : FfiConverterRustBuffer<CbfException> {
    override fun read(buf: ByteBuffer): CbfException {
        return when (buf.getInt()) {
            1 -> CbfException.NodeStopped()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: CbfException): ULong {
        return when (value) {
            is CbfException.NodeStopped -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: CbfException, buf: ByteBuffer) {
        when (value) {
            is CbfException.NodeStopped -> {
                buf.putInt(1)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeChainPosition : FfiConverterRustBuffer<ChainPosition>{
    override fun read(buf: ByteBuffer): ChainPosition {
        return when(buf.getInt()) {
            1 -> ChainPosition.Confirmed(
                FfiConverterTypeConfirmationBlockTime.read(buf),
                FfiConverterOptionalTypeTxid.read(buf),
                )
            2 -> ChainPosition.Unconfirmed(
                FfiConverterOptionalULong.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: ChainPosition): ULong = when(value) {
        is ChainPosition.Confirmed -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterTypeConfirmationBlockTime.allocationSize(value.`confirmationBlockTime`)
                + FfiConverterOptionalTypeTxid.allocationSize(value.`transitively`)
            )
        }
        is ChainPosition.Unconfirmed -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterOptionalULong.allocationSize(value.`timestamp`)
            )
        }
    }

    override fun write(value: ChainPosition, buf: ByteBuffer) {
        when(value) {
            is ChainPosition.Confirmed -> {
                buf.putInt(1)
                FfiConverterTypeConfirmationBlockTime.write(value.`confirmationBlockTime`, buf)
                FfiConverterOptionalTypeTxid.write(value.`transitively`, buf)
                Unit
            }
            is ChainPosition.Unconfirmed -> {
                buf.putInt(2)
                FfiConverterOptionalULong.write(value.`timestamp`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeChangeSpendPolicy: FfiConverterRustBuffer<ChangeSpendPolicy> {
    override fun read(buf: ByteBuffer): ChangeSpendPolicy = try {
        ChangeSpendPolicy.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: ChangeSpendPolicy): ULong = 4UL

    override fun write(value: ChangeSpendPolicy, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}




public object CreateTxExceptionErrorHandler : UniffiRustCallStatusErrorHandler<CreateTxException> {
    override fun lift(errorBuf: RustBufferByValue): CreateTxException = FfiConverterTypeCreateTxError.lift(errorBuf)
}

public object FfiConverterTypeCreateTxError : FfiConverterRustBuffer<CreateTxException> {
    override fun read(buf: ByteBuffer): CreateTxException {
        return when (buf.getInt()) {
            1 -> CreateTxException.Descriptor(
                FfiConverterString.read(buf),
                )
            2 -> CreateTxException.Policy(
                FfiConverterString.read(buf),
                )
            3 -> CreateTxException.SpendingPolicyRequired(
                FfiConverterString.read(buf),
                )
            4 -> CreateTxException.Version0()
            5 -> CreateTxException.Version1Csv()
            6 -> CreateTxException.LockTime(
                FfiConverterString.read(buf),
                FfiConverterString.read(buf),
                )
            7 -> CreateTxException.RbfSequenceCsv(
                FfiConverterString.read(buf),
                FfiConverterString.read(buf),
                )
            8 -> CreateTxException.FeeTooLow(
                FfiConverterString.read(buf),
                )
            9 -> CreateTxException.FeeRateTooLow(
                FfiConverterString.read(buf),
                )
            10 -> CreateTxException.NoUtxosSelected()
            11 -> CreateTxException.OutputBelowDustLimit(
                FfiConverterULong.read(buf),
                )
            12 -> CreateTxException.ChangePolicyDescriptor()
            13 -> CreateTxException.CoinSelection(
                FfiConverterString.read(buf),
                )
            14 -> CreateTxException.InsufficientFunds(
                FfiConverterULong.read(buf),
                FfiConverterULong.read(buf),
                )
            15 -> CreateTxException.NoRecipients()
            16 -> CreateTxException.Psbt(
                FfiConverterString.read(buf),
                )
            17 -> CreateTxException.MissingKeyOrigin(
                FfiConverterString.read(buf),
                )
            18 -> CreateTxException.UnknownUtxo(
                FfiConverterString.read(buf),
                )
            19 -> CreateTxException.MissingNonWitnessUtxo(
                FfiConverterString.read(buf),
                )
            20 -> CreateTxException.MiniscriptPsbt(
                FfiConverterString.read(buf),
                )
            21 -> CreateTxException.PushBytesException()
            22 -> CreateTxException.LockTimeConversionException()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: CreateTxException): ULong {
        return when (value) {
            is CreateTxException.Descriptor -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is CreateTxException.Policy -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is CreateTxException.SpendingPolicyRequired -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`kind`)
            )
            is CreateTxException.Version0 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateTxException.Version1Csv -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateTxException.LockTime -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`requested`)
                + FfiConverterString.allocationSize(value.`required`)
            )
            is CreateTxException.RbfSequenceCsv -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`sequence`)
                + FfiConverterString.allocationSize(value.`csv`)
            )
            is CreateTxException.FeeTooLow -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`required`)
            )
            is CreateTxException.FeeRateTooLow -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`required`)
            )
            is CreateTxException.NoUtxosSelected -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateTxException.OutputBelowDustLimit -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`index`)
            )
            is CreateTxException.ChangePolicyDescriptor -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateTxException.CoinSelection -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is CreateTxException.InsufficientFunds -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`needed`)
                + FfiConverterULong.allocationSize(value.`available`)
            )
            is CreateTxException.NoRecipients -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateTxException.Psbt -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is CreateTxException.MissingKeyOrigin -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`key`)
            )
            is CreateTxException.UnknownUtxo -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`outpoint`)
            )
            is CreateTxException.MissingNonWitnessUtxo -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`outpoint`)
            )
            is CreateTxException.MiniscriptPsbt -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is CreateTxException.PushBytesException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateTxException.LockTimeConversionException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: CreateTxException, buf: ByteBuffer) {
        when (value) {
            is CreateTxException.Descriptor -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is CreateTxException.Policy -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is CreateTxException.SpendingPolicyRequired -> {
                buf.putInt(3)
                FfiConverterString.write(value.`kind`, buf)
                Unit
            }
            is CreateTxException.Version0 -> {
                buf.putInt(4)
                Unit
            }
            is CreateTxException.Version1Csv -> {
                buf.putInt(5)
                Unit
            }
            is CreateTxException.LockTime -> {
                buf.putInt(6)
                FfiConverterString.write(value.`requested`, buf)
                FfiConverterString.write(value.`required`, buf)
                Unit
            }
            is CreateTxException.RbfSequenceCsv -> {
                buf.putInt(7)
                FfiConverterString.write(value.`sequence`, buf)
                FfiConverterString.write(value.`csv`, buf)
                Unit
            }
            is CreateTxException.FeeTooLow -> {
                buf.putInt(8)
                FfiConverterString.write(value.`required`, buf)
                Unit
            }
            is CreateTxException.FeeRateTooLow -> {
                buf.putInt(9)
                FfiConverterString.write(value.`required`, buf)
                Unit
            }
            is CreateTxException.NoUtxosSelected -> {
                buf.putInt(10)
                Unit
            }
            is CreateTxException.OutputBelowDustLimit -> {
                buf.putInt(11)
                FfiConverterULong.write(value.`index`, buf)
                Unit
            }
            is CreateTxException.ChangePolicyDescriptor -> {
                buf.putInt(12)
                Unit
            }
            is CreateTxException.CoinSelection -> {
                buf.putInt(13)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is CreateTxException.InsufficientFunds -> {
                buf.putInt(14)
                FfiConverterULong.write(value.`needed`, buf)
                FfiConverterULong.write(value.`available`, buf)
                Unit
            }
            is CreateTxException.NoRecipients -> {
                buf.putInt(15)
                Unit
            }
            is CreateTxException.Psbt -> {
                buf.putInt(16)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is CreateTxException.MissingKeyOrigin -> {
                buf.putInt(17)
                FfiConverterString.write(value.`key`, buf)
                Unit
            }
            is CreateTxException.UnknownUtxo -> {
                buf.putInt(18)
                FfiConverterString.write(value.`outpoint`, buf)
                Unit
            }
            is CreateTxException.MissingNonWitnessUtxo -> {
                buf.putInt(19)
                FfiConverterString.write(value.`outpoint`, buf)
                Unit
            }
            is CreateTxException.MiniscriptPsbt -> {
                buf.putInt(20)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is CreateTxException.PushBytesException -> {
                buf.putInt(21)
                Unit
            }
            is CreateTxException.LockTimeConversionException -> {
                buf.putInt(22)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object CreateWithPersistExceptionErrorHandler : UniffiRustCallStatusErrorHandler<CreateWithPersistException> {
    override fun lift(errorBuf: RustBufferByValue): CreateWithPersistException = FfiConverterTypeCreateWithPersistError.lift(errorBuf)
}

public object FfiConverterTypeCreateWithPersistError : FfiConverterRustBuffer<CreateWithPersistException> {
    override fun read(buf: ByteBuffer): CreateWithPersistException {
        return when (buf.getInt()) {
            1 -> CreateWithPersistException.Persist(
                FfiConverterString.read(buf),
                )
            2 -> CreateWithPersistException.DataAlreadyExists()
            3 -> CreateWithPersistException.Descriptor(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: CreateWithPersistException): ULong {
        return when (value) {
            is CreateWithPersistException.Persist -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is CreateWithPersistException.DataAlreadyExists -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is CreateWithPersistException.Descriptor -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
        }
    }

    override fun write(value: CreateWithPersistException, buf: ByteBuffer) {
        when (value) {
            is CreateWithPersistException.Persist -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is CreateWithPersistException.DataAlreadyExists -> {
                buf.putInt(2)
                Unit
            }
            is CreateWithPersistException.Descriptor -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object DescriptorExceptionErrorHandler : UniffiRustCallStatusErrorHandler<DescriptorException> {
    override fun lift(errorBuf: RustBufferByValue): DescriptorException = FfiConverterTypeDescriptorError.lift(errorBuf)
}

public object FfiConverterTypeDescriptorError : FfiConverterRustBuffer<DescriptorException> {
    override fun read(buf: ByteBuffer): DescriptorException {
        return when (buf.getInt()) {
            1 -> DescriptorException.InvalidHdKeyPath()
            2 -> DescriptorException.InvalidDescriptorChecksum()
            3 -> DescriptorException.HardenedDerivationXpub()
            4 -> DescriptorException.MultiPath()
            5 -> DescriptorException.Key(
                FfiConverterString.read(buf),
                )
            6 -> DescriptorException.Policy(
                FfiConverterString.read(buf),
                )
            7 -> DescriptorException.InvalidDescriptorCharacter(
                FfiConverterString.read(buf),
                )
            8 -> DescriptorException.Bip32(
                FfiConverterString.read(buf),
                )
            9 -> DescriptorException.Base58(
                FfiConverterString.read(buf),
                )
            10 -> DescriptorException.Pk(
                FfiConverterString.read(buf),
                )
            11 -> DescriptorException.Miniscript(
                FfiConverterString.read(buf),
                )
            12 -> DescriptorException.Hex(
                FfiConverterString.read(buf),
                )
            13 -> DescriptorException.ExternalAndInternalAreTheSame()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: DescriptorException): ULong {
        return when (value) {
            is DescriptorException.InvalidHdKeyPath -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is DescriptorException.InvalidDescriptorChecksum -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is DescriptorException.HardenedDerivationXpub -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is DescriptorException.MultiPath -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is DescriptorException.Key -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.Policy -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.InvalidDescriptorCharacter -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`char_`)
            )
            is DescriptorException.Bip32 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.Base58 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.Pk -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.Miniscript -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.Hex -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorException.ExternalAndInternalAreTheSame -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: DescriptorException, buf: ByteBuffer) {
        when (value) {
            is DescriptorException.InvalidHdKeyPath -> {
                buf.putInt(1)
                Unit
            }
            is DescriptorException.InvalidDescriptorChecksum -> {
                buf.putInt(2)
                Unit
            }
            is DescriptorException.HardenedDerivationXpub -> {
                buf.putInt(3)
                Unit
            }
            is DescriptorException.MultiPath -> {
                buf.putInt(4)
                Unit
            }
            is DescriptorException.Key -> {
                buf.putInt(5)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.Policy -> {
                buf.putInt(6)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.InvalidDescriptorCharacter -> {
                buf.putInt(7)
                FfiConverterString.write(value.`char_`, buf)
                Unit
            }
            is DescriptorException.Bip32 -> {
                buf.putInt(8)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.Base58 -> {
                buf.putInt(9)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.Pk -> {
                buf.putInt(10)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.Miniscript -> {
                buf.putInt(11)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.Hex -> {
                buf.putInt(12)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorException.ExternalAndInternalAreTheSame -> {
                buf.putInt(13)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object DescriptorKeyExceptionErrorHandler : UniffiRustCallStatusErrorHandler<DescriptorKeyException> {
    override fun lift(errorBuf: RustBufferByValue): DescriptorKeyException = FfiConverterTypeDescriptorKeyError.lift(errorBuf)
}

public object FfiConverterTypeDescriptorKeyError : FfiConverterRustBuffer<DescriptorKeyException> {
    override fun read(buf: ByteBuffer): DescriptorKeyException {
        return when (buf.getInt()) {
            1 -> DescriptorKeyException.Parse(
                FfiConverterString.read(buf),
                )
            2 -> DescriptorKeyException.InvalidKeyType()
            3 -> DescriptorKeyException.Bip32(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: DescriptorKeyException): ULong {
        return when (value) {
            is DescriptorKeyException.Parse -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is DescriptorKeyException.InvalidKeyType -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is DescriptorKeyException.Bip32 -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
        }
    }

    override fun write(value: DescriptorKeyException, buf: ByteBuffer) {
        when (value) {
            is DescriptorKeyException.Parse -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is DescriptorKeyException.InvalidKeyType -> {
                buf.putInt(2)
                Unit
            }
            is DescriptorKeyException.Bip32 -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeDescriptorType: FfiConverterRustBuffer<DescriptorType> {
    override fun read(buf: ByteBuffer): DescriptorType = try {
        DescriptorType.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: DescriptorType): ULong = 4UL

    override fun write(value: DescriptorType, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}




public object ElectrumExceptionErrorHandler : UniffiRustCallStatusErrorHandler<ElectrumException> {
    override fun lift(errorBuf: RustBufferByValue): ElectrumException = FfiConverterTypeElectrumError.lift(errorBuf)
}

public object FfiConverterTypeElectrumError : FfiConverterRustBuffer<ElectrumException> {
    override fun read(buf: ByteBuffer): ElectrumException {
        return when (buf.getInt()) {
            1 -> ElectrumException.IoException(
                FfiConverterString.read(buf),
                )
            2 -> ElectrumException.Json(
                FfiConverterString.read(buf),
                )
            3 -> ElectrumException.Hex(
                FfiConverterString.read(buf),
                )
            4 -> ElectrumException.Protocol(
                FfiConverterString.read(buf),
                )
            5 -> ElectrumException.Bitcoin(
                FfiConverterString.read(buf),
                )
            6 -> ElectrumException.AlreadySubscribed()
            7 -> ElectrumException.NotSubscribed()
            8 -> ElectrumException.InvalidResponse(
                FfiConverterString.read(buf),
                )
            9 -> ElectrumException.Message(
                FfiConverterString.read(buf),
                )
            10 -> ElectrumException.InvalidDnsNameException(
                FfiConverterString.read(buf),
                )
            11 -> ElectrumException.MissingDomain()
            12 -> ElectrumException.AllAttemptsErrored()
            13 -> ElectrumException.SharedIoException(
                FfiConverterString.read(buf),
                )
            14 -> ElectrumException.CouldntLockReader()
            15 -> ElectrumException.Mpsc()
            16 -> ElectrumException.CouldNotCreateConnection(
                FfiConverterString.read(buf),
                )
            17 -> ElectrumException.RequestAlreadyConsumed()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: ElectrumException): ULong {
        return when (value) {
            is ElectrumException.IoException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.Json -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.Hex -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.Protocol -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.Bitcoin -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.AlreadySubscribed -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ElectrumException.NotSubscribed -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ElectrumException.InvalidResponse -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.Message -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.InvalidDnsNameException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`domain`)
            )
            is ElectrumException.MissingDomain -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ElectrumException.AllAttemptsErrored -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ElectrumException.SharedIoException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.CouldntLockReader -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ElectrumException.Mpsc -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ElectrumException.CouldNotCreateConnection -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ElectrumException.RequestAlreadyConsumed -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: ElectrumException, buf: ByteBuffer) {
        when (value) {
            is ElectrumException.IoException -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.Json -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.Hex -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.Protocol -> {
                buf.putInt(4)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.Bitcoin -> {
                buf.putInt(5)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.AlreadySubscribed -> {
                buf.putInt(6)
                Unit
            }
            is ElectrumException.NotSubscribed -> {
                buf.putInt(7)
                Unit
            }
            is ElectrumException.InvalidResponse -> {
                buf.putInt(8)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.Message -> {
                buf.putInt(9)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.InvalidDnsNameException -> {
                buf.putInt(10)
                FfiConverterString.write(value.`domain`, buf)
                Unit
            }
            is ElectrumException.MissingDomain -> {
                buf.putInt(11)
                Unit
            }
            is ElectrumException.AllAttemptsErrored -> {
                buf.putInt(12)
                Unit
            }
            is ElectrumException.SharedIoException -> {
                buf.putInt(13)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.CouldntLockReader -> {
                buf.putInt(14)
                Unit
            }
            is ElectrumException.Mpsc -> {
                buf.putInt(15)
                Unit
            }
            is ElectrumException.CouldNotCreateConnection -> {
                buf.putInt(16)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ElectrumException.RequestAlreadyConsumed -> {
                buf.putInt(17)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object EsploraExceptionErrorHandler : UniffiRustCallStatusErrorHandler<EsploraException> {
    override fun lift(errorBuf: RustBufferByValue): EsploraException = FfiConverterTypeEsploraError.lift(errorBuf)
}

public object FfiConverterTypeEsploraError : FfiConverterRustBuffer<EsploraException> {
    override fun read(buf: ByteBuffer): EsploraException {
        return when (buf.getInt()) {
            1 -> EsploraException.Minreq(
                FfiConverterString.read(buf),
                )
            2 -> EsploraException.HttpResponse(
                FfiConverterUShort.read(buf),
                FfiConverterString.read(buf),
                )
            3 -> EsploraException.Parsing(
                FfiConverterString.read(buf),
                )
            4 -> EsploraException.StatusCode(
                FfiConverterString.read(buf),
                )
            5 -> EsploraException.BitcoinEncoding(
                FfiConverterString.read(buf),
                )
            6 -> EsploraException.HexToArray(
                FfiConverterString.read(buf),
                )
            7 -> EsploraException.HexToBytes(
                FfiConverterString.read(buf),
                )
            8 -> EsploraException.TransactionNotFound()
            9 -> EsploraException.HeaderHeightNotFound(
                FfiConverterUInt.read(buf),
                )
            10 -> EsploraException.HeaderHashNotFound()
            11 -> EsploraException.InvalidHttpHeaderName(
                FfiConverterString.read(buf),
                )
            12 -> EsploraException.InvalidHttpHeaderValue(
                FfiConverterString.read(buf),
                )
            13 -> EsploraException.RequestAlreadyConsumed()
            14 -> EsploraException.InvalidResponse()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: EsploraException): ULong {
        return when (value) {
            is EsploraException.Minreq -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.HttpResponse -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUShort.allocationSize(value.`status`)
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.Parsing -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.StatusCode -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.BitcoinEncoding -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.HexToArray -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.HexToBytes -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is EsploraException.TransactionNotFound -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is EsploraException.HeaderHeightNotFound -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`height`)
            )
            is EsploraException.HeaderHashNotFound -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is EsploraException.InvalidHttpHeaderName -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`name`)
            )
            is EsploraException.InvalidHttpHeaderValue -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`value`)
            )
            is EsploraException.RequestAlreadyConsumed -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is EsploraException.InvalidResponse -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: EsploraException, buf: ByteBuffer) {
        when (value) {
            is EsploraException.Minreq -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.HttpResponse -> {
                buf.putInt(2)
                FfiConverterUShort.write(value.`status`, buf)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.Parsing -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.StatusCode -> {
                buf.putInt(4)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.BitcoinEncoding -> {
                buf.putInt(5)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.HexToArray -> {
                buf.putInt(6)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.HexToBytes -> {
                buf.putInt(7)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is EsploraException.TransactionNotFound -> {
                buf.putInt(8)
                Unit
            }
            is EsploraException.HeaderHeightNotFound -> {
                buf.putInt(9)
                FfiConverterUInt.write(value.`height`, buf)
                Unit
            }
            is EsploraException.HeaderHashNotFound -> {
                buf.putInt(10)
                Unit
            }
            is EsploraException.InvalidHttpHeaderName -> {
                buf.putInt(11)
                FfiConverterString.write(value.`name`, buf)
                Unit
            }
            is EsploraException.InvalidHttpHeaderValue -> {
                buf.putInt(12)
                FfiConverterString.write(value.`value`, buf)
                Unit
            }
            is EsploraException.RequestAlreadyConsumed -> {
                buf.putInt(13)
                Unit
            }
            is EsploraException.InvalidResponse -> {
                buf.putInt(14)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object ExtractTxExceptionErrorHandler : UniffiRustCallStatusErrorHandler<ExtractTxException> {
    override fun lift(errorBuf: RustBufferByValue): ExtractTxException = FfiConverterTypeExtractTxError.lift(errorBuf)
}

public object FfiConverterTypeExtractTxError : FfiConverterRustBuffer<ExtractTxException> {
    override fun read(buf: ByteBuffer): ExtractTxException {
        return when (buf.getInt()) {
            1 -> ExtractTxException.AbsurdFeeRate(
                FfiConverterULong.read(buf),
                )
            2 -> ExtractTxException.MissingInputValue()
            3 -> ExtractTxException.SendingTooMuch()
            4 -> ExtractTxException.OtherExtractTxErr()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: ExtractTxException): ULong {
        return when (value) {
            is ExtractTxException.AbsurdFeeRate -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`feeRate`)
            )
            is ExtractTxException.MissingInputValue -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ExtractTxException.SendingTooMuch -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ExtractTxException.OtherExtractTxErr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: ExtractTxException, buf: ByteBuffer) {
        when (value) {
            is ExtractTxException.AbsurdFeeRate -> {
                buf.putInt(1)
                FfiConverterULong.write(value.`feeRate`, buf)
                Unit
            }
            is ExtractTxException.MissingInputValue -> {
                buf.putInt(2)
                Unit
            }
            is ExtractTxException.SendingTooMuch -> {
                buf.putInt(3)
                Unit
            }
            is ExtractTxException.OtherExtractTxErr -> {
                buf.putInt(4)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object FeeRateExceptionErrorHandler : UniffiRustCallStatusErrorHandler<FeeRateException> {
    override fun lift(errorBuf: RustBufferByValue): FeeRateException = FfiConverterTypeFeeRateError.lift(errorBuf)
}

public object FfiConverterTypeFeeRateError : FfiConverterRustBuffer<FeeRateException> {
    override fun read(buf: ByteBuffer): FeeRateException {
        return when (buf.getInt()) {
            1 -> FeeRateException.ArithmeticOverflow()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: FeeRateException): ULong {
        return when (value) {
            is FeeRateException.ArithmeticOverflow -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: FeeRateException, buf: ByteBuffer) {
        when (value) {
            is FeeRateException.ArithmeticOverflow -> {
                buf.putInt(1)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object FromScriptExceptionErrorHandler : UniffiRustCallStatusErrorHandler<FromScriptException> {
    override fun lift(errorBuf: RustBufferByValue): FromScriptException = FfiConverterTypeFromScriptError.lift(errorBuf)
}

public object FfiConverterTypeFromScriptError : FfiConverterRustBuffer<FromScriptException> {
    override fun read(buf: ByteBuffer): FromScriptException {
        return when (buf.getInt()) {
            1 -> FromScriptException.UnrecognizedScript()
            2 -> FromScriptException.WitnessProgram(
                FfiConverterString.read(buf),
                )
            3 -> FromScriptException.WitnessVersion(
                FfiConverterString.read(buf),
                )
            4 -> FromScriptException.OtherFromScriptErr()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: FromScriptException): ULong {
        return when (value) {
            is FromScriptException.UnrecognizedScript -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is FromScriptException.WitnessProgram -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is FromScriptException.WitnessVersion -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is FromScriptException.OtherFromScriptErr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: FromScriptException, buf: ByteBuffer) {
        when (value) {
            is FromScriptException.UnrecognizedScript -> {
                buf.putInt(1)
                Unit
            }
            is FromScriptException.WitnessProgram -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is FromScriptException.WitnessVersion -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is FromScriptException.OtherFromScriptErr -> {
                buf.putInt(4)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object HashParseExceptionErrorHandler : UniffiRustCallStatusErrorHandler<HashParseException> {
    override fun lift(errorBuf: RustBufferByValue): HashParseException = FfiConverterTypeHashParseError.lift(errorBuf)
}

public object FfiConverterTypeHashParseError : FfiConverterRustBuffer<HashParseException> {
    override fun read(buf: ByteBuffer): HashParseException {
        return when (buf.getInt()) {
            1 -> HashParseException.InvalidHash(
                FfiConverterUInt.read(buf),
                )
            2 -> HashParseException.InvalidHexString(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: HashParseException): ULong {
        return when (value) {
            is HashParseException.InvalidHash -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`len`)
            )
            is HashParseException.InvalidHexString -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`hex`)
            )
        }
    }

    override fun write(value: HashParseException, buf: ByteBuffer) {
        when (value) {
            is HashParseException.InvalidHash -> {
                buf.putInt(1)
                FfiConverterUInt.write(value.`len`, buf)
                Unit
            }
            is HashParseException.InvalidHexString -> {
                buf.putInt(2)
                FfiConverterString.write(value.`hex`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeInfo : FfiConverterRustBuffer<Info>{
    override fun read(buf: ByteBuffer): Info {
        return when(buf.getInt()) {
            1 -> Info.ConnectionsMet
            2 -> Info.SuccessfulHandshake
            3 -> Info.Progress(
                FfiConverterUInt.read(buf),
                FfiConverterFloat.read(buf),
                )
            4 -> Info.BlockReceived(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: Info): ULong = when(value) {
        is Info.ConnectionsMet -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Info.SuccessfulHandshake -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Info.Progress -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterUInt.allocationSize(value.`chainHeight`)
                + FfiConverterFloat.allocationSize(value.`filtersDownloadedPercent`)
            )
        }
        is Info.BlockReceived -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.v1)
            )
        }
    }

    override fun write(value: Info, buf: ByteBuffer) {
        when(value) {
            is Info.ConnectionsMet -> {
                buf.putInt(1)
                Unit
            }
            is Info.SuccessfulHandshake -> {
                buf.putInt(2)
                Unit
            }
            is Info.Progress -> {
                buf.putInt(3)
                FfiConverterUInt.write(value.`chainHeight`, buf)
                FfiConverterFloat.write(value.`filtersDownloadedPercent`, buf)
                Unit
            }
            is Info.BlockReceived -> {
                buf.putInt(4)
                FfiConverterString.write(value.v1, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeKeychainKind: FfiConverterRustBuffer<KeychainKind> {
    override fun read(buf: ByteBuffer): KeychainKind = try {
        KeychainKind.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: KeychainKind): ULong = 4UL

    override fun write(value: KeychainKind, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}




public object LoadWithPersistExceptionErrorHandler : UniffiRustCallStatusErrorHandler<LoadWithPersistException> {
    override fun lift(errorBuf: RustBufferByValue): LoadWithPersistException = FfiConverterTypeLoadWithPersistError.lift(errorBuf)
}

public object FfiConverterTypeLoadWithPersistError : FfiConverterRustBuffer<LoadWithPersistException> {
    override fun read(buf: ByteBuffer): LoadWithPersistException {
        return when (buf.getInt()) {
            1 -> LoadWithPersistException.Persist(
                FfiConverterString.read(buf),
                )
            2 -> LoadWithPersistException.InvalidChangeSet(
                FfiConverterString.read(buf),
                )
            3 -> LoadWithPersistException.CouldNotLoad()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: LoadWithPersistException): ULong {
        return when (value) {
            is LoadWithPersistException.Persist -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is LoadWithPersistException.InvalidChangeSet -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is LoadWithPersistException.CouldNotLoad -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: LoadWithPersistException, buf: ByteBuffer) {
        when (value) {
            is LoadWithPersistException.Persist -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is LoadWithPersistException.InvalidChangeSet -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is LoadWithPersistException.CouldNotLoad -> {
                buf.putInt(3)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeLockTime : FfiConverterRustBuffer<LockTime>{
    override fun read(buf: ByteBuffer): LockTime {
        return when(buf.getInt()) {
            1 -> LockTime.Blocks(
                FfiConverterUInt.read(buf),
                )
            2 -> LockTime.Seconds(
                FfiConverterUInt.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: LockTime): ULong = when(value) {
        is LockTime.Blocks -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterUInt.allocationSize(value.`height`)
            )
        }
        is LockTime.Seconds -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterUInt.allocationSize(value.`consensusTime`)
            )
        }
    }

    override fun write(value: LockTime, buf: ByteBuffer) {
        when(value) {
            is LockTime.Blocks -> {
                buf.putInt(1)
                FfiConverterUInt.write(value.`height`, buf)
                Unit
            }
            is LockTime.Seconds -> {
                buf.putInt(2)
                FfiConverterUInt.write(value.`consensusTime`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object MiniscriptExceptionErrorHandler : UniffiRustCallStatusErrorHandler<MiniscriptException> {
    override fun lift(errorBuf: RustBufferByValue): MiniscriptException = FfiConverterTypeMiniscriptError.lift(errorBuf)
}

public object FfiConverterTypeMiniscriptError : FfiConverterRustBuffer<MiniscriptException> {
    override fun read(buf: ByteBuffer): MiniscriptException {
        return when (buf.getInt()) {
            1 -> MiniscriptException.AbsoluteLockTime()
            2 -> MiniscriptException.AddrException(
                FfiConverterString.read(buf),
                )
            3 -> MiniscriptException.AddrP2shException(
                FfiConverterString.read(buf),
                )
            4 -> MiniscriptException.AnalysisException(
                FfiConverterString.read(buf),
                )
            5 -> MiniscriptException.AtOutsideOr()
            6 -> MiniscriptException.BadDescriptor(
                FfiConverterString.read(buf),
                )
            7 -> MiniscriptException.BareDescriptorAddr()
            8 -> MiniscriptException.CmsTooManyKeys(
                FfiConverterUInt.read(buf),
                )
            9 -> MiniscriptException.ContextException(
                FfiConverterString.read(buf),
                )
            10 -> MiniscriptException.CouldNotSatisfy()
            11 -> MiniscriptException.ExpectedChar(
                FfiConverterString.read(buf),
                )
            12 -> MiniscriptException.ImpossibleSatisfaction()
            13 -> MiniscriptException.InvalidOpcode()
            14 -> MiniscriptException.InvalidPush()
            15 -> MiniscriptException.LiftException(
                FfiConverterString.read(buf),
                )
            16 -> MiniscriptException.MaxRecursiveDepthExceeded()
            17 -> MiniscriptException.MissingSig()
            18 -> MiniscriptException.MultiATooManyKeys(
                FfiConverterULong.read(buf),
                )
            19 -> MiniscriptException.MultiColon()
            20 -> MiniscriptException.MultipathDescLenMismatch()
            21 -> MiniscriptException.NonMinimalVerify(
                FfiConverterString.read(buf),
                )
            22 -> MiniscriptException.NonStandardBareScript()
            23 -> MiniscriptException.NonTopLevel(
                FfiConverterString.read(buf),
                )
            24 -> MiniscriptException.ParseThreshold()
            25 -> MiniscriptException.PolicyException(
                FfiConverterString.read(buf),
                )
            26 -> MiniscriptException.PubKeyCtxException()
            27 -> MiniscriptException.RelativeLockTime()
            28 -> MiniscriptException.Script(
                FfiConverterString.read(buf),
                )
            29 -> MiniscriptException.Secp(
                FfiConverterString.read(buf),
                )
            30 -> MiniscriptException.Threshold()
            31 -> MiniscriptException.TrNoScriptCode()
            32 -> MiniscriptException.Trailing(
                FfiConverterString.read(buf),
                )
            33 -> MiniscriptException.TypeCheck(
                FfiConverterString.read(buf),
                )
            34 -> MiniscriptException.Unexpected(
                FfiConverterString.read(buf),
                )
            35 -> MiniscriptException.UnexpectedStart()
            36 -> MiniscriptException.UnknownWrapper(
                FfiConverterString.read(buf),
                )
            37 -> MiniscriptException.Unprintable(
                FfiConverterUByte.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: MiniscriptException): ULong {
        return when (value) {
            is MiniscriptException.AbsoluteLockTime -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.AddrException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.AddrP2shException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.AnalysisException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.AtOutsideOr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.BadDescriptor -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.BareDescriptorAddr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.CmsTooManyKeys -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`keys`)
            )
            is MiniscriptException.ContextException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.CouldNotSatisfy -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.ExpectedChar -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`char_`)
            )
            is MiniscriptException.ImpossibleSatisfaction -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.InvalidOpcode -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.InvalidPush -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.LiftException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.MaxRecursiveDepthExceeded -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.MissingSig -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.MultiATooManyKeys -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterULong.allocationSize(value.`keys`)
            )
            is MiniscriptException.MultiColon -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.MultipathDescLenMismatch -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.NonMinimalVerify -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.NonStandardBareScript -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.NonTopLevel -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.ParseThreshold -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.PolicyException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.PubKeyCtxException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.RelativeLockTime -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.Script -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.Secp -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.Threshold -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.TrNoScriptCode -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.Trailing -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.TypeCheck -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.Unexpected -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is MiniscriptException.UnexpectedStart -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is MiniscriptException.UnknownWrapper -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`char_`)
            )
            is MiniscriptException.Unprintable -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUByte.allocationSize(value.`byte`)
            )
        }
    }

    override fun write(value: MiniscriptException, buf: ByteBuffer) {
        when (value) {
            is MiniscriptException.AbsoluteLockTime -> {
                buf.putInt(1)
                Unit
            }
            is MiniscriptException.AddrException -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.AddrP2shException -> {
                buf.putInt(3)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.AnalysisException -> {
                buf.putInt(4)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.AtOutsideOr -> {
                buf.putInt(5)
                Unit
            }
            is MiniscriptException.BadDescriptor -> {
                buf.putInt(6)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.BareDescriptorAddr -> {
                buf.putInt(7)
                Unit
            }
            is MiniscriptException.CmsTooManyKeys -> {
                buf.putInt(8)
                FfiConverterUInt.write(value.`keys`, buf)
                Unit
            }
            is MiniscriptException.ContextException -> {
                buf.putInt(9)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.CouldNotSatisfy -> {
                buf.putInt(10)
                Unit
            }
            is MiniscriptException.ExpectedChar -> {
                buf.putInt(11)
                FfiConverterString.write(value.`char_`, buf)
                Unit
            }
            is MiniscriptException.ImpossibleSatisfaction -> {
                buf.putInt(12)
                Unit
            }
            is MiniscriptException.InvalidOpcode -> {
                buf.putInt(13)
                Unit
            }
            is MiniscriptException.InvalidPush -> {
                buf.putInt(14)
                Unit
            }
            is MiniscriptException.LiftException -> {
                buf.putInt(15)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.MaxRecursiveDepthExceeded -> {
                buf.putInt(16)
                Unit
            }
            is MiniscriptException.MissingSig -> {
                buf.putInt(17)
                Unit
            }
            is MiniscriptException.MultiATooManyKeys -> {
                buf.putInt(18)
                FfiConverterULong.write(value.`keys`, buf)
                Unit
            }
            is MiniscriptException.MultiColon -> {
                buf.putInt(19)
                Unit
            }
            is MiniscriptException.MultipathDescLenMismatch -> {
                buf.putInt(20)
                Unit
            }
            is MiniscriptException.NonMinimalVerify -> {
                buf.putInt(21)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.NonStandardBareScript -> {
                buf.putInt(22)
                Unit
            }
            is MiniscriptException.NonTopLevel -> {
                buf.putInt(23)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.ParseThreshold -> {
                buf.putInt(24)
                Unit
            }
            is MiniscriptException.PolicyException -> {
                buf.putInt(25)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.PubKeyCtxException -> {
                buf.putInt(26)
                Unit
            }
            is MiniscriptException.RelativeLockTime -> {
                buf.putInt(27)
                Unit
            }
            is MiniscriptException.Script -> {
                buf.putInt(28)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.Secp -> {
                buf.putInt(29)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.Threshold -> {
                buf.putInt(30)
                Unit
            }
            is MiniscriptException.TrNoScriptCode -> {
                buf.putInt(31)
                Unit
            }
            is MiniscriptException.Trailing -> {
                buf.putInt(32)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.TypeCheck -> {
                buf.putInt(33)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.Unexpected -> {
                buf.putInt(34)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is MiniscriptException.UnexpectedStart -> {
                buf.putInt(35)
                Unit
            }
            is MiniscriptException.UnknownWrapper -> {
                buf.putInt(36)
                FfiConverterString.write(value.`char_`, buf)
                Unit
            }
            is MiniscriptException.Unprintable -> {
                buf.putInt(37)
                FfiConverterUByte.write(value.`byte`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeNetwork: FfiConverterRustBuffer<Network> {
    override fun read(buf: ByteBuffer): Network = try {
        Network.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: Network): ULong = 4UL

    override fun write(value: Network, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}




public object ParseAmountExceptionErrorHandler : UniffiRustCallStatusErrorHandler<ParseAmountException> {
    override fun lift(errorBuf: RustBufferByValue): ParseAmountException = FfiConverterTypeParseAmountError.lift(errorBuf)
}

public object FfiConverterTypeParseAmountError : FfiConverterRustBuffer<ParseAmountException> {
    override fun read(buf: ByteBuffer): ParseAmountException {
        return when (buf.getInt()) {
            1 -> ParseAmountException.OutOfRange()
            2 -> ParseAmountException.TooPrecise()
            3 -> ParseAmountException.MissingDigits()
            4 -> ParseAmountException.InputTooLarge()
            5 -> ParseAmountException.InvalidCharacter(
                FfiConverterString.read(buf),
                )
            6 -> ParseAmountException.OtherParseAmountErr()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: ParseAmountException): ULong {
        return when (value) {
            is ParseAmountException.OutOfRange -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ParseAmountException.TooPrecise -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ParseAmountException.MissingDigits -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ParseAmountException.InputTooLarge -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is ParseAmountException.InvalidCharacter -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is ParseAmountException.OtherParseAmountErr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: ParseAmountException, buf: ByteBuffer) {
        when (value) {
            is ParseAmountException.OutOfRange -> {
                buf.putInt(1)
                Unit
            }
            is ParseAmountException.TooPrecise -> {
                buf.putInt(2)
                Unit
            }
            is ParseAmountException.MissingDigits -> {
                buf.putInt(3)
                Unit
            }
            is ParseAmountException.InputTooLarge -> {
                buf.putInt(4)
                Unit
            }
            is ParseAmountException.InvalidCharacter -> {
                buf.putInt(5)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is ParseAmountException.OtherParseAmountErr -> {
                buf.putInt(6)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object PersistenceExceptionErrorHandler : UniffiRustCallStatusErrorHandler<PersistenceException> {
    override fun lift(errorBuf: RustBufferByValue): PersistenceException = FfiConverterTypePersistenceError.lift(errorBuf)
}

public object FfiConverterTypePersistenceError : FfiConverterRustBuffer<PersistenceException> {
    override fun read(buf: ByteBuffer): PersistenceException {
        return when (buf.getInt()) {
            1 -> PersistenceException.Reason(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: PersistenceException): ULong {
        return when (value) {
            is PersistenceException.Reason -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
        }
    }

    override fun write(value: PersistenceException, buf: ByteBuffer) {
        when (value) {
            is PersistenceException.Reason -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypePkOrF : FfiConverterRustBuffer<PkOrF>{
    override fun read(buf: ByteBuffer): PkOrF {
        return when(buf.getInt()) {
            1 -> PkOrF.Pubkey(
                FfiConverterString.read(buf),
                )
            2 -> PkOrF.XOnlyPubkey(
                FfiConverterString.read(buf),
                )
            3 -> PkOrF.Fingerprint(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: PkOrF): ULong = when(value) {
        is PkOrF.Pubkey -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`value`)
            )
        }
        is PkOrF.XOnlyPubkey -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`value`)
            )
        }
        is PkOrF.Fingerprint -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`value`)
            )
        }
    }

    override fun write(value: PkOrF, buf: ByteBuffer) {
        when(value) {
            is PkOrF.Pubkey -> {
                buf.putInt(1)
                FfiConverterString.write(value.`value`, buf)
                Unit
            }
            is PkOrF.XOnlyPubkey -> {
                buf.putInt(2)
                FfiConverterString.write(value.`value`, buf)
                Unit
            }
            is PkOrF.Fingerprint -> {
                buf.putInt(3)
                FfiConverterString.write(value.`value`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object PsbtExceptionErrorHandler : UniffiRustCallStatusErrorHandler<PsbtException> {
    override fun lift(errorBuf: RustBufferByValue): PsbtException = FfiConverterTypePsbtError.lift(errorBuf)
}

public object FfiConverterTypePsbtError : FfiConverterRustBuffer<PsbtException> {
    override fun read(buf: ByteBuffer): PsbtException {
        return when (buf.getInt()) {
            1 -> PsbtException.InvalidMagic()
            2 -> PsbtException.MissingUtxo()
            3 -> PsbtException.InvalidSeparator()
            4 -> PsbtException.PsbtUtxoOutOfBounds()
            5 -> PsbtException.InvalidKey(
                FfiConverterString.read(buf),
                )
            6 -> PsbtException.InvalidProprietaryKey()
            7 -> PsbtException.DuplicateKey(
                FfiConverterString.read(buf),
                )
            8 -> PsbtException.UnsignedTxHasScriptSigs()
            9 -> PsbtException.UnsignedTxHasScriptWitnesses()
            10 -> PsbtException.MustHaveUnsignedTx()
            11 -> PsbtException.NoMorePairs()
            12 -> PsbtException.UnexpectedUnsignedTx()
            13 -> PsbtException.NonStandardSighashType(
                FfiConverterUInt.read(buf),
                )
            14 -> PsbtException.InvalidHash(
                FfiConverterString.read(buf),
                )
            15 -> PsbtException.InvalidPreimageHashPair()
            16 -> PsbtException.CombineInconsistentKeySources(
                FfiConverterString.read(buf),
                )
            17 -> PsbtException.ConsensusEncoding(
                FfiConverterString.read(buf),
                )
            18 -> PsbtException.NegativeFee()
            19 -> PsbtException.FeeOverflow()
            20 -> PsbtException.InvalidPublicKey(
                FfiConverterString.read(buf),
                )
            21 -> PsbtException.InvalidSecp256k1PublicKey(
                FfiConverterString.read(buf),
                )
            22 -> PsbtException.InvalidXOnlyPublicKey()
            23 -> PsbtException.InvalidEcdsaSignature(
                FfiConverterString.read(buf),
                )
            24 -> PsbtException.InvalidTaprootSignature(
                FfiConverterString.read(buf),
                )
            25 -> PsbtException.InvalidControlBlock()
            26 -> PsbtException.InvalidLeafVersion()
            27 -> PsbtException.Taproot()
            28 -> PsbtException.TapTree(
                FfiConverterString.read(buf),
                )
            29 -> PsbtException.XPubKey()
            30 -> PsbtException.Version(
                FfiConverterString.read(buf),
                )
            31 -> PsbtException.PartialDataConsumption()
            32 -> PsbtException.Io(
                FfiConverterString.read(buf),
                )
            33 -> PsbtException.OtherPsbtErr()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: PsbtException): ULong {
        return when (value) {
            is PsbtException.InvalidMagic -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.MissingUtxo -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.InvalidSeparator -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.PsbtUtxoOutOfBounds -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.InvalidKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`key`)
            )
            is PsbtException.InvalidProprietaryKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.DuplicateKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`key`)
            )
            is PsbtException.UnsignedTxHasScriptSigs -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.UnsignedTxHasScriptWitnesses -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.MustHaveUnsignedTx -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.NoMorePairs -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.UnexpectedUnsignedTx -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.NonStandardSighashType -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`sighash`)
            )
            is PsbtException.InvalidHash -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`hash`)
            )
            is PsbtException.InvalidPreimageHashPair -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.CombineInconsistentKeySources -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`xpub`)
            )
            is PsbtException.ConsensusEncoding -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`encodingError`)
            )
            is PsbtException.NegativeFee -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.FeeOverflow -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.InvalidPublicKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtException.InvalidSecp256k1PublicKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`secp256k1Error`)
            )
            is PsbtException.InvalidXOnlyPublicKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.InvalidEcdsaSignature -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtException.InvalidTaprootSignature -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtException.InvalidControlBlock -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.InvalidLeafVersion -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.Taproot -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.TapTree -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtException.XPubKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.Version -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtException.PartialDataConsumption -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is PsbtException.Io -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtException.OtherPsbtErr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: PsbtException, buf: ByteBuffer) {
        when (value) {
            is PsbtException.InvalidMagic -> {
                buf.putInt(1)
                Unit
            }
            is PsbtException.MissingUtxo -> {
                buf.putInt(2)
                Unit
            }
            is PsbtException.InvalidSeparator -> {
                buf.putInt(3)
                Unit
            }
            is PsbtException.PsbtUtxoOutOfBounds -> {
                buf.putInt(4)
                Unit
            }
            is PsbtException.InvalidKey -> {
                buf.putInt(5)
                FfiConverterString.write(value.`key`, buf)
                Unit
            }
            is PsbtException.InvalidProprietaryKey -> {
                buf.putInt(6)
                Unit
            }
            is PsbtException.DuplicateKey -> {
                buf.putInt(7)
                FfiConverterString.write(value.`key`, buf)
                Unit
            }
            is PsbtException.UnsignedTxHasScriptSigs -> {
                buf.putInt(8)
                Unit
            }
            is PsbtException.UnsignedTxHasScriptWitnesses -> {
                buf.putInt(9)
                Unit
            }
            is PsbtException.MustHaveUnsignedTx -> {
                buf.putInt(10)
                Unit
            }
            is PsbtException.NoMorePairs -> {
                buf.putInt(11)
                Unit
            }
            is PsbtException.UnexpectedUnsignedTx -> {
                buf.putInt(12)
                Unit
            }
            is PsbtException.NonStandardSighashType -> {
                buf.putInt(13)
                FfiConverterUInt.write(value.`sighash`, buf)
                Unit
            }
            is PsbtException.InvalidHash -> {
                buf.putInt(14)
                FfiConverterString.write(value.`hash`, buf)
                Unit
            }
            is PsbtException.InvalidPreimageHashPair -> {
                buf.putInt(15)
                Unit
            }
            is PsbtException.CombineInconsistentKeySources -> {
                buf.putInt(16)
                FfiConverterString.write(value.`xpub`, buf)
                Unit
            }
            is PsbtException.ConsensusEncoding -> {
                buf.putInt(17)
                FfiConverterString.write(value.`encodingError`, buf)
                Unit
            }
            is PsbtException.NegativeFee -> {
                buf.putInt(18)
                Unit
            }
            is PsbtException.FeeOverflow -> {
                buf.putInt(19)
                Unit
            }
            is PsbtException.InvalidPublicKey -> {
                buf.putInt(20)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtException.InvalidSecp256k1PublicKey -> {
                buf.putInt(21)
                FfiConverterString.write(value.`secp256k1Error`, buf)
                Unit
            }
            is PsbtException.InvalidXOnlyPublicKey -> {
                buf.putInt(22)
                Unit
            }
            is PsbtException.InvalidEcdsaSignature -> {
                buf.putInt(23)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtException.InvalidTaprootSignature -> {
                buf.putInt(24)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtException.InvalidControlBlock -> {
                buf.putInt(25)
                Unit
            }
            is PsbtException.InvalidLeafVersion -> {
                buf.putInt(26)
                Unit
            }
            is PsbtException.Taproot -> {
                buf.putInt(27)
                Unit
            }
            is PsbtException.TapTree -> {
                buf.putInt(28)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtException.XPubKey -> {
                buf.putInt(29)
                Unit
            }
            is PsbtException.Version -> {
                buf.putInt(30)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtException.PartialDataConsumption -> {
                buf.putInt(31)
                Unit
            }
            is PsbtException.Io -> {
                buf.putInt(32)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtException.OtherPsbtErr -> {
                buf.putInt(33)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object PsbtFinalizeExceptionErrorHandler : UniffiRustCallStatusErrorHandler<PsbtFinalizeException> {
    override fun lift(errorBuf: RustBufferByValue): PsbtFinalizeException = FfiConverterTypePsbtFinalizeError.lift(errorBuf)
}

public object FfiConverterTypePsbtFinalizeError : FfiConverterRustBuffer<PsbtFinalizeException> {
    override fun read(buf: ByteBuffer): PsbtFinalizeException {
        return when (buf.getInt()) {
            1 -> PsbtFinalizeException.InputException(
                FfiConverterString.read(buf),
                FfiConverterUInt.read(buf),
                )
            2 -> PsbtFinalizeException.WrongInputCount(
                FfiConverterUInt.read(buf),
                FfiConverterUInt.read(buf),
                )
            3 -> PsbtFinalizeException.InputIdxOutofBounds(
                FfiConverterUInt.read(buf),
                FfiConverterUInt.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: PsbtFinalizeException): ULong {
        return when (value) {
            is PsbtFinalizeException.InputException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`reason`)
                + FfiConverterUInt.allocationSize(value.`index`)
            )
            is PsbtFinalizeException.WrongInputCount -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`inTx`)
                + FfiConverterUInt.allocationSize(value.`inMap`)
            )
            is PsbtFinalizeException.InputIdxOutofBounds -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUInt.allocationSize(value.`psbtInp`)
                + FfiConverterUInt.allocationSize(value.`requested`)
            )
        }
    }

    override fun write(value: PsbtFinalizeException, buf: ByteBuffer) {
        when (value) {
            is PsbtFinalizeException.InputException -> {
                buf.putInt(1)
                FfiConverterString.write(value.`reason`, buf)
                FfiConverterUInt.write(value.`index`, buf)
                Unit
            }
            is PsbtFinalizeException.WrongInputCount -> {
                buf.putInt(2)
                FfiConverterUInt.write(value.`inTx`, buf)
                FfiConverterUInt.write(value.`inMap`, buf)
                Unit
            }
            is PsbtFinalizeException.InputIdxOutofBounds -> {
                buf.putInt(3)
                FfiConverterUInt.write(value.`psbtInp`, buf)
                FfiConverterUInt.write(value.`requested`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object PsbtParseExceptionErrorHandler : UniffiRustCallStatusErrorHandler<PsbtParseException> {
    override fun lift(errorBuf: RustBufferByValue): PsbtParseException = FfiConverterTypePsbtParseError.lift(errorBuf)
}

public object FfiConverterTypePsbtParseError : FfiConverterRustBuffer<PsbtParseException> {
    override fun read(buf: ByteBuffer): PsbtParseException {
        return when (buf.getInt()) {
            1 -> PsbtParseException.PsbtEncoding(
                FfiConverterString.read(buf),
                )
            2 -> PsbtParseException.Base64Encoding(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: PsbtParseException): ULong {
        return when (value) {
            is PsbtParseException.PsbtEncoding -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is PsbtParseException.Base64Encoding -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
        }
    }

    override fun write(value: PsbtParseException, buf: ByteBuffer) {
        when (value) {
            is PsbtParseException.PsbtEncoding -> {
                buf.putInt(1)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is PsbtParseException.Base64Encoding -> {
                buf.putInt(2)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeRecoveryPoint: FfiConverterRustBuffer<RecoveryPoint> {
    override fun read(buf: ByteBuffer): RecoveryPoint = try {
        RecoveryPoint.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: RecoveryPoint): ULong = 4UL

    override fun write(value: RecoveryPoint, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}




public object RequestBuilderExceptionErrorHandler : UniffiRustCallStatusErrorHandler<RequestBuilderException> {
    override fun lift(errorBuf: RustBufferByValue): RequestBuilderException = FfiConverterTypeRequestBuilderError.lift(errorBuf)
}

public object FfiConverterTypeRequestBuilderError : FfiConverterRustBuffer<RequestBuilderException> {
    override fun read(buf: ByteBuffer): RequestBuilderException {
        return when (buf.getInt()) {
            1 -> RequestBuilderException.RequestAlreadyConsumed()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: RequestBuilderException): ULong {
        return when (value) {
            is RequestBuilderException.RequestAlreadyConsumed -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: RequestBuilderException, buf: ByteBuffer) {
        when (value) {
            is RequestBuilderException.RequestAlreadyConsumed -> {
                buf.putInt(1)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeSatisfaction : FfiConverterRustBuffer<Satisfaction>{
    override fun read(buf: ByteBuffer): Satisfaction {
        return when(buf.getInt()) {
            1 -> Satisfaction.Partial(
                FfiConverterULong.read(buf),
                FfiConverterULong.read(buf),
                FfiConverterSequenceULong.read(buf),
                FfiConverterOptionalBoolean.read(buf),
                FfiConverterMapUIntSequenceTypeCondition.read(buf),
                )
            2 -> Satisfaction.PartialComplete(
                FfiConverterULong.read(buf),
                FfiConverterULong.read(buf),
                FfiConverterSequenceULong.read(buf),
                FfiConverterOptionalBoolean.read(buf),
                FfiConverterMapSequenceUIntSequenceTypeCondition.read(buf),
                )
            3 -> Satisfaction.Complete(
                FfiConverterTypeCondition.read(buf),
                )
            4 -> Satisfaction.None(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: Satisfaction): ULong = when(value) {
        is Satisfaction.Partial -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterULong.allocationSize(value.`n`)
                + FfiConverterULong.allocationSize(value.`m`)
                + FfiConverterSequenceULong.allocationSize(value.`items`)
                + FfiConverterOptionalBoolean.allocationSize(value.`sorted`)
                + FfiConverterMapUIntSequenceTypeCondition.allocationSize(value.`conditions`)
            )
        }
        is Satisfaction.PartialComplete -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterULong.allocationSize(value.`n`)
                + FfiConverterULong.allocationSize(value.`m`)
                + FfiConverterSequenceULong.allocationSize(value.`items`)
                + FfiConverterOptionalBoolean.allocationSize(value.`sorted`)
                + FfiConverterMapSequenceUIntSequenceTypeCondition.allocationSize(value.`conditions`)
            )
        }
        is Satisfaction.Complete -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterTypeCondition.allocationSize(value.`condition`)
            )
        }
        is Satisfaction.None -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`msg`)
            )
        }
    }

    override fun write(value: Satisfaction, buf: ByteBuffer) {
        when(value) {
            is Satisfaction.Partial -> {
                buf.putInt(1)
                FfiConverterULong.write(value.`n`, buf)
                FfiConverterULong.write(value.`m`, buf)
                FfiConverterSequenceULong.write(value.`items`, buf)
                FfiConverterOptionalBoolean.write(value.`sorted`, buf)
                FfiConverterMapUIntSequenceTypeCondition.write(value.`conditions`, buf)
                Unit
            }
            is Satisfaction.PartialComplete -> {
                buf.putInt(2)
                FfiConverterULong.write(value.`n`, buf)
                FfiConverterULong.write(value.`m`, buf)
                FfiConverterSequenceULong.write(value.`items`, buf)
                FfiConverterOptionalBoolean.write(value.`sorted`, buf)
                FfiConverterMapSequenceUIntSequenceTypeCondition.write(value.`conditions`, buf)
                Unit
            }
            is Satisfaction.Complete -> {
                buf.putInt(3)
                FfiConverterTypeCondition.write(value.`condition`, buf)
                Unit
            }
            is Satisfaction.None -> {
                buf.putInt(4)
                FfiConverterString.write(value.`msg`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeSatisfiableItem : FfiConverterRustBuffer<SatisfiableItem>{
    override fun read(buf: ByteBuffer): SatisfiableItem {
        return when(buf.getInt()) {
            1 -> SatisfiableItem.EcdsaSignature(
                FfiConverterTypePkOrF.read(buf),
                )
            2 -> SatisfiableItem.SchnorrSignature(
                FfiConverterTypePkOrF.read(buf),
                )
            3 -> SatisfiableItem.Sha256Preimage(
                FfiConverterString.read(buf),
                )
            4 -> SatisfiableItem.Hash256Preimage(
                FfiConverterString.read(buf),
                )
            5 -> SatisfiableItem.Ripemd160Preimage(
                FfiConverterString.read(buf),
                )
            6 -> SatisfiableItem.Hash160Preimage(
                FfiConverterString.read(buf),
                )
            7 -> SatisfiableItem.AbsoluteTimelock(
                FfiConverterTypeLockTime.read(buf),
                )
            8 -> SatisfiableItem.RelativeTimelock(
                FfiConverterUInt.read(buf),
                )
            9 -> SatisfiableItem.Multisig(
                FfiConverterSequenceTypePkOrF.read(buf),
                FfiConverterULong.read(buf),
                )
            10 -> SatisfiableItem.Thresh(
                FfiConverterSequenceTypePolicy.read(buf),
                FfiConverterULong.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: SatisfiableItem): ULong = when(value) {
        is SatisfiableItem.EcdsaSignature -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterTypePkOrF.allocationSize(value.`key`)
            )
        }
        is SatisfiableItem.SchnorrSignature -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterTypePkOrF.allocationSize(value.`key`)
            )
        }
        is SatisfiableItem.Sha256Preimage -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`hash`)
            )
        }
        is SatisfiableItem.Hash256Preimage -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`hash`)
            )
        }
        is SatisfiableItem.Ripemd160Preimage -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`hash`)
            )
        }
        is SatisfiableItem.Hash160Preimage -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`hash`)
            )
        }
        is SatisfiableItem.AbsoluteTimelock -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterTypeLockTime.allocationSize(value.`value`)
            )
        }
        is SatisfiableItem.RelativeTimelock -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterUInt.allocationSize(value.`value`)
            )
        }
        is SatisfiableItem.Multisig -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterSequenceTypePkOrF.allocationSize(value.`keys`)
                + FfiConverterULong.allocationSize(value.`threshold`)
            )
        }
        is SatisfiableItem.Thresh -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterSequenceTypePolicy.allocationSize(value.`items`)
                + FfiConverterULong.allocationSize(value.`threshold`)
            )
        }
    }

    override fun write(value: SatisfiableItem, buf: ByteBuffer) {
        when(value) {
            is SatisfiableItem.EcdsaSignature -> {
                buf.putInt(1)
                FfiConverterTypePkOrF.write(value.`key`, buf)
                Unit
            }
            is SatisfiableItem.SchnorrSignature -> {
                buf.putInt(2)
                FfiConverterTypePkOrF.write(value.`key`, buf)
                Unit
            }
            is SatisfiableItem.Sha256Preimage -> {
                buf.putInt(3)
                FfiConverterString.write(value.`hash`, buf)
                Unit
            }
            is SatisfiableItem.Hash256Preimage -> {
                buf.putInt(4)
                FfiConverterString.write(value.`hash`, buf)
                Unit
            }
            is SatisfiableItem.Ripemd160Preimage -> {
                buf.putInt(5)
                FfiConverterString.write(value.`hash`, buf)
                Unit
            }
            is SatisfiableItem.Hash160Preimage -> {
                buf.putInt(6)
                FfiConverterString.write(value.`hash`, buf)
                Unit
            }
            is SatisfiableItem.AbsoluteTimelock -> {
                buf.putInt(7)
                FfiConverterTypeLockTime.write(value.`value`, buf)
                Unit
            }
            is SatisfiableItem.RelativeTimelock -> {
                buf.putInt(8)
                FfiConverterUInt.write(value.`value`, buf)
                Unit
            }
            is SatisfiableItem.Multisig -> {
                buf.putInt(9)
                FfiConverterSequenceTypePkOrF.write(value.`keys`, buf)
                FfiConverterULong.write(value.`threshold`, buf)
                Unit
            }
            is SatisfiableItem.Thresh -> {
                buf.putInt(10)
                FfiConverterSequenceTypePolicy.write(value.`items`, buf)
                FfiConverterULong.write(value.`threshold`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeScanType : FfiConverterRustBuffer<ScanType>{
    override fun read(buf: ByteBuffer): ScanType {
        return when(buf.getInt()) {
            1 -> ScanType.Sync
            2 -> ScanType.Recovery(
                FfiConverterUInt.read(buf),
                FfiConverterTypeRecoveryPoint.read(buf),
                )
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: ScanType): ULong = when(value) {
        is ScanType.Sync -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is ScanType.Recovery -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterUInt.allocationSize(value.`usedScriptIndex`)
                + FfiConverterTypeRecoveryPoint.allocationSize(value.`checkpoint`)
            )
        }
    }

    override fun write(value: ScanType, buf: ByteBuffer) {
        when(value) {
            is ScanType.Sync -> {
                buf.putInt(1)
                Unit
            }
            is ScanType.Recovery -> {
                buf.putInt(2)
                FfiConverterUInt.write(value.`usedScriptIndex`, buf)
                FfiConverterTypeRecoveryPoint.write(value.`checkpoint`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object SignerExceptionErrorHandler : UniffiRustCallStatusErrorHandler<SignerException> {
    override fun lift(errorBuf: RustBufferByValue): SignerException = FfiConverterTypeSignerError.lift(errorBuf)
}

public object FfiConverterTypeSignerError : FfiConverterRustBuffer<SignerException> {
    override fun read(buf: ByteBuffer): SignerException {
        return when (buf.getInt()) {
            1 -> SignerException.MissingKey()
            2 -> SignerException.InvalidKey()
            3 -> SignerException.UserCanceled()
            4 -> SignerException.InputIndexOutOfRange()
            5 -> SignerException.MissingNonWitnessUtxo()
            6 -> SignerException.InvalidNonWitnessUtxo()
            7 -> SignerException.MissingWitnessUtxo()
            8 -> SignerException.MissingWitnessScript()
            9 -> SignerException.MissingHdKeypath()
            10 -> SignerException.NonStandardSighash()
            11 -> SignerException.InvalidSighash()
            12 -> SignerException.SighashP2wpkh(
                FfiConverterString.read(buf),
                )
            13 -> SignerException.SighashTaproot(
                FfiConverterString.read(buf),
                )
            14 -> SignerException.TxInputsIndexException(
                FfiConverterString.read(buf),
                )
            15 -> SignerException.MiniscriptPsbt(
                FfiConverterString.read(buf),
                )
            16 -> SignerException.External(
                FfiConverterString.read(buf),
                )
            17 -> SignerException.Psbt(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: SignerException): ULong {
        return when (value) {
            is SignerException.MissingKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.InvalidKey -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.UserCanceled -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.InputIndexOutOfRange -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.MissingNonWitnessUtxo -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.InvalidNonWitnessUtxo -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.MissingWitnessUtxo -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.MissingWitnessScript -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.MissingHdKeypath -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.NonStandardSighash -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.InvalidSighash -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is SignerException.SighashP2wpkh -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is SignerException.SighashTaproot -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is SignerException.TxInputsIndexException -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is SignerException.MiniscriptPsbt -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is SignerException.External -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
            is SignerException.Psbt -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`errorMessage`)
            )
        }
    }

    override fun write(value: SignerException, buf: ByteBuffer) {
        when (value) {
            is SignerException.MissingKey -> {
                buf.putInt(1)
                Unit
            }
            is SignerException.InvalidKey -> {
                buf.putInt(2)
                Unit
            }
            is SignerException.UserCanceled -> {
                buf.putInt(3)
                Unit
            }
            is SignerException.InputIndexOutOfRange -> {
                buf.putInt(4)
                Unit
            }
            is SignerException.MissingNonWitnessUtxo -> {
                buf.putInt(5)
                Unit
            }
            is SignerException.InvalidNonWitnessUtxo -> {
                buf.putInt(6)
                Unit
            }
            is SignerException.MissingWitnessUtxo -> {
                buf.putInt(7)
                Unit
            }
            is SignerException.MissingWitnessScript -> {
                buf.putInt(8)
                Unit
            }
            is SignerException.MissingHdKeypath -> {
                buf.putInt(9)
                Unit
            }
            is SignerException.NonStandardSighash -> {
                buf.putInt(10)
                Unit
            }
            is SignerException.InvalidSighash -> {
                buf.putInt(11)
                Unit
            }
            is SignerException.SighashP2wpkh -> {
                buf.putInt(12)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is SignerException.SighashTaproot -> {
                buf.putInt(13)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is SignerException.TxInputsIndexException -> {
                buf.putInt(14)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is SignerException.MiniscriptPsbt -> {
                buf.putInt(15)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is SignerException.External -> {
                buf.putInt(16)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
            is SignerException.Psbt -> {
                buf.putInt(17)
                FfiConverterString.write(value.`errorMessage`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object TransactionExceptionErrorHandler : UniffiRustCallStatusErrorHandler<TransactionException> {
    override fun lift(errorBuf: RustBufferByValue): TransactionException = FfiConverterTypeTransactionError.lift(errorBuf)
}

public object FfiConverterTypeTransactionError : FfiConverterRustBuffer<TransactionException> {
    override fun read(buf: ByteBuffer): TransactionException {
        return when (buf.getInt()) {
            1 -> TransactionException.Io()
            2 -> TransactionException.OversizedVectorAllocation()
            3 -> TransactionException.InvalidChecksum(
                FfiConverterString.read(buf),
                FfiConverterString.read(buf),
                )
            4 -> TransactionException.NonMinimalVarInt()
            5 -> TransactionException.ParseFailed()
            6 -> TransactionException.UnsupportedSegwitFlag(
                FfiConverterUByte.read(buf),
                )
            7 -> TransactionException.OtherTransactionErr()
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: TransactionException): ULong {
        return when (value) {
            is TransactionException.Io -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is TransactionException.OversizedVectorAllocation -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is TransactionException.InvalidChecksum -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`expected`)
                + FfiConverterString.allocationSize(value.`actual`)
            )
            is TransactionException.NonMinimalVarInt -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is TransactionException.ParseFailed -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
            is TransactionException.UnsupportedSegwitFlag -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterUByte.allocationSize(value.`flag`)
            )
            is TransactionException.OtherTransactionErr -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
            )
        }
    }

    override fun write(value: TransactionException, buf: ByteBuffer) {
        when (value) {
            is TransactionException.Io -> {
                buf.putInt(1)
                Unit
            }
            is TransactionException.OversizedVectorAllocation -> {
                buf.putInt(2)
                Unit
            }
            is TransactionException.InvalidChecksum -> {
                buf.putInt(3)
                FfiConverterString.write(value.`expected`, buf)
                FfiConverterString.write(value.`actual`, buf)
                Unit
            }
            is TransactionException.NonMinimalVarInt -> {
                buf.putInt(4)
                Unit
            }
            is TransactionException.ParseFailed -> {
                buf.putInt(5)
                Unit
            }
            is TransactionException.UnsupportedSegwitFlag -> {
                buf.putInt(6)
                FfiConverterUByte.write(value.`flag`, buf)
                Unit
            }
            is TransactionException.OtherTransactionErr -> {
                buf.putInt(7)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}




public object TxidParseExceptionErrorHandler : UniffiRustCallStatusErrorHandler<TxidParseException> {
    override fun lift(errorBuf: RustBufferByValue): TxidParseException = FfiConverterTypeTxidParseError.lift(errorBuf)
}

public object FfiConverterTypeTxidParseError : FfiConverterRustBuffer<TxidParseException> {
    override fun read(buf: ByteBuffer): TxidParseException {
        return when (buf.getInt()) {
            1 -> TxidParseException.InvalidTxid(
                FfiConverterString.read(buf),
                )
            else -> throw RuntimeException("invalid error enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: TxidParseException): ULong {
        return when (value) {
            is TxidParseException.InvalidTxid -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                + FfiConverterString.allocationSize(value.`txid`)
            )
        }
    }

    override fun write(value: TxidParseException, buf: ByteBuffer) {
        when (value) {
            is TxidParseException.InvalidTxid -> {
                buf.putInt(1)
                FfiConverterString.write(value.`txid`, buf)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeWarning : FfiConverterRustBuffer<Warning>{
    override fun read(buf: ByteBuffer): Warning {
        return when(buf.getInt()) {
            1 -> Warning.NeedConnections
            2 -> Warning.PeerTimedOut
            3 -> Warning.CouldNotConnect
            4 -> Warning.NoCompactFilters
            5 -> Warning.PotentialStaleTip
            6 -> Warning.UnsolicitedMessage
            7 -> Warning.TransactionRejected(
                FfiConverterString.read(buf),
                FfiConverterOptionalString.read(buf),
                )
            8 -> Warning.EvaluatingFork
            9 -> Warning.UnexpectedSyncError(
                FfiConverterString.read(buf),
                )
            10 -> Warning.RequestFailed
            else -> throw RuntimeException("invalid enum value, something is very wrong!!")
        }
    }

    override fun allocationSize(value: Warning): ULong = when(value) {
        is Warning.NeedConnections -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.PeerTimedOut -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.CouldNotConnect -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.NoCompactFilters -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.PotentialStaleTip -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.UnsolicitedMessage -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.TransactionRejected -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`wtxid`)
                + FfiConverterOptionalString.allocationSize(value.`reason`)
            )
        }
        is Warning.EvaluatingFork -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
        is Warning.UnexpectedSyncError -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
                + FfiConverterString.allocationSize(value.`warning`)
            )
        }
        is Warning.RequestFailed -> {
            // Add the size for the Int that specifies the variant plus the size needed for all fields
            (
                4UL
            )
        }
    }

    override fun write(value: Warning, buf: ByteBuffer) {
        when(value) {
            is Warning.NeedConnections -> {
                buf.putInt(1)
                Unit
            }
            is Warning.PeerTimedOut -> {
                buf.putInt(2)
                Unit
            }
            is Warning.CouldNotConnect -> {
                buf.putInt(3)
                Unit
            }
            is Warning.NoCompactFilters -> {
                buf.putInt(4)
                Unit
            }
            is Warning.PotentialStaleTip -> {
                buf.putInt(5)
                Unit
            }
            is Warning.UnsolicitedMessage -> {
                buf.putInt(6)
                Unit
            }
            is Warning.TransactionRejected -> {
                buf.putInt(7)
                FfiConverterString.write(value.`wtxid`, buf)
                FfiConverterOptionalString.write(value.`reason`, buf)
                Unit
            }
            is Warning.EvaluatingFork -> {
                buf.putInt(8)
                Unit
            }
            is Warning.UnexpectedSyncError -> {
                buf.putInt(9)
                FfiConverterString.write(value.`warning`, buf)
                Unit
            }
            is Warning.RequestFailed -> {
                buf.putInt(10)
                Unit
            }
        }.let { /* this makes the `when` an expression, which ensures it is exhaustive */ }
    }
}





public object FfiConverterTypeWordCount: FfiConverterRustBuffer<WordCount> {
    override fun read(buf: ByteBuffer): WordCount = try {
        WordCount.values()[buf.getInt() - 1]
    } catch (e: IndexOutOfBoundsException) {
        throw RuntimeException("invalid enum value, something is very wrong!!", e)
    }

    override fun allocationSize(value: WordCount): ULong = 4UL

    override fun write(value: WordCount, buf: ByteBuffer) {
        buf.putInt(value.ordinal + 1)
    }
}




public object FfiConverterOptionalUShort: FfiConverterRustBuffer<kotlin.UShort?> {
    override fun read(buf: ByteBuffer): kotlin.UShort? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterUShort.read(buf)
    }

    override fun allocationSize(value: kotlin.UShort?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterUShort.allocationSize(value)
        }
    }

    override fun write(value: kotlin.UShort?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterUShort.write(value, buf)
        }
    }
}




public object FfiConverterOptionalUInt: FfiConverterRustBuffer<kotlin.UInt?> {
    override fun read(buf: ByteBuffer): kotlin.UInt? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterUInt.read(buf)
    }

    override fun allocationSize(value: kotlin.UInt?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterUInt.allocationSize(value)
        }
    }

    override fun write(value: kotlin.UInt?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterUInt.write(value, buf)
        }
    }
}




public object FfiConverterOptionalULong: FfiConverterRustBuffer<kotlin.ULong?> {
    override fun read(buf: ByteBuffer): kotlin.ULong? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterULong.read(buf)
    }

    override fun allocationSize(value: kotlin.ULong?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterULong.allocationSize(value)
        }
    }

    override fun write(value: kotlin.ULong?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterULong.write(value, buf)
        }
    }
}




public object FfiConverterOptionalLong: FfiConverterRustBuffer<kotlin.Long?> {
    override fun read(buf: ByteBuffer): kotlin.Long? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterLong.read(buf)
    }

    override fun allocationSize(value: kotlin.Long?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterLong.allocationSize(value)
        }
    }

    override fun write(value: kotlin.Long?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterLong.write(value, buf)
        }
    }
}




public object FfiConverterOptionalFloat: FfiConverterRustBuffer<kotlin.Float?> {
    override fun read(buf: ByteBuffer): kotlin.Float? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterFloat.read(buf)
    }

    override fun allocationSize(value: kotlin.Float?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterFloat.allocationSize(value)
        }
    }

    override fun write(value: kotlin.Float?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterFloat.write(value, buf)
        }
    }
}




public object FfiConverterOptionalBoolean: FfiConverterRustBuffer<kotlin.Boolean?> {
    override fun read(buf: ByteBuffer): kotlin.Boolean? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterBoolean.read(buf)
    }

    override fun allocationSize(value: kotlin.Boolean?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterBoolean.allocationSize(value)
        }
    }

    override fun write(value: kotlin.Boolean?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterBoolean.write(value, buf)
        }
    }
}




public object FfiConverterOptionalString: FfiConverterRustBuffer<kotlin.String?> {
    override fun read(buf: ByteBuffer): kotlin.String? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterString.read(buf)
    }

    override fun allocationSize(value: kotlin.String?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterString.allocationSize(value)
        }
    }

    override fun write(value: kotlin.String?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterString.write(value, buf)
        }
    }
}




public object FfiConverterOptionalByteArray: FfiConverterRustBuffer<kotlin.ByteArray?> {
    override fun read(buf: ByteBuffer): kotlin.ByteArray? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterByteArray.read(buf)
    }

    override fun allocationSize(value: kotlin.ByteArray?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterByteArray.allocationSize(value)
        }
    }

    override fun write(value: kotlin.ByteArray?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterByteArray.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeAmount: FfiConverterRustBuffer<Amount?> {
    override fun read(buf: ByteBuffer): Amount? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeAmount.read(buf)
    }

    override fun allocationSize(value: Amount?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeAmount.allocationSize(value)
        }
    }

    override fun write(value: Amount?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeAmount.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeBlockHash: FfiConverterRustBuffer<BlockHash?> {
    override fun read(buf: ByteBuffer): BlockHash? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeBlockHash.read(buf)
    }

    override fun allocationSize(value: BlockHash?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeBlockHash.allocationSize(value)
        }
    }

    override fun write(value: BlockHash?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeBlockHash.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeChangeSet: FfiConverterRustBuffer<ChangeSet?> {
    override fun read(buf: ByteBuffer): ChangeSet? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeChangeSet.read(buf)
    }

    override fun allocationSize(value: ChangeSet?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeChangeSet.allocationSize(value)
        }
    }

    override fun write(value: ChangeSet?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeChangeSet.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeDescriptor: FfiConverterRustBuffer<Descriptor?> {
    override fun read(buf: ByteBuffer): Descriptor? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeDescriptor.read(buf)
    }

    override fun allocationSize(value: Descriptor?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeDescriptor.allocationSize(value)
        }
    }

    override fun write(value: Descriptor?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeDescriptor.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypePolicy: FfiConverterRustBuffer<Policy?> {
    override fun read(buf: ByteBuffer): Policy? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypePolicy.read(buf)
    }

    override fun allocationSize(value: Policy?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypePolicy.allocationSize(value)
        }
    }

    override fun write(value: Policy?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypePolicy.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeScript: FfiConverterRustBuffer<Script?> {
    override fun read(buf: ByteBuffer): Script? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeScript.read(buf)
    }

    override fun allocationSize(value: Script?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeScript.allocationSize(value)
        }
    }

    override fun write(value: Script?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeScript.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeTransaction: FfiConverterRustBuffer<Transaction?> {
    override fun read(buf: ByteBuffer): Transaction? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeTransaction.read(buf)
    }

    override fun allocationSize(value: Transaction?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeTransaction.allocationSize(value)
        }
    }

    override fun write(value: Transaction?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeTransaction.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeTxid: FfiConverterRustBuffer<Txid?> {
    override fun read(buf: ByteBuffer): Txid? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeTxid.read(buf)
    }

    override fun allocationSize(value: Txid?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeTxid.allocationSize(value)
        }
    }

    override fun write(value: Txid?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeTxid.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeCanonicalTx: FfiConverterRustBuffer<CanonicalTx?> {
    override fun read(buf: ByteBuffer): CanonicalTx? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeCanonicalTx.read(buf)
    }

    override fun allocationSize(value: CanonicalTx?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeCanonicalTx.allocationSize(value)
        }
    }

    override fun write(value: CanonicalTx?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeCanonicalTx.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeKeychainAndIndex: FfiConverterRustBuffer<KeychainAndIndex?> {
    override fun read(buf: ByteBuffer): KeychainAndIndex? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeKeychainAndIndex.read(buf)
    }

    override fun allocationSize(value: KeychainAndIndex?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeKeychainAndIndex.allocationSize(value)
        }
    }

    override fun write(value: KeychainAndIndex?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeKeychainAndIndex.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeLocalOutput: FfiConverterRustBuffer<LocalOutput?> {
    override fun read(buf: ByteBuffer): LocalOutput? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeLocalOutput.read(buf)
    }

    override fun allocationSize(value: LocalOutput?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeLocalOutput.allocationSize(value)
        }
    }

    override fun write(value: LocalOutput?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeLocalOutput.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeSignOptions: FfiConverterRustBuffer<SignOptions?> {
    override fun read(buf: ByteBuffer): SignOptions? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeSignOptions.read(buf)
    }

    override fun allocationSize(value: SignOptions?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeSignOptions.allocationSize(value)
        }
    }

    override fun write(value: SignOptions?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeSignOptions.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeTx: FfiConverterRustBuffer<Tx?> {
    override fun read(buf: ByteBuffer): Tx? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeTx.read(buf)
    }

    override fun allocationSize(value: Tx?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeTx.allocationSize(value)
        }
    }

    override fun write(value: Tx?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeTx.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeTxDetails: FfiConverterRustBuffer<TxDetails?> {
    override fun read(buf: ByteBuffer): TxDetails? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeTxDetails.read(buf)
    }

    override fun allocationSize(value: TxDetails?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeTxDetails.allocationSize(value)
        }
    }

    override fun write(value: TxDetails?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeTxDetails.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeTxOut: FfiConverterRustBuffer<TxOut?> {
    override fun read(buf: ByteBuffer): TxOut? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeTxOut.read(buf)
    }

    override fun allocationSize(value: TxOut?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeTxOut.allocationSize(value)
        }
    }

    override fun write(value: TxOut?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeTxOut.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeLockTime: FfiConverterRustBuffer<LockTime?> {
    override fun read(buf: ByteBuffer): LockTime? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeLockTime.read(buf)
    }

    override fun allocationSize(value: LockTime?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeLockTime.allocationSize(value)
        }
    }

    override fun write(value: LockTime?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeLockTime.write(value, buf)
        }
    }
}




public object FfiConverterOptionalTypeNetwork: FfiConverterRustBuffer<Network?> {
    override fun read(buf: ByteBuffer): Network? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterTypeNetwork.read(buf)
    }

    override fun allocationSize(value: Network?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterTypeNetwork.allocationSize(value)
        }
    }

    override fun write(value: Network?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterTypeNetwork.write(value, buf)
        }
    }
}




public object FfiConverterOptionalSequenceByteArray: FfiConverterRustBuffer<List<kotlin.ByteArray>?> {
    override fun read(buf: ByteBuffer): List<kotlin.ByteArray>? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterSequenceByteArray.read(buf)
    }

    override fun allocationSize(value: List<kotlin.ByteArray>?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterSequenceByteArray.allocationSize(value)
        }
    }

    override fun write(value: List<kotlin.ByteArray>?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterSequenceByteArray.write(value, buf)
        }
    }
}




public object FfiConverterOptionalSequenceTypePsbtFinalizeError: FfiConverterRustBuffer<List<PsbtFinalizeException>?> {
    override fun read(buf: ByteBuffer): List<PsbtFinalizeException>? {
        if (buf.get().toInt() == 0) {
            return null
        }
        return FfiConverterSequenceTypePsbtFinalizeError.read(buf)
    }

    override fun allocationSize(value: List<PsbtFinalizeException>?): ULong {
        if (value == null) {
            return 1UL
        } else {
            return 1UL + FfiConverterSequenceTypePsbtFinalizeError.allocationSize(value)
        }
    }

    override fun write(value: List<PsbtFinalizeException>?, buf: ByteBuffer) {
        if (value == null) {
            buf.put(0)
        } else {
            buf.put(1)
            FfiConverterSequenceTypePsbtFinalizeError.write(value, buf)
        }
    }
}




public object FfiConverterSequenceUInt: FfiConverterRustBuffer<List<kotlin.UInt>> {
    override fun read(buf: ByteBuffer): List<kotlin.UInt> {
        val len = buf.getInt()
        return List<kotlin.UInt>(len) {
            FfiConverterUInt.read(buf)
        }
    }

    override fun allocationSize(value: List<kotlin.UInt>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterUInt.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<kotlin.UInt>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterUInt.write(it, buf)
        }
    }
}




public object FfiConverterSequenceULong: FfiConverterRustBuffer<List<kotlin.ULong>> {
    override fun read(buf: ByteBuffer): List<kotlin.ULong> {
        val len = buf.getInt()
        return List<kotlin.ULong>(len) {
            FfiConverterULong.read(buf)
        }
    }

    override fun allocationSize(value: List<kotlin.ULong>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterULong.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<kotlin.ULong>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterULong.write(it, buf)
        }
    }
}




public object FfiConverterSequenceString: FfiConverterRustBuffer<List<kotlin.String>> {
    override fun read(buf: ByteBuffer): List<kotlin.String> {
        val len = buf.getInt()
        return List<kotlin.String>(len) {
            FfiConverterString.read(buf)
        }
    }

    override fun allocationSize(value: List<kotlin.String>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterString.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<kotlin.String>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterString.write(it, buf)
        }
    }
}




public object FfiConverterSequenceByteArray: FfiConverterRustBuffer<List<kotlin.ByteArray>> {
    override fun read(buf: ByteBuffer): List<kotlin.ByteArray> {
        val len = buf.getInt()
        return List<kotlin.ByteArray>(len) {
            FfiConverterByteArray.read(buf)
        }
    }

    override fun allocationSize(value: List<kotlin.ByteArray>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterByteArray.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<kotlin.ByteArray>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterByteArray.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeDescriptor: FfiConverterRustBuffer<List<Descriptor>> {
    override fun read(buf: ByteBuffer): List<Descriptor> {
        val len = buf.getInt()
        return List<Descriptor>(len) {
            FfiConverterTypeDescriptor.read(buf)
        }
    }

    override fun allocationSize(value: List<Descriptor>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeDescriptor.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Descriptor>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeDescriptor.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeIpAddress: FfiConverterRustBuffer<List<IpAddress>> {
    override fun read(buf: ByteBuffer): List<IpAddress> {
        val len = buf.getInt()
        return List<IpAddress>(len) {
            FfiConverterTypeIpAddress.read(buf)
        }
    }

    override fun allocationSize(value: List<IpAddress>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeIpAddress.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<IpAddress>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeIpAddress.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypePolicy: FfiConverterRustBuffer<List<Policy>> {
    override fun read(buf: ByteBuffer): List<Policy> {
        val len = buf.getInt()
        return List<Policy>(len) {
            FfiConverterTypePolicy.read(buf)
        }
    }

    override fun allocationSize(value: List<Policy>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypePolicy.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Policy>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypePolicy.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeTransaction: FfiConverterRustBuffer<List<Transaction>> {
    override fun read(buf: ByteBuffer): List<Transaction> {
        val len = buf.getInt()
        return List<Transaction>(len) {
            FfiConverterTypeTransaction.read(buf)
        }
    }

    override fun allocationSize(value: List<Transaction>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeTransaction.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Transaction>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeTransaction.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeAddressInfo: FfiConverterRustBuffer<List<AddressInfo>> {
    override fun read(buf: ByteBuffer): List<AddressInfo> {
        val len = buf.getInt()
        return List<AddressInfo>(len) {
            FfiConverterTypeAddressInfo.read(buf)
        }
    }

    override fun allocationSize(value: List<AddressInfo>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeAddressInfo.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<AddressInfo>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeAddressInfo.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeAnchor: FfiConverterRustBuffer<List<Anchor>> {
    override fun read(buf: ByteBuffer): List<Anchor> {
        val len = buf.getInt()
        return List<Anchor>(len) {
            FfiConverterTypeAnchor.read(buf)
        }
    }

    override fun allocationSize(value: List<Anchor>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeAnchor.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Anchor>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeAnchor.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeCanonicalTx: FfiConverterRustBuffer<List<CanonicalTx>> {
    override fun read(buf: ByteBuffer): List<CanonicalTx> {
        val len = buf.getInt()
        return List<CanonicalTx>(len) {
            FfiConverterTypeCanonicalTx.read(buf)
        }
    }

    override fun allocationSize(value: List<CanonicalTx>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeCanonicalTx.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<CanonicalTx>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeCanonicalTx.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeChainChange: FfiConverterRustBuffer<List<ChainChange>> {
    override fun read(buf: ByteBuffer): List<ChainChange> {
        val len = buf.getInt()
        return List<ChainChange>(len) {
            FfiConverterTypeChainChange.read(buf)
        }
    }

    override fun allocationSize(value: List<ChainChange>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeChainChange.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<ChainChange>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeChainChange.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeCondition: FfiConverterRustBuffer<List<Condition>> {
    override fun read(buf: ByteBuffer): List<Condition> {
        val len = buf.getInt()
        return List<Condition>(len) {
            FfiConverterTypeCondition.read(buf)
        }
    }

    override fun allocationSize(value: List<Condition>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeCondition.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Condition>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeCondition.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeEvictedTx: FfiConverterRustBuffer<List<EvictedTx>> {
    override fun read(buf: ByteBuffer): List<EvictedTx> {
        val len = buf.getInt()
        return List<EvictedTx>(len) {
            FfiConverterTypeEvictedTx.read(buf)
        }
    }

    override fun allocationSize(value: List<EvictedTx>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeEvictedTx.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<EvictedTx>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeEvictedTx.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeInput: FfiConverterRustBuffer<List<Input>> {
    override fun read(buf: ByteBuffer): List<Input> {
        val len = buf.getInt()
        return List<Input>(len) {
            FfiConverterTypeInput.read(buf)
        }
    }

    override fun allocationSize(value: List<Input>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeInput.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Input>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeInput.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeLocalOutput: FfiConverterRustBuffer<List<LocalOutput>> {
    override fun read(buf: ByteBuffer): List<LocalOutput> {
        val len = buf.getInt()
        return List<LocalOutput>(len) {
            FfiConverterTypeLocalOutput.read(buf)
        }
    }

    override fun allocationSize(value: List<LocalOutput>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeLocalOutput.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<LocalOutput>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeLocalOutput.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeOutPoint: FfiConverterRustBuffer<List<OutPoint>> {
    override fun read(buf: ByteBuffer): List<OutPoint> {
        val len = buf.getInt()
        return List<OutPoint>(len) {
            FfiConverterTypeOutPoint.read(buf)
        }
    }

    override fun allocationSize(value: List<OutPoint>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeOutPoint.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<OutPoint>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeOutPoint.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypePeer: FfiConverterRustBuffer<List<Peer>> {
    override fun read(buf: ByteBuffer): List<Peer> {
        val len = buf.getInt()
        return List<Peer>(len) {
            FfiConverterTypePeer.read(buf)
        }
    }

    override fun allocationSize(value: List<Peer>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypePeer.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<Peer>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypePeer.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeScriptAmount: FfiConverterRustBuffer<List<ScriptAmount>> {
    override fun read(buf: ByteBuffer): List<ScriptAmount> {
        val len = buf.getInt()
        return List<ScriptAmount>(len) {
            FfiConverterTypeScriptAmount.read(buf)
        }
    }

    override fun allocationSize(value: List<ScriptAmount>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeScriptAmount.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<ScriptAmount>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeScriptAmount.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeTxIn: FfiConverterRustBuffer<List<TxIn>> {
    override fun read(buf: ByteBuffer): List<TxIn> {
        val len = buf.getInt()
        return List<TxIn>(len) {
            FfiConverterTypeTxIn.read(buf)
        }
    }

    override fun allocationSize(value: List<TxIn>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeTxIn.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<TxIn>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeTxIn.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeTxOut: FfiConverterRustBuffer<List<TxOut>> {
    override fun read(buf: ByteBuffer): List<TxOut> {
        val len = buf.getInt()
        return List<TxOut>(len) {
            FfiConverterTypeTxOut.read(buf)
        }
    }

    override fun allocationSize(value: List<TxOut>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeTxOut.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<TxOut>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeTxOut.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypeUnconfirmedTx: FfiConverterRustBuffer<List<UnconfirmedTx>> {
    override fun read(buf: ByteBuffer): List<UnconfirmedTx> {
        val len = buf.getInt()
        return List<UnconfirmedTx>(len) {
            FfiConverterTypeUnconfirmedTx.read(buf)
        }
    }

    override fun allocationSize(value: List<UnconfirmedTx>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypeUnconfirmedTx.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<UnconfirmedTx>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypeUnconfirmedTx.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypePkOrF: FfiConverterRustBuffer<List<PkOrF>> {
    override fun read(buf: ByteBuffer): List<PkOrF> {
        val len = buf.getInt()
        return List<PkOrF>(len) {
            FfiConverterTypePkOrF.read(buf)
        }
    }

    override fun allocationSize(value: List<PkOrF>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypePkOrF.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<PkOrF>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypePkOrF.write(it, buf)
        }
    }
}




public object FfiConverterSequenceTypePsbtFinalizeError: FfiConverterRustBuffer<List<PsbtFinalizeException>> {
    override fun read(buf: ByteBuffer): List<PsbtFinalizeException> {
        val len = buf.getInt()
        return List<PsbtFinalizeException>(len) {
            FfiConverterTypePsbtFinalizeError.read(buf)
        }
    }

    override fun allocationSize(value: List<PsbtFinalizeException>): ULong {
        val sizeForLength = 4UL
        val sizeForItems = value.sumOf { FfiConverterTypePsbtFinalizeError.allocationSize(it) }
        return sizeForLength + sizeForItems
    }

    override fun write(value: List<PsbtFinalizeException>, buf: ByteBuffer) {
        buf.putInt(value.size)
        value.iterator().forEach {
            FfiConverterTypePsbtFinalizeError.write(it, buf)
        }
    }
}



public object FfiConverterMapUShortDouble: FfiConverterRustBuffer<Map<kotlin.UShort, kotlin.Double>> {
    override fun read(buf: ByteBuffer): Map<kotlin.UShort, kotlin.Double> {
        val len = buf.getInt()
        return buildMap<kotlin.UShort, kotlin.Double>(len) {
            repeat(len) {
                val k = FfiConverterUShort.read(buf)
                val v = FfiConverterDouble.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<kotlin.UShort, kotlin.Double>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterUShort.allocationSize(k) +
            FfiConverterDouble.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<kotlin.UShort, kotlin.Double>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterUShort.write(k, buf)
            FfiConverterDouble.write(v, buf)
        }
    }
}



public object FfiConverterMapUIntSequenceTypeCondition: FfiConverterRustBuffer<Map<kotlin.UInt, List<Condition>>> {
    override fun read(buf: ByteBuffer): Map<kotlin.UInt, List<Condition>> {
        val len = buf.getInt()
        return buildMap<kotlin.UInt, List<Condition>>(len) {
            repeat(len) {
                val k = FfiConverterUInt.read(buf)
                val v = FfiConverterSequenceTypeCondition.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<kotlin.UInt, List<Condition>>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterUInt.allocationSize(k) +
            FfiConverterSequenceTypeCondition.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<kotlin.UInt, List<Condition>>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterUInt.write(k, buf)
            FfiConverterSequenceTypeCondition.write(v, buf)
        }
    }
}



public object FfiConverterMapStringByteArray: FfiConverterRustBuffer<Map<kotlin.String, kotlin.ByteArray>> {
    override fun read(buf: ByteBuffer): Map<kotlin.String, kotlin.ByteArray> {
        val len = buf.getInt()
        return buildMap<kotlin.String, kotlin.ByteArray>(len) {
            repeat(len) {
                val k = FfiConverterString.read(buf)
                val v = FfiConverterByteArray.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<kotlin.String, kotlin.ByteArray>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterString.allocationSize(k) +
            FfiConverterByteArray.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<kotlin.String, kotlin.ByteArray>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterString.write(k, buf)
            FfiConverterByteArray.write(v, buf)
        }
    }
}



public object FfiConverterMapStringTypeKeySource: FfiConverterRustBuffer<Map<kotlin.String, KeySource>> {
    override fun read(buf: ByteBuffer): Map<kotlin.String, KeySource> {
        val len = buf.getInt()
        return buildMap<kotlin.String, KeySource>(len) {
            repeat(len) {
                val k = FfiConverterString.read(buf)
                val v = FfiConverterTypeKeySource.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<kotlin.String, KeySource>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterString.allocationSize(k) +
            FfiConverterTypeKeySource.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<kotlin.String, KeySource>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterString.write(k, buf)
            FfiConverterTypeKeySource.write(v, buf)
        }
    }
}



public object FfiConverterMapStringTypeTapKeyOrigin: FfiConverterRustBuffer<Map<kotlin.String, TapKeyOrigin>> {
    override fun read(buf: ByteBuffer): Map<kotlin.String, TapKeyOrigin> {
        val len = buf.getInt()
        return buildMap<kotlin.String, TapKeyOrigin>(len) {
            repeat(len) {
                val k = FfiConverterString.read(buf)
                val v = FfiConverterTypeTapKeyOrigin.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<kotlin.String, TapKeyOrigin>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterString.allocationSize(k) +
            FfiConverterTypeTapKeyOrigin.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<kotlin.String, TapKeyOrigin>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterString.write(k, buf)
            FfiConverterTypeTapKeyOrigin.write(v, buf)
        }
    }
}



public object FfiConverterMapStringSequenceULong: FfiConverterRustBuffer<Map<kotlin.String, List<kotlin.ULong>>> {
    override fun read(buf: ByteBuffer): Map<kotlin.String, List<kotlin.ULong>> {
        val len = buf.getInt()
        return buildMap<kotlin.String, List<kotlin.ULong>>(len) {
            repeat(len) {
                val k = FfiConverterString.read(buf)
                val v = FfiConverterSequenceULong.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<kotlin.String, List<kotlin.ULong>>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterString.allocationSize(k) +
            FfiConverterSequenceULong.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<kotlin.String, List<kotlin.ULong>>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterString.write(k, buf)
            FfiConverterSequenceULong.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeDescriptorIdUInt: FfiConverterRustBuffer<Map<DescriptorId, kotlin.UInt>> {
    override fun read(buf: ByteBuffer): Map<DescriptorId, kotlin.UInt> {
        val len = buf.getInt()
        return buildMap<DescriptorId, kotlin.UInt>(len) {
            repeat(len) {
                val k = FfiConverterTypeDescriptorId.read(buf)
                val v = FfiConverterUInt.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<DescriptorId, kotlin.UInt>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeDescriptorId.allocationSize(k) +
            FfiConverterUInt.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<DescriptorId, kotlin.UInt>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeDescriptorId.write(k, buf)
            FfiConverterUInt.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeHashableOutPointTypeTxOut: FfiConverterRustBuffer<Map<HashableOutPoint, TxOut>> {
    override fun read(buf: ByteBuffer): Map<HashableOutPoint, TxOut> {
        val len = buf.getInt()
        return buildMap<HashableOutPoint, TxOut>(len) {
            repeat(len) {
                val k = FfiConverterTypeHashableOutPoint.read(buf)
                val v = FfiConverterTypeTxOut.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<HashableOutPoint, TxOut>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeHashableOutPoint.allocationSize(k) +
            FfiConverterTypeTxOut.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<HashableOutPoint, TxOut>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeHashableOutPoint.write(k, buf)
            FfiConverterTypeTxOut.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeTxidULong: FfiConverterRustBuffer<Map<Txid, kotlin.ULong>> {
    override fun read(buf: ByteBuffer): Map<Txid, kotlin.ULong> {
        val len = buf.getInt()
        return buildMap<Txid, kotlin.ULong>(len) {
            repeat(len) {
                val k = FfiConverterTypeTxid.read(buf)
                val v = FfiConverterULong.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<Txid, kotlin.ULong>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeTxid.allocationSize(k) +
            FfiConverterULong.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<Txid, kotlin.ULong>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeTxid.write(k, buf)
            FfiConverterULong.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeControlBlockTypeTapScriptEntry: FfiConverterRustBuffer<Map<ControlBlock, TapScriptEntry>> {
    override fun read(buf: ByteBuffer): Map<ControlBlock, TapScriptEntry> {
        val len = buf.getInt()
        return buildMap<ControlBlock, TapScriptEntry>(len) {
            repeat(len) {
                val k = FfiConverterTypeControlBlock.read(buf)
                val v = FfiConverterTypeTapScriptEntry.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<ControlBlock, TapScriptEntry>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeControlBlock.allocationSize(k) +
            FfiConverterTypeTapScriptEntry.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<ControlBlock, TapScriptEntry>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeControlBlock.write(k, buf)
            FfiConverterTypeTapScriptEntry.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeKeyByteArray: FfiConverterRustBuffer<Map<Key, kotlin.ByteArray>> {
    override fun read(buf: ByteBuffer): Map<Key, kotlin.ByteArray> {
        val len = buf.getInt()
        return buildMap<Key, kotlin.ByteArray>(len) {
            repeat(len) {
                val k = FfiConverterTypeKey.read(buf)
                val v = FfiConverterByteArray.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<Key, kotlin.ByteArray>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeKey.allocationSize(k) +
            FfiConverterByteArray.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<Key, kotlin.ByteArray>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeKey.write(k, buf)
            FfiConverterByteArray.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeProprietaryKeyByteArray: FfiConverterRustBuffer<Map<ProprietaryKey, kotlin.ByteArray>> {
    override fun read(buf: ByteBuffer): Map<ProprietaryKey, kotlin.ByteArray> {
        val len = buf.getInt()
        return buildMap<ProprietaryKey, kotlin.ByteArray>(len) {
            repeat(len) {
                val k = FfiConverterTypeProprietaryKey.read(buf)
                val v = FfiConverterByteArray.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<ProprietaryKey, kotlin.ByteArray>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeProprietaryKey.allocationSize(k) +
            FfiConverterByteArray.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<ProprietaryKey, kotlin.ByteArray>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeProprietaryKey.write(k, buf)
            FfiConverterByteArray.write(v, buf)
        }
    }
}



public object FfiConverterMapTypeTapScriptSigKeyByteArray: FfiConverterRustBuffer<Map<TapScriptSigKey, kotlin.ByteArray>> {
    override fun read(buf: ByteBuffer): Map<TapScriptSigKey, kotlin.ByteArray> {
        val len = buf.getInt()
        return buildMap<TapScriptSigKey, kotlin.ByteArray>(len) {
            repeat(len) {
                val k = FfiConverterTypeTapScriptSigKey.read(buf)
                val v = FfiConverterByteArray.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<TapScriptSigKey, kotlin.ByteArray>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterTypeTapScriptSigKey.allocationSize(k) +
            FfiConverterByteArray.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<TapScriptSigKey, kotlin.ByteArray>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterTypeTapScriptSigKey.write(k, buf)
            FfiConverterByteArray.write(v, buf)
        }
    }
}



public object FfiConverterMapSequenceUIntSequenceTypeCondition: FfiConverterRustBuffer<Map<List<kotlin.UInt>, List<Condition>>> {
    override fun read(buf: ByteBuffer): Map<List<kotlin.UInt>, List<Condition>> {
        val len = buf.getInt()
        return buildMap<List<kotlin.UInt>, List<Condition>>(len) {
            repeat(len) {
                val k = FfiConverterSequenceUInt.read(buf)
                val v = FfiConverterSequenceTypeCondition.read(buf)
                this[k] = v
            }
        }
    }

    override fun allocationSize(value: Map<List<kotlin.UInt>, List<Condition>>): ULong {
        val spaceForMapSize = 4UL
        val spaceForChildren = value.entries.sumOf { (k, v) ->
            FfiConverterSequenceUInt.allocationSize(k) +
            FfiConverterSequenceTypeCondition.allocationSize(v)
        }
        return spaceForMapSize + spaceForChildren
    }

    override fun write(value: Map<List<kotlin.UInt>, List<Condition>>, buf: ByteBuffer) {
        buf.putInt(value.size)
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        value.forEach { (k, v) ->
            FfiConverterSequenceUInt.write(k, buf)
            FfiConverterSequenceTypeCondition.write(v, buf)
        }
    }
}














// Async support

internal const val UNIFFI_RUST_FUTURE_POLL_READY = 0.toByte()
internal const val UNIFFI_RUST_FUTURE_POLL_MAYBE_READY = 1.toByte()

internal val uniffiContinuationHandleMap = UniffiHandleMap<CancellableContinuation<Byte>>()

// FFI type for Rust future continuations
internal suspend fun<T, F, E: kotlin.Exception> uniffiRustCallAsync(
    rustFuture: Long,
    pollFunc: (Long, UniffiRustFutureContinuationCallback, Long) -> Unit,
    completeFunc: (Long, UniffiRustCallStatus) -> F,
    freeFunc: (Long) -> Unit,
    cancelFunc: (Long) -> Unit,
    liftFunc: (F) -> T,
    errorHandler: UniffiRustCallStatusErrorHandler<E>
): T {
    return withContext(Dispatchers.IO) {
        try {
            do {
                val pollResult = suspendCancellableCoroutine<Byte> { continuation ->
                    val handle = uniffiContinuationHandleMap.insert(continuation)
                    continuation.invokeOnCancellation {
                        cancelFunc(rustFuture)
                    }
                    pollFunc(
                        rustFuture,
                        uniffiRustFutureContinuationCallbackCallback,
                        handle
                    )
                }
            } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

            return@withContext liftFunc(
                uniffiRustCallWithError(errorHandler) { status -> completeFunc(rustFuture, status) }
            )
        } finally {
            freeFunc(rustFuture)
        }
    }
}

internal object uniffiRustFutureContinuationCallbackCallback: UniffiRustFutureContinuationCallback {
    override fun callback(data: Long, pollResult: Byte) {
        uniffiContinuationHandleMap.remove(data).resume(pollResult)
    }
}