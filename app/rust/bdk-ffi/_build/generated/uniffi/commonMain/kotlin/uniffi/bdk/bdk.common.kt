

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
 * A bitcoin address
 */
public interface AddressInterface {
    
    /**
     * Is the address valid for the provided network
     */
    public fun `isValidForNetwork`(`network`: Network): kotlin.Boolean
    
    /**
     * Return the `scriptPubKey` underlying an address.
     */
    public fun `scriptPubkey`(): Script
    
    /**
     * Return the data for the address.
     */
    public fun `toAddressData`(): AddressData
    
    /**
     * Return a BIP-21 URI string for this address.
     */
    public fun `toQrUri`(): kotlin.String
    
    public companion object
}


/**
 * A bitcoin address
 */
public expect open class Address: Disposable, AddressInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Parse a string as an address for the given network.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Parse a string as an address for the given network.
     */
    public constructor(`address`: kotlin.String, `network`: Network)

    override fun destroy()
    override fun close()

    
    /**
     * Is the address valid for the provided network
     */
    public override fun `isValidForNetwork`(`network`: Network): kotlin.Boolean
    
    /**
     * Return the `scriptPubKey` underlying an address.
     */
    public override fun `scriptPubkey`(): Script
    
    /**
     * Return the data for the address.
     */
    public override fun `toAddressData`(): AddressData
    
    /**
     * Return a BIP-21 URI string for this address.
     */
    public override fun `toQrUri`(): kotlin.String
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    

    public companion object {
        
        /**
         * Parse a script as an address for the given network
         */
        @Throws(FromScriptException::class)
        public fun `fromScript`(`script`: Script, `network`: Network): Address
        
    }
    
}




/**
 * The Amount type can be used to express Bitcoin amounts that support arithmetic and conversion
 * to various denominations. The operations that Amount implements will panic when overflow or
 * underflow occurs. Also note that since the internal representation of amounts is unsigned,
 * subtracting below zero is considered an underflow and will cause a panic.
 */
public interface AmountInterface {
    
    /**
     * Express this Amount as a floating-point value in Bitcoin. Please be aware of the risk of
     * using floating-point numbers.
     */
    public fun `toBtc`(): kotlin.Double
    
    /**
     * Get the number of satoshis in this Amount.
     */
    public fun `toSat`(): kotlin.ULong
    
    public companion object
}


/**
 * The Amount type can be used to express Bitcoin amounts that support arithmetic and conversion
 * to various denominations. The operations that Amount implements will panic when overflow or
 * underflow occurs. Also note that since the internal representation of amounts is unsigned,
 * subtracting below zero is considered an underflow and will cause a panic.
 */
public expect open class Amount: Disposable, AmountInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Express this Amount as a floating-point value in Bitcoin. Please be aware of the risk of
     * using floating-point numbers.
     */
    public override fun `toBtc`(): kotlin.Double
    
    /**
     * Get the number of satoshis in this Amount.
     */
    public override fun `toSat`(): kotlin.ULong
    

    public companion object {
        
        /**
         * Convert from a value expressing bitcoins to an Amount.
         */
        @Throws(ParseAmountException::class)
        public fun `fromBtc`(`btc`: kotlin.Double): Amount
        
        /**
         * Create an Amount with satoshi precision and the given number of satoshis.
         */
        public fun `fromSat`(`satoshi`: kotlin.ULong): Amount
        
    }
    
}




/**
 * A bitcoin Block hash
 */
public interface BlockHashInterface {
    
    /**
     * Serialize this type into a 32 byte array.
     */
    public fun `serialize`(): kotlin.ByteArray
    
    public companion object
}


/**
 * A bitcoin Block hash
 */
public expect open class BlockHash: Disposable, BlockHashInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public override fun `serialize`(): kotlin.ByteArray
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    
    override fun hashCode(): Int

    public companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public fun `fromBytes`(`bytes`: kotlin.ByteArray): BlockHash
        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public fun `fromString`(`hex`: kotlin.String): BlockHash
        
    }
    
}




/**
 * A `BumpFeeTxBuilder` is created by calling `build_fee_bump` on a wallet. After assigning it, you set options on it
 * until finally calling `finish` to consume the builder and generate the transaction.
 */
public interface BumpFeeTxBuilderInterface {
    
    /**
     * Set whether the dust limit is checked.
     *
     * Note: by avoiding a dust limit check you may end up with a transaction that is non-standard.
     */
    public fun `allowDust`(`allowDust`: kotlin.Boolean): BumpFeeTxBuilder
    
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
    public fun `currentHeight`(`height`: kotlin.UInt): BumpFeeTxBuilder
    
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
    public fun `finish`(`wallet`: Wallet): Psbt
    
    /**
     * Use a specific nLockTime while creating the transaction.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "after" (`OP_CLTV`) operator.
     */
    public fun `nlocktime`(`locktime`: LockTime): BumpFeeTxBuilder
    
    /**
     * Set an exact `nSequence` value.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "older" (`OP_CSV`) operator and the given
     * `nsequence` is lower than the CSV value.
     */
    public fun `setExactSequence`(`nsequence`: kotlin.UInt): BumpFeeTxBuilder
    
    /**
     * Build a transaction with a specific version.
     *
     * The version should always be greater than 0 and greater than 1 if the wallet’s descriptors contain an "older"
     * (`OP_CSV`) operator.
     */
    public fun `version`(`version`: kotlin.Int): BumpFeeTxBuilder
    
    public companion object
}


/**
 * A `BumpFeeTxBuilder` is created by calling `build_fee_bump` on a wallet. After assigning it, you set options on it
 * until finally calling `finish` to consume the builder and generate the transaction.
 */
public expect open class BumpFeeTxBuilder: Disposable, BumpFeeTxBuilderInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public constructor(noPointer: NoPointer)

    
    public constructor(`txid`: Txid, `feeRate`: FeeRate)

    override fun destroy()
    override fun close()

    
    /**
     * Set whether the dust limit is checked.
     *
     * Note: by avoiding a dust limit check you may end up with a transaction that is non-standard.
     */
    public override fun `allowDust`(`allowDust`: kotlin.Boolean): BumpFeeTxBuilder
    
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
    public override fun `currentHeight`(`height`: kotlin.UInt): BumpFeeTxBuilder
    
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
    public override fun `finish`(`wallet`: Wallet): Psbt
    
    /**
     * Use a specific nLockTime while creating the transaction.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "after" (`OP_CLTV`) operator.
     */
    public override fun `nlocktime`(`locktime`: LockTime): BumpFeeTxBuilder
    
    /**
     * Set an exact `nSequence` value.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "older" (`OP_CSV`) operator and the given
     * `nsequence` is lower than the CSV value.
     */
    public override fun `setExactSequence`(`nsequence`: kotlin.UInt): BumpFeeTxBuilder
    
    /**
     * Build a transaction with a specific version.
     *
     * The version should always be greater than 0 and greater than 1 if the wallet’s descriptors contain an "older"
     * (`OP_CSV`) operator.
     */
    public override fun `version`(`version`: kotlin.Int): BumpFeeTxBuilder
    

    
    public companion object
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
public interface CbfBuilderInterface {
    
    /**
     * Construct a [`CbfComponents`] for a [`Wallet`].
     */
    public fun `build`(`wallet`: Wallet): CbfComponents
    
    /**
     * Configure the time in milliseconds that a node has to:
     * 1. Respond to the initial connection
     * 2. Respond to a request
     */
    public fun `configureTimeoutMillis`(`handshake`: kotlin.ULong, `response`: kotlin.ULong): CbfBuilder
    
    /**
     * The number of connections for the light client to maintain. Default is two.
     */
    public fun `connections`(`connections`: kotlin.UByte): CbfBuilder
    
    /**
     * Directory to store block headers and peers. If none is provided, the current
     * working directory will be used.
     */
    public fun `dataDir`(`dataDir`: kotlin.String): CbfBuilder
    
    /**
     * Bitcoin full-nodes to attempt a connection with.
     */
    public fun `peers`(`peers`: List<Peer>): CbfBuilder
    
    /**
     * Select between syncing, recovering, or scanning for new wallets.
     */
    public fun `scanType`(`scanType`: ScanType): CbfBuilder
    
    /**
     * Configure connections to be established through a `Socks5 proxy. The vast majority of the
     * time, the connection is to a local Tor daemon, which is typically exposed at
     * `127.0.0.1:9050`.
     */
    public fun `socks5Proxy`(`proxy`: Socks5Proxy): CbfBuilder
    
    public companion object
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
public expect open class CbfBuilder: Disposable, CbfBuilderInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Start a new [`CbfBuilder`]
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Start a new [`CbfBuilder`]
     */
    public constructor()

    override fun destroy()
    override fun close()

    
    /**
     * Construct a [`CbfComponents`] for a [`Wallet`].
     */
    public override fun `build`(`wallet`: Wallet): CbfComponents
    
    /**
     * Configure the time in milliseconds that a node has to:
     * 1. Respond to the initial connection
     * 2. Respond to a request
     */
    public override fun `configureTimeoutMillis`(`handshake`: kotlin.ULong, `response`: kotlin.ULong): CbfBuilder
    
    /**
     * The number of connections for the light client to maintain. Default is two.
     */
    public override fun `connections`(`connections`: kotlin.UByte): CbfBuilder
    
    /**
     * Directory to store block headers and peers. If none is provided, the current
     * working directory will be used.
     */
    public override fun `dataDir`(`dataDir`: kotlin.String): CbfBuilder
    
    /**
     * Bitcoin full-nodes to attempt a connection with.
     */
    public override fun `peers`(`peers`: List<Peer>): CbfBuilder
    
    /**
     * Select between syncing, recovering, or scanning for new wallets.
     */
    public override fun `scanType`(`scanType`: ScanType): CbfBuilder
    
    /**
     * Configure connections to be established through a `Socks5 proxy. The vast majority of the
     * time, the connection is to a local Tor daemon, which is typically exposed at
     * `127.0.0.1:9050`.
     */
    public override fun `socks5Proxy`(`proxy`: Socks5Proxy): CbfBuilder
    

    
    public companion object
}




/**
 * A [`CbfClient`] handles wallet updates from a [`CbfNode`].
 */
public interface CbfClientInterface {
    
    /**
     * Fetch the average fee rate for a block by requesting it from a peer. Not recommend for
     * resource-limited devices.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public suspend fun `averageFeeRate`(`blockhash`: BlockHash): FeeRate
    
    /**
     * Broadcast a transaction to the network, erroring if the node has stopped running.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public suspend fun `broadcast`(`transaction`: Transaction): Wtxid
    
    /**
     * Add another [`Peer`] to attempt a connection with.
     */
    @Throws(CbfException::class)
    public fun `connect`(`peer`: Peer)
    
    /**
     * Check if the node is still running in the background.
     */
    public fun `isRunning`(): kotlin.Boolean
    
    /**
     * Query a Bitcoin DNS seeder using the configured resolver.
     *
     * This is **not** a generic DNS implementation. Host names are prefixed with a `x849` to filter
     * for compact block filter nodes from the seeder. For example `dns.myseeder.com` will be queried
     * as `x849.dns.myseeder.com`. This has no guarantee to return any `IpAddr`.
     */
    public fun `lookupHost`(`hostname`: kotlin.String): List<IpAddress>
    
    /**
     * The minimum fee rate required to broadcast a transcation to all connected peers.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public suspend fun `minBroadcastFeerate`(): FeeRate
    
    /**
     * Return the next available info message from a node. If none is returned, the node has stopped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public suspend fun `nextInfo`(): Info
    
    /**
     * Return the next available warning message from a node. If none is returned, the node has stopped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public suspend fun `nextWarning`(): Warning
    
    /**
     * Stop the [`CbfNode`]. Errors if the node is already stopped.
     */
    @Throws(CbfException::class)
    public fun `shutdown`()
    
    /**
     * Return an [`Update`]. This is method returns once the node syncs to the rest of
     * the network or a new block has been gossiped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public suspend fun `update`(): Update
    
    public companion object
}


/**
 * A [`CbfClient`] handles wallet updates from a [`CbfNode`].
 */
public expect open class CbfClient: Disposable, CbfClientInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Fetch the average fee rate for a block by requesting it from a peer. Not recommend for
     * resource-limited devices.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public override suspend fun `averageFeeRate`(`blockhash`: BlockHash): FeeRate
    
    /**
     * Broadcast a transaction to the network, erroring if the node has stopped running.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public override suspend fun `broadcast`(`transaction`: Transaction): Wtxid
    
    /**
     * Add another [`Peer`] to attempt a connection with.
     */
    @Throws(CbfException::class)
    public override fun `connect`(`peer`: Peer)
    
    /**
     * Check if the node is still running in the background.
     */
    public override fun `isRunning`(): kotlin.Boolean
    
    /**
     * Query a Bitcoin DNS seeder using the configured resolver.
     *
     * This is **not** a generic DNS implementation. Host names are prefixed with a `x849` to filter
     * for compact block filter nodes from the seeder. For example `dns.myseeder.com` will be queried
     * as `x849.dns.myseeder.com`. This has no guarantee to return any `IpAddr`.
     */
    public override fun `lookupHost`(`hostname`: kotlin.String): List<IpAddress>
    
    /**
     * The minimum fee rate required to broadcast a transcation to all connected peers.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public override suspend fun `minBroadcastFeerate`(): FeeRate
    
    /**
     * Return the next available info message from a node. If none is returned, the node has stopped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public override suspend fun `nextInfo`(): Info
    
    /**
     * Return the next available warning message from a node. If none is returned, the node has stopped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public override suspend fun `nextWarning`(): Warning
    
    /**
     * Stop the [`CbfNode`]. Errors if the node is already stopped.
     */
    @Throws(CbfException::class)
    public override fun `shutdown`()
    
    /**
     * Return an [`Update`]. This is method returns once the node syncs to the rest of
     * the network or a new block has been gossiped.
     */
    @Throws(CbfException::class, kotlin.coroutines.cancellation.CancellationException::class)
    public override suspend fun `update`(): Update
    

    
    public companion object
}




/**
 * A [`CbfNode`] gathers transactions for a [`Wallet`].
 * To receive [`Update`] for [`Wallet`], refer to the
 * [`CbfClient`]. The [`CbfNode`] will run until instructed
 * to stop.
 */
public interface CbfNodeInterface {
    
    /**
     * Start the node on a detached OS thread and immediately return.
     */
    public fun `run`()
    
    public companion object
}


/**
 * A [`CbfNode`] gathers transactions for a [`Wallet`].
 * To receive [`Update`] for [`Wallet`], refer to the
 * [`CbfClient`]. The [`CbfNode`] will run until instructed
 * to stop.
 */
public expect open class CbfNode: Disposable, CbfNodeInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Start the node on a detached OS thread and immediately return.
     */
    public override fun `run`()
    

    
    public companion object
}




public interface ChangeSetInterface {
    
    /**
     * Get the change `Descriptor`
     */
    public fun `changeDescriptor`(): Descriptor?
    
    /**
     * Get the receiving `Descriptor`.
     */
    public fun `descriptor`(): Descriptor?
    
    /**
     * Get the changes to the indexer.
     */
    public fun `indexerChangeset`(): IndexerChangeSet
    
    /**
     * Get the changes to the local chain.
     */
    public fun `localchainChangeset`(): LocalChainChangeSet
    
    /**
     * Get the `Network`
     */
    public fun `network`(): Network?
    
    /**
     * Get the changes to the transaction graph.
     */
    public fun `txGraphChangeset`(): TxGraphChangeSet
    
    public companion object
}


public expect open class ChangeSet: Disposable, ChangeSetInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Create an empty `ChangeSet`.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Create an empty `ChangeSet`.
     */
    public constructor()

    override fun destroy()
    override fun close()

    
    /**
     * Get the change `Descriptor`
     */
    public override fun `changeDescriptor`(): Descriptor?
    
    /**
     * Get the receiving `Descriptor`.
     */
    public override fun `descriptor`(): Descriptor?
    
    /**
     * Get the changes to the indexer.
     */
    public override fun `indexerChangeset`(): IndexerChangeSet
    
    /**
     * Get the changes to the local chain.
     */
    public override fun `localchainChangeset`(): LocalChainChangeSet
    
    /**
     * Get the `Network`
     */
    public override fun `network`(): Network?
    
    /**
     * Get the changes to the transaction graph.
     */
    public override fun `txGraphChangeset`(): TxGraphChangeSet
    

    public companion object {
        
        public fun `fromAggregate`(`descriptor`: Descriptor?, `changeDescriptor`: Descriptor?, `network`: Network?, `localChain`: LocalChainChangeSet, `txGraph`: TxGraphChangeSet, `indexer`: IndexerChangeSet): ChangeSet
        
        public fun `fromDescriptorAndNetwork`(`descriptor`: Descriptor?, `changeDescriptor`: Descriptor?, `network`: Network?): ChangeSet
        
        /**
         * Start a wallet `ChangeSet` from indexer changes.
         */
        public fun `fromIndexerChangeset`(`indexerChanges`: IndexerChangeSet): ChangeSet
        
        /**
         * Start a wallet `ChangeSet` from local chain changes.
         */
        public fun `fromLocalChainChanges`(`localChainChanges`: LocalChainChangeSet): ChangeSet
        
        /**
         * Build a `ChangeSet` by merging together two `ChangeSet`.
         */
        public fun `fromMerge`(`left`: ChangeSet, `right`: ChangeSet): ChangeSet
        
        /**
         * Start a wallet `ChangeSet` from transaction graph changes.
         */
        public fun `fromTxGraphChangeset`(`txGraphChangeset`: TxGraphChangeSet): ChangeSet
        
    }
    
}




/**
 * A BIP-32 derivation path.
 */
public interface DerivationPathInterface {
    
    /**
     * Returns `true` if the derivation path is empty
     */
    public fun `isEmpty`(): kotlin.Boolean
    
    /**
     * Returns whether derivation path represents master key (i.e. it's length
     * is empty). True for `m` path.
     */
    public fun `isMaster`(): kotlin.Boolean
    
    /**
     * Returns length of the derivation path
     */
    public fun `len`(): kotlin.ULong
    
    public companion object
}


/**
 * A BIP-32 derivation path.
 */
public expect open class DerivationPath: Disposable, DerivationPathInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Parse a string as a BIP-32 derivation path.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Parse a string as a BIP-32 derivation path.
     */
    public constructor(`path`: kotlin.String)

    override fun destroy()
    override fun close()

    
    /**
     * Returns `true` if the derivation path is empty
     */
    public override fun `isEmpty`(): kotlin.Boolean
    
    /**
     * Returns whether derivation path represents master key (i.e. it's length
     * is empty). True for `m` path.
     */
    public override fun `isMaster`(): kotlin.Boolean
    
    /**
     * Returns length of the derivation path
     */
    public override fun `len`(): kotlin.ULong
    
    override fun toString(): String
    

    public companion object {
        
        /**
         * Returns derivation path for a master key (i.e. empty derivation path)
         */
        public fun `master`(): DerivationPath
        
    }
    
}




/**
 * An expression of how to derive output scripts: https://github.com/bitcoin/bitcoin/blob/master/doc/descriptors.md
 */
public interface DescriptorInterface {
    
    public fun `descType`(): DescriptorType
    
    /**
     * A unique identifier for the descriptor.
     */
    public fun `descriptorId`(): DescriptorId
    
    /**
     * Does this descriptor contain paths: https://github.com/bitcoin/bips/blob/master/bip-0389.mediawiki
     */
    public fun `isMultipath`(): kotlin.Boolean
    
    /**
     * Computes an upper bound on the difference between a non-satisfied `TxIn`'s
     * `segwit_weight` and a satisfied `TxIn`'s `segwit_weight`.
     */
    @Throws(DescriptorException::class)
    public fun `maxWeightToSatisfy`(): kotlin.ULong
    
    /**
     * Return descriptors for all valid paths.
     */
    @Throws(MiniscriptException::class)
    public fun `toSingleDescriptors`(): List<Descriptor>
    
    /**
     * Dangerously convert the descriptor to a string.
     */
    public fun `toStringWithSecret`(): kotlin.String
    
    public companion object
}


/**
 * An expression of how to derive output scripts: https://github.com/bitcoin/bitcoin/blob/master/doc/descriptors.md
 */
public expect open class Descriptor: Disposable, DescriptorInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Parse a string as a descriptor for the given network.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Parse a string as a descriptor for the given network.
     */
    public constructor(`descriptor`: kotlin.String, `network`: Network)

    override fun destroy()
    override fun close()

    
    public override fun `descType`(): DescriptorType
    
    /**
     * A unique identifier for the descriptor.
     */
    public override fun `descriptorId`(): DescriptorId
    
    /**
     * Does this descriptor contain paths: https://github.com/bitcoin/bips/blob/master/bip-0389.mediawiki
     */
    public override fun `isMultipath`(): kotlin.Boolean
    
    /**
     * Computes an upper bound on the difference between a non-satisfied `TxIn`'s
     * `segwit_weight` and a satisfied `TxIn`'s `segwit_weight`.
     */
    @Throws(DescriptorException::class)
    public override fun `maxWeightToSatisfy`(): kotlin.ULong
    
    /**
     * Return descriptors for all valid paths.
     */
    @Throws(MiniscriptException::class)
    public override fun `toSingleDescriptors`(): List<Descriptor>
    
    /**
     * Dangerously convert the descriptor to a string.
     */
    public override fun `toStringWithSecret`(): kotlin.String
    
    override fun toString(): String
    

    public companion object {
        
        /**
         * Multi-account hierarchy descriptor: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
         */
        public fun `newBip44`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * Multi-account hierarchy descriptor: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
         */
        @Throws(DescriptorException::class)
        public fun `newBip44Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * P2SH nested P2WSH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki
         */
        public fun `newBip49`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * P2SH nested P2WSH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki
         */
        @Throws(DescriptorException::class)
        public fun `newBip49Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * Pay to witness PKH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
         */
        public fun `newBip84`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * Pay to witness PKH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
         */
        @Throws(DescriptorException::class)
        public fun `newBip84Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * Single key P2TR descriptor: https://github.com/bitcoin/bips/blob/master/bip-0086.mediawiki
         */
        public fun `newBip86`(`secretKey`: DescriptorSecretKey, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
        /**
         * Single key P2TR descriptor: https://github.com/bitcoin/bips/blob/master/bip-0086.mediawiki
         */
        @Throws(DescriptorException::class)
        public fun `newBip86Public`(`publicKey`: DescriptorPublicKey, `fingerprint`: kotlin.String, `keychainKind`: KeychainKind, `network`: Network): Descriptor
        
    }
    
}




/**
 * A collision-proof unique identifier for a descriptor.
 */
public interface DescriptorIdInterface {
    
    /**
     * Serialize this type into a 32 byte array.
     */
    public fun `serialize`(): kotlin.ByteArray
    
    public companion object
}


/**
 * A collision-proof unique identifier for a descriptor.
 */
public expect open class DescriptorId: Disposable, DescriptorIdInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public override fun `serialize`(): kotlin.ByteArray
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    
    override fun hashCode(): Int

    public companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public fun `fromBytes`(`bytes`: kotlin.ByteArray): DescriptorId
        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public fun `fromString`(`hex`: kotlin.String): DescriptorId
        
    }
    
}




/**
 * A descriptor public key.
 */
public interface DescriptorPublicKeyInterface {
    
    /**
     * Derive the descriptor public key at the given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public fun `derive`(`path`: DerivationPath): DescriptorPublicKey
    
    /**
     * Extend the descriptor public key by the given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public fun `extend`(`path`: DerivationPath): DescriptorPublicKey
    
    /**
     * Whether or not this key has multiple derivation paths.
     */
    public fun `isMultipath`(): kotlin.Boolean
    
    /**
     * The fingerprint of the master key associated with this key, `0x00000000` if none.
     */
    public fun `masterFingerprint`(): kotlin.String
    
    public companion object
}


/**
 * A descriptor public key.
 */
public expect open class DescriptorPublicKey: Disposable, DescriptorPublicKeyInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Derive the descriptor public key at the given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public override fun `derive`(`path`: DerivationPath): DescriptorPublicKey
    
    /**
     * Extend the descriptor public key by the given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public override fun `extend`(`path`: DerivationPath): DescriptorPublicKey
    
    /**
     * Whether or not this key has multiple derivation paths.
     */
    public override fun `isMultipath`(): kotlin.Boolean
    
    /**
     * The fingerprint of the master key associated with this key, `0x00000000` if none.
     */
    public override fun `masterFingerprint`(): kotlin.String
    
    override fun toString(): String
    

    public companion object {
        
        /**
         * Attempt to parse a string as a descriptor public key.
         */
        @Throws(DescriptorKeyException::class)
        public fun `fromString`(`publicKey`: kotlin.String): DescriptorPublicKey
        
    }
    
}




/**
 * A descriptor containing secret data.
 */
public interface DescriptorSecretKeyInterface {
    
    /**
     * Return the descriptor public key corresponding to this secret.
     */
    public fun `asPublic`(): DescriptorPublicKey
    
    /**
     * Derive a descriptor secret key at a given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public fun `derive`(`path`: DerivationPath): DescriptorSecretKey
    
    /**
     * Extend the descriptor secret key by the derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public fun `extend`(`path`: DerivationPath): DescriptorSecretKey
    
    /**
     * Return the bytes of this descriptor secret key.
     */
    public fun `secretBytes`(): kotlin.ByteArray
    
    public companion object
}


/**
 * A descriptor containing secret data.
 */
public expect open class DescriptorSecretKey: Disposable, DescriptorSecretKeyInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Construct a secret descriptor using a mnemonic.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Construct a secret descriptor using a mnemonic.
     */
    public constructor(`network`: Network, `mnemonic`: Mnemonic, `password`: kotlin.String?)

    override fun destroy()
    override fun close()

    
    /**
     * Return the descriptor public key corresponding to this secret.
     */
    public override fun `asPublic`(): DescriptorPublicKey
    
    /**
     * Derive a descriptor secret key at a given derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public override fun `derive`(`path`: DerivationPath): DescriptorSecretKey
    
    /**
     * Extend the descriptor secret key by the derivation path.
     */
    @Throws(DescriptorKeyException::class)
    public override fun `extend`(`path`: DerivationPath): DescriptorSecretKey
    
    /**
     * Return the bytes of this descriptor secret key.
     */
    public override fun `secretBytes`(): kotlin.ByteArray
    
    override fun toString(): String
    

    public companion object {
        
        /**
         * Attempt to parse a string as a descriptor secret key.
         */
        @Throws(DescriptorKeyException::class)
        public fun `fromString`(`privateKey`: kotlin.String): DescriptorSecretKey
        
    }
    
}




/**
 * Wrapper around an electrum_client::ElectrumApi which includes an internal in-memory transaction
 * cache to avoid re-fetching already downloaded transactions.
 */
public interface ElectrumClientInterface {
    
    /**
     * Subscribes to notifications for new block headers, by sending a blockchain.headers.subscribe call.
     */
    @Throws(ElectrumException::class)
    public fun `blockHeadersSubscribe`(): HeaderNotification
    
    /**
     * Estimates the fee required in bitcoin per kilobyte to confirm a transaction in `number` blocks.
     */
    @Throws(ElectrumException::class)
    public fun `estimateFee`(`number`: kotlin.ULong): kotlin.Double
    
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
    public fun `fullScan`(`request`: FullScanRequest, `stopGap`: kotlin.ULong, `batchSize`: kotlin.ULong, `fetchPrevTxouts`: kotlin.Boolean): Update
    
    /**
     * Pings the server.
     */
    @Throws(ElectrumException::class)
    public fun `ping`()
    
    /**
     * Returns the capabilities of the server.
     */
    @Throws(ElectrumException::class)
    public fun `serverFeatures`(): ServerFeaturesRes
    
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
    public fun `sync`(`request`: SyncRequest, `batchSize`: kotlin.ULong, `fetchPrevTxouts`: kotlin.Boolean): Update
    
    /**
     * Broadcasts a transaction to the network.
     */
    @Throws(ElectrumException::class)
    public fun `transactionBroadcast`(`tx`: Transaction): Txid
    
    public companion object
}


/**
 * Wrapper around an electrum_client::ElectrumApi which includes an internal in-memory transaction
 * cache to avoid re-fetching already downloaded transactions.
 */
public expect open class ElectrumClient: Disposable, ElectrumClientInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Creates a new bdk client from a electrum_client::ElectrumApi
     * Optional: Set the proxy of the builder
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Creates a new bdk client from a electrum_client::ElectrumApi
     * Optional: Set the proxy of the builder
     */
    public constructor(`url`: kotlin.String, `socks5`: kotlin.String? = null)

    override fun destroy()
    override fun close()

    
    /**
     * Subscribes to notifications for new block headers, by sending a blockchain.headers.subscribe call.
     */
    @Throws(ElectrumException::class)
    public override fun `blockHeadersSubscribe`(): HeaderNotification
    
    /**
     * Estimates the fee required in bitcoin per kilobyte to confirm a transaction in `number` blocks.
     */
    @Throws(ElectrumException::class)
    public override fun `estimateFee`(`number`: kotlin.ULong): kotlin.Double
    
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
    public override fun `fullScan`(`request`: FullScanRequest, `stopGap`: kotlin.ULong, `batchSize`: kotlin.ULong, `fetchPrevTxouts`: kotlin.Boolean): Update
    
    /**
     * Pings the server.
     */
    @Throws(ElectrumException::class)
    public override fun `ping`()
    
    /**
     * Returns the capabilities of the server.
     */
    @Throws(ElectrumException::class)
    public override fun `serverFeatures`(): ServerFeaturesRes
    
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
    public override fun `sync`(`request`: SyncRequest, `batchSize`: kotlin.ULong, `fetchPrevTxouts`: kotlin.Boolean): Update
    
    /**
     * Broadcasts a transaction to the network.
     */
    @Throws(ElectrumException::class)
    public override fun `transactionBroadcast`(`tx`: Transaction): Txid
    

    
    public companion object
}




/**
 * Wrapper around an esplora_client::BlockingClient which includes an internal in-memory transaction
 * cache to avoid re-fetching already downloaded transactions.
 */
public interface EsploraClientInterface {
    
    /**
     * Broadcast a [`Transaction`] to Esplora.
     */
    @Throws(EsploraException::class)
    public fun `broadcast`(`transaction`: Transaction)
    
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
    public fun `fullScan`(`request`: FullScanRequest, `stopGap`: kotlin.ULong, `parallelRequests`: kotlin.ULong): Update
    
    /**
     * Get the [`BlockHash`] of a specific block height.
     */
    @Throws(EsploraException::class)
    public fun `getBlockHash`(`blockHeight`: kotlin.UInt): BlockHash
    
    /**
     * Get a map where the key is the confirmation target (in number of
     * blocks) and the value is the estimated feerate (in sat/vB).
     */
    @Throws(EsploraException::class)
    public fun `getFeeEstimates`(): Map<kotlin.UShort, kotlin.Double>
    
    /**
     * Get the height of the current blockchain tip.
     */
    @Throws(EsploraException::class)
    public fun `getHeight`(): kotlin.UInt
    
    /**
     * Get a [`Transaction`] option given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public fun `getTx`(`txid`: Txid): Transaction?
    
    /**
     * Get transaction info given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public fun `getTxInfo`(`txid`: Txid): Tx?
    
    /**
     * Get the status of a [`Transaction`] given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public fun `getTxStatus`(`txid`: Txid): TxStatus
    
    /**
     * Sync a set of scripts, txids, and/or outpoints against Esplora.
     *
     * `request` provides the data required to perform a script-pubkey-based sync (see
     * [`SyncRequest`]). `parallel_requests` specifies the maximum number of HTTP requests to make
     * in parallel.
     */
    @Throws(EsploraException::class)
    public fun `sync`(`request`: SyncRequest, `parallelRequests`: kotlin.ULong): Update
    
    public companion object
}


/**
 * Wrapper around an esplora_client::BlockingClient which includes an internal in-memory transaction
 * cache to avoid re-fetching already downloaded transactions.
 */
public expect open class EsploraClient: Disposable, EsploraClientInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Creates a new bdk client from an esplora_client::BlockingClient.
     * Optional: Set the proxy of the builder.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Creates a new bdk client from an esplora_client::BlockingClient.
     * Optional: Set the proxy of the builder.
     */
    public constructor(`url`: kotlin.String, `proxy`: kotlin.String? = null)

    override fun destroy()
    override fun close()

    
    /**
     * Broadcast a [`Transaction`] to Esplora.
     */
    @Throws(EsploraException::class)
    public override fun `broadcast`(`transaction`: Transaction)
    
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
    public override fun `fullScan`(`request`: FullScanRequest, `stopGap`: kotlin.ULong, `parallelRequests`: kotlin.ULong): Update
    
    /**
     * Get the [`BlockHash`] of a specific block height.
     */
    @Throws(EsploraException::class)
    public override fun `getBlockHash`(`blockHeight`: kotlin.UInt): BlockHash
    
    /**
     * Get a map where the key is the confirmation target (in number of
     * blocks) and the value is the estimated feerate (in sat/vB).
     */
    @Throws(EsploraException::class)
    public override fun `getFeeEstimates`(): Map<kotlin.UShort, kotlin.Double>
    
    /**
     * Get the height of the current blockchain tip.
     */
    @Throws(EsploraException::class)
    public override fun `getHeight`(): kotlin.UInt
    
    /**
     * Get a [`Transaction`] option given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public override fun `getTx`(`txid`: Txid): Transaction?
    
    /**
     * Get transaction info given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public override fun `getTxInfo`(`txid`: Txid): Tx?
    
    /**
     * Get the status of a [`Transaction`] given its [`Txid`].
     */
    @Throws(EsploraException::class)
    public override fun `getTxStatus`(`txid`: Txid): TxStatus
    
    /**
     * Sync a set of scripts, txids, and/or outpoints against Esplora.
     *
     * `request` provides the data required to perform a script-pubkey-based sync (see
     * [`SyncRequest`]). `parallel_requests` specifies the maximum number of HTTP requests to make
     * in parallel.
     */
    @Throws(EsploraException::class)
    public override fun `sync`(`request`: SyncRequest, `parallelRequests`: kotlin.ULong): Update
    

    
    public companion object
}




/**
 * Represents fee rate.
 *
 * This is an integer type representing fee rate in sat/kwu. It provides protection against mixing
 * up the types as well as basic formatting features.
 */
public interface FeeRateInterface {
    
    public fun `toSatPerKwu`(): kotlin.ULong
    
    public fun `toSatPerVbCeil`(): kotlin.ULong
    
    public fun `toSatPerVbFloor`(): kotlin.ULong
    
    public companion object
}


/**
 * Represents fee rate.
 *
 * This is an integer type representing fee rate in sat/kwu. It provides protection against mixing
 * up the types as well as basic formatting features.
 */
public expect open class FeeRate: Disposable, FeeRateInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    public override fun `toSatPerKwu`(): kotlin.ULong
    
    public override fun `toSatPerVbCeil`(): kotlin.ULong
    
    public override fun `toSatPerVbFloor`(): kotlin.ULong
    

    public companion object {
        
        public fun `fromSatPerKwu`(`satKwu`: kotlin.ULong): FeeRate
        
        @Throws(FeeRateException::class)
        public fun `fromSatPerVb`(`satVb`: kotlin.ULong): FeeRate
        
    }
    
}




public interface FullScanRequestInterface {
    
    public companion object
}


public expect open class FullScanRequest: Disposable, FullScanRequestInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    

    
    public companion object
}




public interface FullScanRequestBuilderInterface {
    
    @Throws(RequestBuilderException::class)
    public fun `build`(): FullScanRequest
    
    @Throws(RequestBuilderException::class)
    public fun `inspectSpksForAllKeychains`(`inspector`: FullScanScriptInspector): FullScanRequestBuilder
    
    public companion object
}


public expect open class FullScanRequestBuilder: Disposable, FullScanRequestBuilderInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    @Throws(RequestBuilderException::class)
    public override fun `build`(): FullScanRequest
    
    @Throws(RequestBuilderException::class)
    public override fun `inspectSpksForAllKeychains`(`inspector`: FullScanScriptInspector): FullScanRequestBuilder
    

    
    public companion object
}




public interface FullScanScriptInspector {
    
    public fun `inspect`(`keychain`: KeychainKind, `index`: kotlin.UInt, `script`: Script)
    
    public companion object
}


public expect open class FullScanScriptInspectorImpl: Disposable, FullScanScriptInspector {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    public override fun `inspect`(`keychain`: KeychainKind, `index`: kotlin.UInt, `script`: Script)
    

    
    public companion object
}




/**
 * An [`OutPoint`] used as a key in a hash map.
 *
 * Due to limitations in generating the foreign language bindings, we cannot use [`OutPoint`] as a
 * key for hash maps.
 */
public interface HashableOutPointInterface {
    
    /**
     * Get the internal [`OutPoint`]
     */
    public fun `outpoint`(): OutPoint
    
    public companion object
}


/**
 * An [`OutPoint`] used as a key in a hash map.
 *
 * Due to limitations in generating the foreign language bindings, we cannot use [`OutPoint`] as a
 * key for hash maps.
 */
public expect open class HashableOutPoint: Disposable, HashableOutPointInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Create a key for a key-value store from an [`OutPoint`]
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Create a key for a key-value store from an [`OutPoint`]
     */
    public constructor(`outpoint`: OutPoint)

    override fun destroy()
    override fun close()

    
    /**
     * Get the internal [`OutPoint`]
     */
    public override fun `outpoint`(): OutPoint
    
    
    override fun equals(other: Any?): Boolean
    
    override fun hashCode(): Int

    
    public companion object
}




/**
 * An IP address to connect to over TCP.
 */
public interface IpAddressInterface {
    
    public companion object
}


/**
 * An IP address to connect to over TCP.
 */
public expect open class IpAddress: Disposable, IpAddressInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    

    public companion object {
        
        /**
         * Build an IPv4 address.
         */
        public fun `fromIpv4`(`q1`: kotlin.UByte, `q2`: kotlin.UByte, `q3`: kotlin.UByte, `q4`: kotlin.UByte): IpAddress
        
        /**
         * Build an IPv6 address.
         */
        public fun `fromIpv6`(`a`: kotlin.UShort, `b`: kotlin.UShort, `c`: kotlin.UShort, `d`: kotlin.UShort, `e`: kotlin.UShort, `f`: kotlin.UShort, `g`: kotlin.UShort, `h`: kotlin.UShort): IpAddress
        
    }
    
}




/**
 * A mnemonic seed phrase to recover a BIP-32 wallet.
 */
public interface MnemonicInterface {
    
    public companion object
}


/**
 * A mnemonic seed phrase to recover a BIP-32 wallet.
 */
public expect open class Mnemonic: Disposable, MnemonicInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Generate a mnemonic given a word count.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Generate a mnemonic given a word count.
     */
    public constructor(`wordCount`: WordCount)

    override fun destroy()
    override fun close()

    
    override fun toString(): String
    

    public companion object {
        
        /**
         * Construct a mnemonic given an array of bytes. Note that using weak entropy will result in a loss
         * of funds. To ensure the entropy is generated properly, read about your operating
         * system specific ways to generate secure random numbers.
         */
        @Throws(Bip39Exception::class)
        public fun `fromEntropy`(`entropy`: kotlin.ByteArray): Mnemonic
        
        /**
         * Parse a string as a mnemonic seed phrase.
         */
        @Throws(Bip39Exception::class)
        public fun `fromString`(`mnemonic`: kotlin.String): Mnemonic
        
    }
    
}




/**
 * Definition of a wallet persistence implementation.
 */
public interface Persistence {
    
    /**
     * Initialize the total aggregate `ChangeSet` for the underlying wallet.
     */
    @Throws(PersistenceException::class)
    public fun `initialize`(): ChangeSet
    
    /**
     * Persist a `ChangeSet` to the total aggregate changeset of the wallet.
     */
    @Throws(PersistenceException::class)
    public fun `persist`(`changeset`: ChangeSet)
    
    public companion object
}


/**
 * Definition of a wallet persistence implementation.
 */
public expect open class PersistenceImpl: Disposable, Persistence {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Initialize the total aggregate `ChangeSet` for the underlying wallet.
     */
    @Throws(PersistenceException::class)
    public override fun `initialize`(): ChangeSet
    
    /**
     * Persist a `ChangeSet` to the total aggregate changeset of the wallet.
     */
    @Throws(PersistenceException::class)
    public override fun `persist`(`changeset`: ChangeSet)
    

    
    public companion object
}




/**
 * Wallet backend implementations.
 */
public interface PersisterInterface {
    
    public companion object
}


/**
 * Wallet backend implementations.
 */
public expect open class Persister: Disposable, PersisterInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    

    public companion object {
        
        /**
         * Use a native persistence layer.
         */
        public fun `custom`(`persistence`: Persistence): Persister
        
        /**
         * Create a new connection in memory.
         */
        @Throws(PersistenceException::class)
        public fun `newInMemory`(): Persister
        
        /**
         * Create a new Sqlite connection at the specified file path.
         */
        @Throws(PersistenceException::class)
        public fun `newSqlite`(`path`: kotlin.String): Persister
        
    }
    
}




/**
 * Descriptor spending policy
 */
public interface PolicyInterface {
    
    public fun `asString`(): kotlin.String
    
    public fun `contribution`(): Satisfaction
    
    public fun `id`(): kotlin.String
    
    public fun `item`(): SatisfiableItem
    
    public fun `requiresPath`(): kotlin.Boolean
    
    public fun `satisfaction`(): Satisfaction
    
    public companion object
}


/**
 * Descriptor spending policy
 */
public expect open class Policy: Disposable, PolicyInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    public override fun `asString`(): kotlin.String
    
    public override fun `contribution`(): Satisfaction
    
    public override fun `id`(): kotlin.String
    
    public override fun `item`(): SatisfiableItem
    
    public override fun `requiresPath`(): kotlin.Boolean
    
    public override fun `satisfaction`(): Satisfaction
    

    
    public companion object
}




/**
 * A Partially Signed Transaction.
 */
public interface PsbtInterface {
    
    /**
     * Combines this `Psbt` with `other` PSBT as described by BIP 174.
     *
     * In accordance with BIP 174 this function is commutative i.e., `A.combine(B) == B.combine(A)`
     */
    @Throws(PsbtException::class)
    public fun `combine`(`other`: Psbt): Psbt
    
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
    public fun `extractTx`(): Transaction
    
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
    public fun `fee`(): kotlin.ULong
    
    /**
     * Finalizes the current PSBT and produces a result indicating
     *
     * whether the finalization was successful or not.
     */
    public fun `finalize`(): FinalizedPsbtResult
    
    /**
     * The corresponding key-value map for each input in the unsigned transaction.
     */
    public fun `input`(): List<Input>
    
    /**
     * Serializes the PSBT into a JSON string representation.
     */
    public fun `jsonSerialize`(): kotlin.String
    
    /**
     * Serialize the PSBT into a base64-encoded string.
     */
    public fun `serialize`(): kotlin.String
    
    /**
     * Returns the spending utxo for this PSBT's input at `input_index`.
     */
    public fun `spendUtxo`(`inputIndex`: kotlin.ULong): kotlin.String
    
    /**
     * Write the `Psbt` to a file. Note that the file must not yet exist.
     */
    @Throws(PsbtException::class)
    public fun `writeToFile`(`path`: kotlin.String)
    
    public companion object
}


/**
 * A Partially Signed Transaction.
 */
public expect open class Psbt: Disposable, PsbtInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Creates a new `Psbt` instance from a base64-encoded string.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Creates a new `Psbt` instance from a base64-encoded string.
     */
    public constructor(`psbtBase64`: kotlin.String)

    override fun destroy()
    override fun close()

    
    /**
     * Combines this `Psbt` with `other` PSBT as described by BIP 174.
     *
     * In accordance with BIP 174 this function is commutative i.e., `A.combine(B) == B.combine(A)`
     */
    @Throws(PsbtException::class)
    public override fun `combine`(`other`: Psbt): Psbt
    
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
    public override fun `extractTx`(): Transaction
    
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
    public override fun `fee`(): kotlin.ULong
    
    /**
     * Finalizes the current PSBT and produces a result indicating
     *
     * whether the finalization was successful or not.
     */
    public override fun `finalize`(): FinalizedPsbtResult
    
    /**
     * The corresponding key-value map for each input in the unsigned transaction.
     */
    public override fun `input`(): List<Input>
    
    /**
     * Serializes the PSBT into a JSON string representation.
     */
    public override fun `jsonSerialize`(): kotlin.String
    
    /**
     * Serialize the PSBT into a base64-encoded string.
     */
    public override fun `serialize`(): kotlin.String
    
    /**
     * Returns the spending utxo for this PSBT's input at `input_index`.
     */
    public override fun `spendUtxo`(`inputIndex`: kotlin.ULong): kotlin.String
    
    /**
     * Write the `Psbt` to a file. Note that the file must not yet exist.
     */
    @Throws(PsbtException::class)
    public override fun `writeToFile`(`path`: kotlin.String)
    

    public companion object {
        
        /**
         * Create a new `Psbt` from a `.psbt` file.
         */
        @Throws(PsbtException::class)
        public fun `fromFile`(`path`: kotlin.String): Psbt
        
        /**
         * Creates a PSBT from an unsigned transaction.
         *
         * # Errors
         *
         * If transactions is not unsigned.
         */
        @Throws(PsbtException::class)
        public fun `fromUnsignedTx`(`tx`: Transaction): Psbt
        
    }
    
}




/**
 * A bitcoin script: https://en.bitcoin.it/wiki/Script
 */
public interface ScriptInterface {
    
    /**
     * Convert a script into an array of bytes.
     */
    public fun `toBytes`(): kotlin.ByteArray
    
    public companion object
}


/**
 * A bitcoin script: https://en.bitcoin.it/wiki/Script
 */
public expect open class Script: Disposable, ScriptInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Interpret an array of bytes as a bitcoin script.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Interpret an array of bytes as a bitcoin script.
     */
    public constructor(`rawOutputScript`: kotlin.ByteArray)

    override fun destroy()
    override fun close()

    
    /**
     * Convert a script into an array of bytes.
     */
    public override fun `toBytes`(): kotlin.ByteArray
    
    override fun toString(): String
    

    
    public companion object
}




public interface SyncRequestInterface {
    
    public companion object
}


public expect open class SyncRequest: Disposable, SyncRequestInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    

    
    public companion object
}




public interface SyncRequestBuilderInterface {
    
    @Throws(RequestBuilderException::class)
    public fun `build`(): SyncRequest
    
    @Throws(RequestBuilderException::class)
    public fun `inspectSpks`(`inspector`: SyncScriptInspector): SyncRequestBuilder
    
    public companion object
}


public expect open class SyncRequestBuilder: Disposable, SyncRequestBuilderInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    @Throws(RequestBuilderException::class)
    public override fun `build`(): SyncRequest
    
    @Throws(RequestBuilderException::class)
    public override fun `inspectSpks`(`inspector`: SyncScriptInspector): SyncRequestBuilder
    

    
    public companion object
}




public interface SyncScriptInspector {
    
    public fun `inspect`(`script`: Script, `total`: kotlin.ULong)
    
    public companion object
}


public expect open class SyncScriptInspectorImpl: Disposable, SyncScriptInspector {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    public override fun `inspect`(`script`: Script, `total`: kotlin.ULong)
    

    
    public companion object
}




/**
 * Bitcoin transaction.
 * An authenticated movement of coins.
 */
public interface TransactionInterface {
    
    /**
     * Computes the Txid.
     * Hashes the transaction excluding the segwit data (i.e. the marker, flag bytes, and the witness fields themselves).
     */
    public fun `computeTxid`(): Txid
    
    /**
     * Compute the Wtxid, which includes the witness in the transaction hash.
     */
    public fun `computeWtxid`(): Wtxid
    
    /**
     * List of transaction inputs.
     */
    public fun `input`(): List<TxIn>
    
    /**
     * Checks if this is a coinbase transaction.
     * The first transaction in the block distributes the mining reward and is called the coinbase transaction.
     * It is impossible to check if the transaction is first in the block, so this function checks the structure
     * of the transaction instead - the previous output must be all-zeros (creates satoshis “out of thin air”).
     */
    public fun `isCoinbase`(): kotlin.Boolean
    
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
    public fun `isExplicitlyRbf`(): kotlin.Boolean
    
    /**
     * Returns `true` if this transactions nLockTime is enabled ([BIP-65]).
     *
     * [BIP-65]: https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
     */
    public fun `isLockTimeEnabled`(): kotlin.Boolean
    
    /**
     * Block height or timestamp. Transaction cannot be included in a block until this height/time.
     *
     * /// ### Relevant BIPs
     *
     * * [BIP-65 OP_CHECKLOCKTIMEVERIFY](https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki)
     * * [BIP-113 Median time-past as endpoint for lock-time calculations](https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki)
     */
    public fun `lockTime`(): kotlin.UInt
    
    /**
     * List of transaction outputs.
     */
    public fun `output`(): List<TxOut>
    
    /**
     * Serialize transaction into consensus-valid format. See https://docs.rs/bitcoin/latest/bitcoin/struct.Transaction.html#serialization-notes for more notes on transaction serialization.
     */
    public fun `serialize`(): kotlin.ByteArray
    
    /**
     * Returns the total transaction size
     *
     * Total transaction size is the transaction size in bytes serialized as described in BIP144,
     * including base data and witness data.
     */
    public fun `totalSize`(): kotlin.ULong
    
    /**
     * The protocol version, is currently expected to be 1 or 2 (BIP 68).
     */
    public fun `version`(): kotlin.Int
    
    /**
     * Returns the "virtual size" (vsize) of this transaction.
     *
     * Will be `ceil(weight / 4.0)`. Note this implements the virtual size as per [`BIP141`], which
     * is different to what is implemented in Bitcoin Core.
     * > Virtual transaction size is defined as Transaction weight / 4 (rounded up to the next integer).
     *
     * [`BIP141`]: https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki
     */
    public fun `vsize`(): kotlin.ULong
    
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
    public fun `weight`(): kotlin.ULong
    
    public companion object
}


/**
 * Bitcoin transaction.
 * An authenticated movement of coins.
 */
public expect open class Transaction: Disposable, TransactionInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Creates a new `Transaction` instance from serialized transaction bytes.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Creates a new `Transaction` instance from serialized transaction bytes.
     */
    public constructor(`transactionBytes`: kotlin.ByteArray)

    override fun destroy()
    override fun close()

    
    /**
     * Computes the Txid.
     * Hashes the transaction excluding the segwit data (i.e. the marker, flag bytes, and the witness fields themselves).
     */
    public override fun `computeTxid`(): Txid
    
    /**
     * Compute the Wtxid, which includes the witness in the transaction hash.
     */
    public override fun `computeWtxid`(): Wtxid
    
    /**
     * List of transaction inputs.
     */
    public override fun `input`(): List<TxIn>
    
    /**
     * Checks if this is a coinbase transaction.
     * The first transaction in the block distributes the mining reward and is called the coinbase transaction.
     * It is impossible to check if the transaction is first in the block, so this function checks the structure
     * of the transaction instead - the previous output must be all-zeros (creates satoshis “out of thin air”).
     */
    public override fun `isCoinbase`(): kotlin.Boolean
    
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
    public override fun `isExplicitlyRbf`(): kotlin.Boolean
    
    /**
     * Returns `true` if this transactions nLockTime is enabled ([BIP-65]).
     *
     * [BIP-65]: https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
     */
    public override fun `isLockTimeEnabled`(): kotlin.Boolean
    
    /**
     * Block height or timestamp. Transaction cannot be included in a block until this height/time.
     *
     * /// ### Relevant BIPs
     *
     * * [BIP-65 OP_CHECKLOCKTIMEVERIFY](https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki)
     * * [BIP-113 Median time-past as endpoint for lock-time calculations](https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki)
     */
    public override fun `lockTime`(): kotlin.UInt
    
    /**
     * List of transaction outputs.
     */
    public override fun `output`(): List<TxOut>
    
    /**
     * Serialize transaction into consensus-valid format. See https://docs.rs/bitcoin/latest/bitcoin/struct.Transaction.html#serialization-notes for more notes on transaction serialization.
     */
    public override fun `serialize`(): kotlin.ByteArray
    
    /**
     * Returns the total transaction size
     *
     * Total transaction size is the transaction size in bytes serialized as described in BIP144,
     * including base data and witness data.
     */
    public override fun `totalSize`(): kotlin.ULong
    
    /**
     * The protocol version, is currently expected to be 1 or 2 (BIP 68).
     */
    public override fun `version`(): kotlin.Int
    
    /**
     * Returns the "virtual size" (vsize) of this transaction.
     *
     * Will be `ceil(weight / 4.0)`. Note this implements the virtual size as per [`BIP141`], which
     * is different to what is implemented in Bitcoin Core.
     * > Virtual transaction size is defined as Transaction weight / 4 (rounded up to the next integer).
     *
     * [`BIP141`]: https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki
     */
    public override fun `vsize`(): kotlin.ULong
    
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
    public override fun `weight`(): kotlin.ULong
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    

    
    public companion object
}




/**
 * A `TxBuilder` is created by calling `build_tx` on a wallet. After assigning it, you set options on it until finally
 * calling `finish` to consume the builder and generate the transaction.
 */
public interface TxBuilderInterface {
    
    /**
     * Add data as an output using `OP_RETURN`.
     */
    public fun `addData`(`data`: kotlin.ByteArray): TxBuilder
    
    /**
     * Fill-in the `PSBT_GLOBAL_XPUB` field with the extended keys contained in both the external and internal
     * descriptors.
     *
     * This is useful for offline signers that take part to a multisig. Some hardware wallets like BitBox and ColdCard
     * are known to require this.
     */
    public fun `addGlobalXpubs`(): TxBuilder
    
    /**
     * Add a recipient to the internal list of recipients.
     */
    public fun `addRecipient`(`script`: Script, `amount`: Amount): TxBuilder
    
    /**
     * Add a utxo to the internal list of unspendable utxos.
     *
     * It’s important to note that the "must-be-spent" utxos added with `TxBuilder::add_utxo` have priority over this.
     */
    public fun `addUnspendable`(`unspendable`: OutPoint): TxBuilder
    
    /**
     * Add a utxo to the internal list of utxos that must be spent.
     *
     * These have priority over the "unspendable" utxos, meaning that if a utxo is present both in the "utxos" and the
     * "unspendable" list, it will be spent.
     */
    public fun `addUtxo`(`outpoint`: OutPoint): TxBuilder
    
    /**
     * Add the list of outpoints to the internal list of UTXOs that must be spent.
     */
    public fun `addUtxos`(`outpoints`: List<OutPoint>): TxBuilder
    
    /**
     * Set whether or not the dust limit is checked.
     *
     * Note: by avoiding a dust limit check you may end up with a transaction that is non-standard.
     */
    public fun `allowDust`(`allowDust`: kotlin.Boolean): TxBuilder
    
    /**
     * Set a specific `ChangeSpendPolicy`. See `TxBuilder::do_not_spend_change` and `TxBuilder::only_spend_change` for
     * some shortcuts. This method assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public fun `changePolicy`(`changePolicy`: ChangeSpendPolicy): TxBuilder
    
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
    public fun `currentHeight`(`height`: kotlin.UInt): TxBuilder
    
    /**
     * Do not spend change outputs.
     *
     * This effectively adds all the change outputs to the "unspendable" list. See `TxBuilder::unspendable`. This method
     * assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public fun `doNotSpendChange`(): TxBuilder
    
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
    public fun `drainTo`(`script`: Script): TxBuilder
    
    /**
     * Spend all the available inputs. This respects filters like `TxBuilder::unspendable` and the change policy.
     */
    public fun `drainWallet`(): TxBuilder
    
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
    public fun `excludeBelowConfirmations`(`minConfirms`: kotlin.UInt): TxBuilder
    
    /**
     * Exclude outpoints whose enclosing transaction is unconfirmed.
     * This is a shorthand for exclude_below_confirmations(1).
     */
    public fun `excludeUnconfirmed`(): TxBuilder
    
    /**
     * Set an absolute fee The `fee_absolute` method refers to the absolute transaction fee in `Amount`. If anyone sets
     * both the `fee_absolute` method and the `fee_rate` method, the `FeePolicy` enum will be set by whichever method was
     * called last, as the `FeeRate` and `FeeAmount` are mutually exclusive.
     *
     * Note that this is really a minimum absolute fee – it’s possible to overshoot it slightly since adding a change output to drain the remaining excess might not be viable.
     */
    public fun `feeAbsolute`(`feeAmount`: Amount): TxBuilder
    
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
    public fun `feeRate`(`feeRate`: FeeRate): TxBuilder
    
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
    public fun `finish`(`wallet`: Wallet): Psbt
    
    /**
     * Only spend utxos added by `TxBuilder::add_utxo`.
     *
     * The wallet will not add additional utxos to the transaction even if they are needed to make the transaction valid.
     */
    public fun `manuallySelectedOnly`(): TxBuilder
    
    /**
     * Use a specific nLockTime while creating the transaction.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "after" (`OP_CLTV`) operator.
     */
    public fun `nlocktime`(`locktime`: LockTime): TxBuilder
    
    /**
     * Only spend change outputs.
     *
     * This effectively adds all the non-change outputs to the "unspendable" list. See `TxBuilder::unspendable`. This
     * method assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public fun `onlySpendChange`(): TxBuilder
    
    /**
     * The TxBuilder::policy_path is a complex API. See the Rust docs for complete       information: https://docs.rs/bdk_wallet/latest/bdk_wallet/struct.TxBuilder.html#method.policy_path
     */
    public fun `policyPath`(`policyPath`: Map<kotlin.String, List<kotlin.ULong>>, `keychain`: KeychainKind): TxBuilder
    
    /**
     * Set an exact `nSequence` value.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "older" (`OP_CSV`) operator and the given
     * `nsequence` is lower than the CSV value.
     */
    public fun `setExactSequence`(`nsequence`: kotlin.UInt): TxBuilder
    
    /**
     * Replace the recipients already added with a new list of recipients.
     */
    public fun `setRecipients`(`recipients`: List<ScriptAmount>): TxBuilder
    
    /**
     * Replace the internal list of unspendable utxos with a new list.
     *
     * It’s important to note that the "must-be-spent" utxos added with `TxBuilder::add_utxo` have priority over these.
     */
    public fun `unspendable`(`unspendable`: List<OutPoint>): TxBuilder
    
    /**
     * Build a transaction with a specific version.
     *
     * The version should always be greater than 0 and greater than 1 if the wallet’s descriptors contain an "older"
     * (`OP_CSV`) operator.
     */
    public fun `version`(`version`: kotlin.Int): TxBuilder
    
    public companion object
}


/**
 * A `TxBuilder` is created by calling `build_tx` on a wallet. After assigning it, you set options on it until finally
 * calling `finish` to consume the builder and generate the transaction.
 */
public expect open class TxBuilder: Disposable, TxBuilderInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    public constructor(noPointer: NoPointer)

    
    public constructor()

    override fun destroy()
    override fun close()

    
    /**
     * Add data as an output using `OP_RETURN`.
     */
    public override fun `addData`(`data`: kotlin.ByteArray): TxBuilder
    
    /**
     * Fill-in the `PSBT_GLOBAL_XPUB` field with the extended keys contained in both the external and internal
     * descriptors.
     *
     * This is useful for offline signers that take part to a multisig. Some hardware wallets like BitBox and ColdCard
     * are known to require this.
     */
    public override fun `addGlobalXpubs`(): TxBuilder
    
    /**
     * Add a recipient to the internal list of recipients.
     */
    public override fun `addRecipient`(`script`: Script, `amount`: Amount): TxBuilder
    
    /**
     * Add a utxo to the internal list of unspendable utxos.
     *
     * It’s important to note that the "must-be-spent" utxos added with `TxBuilder::add_utxo` have priority over this.
     */
    public override fun `addUnspendable`(`unspendable`: OutPoint): TxBuilder
    
    /**
     * Add a utxo to the internal list of utxos that must be spent.
     *
     * These have priority over the "unspendable" utxos, meaning that if a utxo is present both in the "utxos" and the
     * "unspendable" list, it will be spent.
     */
    public override fun `addUtxo`(`outpoint`: OutPoint): TxBuilder
    
    /**
     * Add the list of outpoints to the internal list of UTXOs that must be spent.
     */
    public override fun `addUtxos`(`outpoints`: List<OutPoint>): TxBuilder
    
    /**
     * Set whether or not the dust limit is checked.
     *
     * Note: by avoiding a dust limit check you may end up with a transaction that is non-standard.
     */
    public override fun `allowDust`(`allowDust`: kotlin.Boolean): TxBuilder
    
    /**
     * Set a specific `ChangeSpendPolicy`. See `TxBuilder::do_not_spend_change` and `TxBuilder::only_spend_change` for
     * some shortcuts. This method assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public override fun `changePolicy`(`changePolicy`: ChangeSpendPolicy): TxBuilder
    
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
    public override fun `currentHeight`(`height`: kotlin.UInt): TxBuilder
    
    /**
     * Do not spend change outputs.
     *
     * This effectively adds all the change outputs to the "unspendable" list. See `TxBuilder::unspendable`. This method
     * assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public override fun `doNotSpendChange`(): TxBuilder
    
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
    public override fun `drainTo`(`script`: Script): TxBuilder
    
    /**
     * Spend all the available inputs. This respects filters like `TxBuilder::unspendable` and the change policy.
     */
    public override fun `drainWallet`(): TxBuilder
    
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
    public override fun `excludeBelowConfirmations`(`minConfirms`: kotlin.UInt): TxBuilder
    
    /**
     * Exclude outpoints whose enclosing transaction is unconfirmed.
     * This is a shorthand for exclude_below_confirmations(1).
     */
    public override fun `excludeUnconfirmed`(): TxBuilder
    
    /**
     * Set an absolute fee The `fee_absolute` method refers to the absolute transaction fee in `Amount`. If anyone sets
     * both the `fee_absolute` method and the `fee_rate` method, the `FeePolicy` enum will be set by whichever method was
     * called last, as the `FeeRate` and `FeeAmount` are mutually exclusive.
     *
     * Note that this is really a minimum absolute fee – it’s possible to overshoot it slightly since adding a change output to drain the remaining excess might not be viable.
     */
    public override fun `feeAbsolute`(`feeAmount`: Amount): TxBuilder
    
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
    public override fun `feeRate`(`feeRate`: FeeRate): TxBuilder
    
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
    public override fun `finish`(`wallet`: Wallet): Psbt
    
    /**
     * Only spend utxos added by `TxBuilder::add_utxo`.
     *
     * The wallet will not add additional utxos to the transaction even if they are needed to make the transaction valid.
     */
    public override fun `manuallySelectedOnly`(): TxBuilder
    
    /**
     * Use a specific nLockTime while creating the transaction.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "after" (`OP_CLTV`) operator.
     */
    public override fun `nlocktime`(`locktime`: LockTime): TxBuilder
    
    /**
     * Only spend change outputs.
     *
     * This effectively adds all the non-change outputs to the "unspendable" list. See `TxBuilder::unspendable`. This
     * method assumes the presence of an internal keychain, otherwise it has no effect.
     */
    public override fun `onlySpendChange`(): TxBuilder
    
    /**
     * The TxBuilder::policy_path is a complex API. See the Rust docs for complete       information: https://docs.rs/bdk_wallet/latest/bdk_wallet/struct.TxBuilder.html#method.policy_path
     */
    public override fun `policyPath`(`policyPath`: Map<kotlin.String, List<kotlin.ULong>>, `keychain`: KeychainKind): TxBuilder
    
    /**
     * Set an exact `nSequence` value.
     *
     * This can cause conflicts if the wallet’s descriptors contain an "older" (`OP_CSV`) operator and the given
     * `nsequence` is lower than the CSV value.
     */
    public override fun `setExactSequence`(`nsequence`: kotlin.UInt): TxBuilder
    
    /**
     * Replace the recipients already added with a new list of recipients.
     */
    public override fun `setRecipients`(`recipients`: List<ScriptAmount>): TxBuilder
    
    /**
     * Replace the internal list of unspendable utxos with a new list.
     *
     * It’s important to note that the "must-be-spent" utxos added with `TxBuilder::add_utxo` have priority over these.
     */
    public override fun `unspendable`(`unspendable`: List<OutPoint>): TxBuilder
    
    /**
     * Build a transaction with a specific version.
     *
     * The version should always be greater than 0 and greater than 1 if the wallet’s descriptors contain an "older"
     * (`OP_CSV`) operator.
     */
    public override fun `version`(`version`: kotlin.Int): TxBuilder
    

    
    public companion object
}




/**
 * The merkle root of the merkle tree corresponding to a block's transactions.
 */
public interface TxMerkleNodeInterface {
    
    /**
     * Serialize this type into a 32 byte array.
     */
    public fun `serialize`(): kotlin.ByteArray
    
    public companion object
}


/**
 * The merkle root of the merkle tree corresponding to a block's transactions.
 */
public expect open class TxMerkleNode: Disposable, TxMerkleNodeInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public override fun `serialize`(): kotlin.ByteArray
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    
    override fun hashCode(): Int

    public companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public fun `fromBytes`(`bytes`: kotlin.ByteArray): TxMerkleNode
        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public fun `fromString`(`hex`: kotlin.String): TxMerkleNode
        
    }
    
}




/**
 * A bitcoin transaction identifier
 */
public interface TxidInterface {
    
    /**
     * Serialize this type into a 32 byte array.
     */
    public fun `serialize`(): kotlin.ByteArray
    
    public companion object
}


/**
 * A bitcoin transaction identifier
 */
public expect open class Txid: Disposable, TxidInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public override fun `serialize`(): kotlin.ByteArray
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    
    override fun hashCode(): Int

    public companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public fun `fromBytes`(`bytes`: kotlin.ByteArray): Txid
        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public fun `fromString`(`hex`: kotlin.String): Txid
        
    }
    
}




/**
 * An update for a wallet containing chain, descriptor index, and transaction data.
 */
public interface UpdateInterface {
    
    public companion object
}


/**
 * An update for a wallet containing chain, descriptor index, and transaction data.
 */
public expect open class Update: Disposable, UpdateInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    

    
    public companion object
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
public interface WalletInterface {
    
    /**
     * Apply transactions that have been evicted from the mempool.
     * Transactions may be evicted for paying too-low fee, or for being malformed.
     * Irrelevant transactions are ignored.
     *
     * For more information: https://docs.rs/bdk_wallet/latest/bdk_wallet/struct.Wallet.html#method.apply_evicted_txs
     */
    public fun `applyEvictedTxs`(`evictedTxs`: List<EvictedTx>)
    
    /**
     * Apply relevant unconfirmed transactions to the wallet.
     * Transactions that are not relevant are filtered out.
     */
    public fun `applyUnconfirmedTxs`(`unconfirmedTxs`: List<UnconfirmedTx>)
    
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
    public fun `applyUpdate`(`update`: Update)
    
    /**
     * Return the balance, separated into available, trusted-pending, untrusted-pending and
     * immature values.
     */
    public fun `balance`(): Balance
    
    /**
     * Calculates the fee of a given transaction. Returns [`Amount::ZERO`] if `tx` is a coinbase transaction.
     *
     * To calculate the fee for a [`Transaction`] with inputs not owned by this wallet you must
     * manually insert the TxOut(s) into the tx graph using the [`insert_txout`] function.
     *
     * Note `tx` does not have to be in the graph for this to work.
     */
    @Throws(CalculateFeeException::class)
    public fun `calculateFee`(`tx`: Transaction): Amount
    
    /**
     * Calculate the [`FeeRate`] for a given transaction.
     *
     * To calculate the fee rate for a [`Transaction`] with inputs not owned by this wallet you must
     * manually insert the TxOut(s) into the tx graph using the [`insert_txout`] function.
     *
     * Note `tx` does not have to be in the graph for this to work.
     */
    @Throws(CalculateFeeException::class)
    public fun `calculateFeeRate`(`tx`: Transaction): FeeRate
    
    /**
     * Informs the wallet that you no longer intend to broadcast a tx that was built from it.
     *
     * This frees up the change address used when creating the tx for use in future transactions.
     */
    public fun `cancelTx`(`tx`: Transaction)
    
    /**
     * The derivation index of this wallet. It will return `None` if it has not derived any addresses.
     * Otherwise, it will return the index of the highest address it has derived.
     */
    public fun `derivationIndex`(`keychain`: KeychainKind): kotlin.UInt?
    
    /**
     * Finds how the wallet derived the script pubkey `spk`.
     *
     * Will only return `Some(_)` if the wallet has given out the spk.
     */
    public fun `derivationOfSpk`(`spk`: Script): KeychainAndIndex?
    
    /**
     * Return the checksum of the public descriptor associated to `keychain`.
     *
     * Internally calls [`Self::public_descriptor`] to fetch the right descriptor.
     */
    public fun `descriptorChecksum`(`keychain`: KeychainKind): kotlin.String
    
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
    public fun `finalizePsbt`(`psbt`: Psbt, `signOptions`: SignOptions? = null): kotlin.Boolean
    
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
    public fun `getTx`(`txid`: Txid): CanonicalTx?
    
    /**
     * Returns the utxo owned by this wallet corresponding to `outpoint` if it exists in the
     * wallet's database.
     */
    public fun `getUtxo`(`op`: OutPoint): LocalOutput?
    
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
    public fun `insertTxout`(`outpoint`: OutPoint, `txout`: TxOut)
    
    /**
     * Return whether or not a `script` is part of this wallet (either internal or external).
     */
    public fun `isMine`(`script`: Script): kotlin.Boolean
    
    /**
     * Returns the latest checkpoint.
     */
    public fun `latestCheckpoint`(): BlockId
    
    /**
     * List all relevant outputs (includes both spent and unspent, confirmed and unconfirmed).
     *
     * To list only unspent outputs (UTXOs), use [`Wallet::list_unspent`] instead.
     */
    public fun `listOutput`(): List<LocalOutput>
    
    /**
     * Return the list of unspent outputs of this wallet.
     */
    public fun `listUnspent`(): List<LocalOutput>
    
    /**
     * List addresses that are revealed but unused.
     *
     * Note if the returned iterator is empty you can reveal more addresses
     * by using [`reveal_next_address`](Self::reveal_next_address) or
     * [`reveal_addresses_to`](Self::reveal_addresses_to).
     */
    public fun `listUnusedAddresses`(`keychain`: KeychainKind): List<AddressInfo>
    
    /**
     * Marks an address used of the given `keychain` at `index`.
     *
     * Returns whether the given index was present and then removed from the unused set.
     */
    public fun `markUsed`(`keychain`: KeychainKind, `index`: kotlin.UInt): kotlin.Boolean
    
    /**
     * Get the Bitcoin network the wallet is using.
     */
    public fun `network`(): Network
    
    /**
     * The index of the next address that you would get if you were to ask the wallet for a new
     * address.
     */
    public fun `nextDerivationIndex`(`keychain`: KeychainKind): kotlin.UInt
    
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
    public fun `nextUnusedAddress`(`keychain`: KeychainKind): AddressInfo
    
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
    public fun `peekAddress`(`keychain`: KeychainKind, `index`: kotlin.UInt): AddressInfo
    
    /**
     * Persist staged changes of wallet into persister.
     *
     * Returns whether any new changes were persisted.
     *
     * If the persister errors, the staged changes will not be cleared.
     */
    @Throws(PersistenceException::class)
    public fun `persist`(`persister`: Persister): kotlin.Boolean
    
    /**
     * Return the spending policies for the wallet’s descriptor.
     */
    @Throws(DescriptorException::class)
    public fun `policies`(`keychain`: KeychainKind): Policy?
    
    /**
     * Returns the descriptor used to create addresses for a particular `keychain`.
     *
     * It's the "public" version of the wallet's descriptor, meaning a new descriptor that has
     * the same structure but with the all secret keys replaced by their corresponding public key.
     * This can be used to build a watch-only version of a wallet.
     */
    public fun `publicDescriptor`(`keychain`: KeychainKind): kotlin.String
    
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
    public fun `revealAddressesTo`(`keychain`: KeychainKind, `index`: kotlin.UInt): List<AddressInfo>
    
    /**
     * Attempt to reveal the next address of the given `keychain`.
     *
     * This will increment the keychain's derivation index. If the keychain's descriptor doesn't
     * contain a wildcard or every address is already revealed up to the maximum derivation
     * index defined in [BIP32](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki),
     * then the last revealed address will be returned.
     */
    public fun `revealNextAddress`(`keychain`: KeychainKind): AddressInfo
    
    /**
     * Compute the `tx`'s sent and received [`Amount`]s.
     *
     * This method returns a tuple `(sent, received)`. Sent is the sum of the txin amounts
     * that spend from previous txouts tracked by this wallet. Received is the summation
     * of this tx's outputs that send to script pubkeys tracked by this wallet.
     */
    public fun `sentAndReceived`(`tx`: Transaction): SentAndReceivedValues
    
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
    public fun `sign`(`psbt`: Psbt, `signOptions`: SignOptions? = null): kotlin.Boolean
    
    /**
     * Get a reference of the staged [`ChangeSet`] that is yet to be committed (if any).
     */
    public fun `staged`(): ChangeSet?
    
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
    public fun `startFullScan`(): FullScanRequestBuilder
    
    /**
     * Create a partial [`SyncRequest`] for this wallet for all revealed spks.
     *
     * This is the first step when performing a spk-based wallet partial sync, the returned
     * [`SyncRequest`] collects all revealed script pubkeys from the wallet keychain needed to
     * start a blockchain sync with a spk based blockchain client.
     */
    public fun `startSyncWithRevealedSpks`(): SyncRequestBuilder
    
    /**
     * Take the staged [`ChangeSet`] to be persisted now (if any).
     */
    public fun `takeStaged`(): ChangeSet?
    
    /**
     * Iterate over the transactions in the wallet.
     */
    public fun `transactions`(): List<CanonicalTx>
    
    /**
     * Get the [`TxDetails`] of a wallet transaction.
     */
    public fun `txDetails`(`txid`: Txid): TxDetails?
    
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
    public fun `unmarkUsed`(`keychain`: KeychainKind, `index`: kotlin.UInt): kotlin.Boolean
    
    public companion object
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
public expect open class Wallet: Disposable, WalletInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    /**
     * Build a new Wallet.
     *
     * If you have previously created a wallet, use load instead.
     */
    public constructor(noPointer: NoPointer)

    
    /**
     * Build a new Wallet.
     *
     * If you have previously created a wallet, use load instead.
     */
    public constructor(`descriptor`: Descriptor, `changeDescriptor`: Descriptor, `network`: Network, `persister`: Persister, `lookahead`: kotlin.UInt = 25u)

    override fun destroy()
    override fun close()

    
    /**
     * Apply transactions that have been evicted from the mempool.
     * Transactions may be evicted for paying too-low fee, or for being malformed.
     * Irrelevant transactions are ignored.
     *
     * For more information: https://docs.rs/bdk_wallet/latest/bdk_wallet/struct.Wallet.html#method.apply_evicted_txs
     */
    public override fun `applyEvictedTxs`(`evictedTxs`: List<EvictedTx>)
    
    /**
     * Apply relevant unconfirmed transactions to the wallet.
     * Transactions that are not relevant are filtered out.
     */
    public override fun `applyUnconfirmedTxs`(`unconfirmedTxs`: List<UnconfirmedTx>)
    
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
    public override fun `applyUpdate`(`update`: Update)
    
    /**
     * Return the balance, separated into available, trusted-pending, untrusted-pending and
     * immature values.
     */
    public override fun `balance`(): Balance
    
    /**
     * Calculates the fee of a given transaction. Returns [`Amount::ZERO`] if `tx` is a coinbase transaction.
     *
     * To calculate the fee for a [`Transaction`] with inputs not owned by this wallet you must
     * manually insert the TxOut(s) into the tx graph using the [`insert_txout`] function.
     *
     * Note `tx` does not have to be in the graph for this to work.
     */
    @Throws(CalculateFeeException::class)
    public override fun `calculateFee`(`tx`: Transaction): Amount
    
    /**
     * Calculate the [`FeeRate`] for a given transaction.
     *
     * To calculate the fee rate for a [`Transaction`] with inputs not owned by this wallet you must
     * manually insert the TxOut(s) into the tx graph using the [`insert_txout`] function.
     *
     * Note `tx` does not have to be in the graph for this to work.
     */
    @Throws(CalculateFeeException::class)
    public override fun `calculateFeeRate`(`tx`: Transaction): FeeRate
    
    /**
     * Informs the wallet that you no longer intend to broadcast a tx that was built from it.
     *
     * This frees up the change address used when creating the tx for use in future transactions.
     */
    public override fun `cancelTx`(`tx`: Transaction)
    
    /**
     * The derivation index of this wallet. It will return `None` if it has not derived any addresses.
     * Otherwise, it will return the index of the highest address it has derived.
     */
    public override fun `derivationIndex`(`keychain`: KeychainKind): kotlin.UInt?
    
    /**
     * Finds how the wallet derived the script pubkey `spk`.
     *
     * Will only return `Some(_)` if the wallet has given out the spk.
     */
    public override fun `derivationOfSpk`(`spk`: Script): KeychainAndIndex?
    
    /**
     * Return the checksum of the public descriptor associated to `keychain`.
     *
     * Internally calls [`Self::public_descriptor`] to fetch the right descriptor.
     */
    public override fun `descriptorChecksum`(`keychain`: KeychainKind): kotlin.String
    
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
    public override fun `finalizePsbt`(`psbt`: Psbt, `signOptions`: SignOptions?): kotlin.Boolean
    
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
    public override fun `getTx`(`txid`: Txid): CanonicalTx?
    
    /**
     * Returns the utxo owned by this wallet corresponding to `outpoint` if it exists in the
     * wallet's database.
     */
    public override fun `getUtxo`(`op`: OutPoint): LocalOutput?
    
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
    public override fun `insertTxout`(`outpoint`: OutPoint, `txout`: TxOut)
    
    /**
     * Return whether or not a `script` is part of this wallet (either internal or external).
     */
    public override fun `isMine`(`script`: Script): kotlin.Boolean
    
    /**
     * Returns the latest checkpoint.
     */
    public override fun `latestCheckpoint`(): BlockId
    
    /**
     * List all relevant outputs (includes both spent and unspent, confirmed and unconfirmed).
     *
     * To list only unspent outputs (UTXOs), use [`Wallet::list_unspent`] instead.
     */
    public override fun `listOutput`(): List<LocalOutput>
    
    /**
     * Return the list of unspent outputs of this wallet.
     */
    public override fun `listUnspent`(): List<LocalOutput>
    
    /**
     * List addresses that are revealed but unused.
     *
     * Note if the returned iterator is empty you can reveal more addresses
     * by using [`reveal_next_address`](Self::reveal_next_address) or
     * [`reveal_addresses_to`](Self::reveal_addresses_to).
     */
    public override fun `listUnusedAddresses`(`keychain`: KeychainKind): List<AddressInfo>
    
    /**
     * Marks an address used of the given `keychain` at `index`.
     *
     * Returns whether the given index was present and then removed from the unused set.
     */
    public override fun `markUsed`(`keychain`: KeychainKind, `index`: kotlin.UInt): kotlin.Boolean
    
    /**
     * Get the Bitcoin network the wallet is using.
     */
    public override fun `network`(): Network
    
    /**
     * The index of the next address that you would get if you were to ask the wallet for a new
     * address.
     */
    public override fun `nextDerivationIndex`(`keychain`: KeychainKind): kotlin.UInt
    
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
    public override fun `nextUnusedAddress`(`keychain`: KeychainKind): AddressInfo
    
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
    public override fun `peekAddress`(`keychain`: KeychainKind, `index`: kotlin.UInt): AddressInfo
    
    /**
     * Persist staged changes of wallet into persister.
     *
     * Returns whether any new changes were persisted.
     *
     * If the persister errors, the staged changes will not be cleared.
     */
    @Throws(PersistenceException::class)
    public override fun `persist`(`persister`: Persister): kotlin.Boolean
    
    /**
     * Return the spending policies for the wallet’s descriptor.
     */
    @Throws(DescriptorException::class)
    public override fun `policies`(`keychain`: KeychainKind): Policy?
    
    /**
     * Returns the descriptor used to create addresses for a particular `keychain`.
     *
     * It's the "public" version of the wallet's descriptor, meaning a new descriptor that has
     * the same structure but with the all secret keys replaced by their corresponding public key.
     * This can be used to build a watch-only version of a wallet.
     */
    public override fun `publicDescriptor`(`keychain`: KeychainKind): kotlin.String
    
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
    public override fun `revealAddressesTo`(`keychain`: KeychainKind, `index`: kotlin.UInt): List<AddressInfo>
    
    /**
     * Attempt to reveal the next address of the given `keychain`.
     *
     * This will increment the keychain's derivation index. If the keychain's descriptor doesn't
     * contain a wildcard or every address is already revealed up to the maximum derivation
     * index defined in [BIP32](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki),
     * then the last revealed address will be returned.
     */
    public override fun `revealNextAddress`(`keychain`: KeychainKind): AddressInfo
    
    /**
     * Compute the `tx`'s sent and received [`Amount`]s.
     *
     * This method returns a tuple `(sent, received)`. Sent is the sum of the txin amounts
     * that spend from previous txouts tracked by this wallet. Received is the summation
     * of this tx's outputs that send to script pubkeys tracked by this wallet.
     */
    public override fun `sentAndReceived`(`tx`: Transaction): SentAndReceivedValues
    
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
    public override fun `sign`(`psbt`: Psbt, `signOptions`: SignOptions?): kotlin.Boolean
    
    /**
     * Get a reference of the staged [`ChangeSet`] that is yet to be committed (if any).
     */
    public override fun `staged`(): ChangeSet?
    
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
    public override fun `startFullScan`(): FullScanRequestBuilder
    
    /**
     * Create a partial [`SyncRequest`] for this wallet for all revealed spks.
     *
     * This is the first step when performing a spk-based wallet partial sync, the returned
     * [`SyncRequest`] collects all revealed script pubkeys from the wallet keychain needed to
     * start a blockchain sync with a spk based blockchain client.
     */
    public override fun `startSyncWithRevealedSpks`(): SyncRequestBuilder
    
    /**
     * Take the staged [`ChangeSet`] to be persisted now (if any).
     */
    public override fun `takeStaged`(): ChangeSet?
    
    /**
     * Iterate over the transactions in the wallet.
     */
    public override fun `transactions`(): List<CanonicalTx>
    
    /**
     * Get the [`TxDetails`] of a wallet transaction.
     */
    public override fun `txDetails`(`txid`: Txid): TxDetails?
    
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
    public override fun `unmarkUsed`(`keychain`: KeychainKind, `index`: kotlin.UInt): kotlin.Boolean
    

    public companion object {
        
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
        public fun `createFromTwoPathDescriptor`(`twoPathDescriptor`: Descriptor, `network`: Network, `persister`: Persister, `lookahead`: kotlin.UInt = 25u): Wallet
        
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
        public fun `createSingle`(`descriptor`: Descriptor, `network`: Network, `persister`: Persister, `lookahead`: kotlin.UInt = 25u): Wallet
        
        /**
         * Build Wallet by loading from persistence.
         *
         * Note that the descriptor secret keys are not persisted to the db.
         */
        @Throws(LoadWithPersistException::class)
        public fun `load`(`descriptor`: Descriptor, `changeDescriptor`: Descriptor, `persister`: Persister, `lookahead`: kotlin.UInt = 25u): Wallet
        
        /**
         * Build a single-descriptor Wallet by loading from persistence.
         *
         * Note that the descriptor secret keys are not persisted to the db.
         */
        @Throws(LoadWithPersistException::class)
        public fun `loadSingle`(`descriptor`: Descriptor, `persister`: Persister, `lookahead`: kotlin.UInt = 25u): Wallet
        
    }
    
}




/**
 * A bitcoin transaction identifier, including witness data.
 * For transactions with no SegWit inputs, the `txid` will be equivalent to `wtxid`.
 */
public interface WtxidInterface {
    
    /**
     * Serialize this type into a 32 byte array.
     */
    public fun `serialize`(): kotlin.ByteArray
    
    public companion object
}


/**
 * A bitcoin transaction identifier, including witness data.
 * For transactions with no SegWit inputs, the `txid` will be equivalent to `wtxid`.
 */
public expect open class Wtxid: Disposable, WtxidInterface {
    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    public constructor(noPointer: NoPointer)

    

    override fun destroy()
    override fun close()

    
    /**
     * Serialize this type into a 32 byte array.
     */
    public override fun `serialize`(): kotlin.ByteArray
    
    override fun toString(): String
    
    
    override fun equals(other: Any?): Boolean
    
    override fun hashCode(): Int

    public companion object {
        
        /**
         * Construct a hash-like type from 32 bytes.
         */
        @Throws(HashParseException::class)
        public fun `fromBytes`(`bytes`: kotlin.ByteArray): Wtxid
        
        /**
         * Construct a hash-like type from a hex string.
         */
        @Throws(HashParseException::class)
        public fun `fromString`(`hex`: kotlin.String): Wtxid
        
    }
    
}




/**
 * A derived address and the index it was found at.
 */

public data class AddressInfo (
    /**
     * Child index of this address
     */
    var `index`: kotlin.UInt, 
    /**
     * The address
     */
    var `address`: Address, 
    /**
     * Type of keychain
     */
    var `keychain`: KeychainKind
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`index`,
            this.`address`,
            this.`keychain`,
        )
    }
    public companion object
}




public data class Anchor (
    var `confirmationBlockTime`: ConfirmationBlockTime, 
    var `txid`: Txid
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`confirmationBlockTime`,
            this.`txid`,
        )
    }
    public companion object
}



/**
 * Balance, differentiated into various categories.
 */

public data class Balance (
    /**
     * All coinbase outputs not yet matured
     */
    var `immature`: Amount, 
    /**
     * Unconfirmed UTXOs generated by a wallet tx
     */
    var `trustedPending`: Amount, 
    /**
     * Unconfirmed UTXOs received from an external wallet
     */
    var `untrustedPending`: Amount, 
    /**
     * Confirmed and immediately spendable balance
     */
    var `confirmed`: Amount, 
    /**
     * Get sum of trusted_pending and confirmed coins.
     *
     * This is the balance you can spend right now that shouldn't get cancelled via another party
     * double spending it.
     */
    var `trustedSpendable`: Amount, 
    /**
     * Get the whole balance visible to the wallet.
     */
    var `total`: Amount
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`immature`,
            this.`trustedPending`,
            this.`untrustedPending`,
            this.`confirmed`,
            this.`trustedSpendable`,
            this.`total`,
        )
    }
    public companion object
}



/**
 * A reference to a block in the canonical chain.
 */

public data class BlockId (
    /**
     * The height of the block.
     */
    var `height`: kotlin.UInt, 
    /**
     * The hash of the block.
     */
    var `hash`: BlockHash
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`height`,
            this.`hash`,
        )
    }
    public companion object
}



/**
 * A transaction that is deemed to be part of the canonical history.
 */

public data class CanonicalTx (
    /**
     * The transaction.
     */
    var `transaction`: Transaction, 
    /**
     * How the transaction is observed in the canonical chain (confirmed or unconfirmed).
     */
    var `chainPosition`: ChainPosition
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`transaction`,
            this.`chainPosition`,
        )
    }
    public companion object
}



/**
 * Receive a [`CbfClient`] and [`CbfNode`].
 */

public data class CbfComponents (
    /**
     * Publish events to the node, like broadcasting transactions or adding scripts.
     */
    var `client`: CbfClient, 
    /**
     * The node to run and fetch transactions for a [`Wallet`].
     */
    var `node`: CbfNode
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`client`,
            this.`node`,
        )
    }
    public companion object
}



/**
 * The hash added or removed at the given height.
 */

public data class ChainChange (
    /**
     * Effected height
     */
    var `height`: kotlin.UInt, 
    /**
     * A hash was added or must be removed.
     */
    var `hash`: BlockHash?
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`height`,
            this.`hash`,
        )
    }
    public companion object
}



/**
 * An extra condition that must be satisfied but that is out of control of the user
 */

public data class Condition (
    /**
     * Optional CheckSequenceVerify condition
     */
    var `csv`: kotlin.UInt?, 
    /**
     * Optional timelock condition
     */
    var `timelock`: LockTime?
) {
    public companion object
}



/**
 * Represents the confirmation block and time of a transaction.
 */

public data class ConfirmationBlockTime (
    /**
     * The anchor block.
     */
    var `blockId`: BlockId, 
    /**
     * The confirmation time of the transaction being anchored.
     */
    var `confirmationTime`: kotlin.ULong
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`blockId`,
            this.`confirmationTime`,
        )
    }
    public companion object
}




public data class ControlBlock (
    /**
     * The internal key.
     */
    var `internalKey`: kotlin.ByteArray, 
    /**
     * The merkle proof of a script associated with this leaf.
     */
    var `merkleBranch`: List<kotlin.String>, 
    /**
     * The parity of the output key (NOT THE INTERNAL KEY WHICH IS ALWAYS XONLY).
     */
    var `outputKeyParity`: kotlin.UByte, 
    /**
     * The tapleaf version.
     */
    var `leafVersion`: kotlin.UByte
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ControlBlock
        if (!`internalKey`.contentEquals(other.`internalKey`)) return false
        if (`merkleBranch` != other.`merkleBranch`) return false
        if (`outputKeyParity` != other.`outputKeyParity`) return false
        if (`leafVersion` != other.`leafVersion`) return false

        return true
    }
    override fun hashCode(): Int {
        var result = `internalKey`.contentHashCode()
        result = 31 * result + `merkleBranch`.hashCode()
        result = 31 * result + `outputKeyParity`.hashCode()
        result = 31 * result + `leafVersion`.hashCode()
        return result
    }
    public companion object
}



/**
 * This type replaces the Rust tuple `(txid, evicted_at)` used in the Wallet::apply_evicted_txs` method,
 * where `evicted_at` is the timestamp of when the transaction `txid` was evicted from the mempool.
 * Transactions may be evicted for paying a low fee rate or having invalid scripts.
 */

public data class EvictedTx (
    var `txid`: Txid, 
    var `evictedAt`: kotlin.ULong
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`txid`,
            this.`evictedAt`,
        )
    }
    public companion object
}




public data class FinalizedPsbtResult (
    var `psbt`: Psbt, 
    var `couldFinalize`: kotlin.Boolean, 
    var `errors`: List<PsbtFinalizeException>?
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`psbt`,
            this.`couldFinalize`,
            this.`errors`,
        )
    }
    public companion object
}



/**
 * Bitcoin block header.
 * Contains all the block’s information except the actual transactions, but including a root of a merkle tree
 * committing to all transactions in the block.
 */

public data class Header (
    /**
     * Block version, now repurposed for soft fork signalling.
     */
    var `version`: kotlin.Int, 
    /**
     * Reference to the previous block in the chain.
     */
    var `prevBlockhash`: BlockHash, 
    /**
     * The root hash of the merkle tree of transactions in the block.
     */
    var `merkleRoot`: TxMerkleNode, 
    /**
     * The timestamp of the block, as claimed by the miner.
     */
    var `time`: kotlin.UInt, 
    /**
     * The target value below which the blockhash must lie.
     */
    var `bits`: kotlin.UInt, 
    /**
     * The nonce, selected to obtain a low enough blockhash.
     */
    var `nonce`: kotlin.UInt
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`version`,
            this.`prevBlockhash`,
            this.`merkleRoot`,
            this.`time`,
            this.`bits`,
            this.`nonce`,
        )
    }
    public companion object
}



/**
 * Notification of a new block header.
 */

public data class HeaderNotification (
    /**
     * New block height.
     */
    var `height`: kotlin.ULong, 
    /**
     * Newly added header.
     */
    var `header`: Header
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`height`,
            this.`header`,
        )
    }
    public companion object
}



/**
 * Mapping of descriptors to their last revealed index.
 */

public data class IndexerChangeSet (
    var `lastRevealed`: Map<DescriptorId, kotlin.UInt>
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`lastRevealed`,
        )
    }
    public companion object
}



/**
 * A key-value map for an input of the corresponding index in the unsigned transaction.
 */

public data class Input (
    /**
     * The non-witness transaction this input spends from. Should only be
     * `Option::Some` for inputs which spend non-segwit outputs or
     * if it is unknown whether an input spends a segwit output.
     */
    var `nonWitnessUtxo`: Transaction?, 
    /**
     * The transaction output this input spends from. Should only be
     * `Option::Some` for inputs which spend segwit outputs,
     * including P2SH embedded ones.
     */
    var `witnessUtxo`: TxOut?, 
    /**
     * A map from public keys to their corresponding signature as would be
     * pushed to the stack from a scriptSig or witness for a non-taproot inputs.
     */
    var `partialSigs`: Map<kotlin.String, kotlin.ByteArray>, 
    /**
     * The sighash type to be used for this input. Signatures for this input
     * must use the sighash type.
     */
    var `sighashType`: kotlin.String?, 
    /**
     * The redeem script for this input.
     */
    var `redeemScript`: Script?, 
    /**
     * The witness script for this input.
     */
    var `witnessScript`: Script?, 
    /**
     * A map from public keys needed to sign this input to their corresponding
     * master key fingerprints and derivation paths.
     */
    var `bip32Derivation`: Map<kotlin.String, KeySource>, 
    /**
     * The finalized, fully-constructed scriptSig with signatures and any other
     * scripts necessary for this input to pass validation.
     */
    var `finalScriptSig`: Script?, 
    /**
     * The finalized, fully-constructed scriptWitness with signatures and any
     * other scripts necessary for this input to pass validation.
     */
    var `finalScriptWitness`: List<kotlin.ByteArray>?, 
    /**
     * RIPEMD160 hash to preimage map.
     */
    var `ripemd160Preimages`: Map<kotlin.String, kotlin.ByteArray>, 
    /**
     * SHA256 hash to preimage map.
     */
    var `sha256Preimages`: Map<kotlin.String, kotlin.ByteArray>, 
    /**
     * HASH160 hash to preimage map.
     */
    var `hash160Preimages`: Map<kotlin.String, kotlin.ByteArray>, 
    /**
     * HASH256 hash to preimage map.
     */
    var `hash256Preimages`: Map<kotlin.String, kotlin.ByteArray>, 
    /**
     * Serialized taproot signature with sighash type for key spend.
     */
    var `tapKeySig`: kotlin.ByteArray?, 
    /**
     * Map of `<xonlypubkey>|<leafhash>` with signature.
     */
    var `tapScriptSigs`: Map<TapScriptSigKey, kotlin.ByteArray>, 
    /**
     * Map of Control blocks to Script version pair.
     */
    var `tapScripts`: Map<ControlBlock, TapScriptEntry>, 
    /**
     * Map of tap root x only keys to origin info and leaf hashes contained in it.
     */
    var `tapKeyOrigins`: Map<kotlin.String, TapKeyOrigin>, 
    /**
     * Taproot Internal key.
     */
    var `tapInternalKey`: kotlin.String?, 
    /**
     * Taproot Merkle root.
     */
    var `tapMerkleRoot`: kotlin.String?, 
    /**
     * Proprietary key-value pairs for this input.
     */
    var `proprietary`: Map<ProprietaryKey, kotlin.ByteArray>, 
    /**
     * Unknown key-value pairs for this input.
     */
    var `unknown`: Map<Key, kotlin.ByteArray>
) : Disposable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Input
        if (`nonWitnessUtxo` != other.`nonWitnessUtxo`) return false
        if (`witnessUtxo` != other.`witnessUtxo`) return false
        if (`partialSigs` != other.`partialSigs`) return false
        if (`sighashType` != other.`sighashType`) return false
        if (`redeemScript` != other.`redeemScript`) return false
        if (`witnessScript` != other.`witnessScript`) return false
        if (`bip32Derivation` != other.`bip32Derivation`) return false
        if (`finalScriptSig` != other.`finalScriptSig`) return false
        if (`finalScriptWitness` != other.`finalScriptWitness`) return false
        if (`ripemd160Preimages` != other.`ripemd160Preimages`) return false
        if (`sha256Preimages` != other.`sha256Preimages`) return false
        if (`hash160Preimages` != other.`hash160Preimages`) return false
        if (`hash256Preimages` != other.`hash256Preimages`) return false
        if (`tapKeySig` != null) {
            if (other.`tapKeySig` == null) return false
            if (!`tapKeySig`.contentEquals(other.`tapKeySig`)) return false
        }
        if (`tapScriptSigs` != other.`tapScriptSigs`) return false
        if (`tapScripts` != other.`tapScripts`) return false
        if (`tapKeyOrigins` != other.`tapKeyOrigins`) return false
        if (`tapInternalKey` != other.`tapInternalKey`) return false
        if (`tapMerkleRoot` != other.`tapMerkleRoot`) return false
        if (`proprietary` != other.`proprietary`) return false
        if (`unknown` != other.`unknown`) return false

        return true
    }
    override fun hashCode(): Int {
        var result = (`nonWitnessUtxo`?.hashCode() ?: 0)
        result = 31 * result + (`witnessUtxo`?.hashCode() ?: 0)
        result = 31 * result + `partialSigs`.hashCode()
        result = 31 * result + (`sighashType`?.hashCode() ?: 0)
        result = 31 * result + (`redeemScript`?.hashCode() ?: 0)
        result = 31 * result + (`witnessScript`?.hashCode() ?: 0)
        result = 31 * result + `bip32Derivation`.hashCode()
        result = 31 * result + (`finalScriptSig`?.hashCode() ?: 0)
        result = 31 * result + (`finalScriptWitness`?.hashCode() ?: 0)
        result = 31 * result + `ripemd160Preimages`.hashCode()
        result = 31 * result + `sha256Preimages`.hashCode()
        result = 31 * result + `hash160Preimages`.hashCode()
        result = 31 * result + `hash256Preimages`.hashCode()
        result = 31 * result + (`tapKeySig`?.contentHashCode() ?: 0)
        result = 31 * result + `tapScriptSigs`.hashCode()
        result = 31 * result + `tapScripts`.hashCode()
        result = 31 * result + `tapKeyOrigins`.hashCode()
        result = 31 * result + (`tapInternalKey`?.hashCode() ?: 0)
        result = 31 * result + (`tapMerkleRoot`?.hashCode() ?: 0)
        result = 31 * result + `proprietary`.hashCode()
        result = 31 * result + `unknown`.hashCode()
        return result
    }
    override fun destroy() {
        Disposable.destroy(
            this.`nonWitnessUtxo`,
            this.`witnessUtxo`,
            this.`partialSigs`,
            this.`sighashType`,
            this.`redeemScript`,
            this.`witnessScript`,
            this.`bip32Derivation`,
            this.`finalScriptSig`,
            this.`finalScriptWitness`,
            this.`ripemd160Preimages`,
            this.`sha256Preimages`,
            this.`hash160Preimages`,
            this.`hash256Preimages`,
            this.`tapKeySig`,
            this.`tapScriptSigs`,
            this.`tapScripts`,
            this.`tapKeyOrigins`,
            this.`tapInternalKey`,
            this.`tapMerkleRoot`,
            this.`proprietary`,
            this.`unknown`,
        )
    }
    public companion object
}




public data class Key (
    /**
     * The type of this PSBT key.
     */
    var `typeValue`: kotlin.UByte, 
    /**
     * The key itself in raw byte form.
     * `<key> := <keylen> <keytype> <keydata>`
     */
    var `key`: kotlin.ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Key
        if (`typeValue` != other.`typeValue`) return false
        if (!`key`.contentEquals(other.`key`)) return false

        return true
    }
    override fun hashCode(): Int {
        var result = `typeValue`.hashCode()
        result = 31 * result + `key`.contentHashCode()
        return result
    }
    public companion object
}




public data class KeySource (
    /**
     * A fingerprint
     */
    var `fingerprint`: kotlin.String, 
    /**
     * A BIP-32 derivation path.
     */
    var `path`: DerivationPath
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`fingerprint`,
            this.`path`,
        )
    }
    public companion object
}



/**
 * The keychain kind and the index in that keychain.
 */

public data class KeychainAndIndex (
    /**
     * Type of keychains.
     */
    var `keychain`: KeychainKind, 
    /**
     * The index in the keychain.
     */
    var `index`: kotlin.UInt
) {
    public companion object
}



/**
 * Changes to the local chain
 */

public data class LocalChainChangeSet (
    var `changes`: List<ChainChange>
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`changes`,
        )
    }
    public companion object
}



/**
 * An unspent output owned by a [`Wallet`].
 */

public data class LocalOutput (
    /**
     * Reference to a transaction output
     */
    var `outpoint`: OutPoint, 
    /**
     * Transaction output
     */
    var `txout`: TxOut, 
    /**
     * Type of keychain
     */
    var `keychain`: KeychainKind, 
    /**
     * Whether this UTXO is spent or not
     */
    var `isSpent`: kotlin.Boolean, 
    /**
     * The derivation index for the script pubkey in the wallet
     */
    var `derivationIndex`: kotlin.UInt, 
    /**
     * The position of the output in the blockchain.
     */
    var `chainPosition`: ChainPosition
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`outpoint`,
            this.`txout`,
            this.`keychain`,
            this.`isSpent`,
            this.`derivationIndex`,
            this.`chainPosition`,
        )
    }
    public companion object
}



/**
 * A reference to an unspent output by TXID and output index.
 */

public data class OutPoint (
    /**
     * The transaction.
     */
    var `txid`: Txid, 
    /**
     * The index of the output in the transaction.
     */
    var `vout`: kotlin.UInt
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`txid`,
            this.`vout`,
        )
    }
    public companion object
}



/**
 * A peer to connect to over the Bitcoin peer-to-peer network.
 */

public data class Peer (
    /**
     * The IP address to reach the node.
     */
    var `address`: IpAddress, 
    /**
     * The port to reach the node. If none is provided, the default
     * port for the selected network will be used.
     */
    var `port`: kotlin.UShort?, 
    /**
     * Does the remote node offer encrypted peer-to-peer connection.
     */
    var `v2Transport`: kotlin.Boolean
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`address`,
            this.`port`,
            this.`v2Transport`,
        )
    }
    public companion object
}




public data class ProprietaryKey (
    /**
     * Proprietary type prefix used for grouping together keys under some
     * application and avoid namespace collision
     */
    var `prefix`: kotlin.ByteArray, 
    /**
     * Custom proprietary subtype
     */
    var `subtype`: kotlin.UByte, 
    /**
     * Additional key bytes (like serialized public key data etc)
     */
    var `key`: kotlin.ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ProprietaryKey
        if (!`prefix`.contentEquals(other.`prefix`)) return false
        if (`subtype` != other.`subtype`) return false
        if (!`key`.contentEquals(other.`key`)) return false

        return true
    }
    override fun hashCode(): Int {
        var result = `prefix`.contentHashCode()
        result = 31 * result + `subtype`.hashCode()
        result = 31 * result + `key`.contentHashCode()
        return result
    }
    public companion object
}



/**
 * A bitcoin script and associated amount.
 */

public data class ScriptAmount (
    /**
     * The underlying script.
     */
    var `script`: Script, 
    /**
     * The amount owned by the script.
     */
    var `amount`: Amount
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`script`,
            this.`amount`,
        )
    }
    public companion object
}



/**
 * The total value sent and received.
 */

public data class SentAndReceivedValues (
    /**
     * Amount sent in the transaction.
     */
    var `sent`: Amount, 
    /**
     * The amount received in the transaction, possibly as a change output(s).
     */
    var `received`: Amount
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`sent`,
            this.`received`,
        )
    }
    public companion object
}



/**
 * Response to an ElectrumClient.server_features request.
 */

public data class ServerFeaturesRes (
    /**
     * Server version reported.
     */
    var `serverVersion`: kotlin.String, 
    /**
     * Hash of the genesis block.
     */
    var `genesisHash`: BlockHash, 
    /**
     * Minimum supported version of the protocol.
     */
    var `protocolMin`: kotlin.String, 
    /**
     * Maximum supported version of the protocol.
     */
    var `protocolMax`: kotlin.String, 
    /**
     * Hash function used to create the `ScriptHash`.
     */
    var `hashFunction`: kotlin.String?, 
    /**
     * Pruned height of the server.
     */
    var `pruning`: kotlin.Long?
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`serverVersion`,
            this.`genesisHash`,
            this.`protocolMin`,
            this.`protocolMax`,
            this.`hashFunction`,
            this.`pruning`,
        )
    }
    public companion object
}



/**
 * Options for a software signer.
 *
 * Adjust the behavior of our software signers and the way a transaction is finalized.
 */

public data class SignOptions (
    /**
     * Whether the signer should trust the `witness_utxo`, if the `non_witness_utxo` hasn't been
     * provided
     *
     * Defaults to `false` to mitigate the "SegWit bug" which could trick the wallet into
     * paying a fee larger than expected.
     *
     * Some wallets, especially if relatively old, might not provide the `non_witness_utxo` for
     * SegWit transactions in the PSBT they generate: in those cases setting this to `true`
     * should correctly produce a signature, at the expense of an increased trust in the creator
     * of the PSBT.
     *
     * For more details see: <https://blog.trezor.io/details-of-firmware-updates-for-trezor-one-version-1-9-1-and-trezor-model-t-version-2-3-1-1eba8f60f2dd>
     */
    var `trustWitnessUtxo`: kotlin.Boolean, 
    /**
     * Whether the wallet should assume a specific height has been reached when trying to finalize
     * a transaction
     *
     * The wallet will only "use" a timelock to satisfy the spending policy of an input if the
     * timelock height has already been reached. This option allows overriding the "current height" to let the
     * wallet use timelocks in the future to spend a coin.
     */
    var `assumeHeight`: kotlin.UInt?, 
    /**
     * Whether the signer should use the `sighash_type` set in the PSBT when signing, no matter
     * what its value is
     *
     * Defaults to `false` which will only allow signing using `SIGHASH_ALL`.
     */
    var `allowAllSighashes`: kotlin.Boolean, 
    /**
     * Whether to try finalizing the PSBT after the inputs are signed.
     *
     * Defaults to `true` which will try finalizing PSBT after inputs are signed.
     */
    var `tryFinalize`: kotlin.Boolean, 
    /**
     * Whether we should try to sign a taproot transaction with the taproot internal key
     * or not. This option is ignored if we're signing a non-taproot PSBT.
     *
     * Defaults to `true`, i.e., we always try to sign with the taproot internal key.
     */
    var `signWithTapInternalKey`: kotlin.Boolean, 
    /**
     * Whether we should grind ECDSA signature to ensure signing with low r
     * or not.
     * Defaults to `true`, i.e., we always grind ECDSA signature to sign with low r.
     */
    var `allowGrinding`: kotlin.Boolean
) {
    public companion object
}



/**
 * A proxy to route network traffic, most likely through a Tor daemon. Normally this proxy is
 * exposed at 127.0.0.1:9050.
 */

public data class Socks5Proxy (
    /**
     * The IP address, likely `127.0.0.1`
     */
    var `address`: IpAddress, 
    /**
     * The listening port, likely `9050`
     */
    var `port`: kotlin.UShort
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`address`,
            this.`port`,
        )
    }
    public companion object
}




public data class TapKeyOrigin (
    /**
     * leaf hashes as hex strings
     */
    var `tapLeafHashes`: List<kotlin.String>, 
    /**
     * key source
     */
    var `keySource`: KeySource
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`tapLeafHashes`,
            this.`keySource`,
        )
    }
    public companion object
}




public data class TapScriptEntry (
    /**
     * script (reuse existing `Script` FFI type)
     */
    var `script`: Script, 
    /**
     * leaf version
     */
    var `leafVersion`: kotlin.UByte
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`script`,
            this.`leafVersion`,
        )
    }
    public companion object
}




public data class TapScriptSigKey (
    /**
     * An x-only public key, used for verification of Taproot signatures and serialized according to BIP-340.
     */
    var `xonlyPubkey`: kotlin.String, 
    /**
     * Taproot-tagged hash with tag "TapLeaf".
     * This is used for computing tapscript script spend hash.
     */
    var `tapLeafHash`: kotlin.String
) {
    public companion object
}



/**
 * Bitcoin transaction metadata.
 */

public data class Tx (
    /**
     * The transaction identifier.
     */
    var `txid`: Txid, 
    /**
     * The transaction version, of which 0, 1, 2 are standard.
     */
    var `version`: kotlin.Int, 
    /**
     * The block height or time restriction on the transaction.
     */
    var `locktime`: kotlin.UInt, 
    /**
     * The size of the transaction in bytes.
     */
    var `size`: kotlin.ULong, 
    /**
     * The weight units of this transaction.
     */
    var `weight`: kotlin.ULong, 
    /**
     * The fee of this transaction in satoshis.
     */
    var `fee`: kotlin.ULong, 
    /**
     * Confirmation status and data.
     */
    var `status`: TxStatus
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`txid`,
            this.`version`,
            this.`locktime`,
            this.`size`,
            this.`weight`,
            this.`fee`,
            this.`status`,
        )
    }
    public companion object
}




public data class TxDetails (
    var `txid`: Txid, 
    var `sent`: Amount, 
    var `received`: Amount, 
    var `fee`: Amount?, 
    var `feeRate`: kotlin.Float?, 
    var `balanceDelta`: kotlin.Long, 
    var `chainPosition`: ChainPosition, 
    var `tx`: Transaction
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`txid`,
            this.`sent`,
            this.`received`,
            this.`fee`,
            this.`feeRate`,
            this.`balanceDelta`,
            this.`chainPosition`,
            this.`tx`,
        )
    }
    public companion object
}




public data class TxGraphChangeSet (
    var `txs`: List<Transaction>, 
    var `txouts`: Map<HashableOutPoint, TxOut>, 
    var `anchors`: List<Anchor>, 
    var `lastSeen`: Map<Txid, kotlin.ULong>, 
    var `firstSeen`: Map<Txid, kotlin.ULong>, 
    var `lastEvicted`: Map<Txid, kotlin.ULong>
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`txs`,
            this.`txouts`,
            this.`anchors`,
            this.`lastSeen`,
            this.`firstSeen`,
            this.`lastEvicted`,
        )
    }
    public companion object
}



/**
 * A transcation input.
 */

public data class TxIn (
    /**
     * A pointer to the previous output this input spends from.
     */
    var `previousOutput`: OutPoint, 
    /**
     * The script corresponding to the `scriptPubKey`, empty in SegWit transactions.
     */
    var `scriptSig`: Script, 
    /**
     * https://bitcoin.stackexchange.com/questions/87372/what-does-the-sequence-in-a-transaction-input-mean
     */
    var `sequence`: kotlin.UInt, 
    /**
     * A proof for the script that authorizes the spend of the output.
     */
    var `witness`: List<kotlin.ByteArray>
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`previousOutput`,
            this.`scriptSig`,
            this.`sequence`,
            this.`witness`,
        )
    }
    public companion object
}



/**
 * Bitcoin transaction output.
 *
 * Defines new coins to be created as a result of the transaction,
 * along with spending conditions ("script", aka "output script"),
 * which an input spending it must satisfy.
 *
 * An output that is not yet spent by an input is called Unspent Transaction Output ("UTXO").
 */

public data class TxOut (
    /**
     * The value of the output, in satoshis.
     */
    var `value`: Amount, 
    /**
     * The script which must be satisfied for the output to be spent.
     */
    var `scriptPubkey`: Script
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`value`,
            this.`scriptPubkey`,
        )
    }
    public companion object
}



/**
 * Transaction confirmation metadata.
 */

public data class TxStatus (
    /**
     * Is the transaction in a block.
     */
    var `confirmed`: kotlin.Boolean, 
    /**
     * Height of the block this transaction was included.
     */
    var `blockHeight`: kotlin.UInt?, 
    /**
     * Hash of the block.
     */
    var `blockHash`: BlockHash?, 
    /**
     * The time shown in the block, not necessarily the same time as when the block was found.
     */
    var `blockTime`: kotlin.ULong?
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`confirmed`,
            this.`blockHeight`,
            this.`blockHash`,
            this.`blockTime`,
        )
    }
    public companion object
}



/**
 * This type replaces the Rust tuple `(tx, last_seen)` used in the Wallet::apply_unconfirmed_txs` method,
 * where `last_seen` is the timestamp of when the transaction `tx` was last seen in the mempool.
 */

public data class UnconfirmedTx (
    var `tx`: Transaction, 
    var `lastSeen`: kotlin.ULong
) : Disposable {
    override fun destroy() {
        Disposable.destroy(
            this.`tx`,
            this.`lastSeen`,
        )
    }
    public companion object
}



/**
 * The version and program of a Segwit address.
 */

public data class WitnessProgram (
    /**
     * Version. For example 1 for Taproot.
     */
    var `version`: kotlin.UByte, 
    /**
     * The witness program.
     */
    var `program`: kotlin.ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WitnessProgram
        if (`version` != other.`version`) return false
        if (!`program`.contentEquals(other.`program`)) return false

        return true
    }
    override fun hashCode(): Int {
        var result = `version`.hashCode()
        result = 31 * result + `program`.contentHashCode()
        return result
    }
    public companion object
}




/**
 * The type of address.
 */

public sealed class AddressData {
    
    /**
     * Legacy.
     */
    public data class P2pkh(
        val `pubkeyHash`: kotlin.String,
    ) : AddressData() {
    }
    
    /**
     * Wrapped Segwit
     */
    public data class P2sh(
        val `scriptHash`: kotlin.String,
    ) : AddressData() {
    }
    
    /**
     * Segwit
     */
    public data class Segwit(
        val `witnessProgram`: WitnessProgram,
    ) : AddressData() {
    }
    
}







public sealed class AddressParseException: kotlin.Exception() {
    
    public class Base58(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class Bech32(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class WitnessVersion(
        public val `errorMessage`: kotlin.String,
    ) : AddressParseException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class WitnessProgram(
        public val `errorMessage`: kotlin.String,
    ) : AddressParseException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class UnknownHrp(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class LegacyAddressTooLong(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidBase58PayloadLength(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidLegacyPrefix(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class NetworkValidation(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
    public class OtherAddressParseErr(
    ) : AddressParseException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class Bip32Exception: kotlin.Exception() {
    
    public class CannotDeriveFromHardenedKey(
    ) : Bip32Exception() {
        override val message: String
            get() = ""
    }
    
    public class Secp256k1(
        public val `errorMessage`: kotlin.String,
    ) : Bip32Exception() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidChildNumber(
        public val `childNumber`: kotlin.UInt,
    ) : Bip32Exception() {
        override val message: String
            get() = "childNumber=${ `childNumber` }"
    }
    
    public class InvalidChildNumberFormat(
    ) : Bip32Exception() {
        override val message: String
            get() = ""
    }
    
    public class InvalidDerivationPathFormat(
    ) : Bip32Exception() {
        override val message: String
            get() = ""
    }
    
    public class UnknownVersion(
        public val `version`: kotlin.String,
    ) : Bip32Exception() {
        override val message: String
            get() = "version=${ `version` }"
    }
    
    public class WrongExtendedKeyLength(
        public val `length`: kotlin.UInt,
    ) : Bip32Exception() {
        override val message: String
            get() = "length=${ `length` }"
    }
    
    public class Base58(
        public val `errorMessage`: kotlin.String,
    ) : Bip32Exception() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Hex(
        public val `errorMessage`: kotlin.String,
    ) : Bip32Exception() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidPublicKeyHexLength(
        public val `length`: kotlin.UInt,
    ) : Bip32Exception() {
        override val message: String
            get() = "length=${ `length` }"
    }
    
    public class UnknownException(
        public val `errorMessage`: kotlin.String,
    ) : Bip32Exception() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
}





public sealed class Bip39Exception: kotlin.Exception() {
    
    public class BadWordCount(
        public val `wordCount`: kotlin.ULong,
    ) : Bip39Exception() {
        override val message: String
            get() = "wordCount=${ `wordCount` }"
    }
    
    public class UnknownWord(
        public val `index`: kotlin.ULong,
    ) : Bip39Exception() {
        override val message: String
            get() = "index=${ `index` }"
    }
    
    public class BadEntropyBitCount(
        public val `bitCount`: kotlin.ULong,
    ) : Bip39Exception() {
        override val message: String
            get() = "bitCount=${ `bitCount` }"
    }
    
    public class InvalidChecksum(
    ) : Bip39Exception() {
        override val message: String
            get() = ""
    }
    
    public class AmbiguousLanguages(
        public val `languages`: kotlin.String,
    ) : Bip39Exception() {
        override val message: String
            get() = "languages=${ `languages` }"
    }
    
}





public sealed class CalculateFeeException: kotlin.Exception(), Disposable  {
    
    public class MissingTxOut(
        public val `outPoints`: List<OutPoint>,
    ) : CalculateFeeException() {
        override val message: String
            get() = "outPoints=${ `outPoints` }"

        override fun destroy() {
            
            Disposable.destroy(
                this.`outPoints`,
            )
        }
    }
    
    public class NegativeFee(
        public val `amount`: kotlin.String,
    ) : CalculateFeeException() {
        override val message: String
            get() = "amount=${ `amount` }"

        override fun destroy() {
            
            Disposable.destroy(
                this.`amount`,
            )
        }
    }
    
}





public sealed class CannotConnectException: kotlin.Exception() {
    
    public class Include(
        public val `height`: kotlin.UInt,
    ) : CannotConnectException() {
        override val message: String
            get() = "height=${ `height` }"
    }
    
}





public sealed class CbfException: kotlin.Exception() {
    
    public class NodeStopped(
    ) : CbfException() {
        override val message: String
            get() = ""
    }
    
}




/**
 * Represents the observed position of some chain data.
 */

public sealed class ChainPosition: Disposable  {
    
    /**
     * The chain data is confirmed as it is anchored in the best chain by `A`.
     */
    public data class Confirmed(
        val `confirmationBlockTime`: ConfirmationBlockTime,
        /**
         * A child transaction that has been confirmed. Due to incomplete information,
         * it is only known that this transaction is confirmed at a chain height less than
         * or equal to this child TXID.
         */
        val `transitively`: Txid?,
    ) : ChainPosition() {
        override fun destroy() {
            Disposable.destroy(
                this.`confirmationBlockTime`,
                this.`transitively`,
            )
        }
    }
    
    /**
     * The transaction was last seen in the mempool at this timestamp.
     */
    public data class Unconfirmed(
        val `timestamp`: kotlin.ULong?,
    ) : ChainPosition() {
        override fun destroy() {
            Disposable.destroy(
                this.`timestamp`,
            )
        }
    }
    
}






/**
 * Policy regarding the use of change outputs when creating a transaction.
 */


public enum class ChangeSpendPolicy {
    
    /**
     * Use both change and non-change outputs (default).
     */
    CHANGE_ALLOWED,
    /**
     * Only use change outputs (see [`bdk_wallet::TxBuilder::only_spend_change`]).
     */
    ONLY_CHANGE,
    /**
     * Only use non-change outputs (see [`bdk_wallet::TxBuilder::do_not_spend_change`]).
     */
    CHANGE_FORBIDDEN;
    public companion object
}







public sealed class CreateTxException: kotlin.Exception() {
    
    public class Descriptor(
        public val `errorMessage`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Policy(
        public val `errorMessage`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class SpendingPolicyRequired(
        public val `kind`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "kind=${ `kind` }"
    }
    
    public class Version0(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
    public class Version1Csv(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
    public class LockTime(
        public val `requested`: kotlin.String,
        public val `required`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "requested=${ `requested` }, required=${ `required` }"
    }
    
    public class RbfSequenceCsv(
        public val `sequence`: kotlin.String,
        public val `csv`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "sequence=${ `sequence` }, csv=${ `csv` }"
    }
    
    public class FeeTooLow(
        public val `required`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "required=${ `required` }"
    }
    
    public class FeeRateTooLow(
        public val `required`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "required=${ `required` }"
    }
    
    public class NoUtxosSelected(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
    public class OutputBelowDustLimit(
        public val `index`: kotlin.ULong,
    ) : CreateTxException() {
        override val message: String
            get() = "index=${ `index` }"
    }
    
    public class ChangePolicyDescriptor(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
    public class CoinSelection(
        public val `errorMessage`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InsufficientFunds(
        public val `needed`: kotlin.ULong,
        public val `available`: kotlin.ULong,
    ) : CreateTxException() {
        override val message: String
            get() = "needed=${ `needed` }, available=${ `available` }"
    }
    
    public class NoRecipients(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
    public class Psbt(
        public val `errorMessage`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class MissingKeyOrigin(
        public val `key`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "key=${ `key` }"
    }
    
    public class UnknownUtxo(
        public val `outpoint`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "outpoint=${ `outpoint` }"
    }
    
    public class MissingNonWitnessUtxo(
        public val `outpoint`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "outpoint=${ `outpoint` }"
    }
    
    public class MiniscriptPsbt(
        public val `errorMessage`: kotlin.String,
    ) : CreateTxException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class PushBytesException(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
    public class LockTimeConversionException(
    ) : CreateTxException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class CreateWithPersistException: kotlin.Exception() {
    
    public class Persist(
        public val `errorMessage`: kotlin.String,
    ) : CreateWithPersistException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class DataAlreadyExists(
    ) : CreateWithPersistException() {
        override val message: String
            get() = ""
    }
    
    public class Descriptor(
        public val `errorMessage`: kotlin.String,
    ) : CreateWithPersistException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
}





public sealed class DescriptorException: kotlin.Exception() {
    
    public class InvalidHdKeyPath(
    ) : DescriptorException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidDescriptorChecksum(
    ) : DescriptorException() {
        override val message: String
            get() = ""
    }
    
    public class HardenedDerivationXpub(
    ) : DescriptorException() {
        override val message: String
            get() = ""
    }
    
    public class MultiPath(
    ) : DescriptorException() {
        override val message: String
            get() = ""
    }
    
    public class Key(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Policy(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidDescriptorCharacter(
        public val `char_`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "char_=${ `char_` }"
    }
    
    public class Bip32(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Base58(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Pk(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Miniscript(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Hex(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class ExternalAndInternalAreTheSame(
    ) : DescriptorException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class DescriptorKeyException: kotlin.Exception() {
    
    public class Parse(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorKeyException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidKeyType(
    ) : DescriptorKeyException() {
        override val message: String
            get() = ""
    }
    
    public class Bip32(
        public val `errorMessage`: kotlin.String,
    ) : DescriptorKeyException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
}




/**
 * Descriptor Type of the descriptor
 */


public enum class DescriptorType {
    
    /**
     * Bare descriptor(Contains the native P2pk)
     */
    BARE,
    /**
     * Pure Sh Descriptor. Does not contain nested Wsh/Wpkh
     */
    SH,
    /**
     * Pkh Descriptor
     */
    PKH,
    /**
     * Wpkh Descriptor
     */
    WPKH,
    /**
     * Wsh
     */
    WSH,
    /**
     * Sh Wrapped Wsh
     */
    SH_WSH,
    /**
     * Sh wrapped Wpkh
     */
    SH_WPKH,
    /**
     * Sh Sorted Multi
     */
    SH_SORTED_MULTI,
    /**
     * Wsh Sorted Multi
     */
    WSH_SORTED_MULTI,
    /**
     * Sh Wsh Sorted Multi
     */
    SH_WSH_SORTED_MULTI,
    /**
     * Tr Descriptor
     */
    TR;
    public companion object
}







public sealed class ElectrumException: kotlin.Exception() {
    
    public class IoException(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Json(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Hex(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Protocol(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Bitcoin(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class AlreadySubscribed(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
    public class NotSubscribed(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidResponse(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Message(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidDnsNameException(
        public val `domain`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "domain=${ `domain` }"
    }
    
    public class MissingDomain(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
    public class AllAttemptsErrored(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
    public class SharedIoException(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class CouldntLockReader(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
    public class Mpsc(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
    public class CouldNotCreateConnection(
        public val `errorMessage`: kotlin.String,
    ) : ElectrumException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class RequestAlreadyConsumed(
    ) : ElectrumException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class EsploraException: kotlin.Exception() {
    
    public class Minreq(
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class HttpResponse(
        public val `status`: kotlin.UShort,
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "status=${ `status` }, errorMessage=${ `errorMessage` }"
    }
    
    public class Parsing(
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class StatusCode(
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class BitcoinEncoding(
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class HexToArray(
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class HexToBytes(
        public val `errorMessage`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class TransactionNotFound(
    ) : EsploraException() {
        override val message: String
            get() = ""
    }
    
    public class HeaderHeightNotFound(
        public val `height`: kotlin.UInt,
    ) : EsploraException() {
        override val message: String
            get() = "height=${ `height` }"
    }
    
    public class HeaderHashNotFound(
    ) : EsploraException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidHttpHeaderName(
        public val `name`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "name=${ `name` }"
    }
    
    public class InvalidHttpHeaderValue(
        public val `value`: kotlin.String,
    ) : EsploraException() {
        override val message: String
            get() = "value=${ `value` }"
    }
    
    public class RequestAlreadyConsumed(
    ) : EsploraException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidResponse(
    ) : EsploraException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class ExtractTxException: kotlin.Exception() {
    
    public class AbsurdFeeRate(
        public val `feeRate`: kotlin.ULong,
    ) : ExtractTxException() {
        override val message: String
            get() = "feeRate=${ `feeRate` }"
    }
    
    public class MissingInputValue(
    ) : ExtractTxException() {
        override val message: String
            get() = ""
    }
    
    public class SendingTooMuch(
    ) : ExtractTxException() {
        override val message: String
            get() = ""
    }
    
    public class OtherExtractTxErr(
    ) : ExtractTxException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class FeeRateException: kotlin.Exception() {
    
    public class ArithmeticOverflow(
    ) : FeeRateException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class FromScriptException: kotlin.Exception() {
    
    public class UnrecognizedScript(
    ) : FromScriptException() {
        override val message: String
            get() = ""
    }
    
    public class WitnessProgram(
        public val `errorMessage`: kotlin.String,
    ) : FromScriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class WitnessVersion(
        public val `errorMessage`: kotlin.String,
    ) : FromScriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class OtherFromScriptErr(
    ) : FromScriptException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class HashParseException: kotlin.Exception() {
    
    public class InvalidHash(
        public val `len`: kotlin.UInt,
    ) : HashParseException() {
        override val message: String
            get() = "len=${ `len` }"
    }
    
    public class InvalidHexString(
        public val `hex`: kotlin.String,
    ) : HashParseException() {
        override val message: String
            get() = "hex=${ `hex` }"
    }
    
}




/**
 * A log message from the node.
 */

public sealed class Info {
    
    /**
     * All the required connections have been met. This is subject to change.
     */
    
    public object ConnectionsMet : Info() 
    
    
    /**
     * The node was able to successfully connect to a remote peer.
     */
    
    public object SuccessfulHandshake : Info() 
    
    
    /**
     * A percentage value of filters that have been scanned.
     */
    public data class Progress(
        /**
         * The height of the local block chain.
         */
        val `chainHeight`: kotlin.UInt,
        /**
         * The percent of filters downloaded.
         */
        val `filtersDownloadedPercent`: kotlin.Float,
    ) : Info() {
    }
    
    /**
     * A relevant block was downloaded from a peer.
     */
    public data class BlockReceived(
        val v1: kotlin.String,
    ) : Info() {
    }
    
}






/**
 * Types of keychains.
 */


public enum class KeychainKind {
    
    /**
     * External keychain, used for deriving recipient addresses.
     */
    EXTERNAL,
    /**
     * Internal keychain, used for deriving change addresses.
     */
    INTERNAL;
    public companion object
}







public sealed class LoadWithPersistException: kotlin.Exception() {
    
    public class Persist(
        public val `errorMessage`: kotlin.String,
    ) : LoadWithPersistException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidChangeSet(
        public val `errorMessage`: kotlin.String,
    ) : LoadWithPersistException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class CouldNotLoad(
    ) : LoadWithPersistException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class LockTime {
    
    public data class Blocks(
        val `height`: kotlin.UInt,
    ) : LockTime() {
    }
    
    public data class Seconds(
        val `consensusTime`: kotlin.UInt,
    ) : LockTime() {
    }
    
}







public sealed class MiniscriptException: kotlin.Exception() {
    
    public class AbsoluteLockTime(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class AddrException(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class AddrP2shException(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class AnalysisException(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class AtOutsideOr(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class BadDescriptor(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class BareDescriptorAddr(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class CmsTooManyKeys(
        public val `keys`: kotlin.UInt,
    ) : MiniscriptException() {
        override val message: String
            get() = "keys=${ `keys` }"
    }
    
    public class ContextException(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class CouldNotSatisfy(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class ExpectedChar(
        public val `char_`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "char_=${ `char_` }"
    }
    
    public class ImpossibleSatisfaction(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidOpcode(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidPush(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class LiftException(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class MaxRecursiveDepthExceeded(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class MissingSig(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class MultiATooManyKeys(
        public val `keys`: kotlin.ULong,
    ) : MiniscriptException() {
        override val message: String
            get() = "keys=${ `keys` }"
    }
    
    public class MultiColon(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class MultipathDescLenMismatch(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class NonMinimalVerify(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class NonStandardBareScript(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class NonTopLevel(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class ParseThreshold(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class PolicyException(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class PubKeyCtxException(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class RelativeLockTime(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class Script(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Secp(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Threshold(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class TrNoScriptCode(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class Trailing(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class TypeCheck(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Unexpected(
        public val `errorMessage`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class UnexpectedStart(
    ) : MiniscriptException() {
        override val message: String
            get() = ""
    }
    
    public class UnknownWrapper(
        public val `char_`: kotlin.String,
    ) : MiniscriptException() {
        override val message: String
            get() = "char_=${ `char_` }"
    }
    
    public class Unprintable(
        public val `byte`: kotlin.UByte,
    ) : MiniscriptException() {
        override val message: String
            get() = "byte=${ `byte` }"
    }
    
}




/**
 * The cryptocurrency network to act on.
 *
 * This is an exhaustive enum, meaning that we cannot add any future networks without defining a
 * new, incompatible version of this type. If you are using this type directly and wish to support
 * the new network, this will be a breaking change to your APIs and likely require changes in your
 * code.
 *
 * If you are concerned about forward compatibility, consider using T: Into<Params> instead of this
 * type as a parameter to functions in your public API, or directly using the Params type.
 */


public enum class Network {
    
    BITCOIN,
    TESTNET,
    TESTNET4,
    SIGNET,
    REGTEST;
    public companion object
}







public sealed class ParseAmountException: kotlin.Exception() {
    
    public class OutOfRange(
    ) : ParseAmountException() {
        override val message: String
            get() = ""
    }
    
    public class TooPrecise(
    ) : ParseAmountException() {
        override val message: String
            get() = ""
    }
    
    public class MissingDigits(
    ) : ParseAmountException() {
        override val message: String
            get() = ""
    }
    
    public class InputTooLarge(
    ) : ParseAmountException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidCharacter(
        public val `errorMessage`: kotlin.String,
    ) : ParseAmountException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class OtherParseAmountErr(
    ) : ParseAmountException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class PersistenceException: kotlin.Exception() {
    
    public class Reason(
        public val `errorMessage`: kotlin.String,
    ) : PersistenceException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
}





public sealed class PkOrF {
    
    public data class Pubkey(
        val `value`: kotlin.String,
    ) : PkOrF() {
    }
    
    public data class XOnlyPubkey(
        val `value`: kotlin.String,
    ) : PkOrF() {
    }
    
    public data class Fingerprint(
        val `value`: kotlin.String,
    ) : PkOrF() {
    }
    
}







public sealed class PsbtException: kotlin.Exception() {
    
    public class InvalidMagic(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class MissingUtxo(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidSeparator(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class PsbtUtxoOutOfBounds(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidKey(
        public val `key`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "key=${ `key` }"
    }
    
    public class InvalidProprietaryKey(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class DuplicateKey(
        public val `key`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "key=${ `key` }"
    }
    
    public class UnsignedTxHasScriptSigs(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class UnsignedTxHasScriptWitnesses(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class MustHaveUnsignedTx(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class NoMorePairs(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class UnexpectedUnsignedTx(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class NonStandardSighashType(
        public val `sighash`: kotlin.UInt,
    ) : PsbtException() {
        override val message: String
            get() = "sighash=${ `sighash` }"
    }
    
    public class InvalidHash(
        public val `hash`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "hash=${ `hash` }"
    }
    
    public class InvalidPreimageHashPair(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class CombineInconsistentKeySources(
        public val `xpub`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "xpub=${ `xpub` }"
    }
    
    public class ConsensusEncoding(
        public val `encodingError`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "encodingError=${ `encodingError` }"
    }
    
    public class NegativeFee(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class FeeOverflow(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidPublicKey(
        public val `errorMessage`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidSecp256k1PublicKey(
        public val `secp256k1Error`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "secp256k1Error=${ `secp256k1Error` }"
    }
    
    public class InvalidXOnlyPublicKey(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidEcdsaSignature(
        public val `errorMessage`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidTaprootSignature(
        public val `errorMessage`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class InvalidControlBlock(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidLeafVersion(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class Taproot(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class TapTree(
        public val `errorMessage`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class XPubKey(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class Version(
        public val `errorMessage`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class PartialDataConsumption(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
    public class Io(
        public val `errorMessage`: kotlin.String,
    ) : PsbtException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class OtherPsbtErr(
    ) : PsbtException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class PsbtFinalizeException: kotlin.Exception() {
    
    public class InputException(
        public val `reason`: kotlin.String,
        public val `index`: kotlin.UInt,
    ) : PsbtFinalizeException() {
        override val message: String
            get() = "reason=${ `reason` }, index=${ `index` }"
    }
    
    public class WrongInputCount(
        public val `inTx`: kotlin.UInt,
        public val `inMap`: kotlin.UInt,
    ) : PsbtFinalizeException() {
        override val message: String
            get() = "inTx=${ `inTx` }, inMap=${ `inMap` }"
    }
    
    public class InputIdxOutofBounds(
        public val `psbtInp`: kotlin.UInt,
        public val `requested`: kotlin.UInt,
    ) : PsbtFinalizeException() {
        override val message: String
            get() = "psbtInp=${ `psbtInp` }, requested=${ `requested` }"
    }
    
}





public sealed class PsbtParseException: kotlin.Exception() {
    
    public class PsbtEncoding(
        public val `errorMessage`: kotlin.String,
    ) : PsbtParseException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Base64Encoding(
        public val `errorMessage`: kotlin.String,
    ) : PsbtParseException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
}






public enum class RecoveryPoint {
    
    GENESIS_BLOCK,
    SEGWIT_ACTIVATION,
    TAPROOT_ACTIVATION;
    public companion object
}







public sealed class RequestBuilderException: kotlin.Exception() {
    
    public class RequestAlreadyConsumed(
    ) : RequestBuilderException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class Satisfaction {
    
    public data class Partial(
        val `n`: kotlin.ULong,
        val `m`: kotlin.ULong,
        val `items`: List<kotlin.ULong>,
        val `sorted`: kotlin.Boolean?,
        val `conditions`: Map<kotlin.UInt, List<Condition>>,
    ) : Satisfaction() {
    }
    
    public data class PartialComplete(
        val `n`: kotlin.ULong,
        val `m`: kotlin.ULong,
        val `items`: List<kotlin.ULong>,
        val `sorted`: kotlin.Boolean?,
        val `conditions`: Map<List<kotlin.UInt>, List<Condition>>,
    ) : Satisfaction() {
    }
    
    public data class Complete(
        val `condition`: Condition,
    ) : Satisfaction() {
    }
    
    public data class None(
        val `msg`: kotlin.String,
    ) : Satisfaction() {
    }
    
}







public sealed class SatisfiableItem: Disposable  {
    
    public data class EcdsaSignature(
        val `key`: PkOrF,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`key`,
            )
        }
    }
    
    public data class SchnorrSignature(
        val `key`: PkOrF,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`key`,
            )
        }
    }
    
    public data class Sha256Preimage(
        val `hash`: kotlin.String,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`hash`,
            )
        }
    }
    
    public data class Hash256Preimage(
        val `hash`: kotlin.String,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`hash`,
            )
        }
    }
    
    public data class Ripemd160Preimage(
        val `hash`: kotlin.String,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`hash`,
            )
        }
    }
    
    public data class Hash160Preimage(
        val `hash`: kotlin.String,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`hash`,
            )
        }
    }
    
    public data class AbsoluteTimelock(
        val `value`: LockTime,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`value`,
            )
        }
    }
    
    public data class RelativeTimelock(
        val `value`: kotlin.UInt,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`value`,
            )
        }
    }
    
    public data class Multisig(
        val `keys`: List<PkOrF>,
        val `threshold`: kotlin.ULong,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`keys`,
                this.`threshold`,
            )
        }
    }
    
    public data class Thresh(
        val `items`: List<Policy>,
        val `threshold`: kotlin.ULong,
    ) : SatisfiableItem() {
        override fun destroy() {
            Disposable.destroy(
                this.`items`,
                this.`threshold`,
            )
        }
    }
    
}






/**
 * Sync a wallet from the last known block hash or recover a wallet from a specified recovery
 * point.
 */

public sealed class ScanType {
    
    /**
     * Sync an existing wallet from the last stored chain checkpoint.
     */
    
    public object Sync : ScanType() 
    
    
    /**
     * Recover an existing wallet by scanning from the specified height.
     */
    public data class Recovery(
        /**
         * The estimated number of scripts the user has revealed for the wallet being recovered.
         * If unknown, a conservative estimate, say 1,000, could be used.
         */
        val `usedScriptIndex`: kotlin.UInt,
        /**
         * A relevant starting point or soft fork to start the sync.
         */
        val `checkpoint`: RecoveryPoint,
    ) : ScanType() {
    }
    
}







public sealed class SignerException: kotlin.Exception() {
    
    public class MissingKey(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidKey(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class UserCanceled(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class InputIndexOutOfRange(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class MissingNonWitnessUtxo(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidNonWitnessUtxo(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class MissingWitnessUtxo(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class MissingWitnessScript(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class MissingHdKeypath(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class NonStandardSighash(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidSighash(
    ) : SignerException() {
        override val message: String
            get() = ""
    }
    
    public class SighashP2wpkh(
        public val `errorMessage`: kotlin.String,
    ) : SignerException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class SighashTaproot(
        public val `errorMessage`: kotlin.String,
    ) : SignerException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class TxInputsIndexException(
        public val `errorMessage`: kotlin.String,
    ) : SignerException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class MiniscriptPsbt(
        public val `errorMessage`: kotlin.String,
    ) : SignerException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class External(
        public val `errorMessage`: kotlin.String,
    ) : SignerException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
    public class Psbt(
        public val `errorMessage`: kotlin.String,
    ) : SignerException() {
        override val message: String
            get() = "errorMessage=${ `errorMessage` }"
    }
    
}





public sealed class TransactionException: kotlin.Exception() {
    
    public class Io(
    ) : TransactionException() {
        override val message: String
            get() = ""
    }
    
    public class OversizedVectorAllocation(
    ) : TransactionException() {
        override val message: String
            get() = ""
    }
    
    public class InvalidChecksum(
        public val `expected`: kotlin.String,
        public val `actual`: kotlin.String,
    ) : TransactionException() {
        override val message: String
            get() = "expected=${ `expected` }, actual=${ `actual` }"
    }
    
    public class NonMinimalVarInt(
    ) : TransactionException() {
        override val message: String
            get() = ""
    }
    
    public class ParseFailed(
    ) : TransactionException() {
        override val message: String
            get() = ""
    }
    
    public class UnsupportedSegwitFlag(
        public val `flag`: kotlin.UByte,
    ) : TransactionException() {
        override val message: String
            get() = "flag=${ `flag` }"
    }
    
    public class OtherTransactionErr(
    ) : TransactionException() {
        override val message: String
            get() = ""
    }
    
}





public sealed class TxidParseException: kotlin.Exception() {
    
    public class InvalidTxid(
        public val `txid`: kotlin.String,
    ) : TxidParseException() {
        override val message: String
            get() = "txid=${ `txid` }"
    }
    
}




/**
 * Warnings a node may issue while running.
 */

public sealed class Warning {
    
    /**
     * The node is looking for connections to peers.
     */
    
    public object NeedConnections : Warning() 
    
    
    /**
     * A connection to a peer timed out.
     */
    
    public object PeerTimedOut : Warning() 
    
    
    /**
     * The node was unable to connect to a peer in the database.
     */
    
    public object CouldNotConnect : Warning() 
    
    
    /**
     * A connection was maintained, but the peer does not signal for compact block filers.
     */
    
    public object NoCompactFilters : Warning() 
    
    
    /**
     * The node has been waiting for new inv and will find new peers to avoid block withholding.
     */
    
    public object PotentialStaleTip : Warning() 
    
    
    /**
     * A peer sent us a peer-to-peer message the node did not request.
     */
    
    public object UnsolicitedMessage : Warning() 
    
    
    /**
     * A transaction got rejected, likely for being an insufficient fee or non-standard transaction.
     */
    public data class TransactionRejected(
        val `wtxid`: kotlin.String,
        val `reason`: kotlin.String?,
    ) : Warning() {
    }
    
    /**
     * The peer sent us a potential fork.
     */
    
    public object EvaluatingFork : Warning() 
    
    
    /**
     * An unexpected error occurred processing a peer-to-peer message.
     */
    public data class UnexpectedSyncError(
        val `warning`: kotlin.String,
    ) : Warning() {
    }
    
    /**
     * The node failed to respond to a message sent from the client.
     */
    
    public object RequestFailed : Warning() 
    
    
}








public enum class WordCount {
    
    WORDS12,
    WORDS15,
    WORDS18,
    WORDS21,
    WORDS24;
    public companion object
}





































































































































