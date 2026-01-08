#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>


typedef struct RustBuffer
{
    int64_t capacity;
    int64_t len;
    uint8_t *_Nullable data;
} RustBuffer;

typedef struct ForeignBytes
{
    int32_t len;
    const uint8_t *_Nullable data;
} ForeignBytes;

typedef struct UniffiRustCallStatus {
  int8_t code;
  RustBuffer errorBuf;
} UniffiRustCallStatus;

// Public interface members begin here.


// Contains loading, initialization code,
// and the FFI Function declarations.

typedef void (*UniffiRustFutureContinuationCallback)(int64_t, int8_t
    );

typedef void (*UniffiForeignFutureFree)(int64_t
    );

typedef void (*UniffiCallbackInterfaceFree)(int64_t
    );

typedef struct UniffiForeignFuture {
    int64_t handle;
    UniffiForeignFutureFree free;
} UniffiForeignFuture;

typedef struct UniffiForeignFutureStructU8 {
    int8_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructU8;

typedef void (*UniffiForeignFutureCompleteU8)(int64_t, UniffiForeignFutureStructU8
    );

typedef struct UniffiForeignFutureStructI8 {
    int8_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructI8;

typedef void (*UniffiForeignFutureCompleteI8)(int64_t, UniffiForeignFutureStructI8
    );

typedef struct UniffiForeignFutureStructU16 {
    int16_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructU16;

typedef void (*UniffiForeignFutureCompleteU16)(int64_t, UniffiForeignFutureStructU16
    );

typedef struct UniffiForeignFutureStructI16 {
    int16_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructI16;

typedef void (*UniffiForeignFutureCompleteI16)(int64_t, UniffiForeignFutureStructI16
    );

typedef struct UniffiForeignFutureStructU32 {
    int32_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructU32;

typedef void (*UniffiForeignFutureCompleteU32)(int64_t, UniffiForeignFutureStructU32
    );

typedef struct UniffiForeignFutureStructI32 {
    int32_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructI32;

typedef void (*UniffiForeignFutureCompleteI32)(int64_t, UniffiForeignFutureStructI32
    );

typedef struct UniffiForeignFutureStructU64 {
    int64_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructU64;

typedef void (*UniffiForeignFutureCompleteU64)(int64_t, UniffiForeignFutureStructU64
    );

typedef struct UniffiForeignFutureStructI64 {
    int64_t returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructI64;

typedef void (*UniffiForeignFutureCompleteI64)(int64_t, UniffiForeignFutureStructI64
    );

typedef struct UniffiForeignFutureStructF32 {
    float returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructF32;

typedef void (*UniffiForeignFutureCompleteF32)(int64_t, UniffiForeignFutureStructF32
    );

typedef struct UniffiForeignFutureStructF64 {
    double returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructF64;

typedef void (*UniffiForeignFutureCompleteF64)(int64_t, UniffiForeignFutureStructF64
    );

typedef struct UniffiForeignFutureStructPointer {
    void * returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructPointer;

typedef void (*UniffiForeignFutureCompletePointer)(int64_t, UniffiForeignFutureStructPointer
    );

typedef struct UniffiForeignFutureStructRustBuffer {
    RustBuffer returnValue;
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructRustBuffer;

typedef void (*UniffiForeignFutureCompleteRustBuffer)(int64_t, UniffiForeignFutureStructRustBuffer
    );

typedef struct UniffiForeignFutureStructVoid {
    UniffiRustCallStatus callStatus;
} UniffiForeignFutureStructVoid;

typedef void (*UniffiForeignFutureCompleteVoid)(int64_t, UniffiForeignFutureStructVoid
    );

typedef void (*UniffiCallbackInterfaceFullScanScriptInspectorMethod0)(int64_t, RustBuffer, int32_t, void *, void *, 
        UniffiRustCallStatus *_Nonnull uniffiCallStatus
    );

typedef void (*UniffiCallbackInterfacePersistenceMethod0)(int64_t, void **, 
        UniffiRustCallStatus *_Nonnull uniffiCallStatus
    );

typedef void (*UniffiCallbackInterfacePersistenceMethod1)(int64_t, void *, void *, 
        UniffiRustCallStatus *_Nonnull uniffiCallStatus
    );

typedef void (*UniffiCallbackInterfaceSyncScriptInspectorMethod0)(int64_t, void *, int64_t, void *, 
        UniffiRustCallStatus *_Nonnull uniffiCallStatus
    );

typedef struct UniffiVTableCallbackInterfaceFullScanScriptInspector {
    UniffiCallbackInterfaceFullScanScriptInspectorMethod0 inspect;
    UniffiCallbackInterfaceFree uniffiFree;
} UniffiVTableCallbackInterfaceFullScanScriptInspector;

typedef struct UniffiVTableCallbackInterfacePersistence {
    UniffiCallbackInterfacePersistenceMethod0 initialize;
    UniffiCallbackInterfacePersistenceMethod1 persist;
    UniffiCallbackInterfaceFree uniffiFree;
} UniffiVTableCallbackInterfacePersistence;

typedef struct UniffiVTableCallbackInterfaceSyncScriptInspector {
    UniffiCallbackInterfaceSyncScriptInspectorMethod0 inspect;
    UniffiCallbackInterfaceFree uniffiFree;
} UniffiVTableCallbackInterfaceSyncScriptInspector;

void * uniffi_bdk_fn_clone_address(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_address(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_address_from_script(void * script, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_address_new(RustBuffer address, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_address_is_valid_for_network(void * ptr, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_address_script_pubkey(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_address_to_address_data(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_address_to_qr_uri(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_address_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_address_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_address_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_amount(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_amount(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_amount_from_btc(double btc, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_amount_from_sat(int64_t satoshi, UniffiRustCallStatus *_Nonnull out_status
);
double uniffi_bdk_fn_method_amount_to_btc(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_amount_to_sat(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_blockhash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_blockhash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_blockhash_from_bytes(RustBuffer bytes, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_blockhash_from_string(RustBuffer hex, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_blockhash_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_blockhash_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_blockhash_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_blockhash_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_blockhash_uniffi_trait_hash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_bumpfeetxbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_bumpfeetxbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_bumpfeetxbuilder_new(void * txid, void * feeRate, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_bumpfeetxbuilder_allow_dust(void * ptr, int8_t allowDust, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_bumpfeetxbuilder_current_height(void * ptr, int32_t height, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_bumpfeetxbuilder_finish(void * ptr, void * wallet, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_bumpfeetxbuilder_nlocktime(void * ptr, RustBuffer locktime, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_bumpfeetxbuilder_set_exact_sequence(void * ptr, int32_t nsequence, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_bumpfeetxbuilder_version(void * ptr, int32_t version, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_cbfbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_cbfbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_cbfbuilder_new(UniffiRustCallStatus *_Nonnull out_status
    
);
RustBuffer uniffi_bdk_fn_method_cbfbuilder_build(void * ptr, void * wallet, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_cbfbuilder_configure_timeout_millis(void * ptr, int64_t handshake, int64_t response, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_cbfbuilder_connections(void * ptr, int8_t connections, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_cbfbuilder_data_dir(void * ptr, RustBuffer dataDir, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_cbfbuilder_peers(void * ptr, RustBuffer peers, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_cbfbuilder_scan_type(void * ptr, RustBuffer scanType, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_cbfbuilder_socks5_proxy(void * ptr, RustBuffer proxy, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_cbfclient(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_cbfclient(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_cbfclient_average_fee_rate(void * ptr, void * blockhash
);
int64_t uniffi_bdk_fn_method_cbfclient_broadcast(void * ptr, void * transaction
);
void uniffi_bdk_fn_method_cbfclient_connect(void * ptr, RustBuffer peer, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_cbfclient_is_running(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_cbfclient_lookup_host(void * ptr, RustBuffer hostname, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_cbfclient_min_broadcast_feerate(void * ptr
);
int64_t uniffi_bdk_fn_method_cbfclient_next_info(void * ptr
);
int64_t uniffi_bdk_fn_method_cbfclient_next_warning(void * ptr
);
void uniffi_bdk_fn_method_cbfclient_shutdown(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_cbfclient_update(void * ptr
);
void * uniffi_bdk_fn_clone_cbfnode(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_cbfnode(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_cbfnode_run(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_changeset(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_changeset(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_from_aggregate(RustBuffer descriptor, RustBuffer changeDescriptor, RustBuffer network, RustBuffer localChain, RustBuffer txGraph, RustBuffer indexer, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_from_descriptor_and_network(RustBuffer descriptor, RustBuffer changeDescriptor, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_from_indexer_changeset(RustBuffer indexerChanges, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_from_local_chain_changes(RustBuffer localChainChanges, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_from_merge(void * left, void * right, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_from_tx_graph_changeset(RustBuffer txGraphChangeset, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_changeset_new(UniffiRustCallStatus *_Nonnull out_status
    
);
RustBuffer uniffi_bdk_fn_method_changeset_change_descriptor(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_changeset_descriptor(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_changeset_indexer_changeset(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_changeset_localchain_changeset(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_changeset_network(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_changeset_tx_graph_changeset(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_derivationpath(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_derivationpath(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_derivationpath_master(UniffiRustCallStatus *_Nonnull out_status
    
);
void * uniffi_bdk_fn_constructor_derivationpath_new(RustBuffer path, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_derivationpath_is_empty(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_derivationpath_is_master(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_derivationpath_len(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_derivationpath_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_descriptor(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_descriptor(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new(RustBuffer descriptor, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip44(void * secretKey, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip44_public(void * publicKey, RustBuffer fingerprint, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip49(void * secretKey, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip49_public(void * publicKey, RustBuffer fingerprint, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip84(void * secretKey, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip84_public(void * publicKey, RustBuffer fingerprint, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip86(void * secretKey, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptor_new_bip86_public(void * publicKey, RustBuffer fingerprint, RustBuffer keychainKind, RustBuffer network, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptor_desc_type(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_descriptor_descriptor_id(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_descriptor_is_multipath(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_descriptor_max_weight_to_satisfy(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptor_to_single_descriptors(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptor_to_string_with_secret(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptor_uniffi_trait_debug(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptor_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_descriptorid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_descriptorid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptorid_from_bytes(RustBuffer bytes, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptorid_from_string(RustBuffer hex, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorid_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorid_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_descriptorid_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_descriptorid_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_descriptorid_uniffi_trait_hash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_descriptorpublickey(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_descriptorpublickey(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptorpublickey_from_string(RustBuffer publicKey, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_descriptorpublickey_derive(void * ptr, void * path, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_descriptorpublickey_extend(void * ptr, void * path, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_descriptorpublickey_is_multipath(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorpublickey_master_fingerprint(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorpublickey_uniffi_trait_debug(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorpublickey_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_descriptorsecretkey(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_descriptorsecretkey(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptorsecretkey_from_string(RustBuffer privateKey, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_descriptorsecretkey_new(RustBuffer network, void * mnemonic, RustBuffer password, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_descriptorsecretkey_as_public(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_descriptorsecretkey_derive(void * ptr, void * path, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_descriptorsecretkey_extend(void * ptr, void * path, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorsecretkey_secret_bytes(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorsecretkey_uniffi_trait_debug(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_descriptorsecretkey_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_electrumclient(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_electrumclient(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_electrumclient_new(RustBuffer url, RustBuffer socks5, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_electrumclient_block_headers_subscribe(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
double uniffi_bdk_fn_method_electrumclient_estimate_fee(void * ptr, int64_t number, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_electrumclient_full_scan(void * ptr, void * request, int64_t stopGap, int64_t batchSize, int8_t fetchPrevTxouts, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_electrumclient_ping(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_electrumclient_server_features(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_electrumclient_sync(void * ptr, void * request, int64_t batchSize, int8_t fetchPrevTxouts, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_electrumclient_transaction_broadcast(void * ptr, void * tx, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_esploraclient(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_esploraclient(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_esploraclient_new(RustBuffer url, RustBuffer proxy, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_esploraclient_broadcast(void * ptr, void * transaction, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_esploraclient_full_scan(void * ptr, void * request, int64_t stopGap, int64_t parallelRequests, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_esploraclient_get_block_hash(void * ptr, int32_t blockHeight, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_esploraclient_get_fee_estimates(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int32_t uniffi_bdk_fn_method_esploraclient_get_height(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_esploraclient_get_tx(void * ptr, void * txid, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_esploraclient_get_tx_info(void * ptr, void * txid, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_esploraclient_get_tx_status(void * ptr, void * txid, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_esploraclient_sync(void * ptr, void * request, int64_t parallelRequests, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_feerate(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_feerate(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_feerate_from_sat_per_kwu(int64_t satKwu, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_feerate_from_sat_per_vb(int64_t satVb, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_feerate_to_sat_per_kwu(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_feerate_to_sat_per_vb_ceil(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_feerate_to_sat_per_vb_floor(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_fullscanrequest(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_fullscanrequest(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_fullscanrequestbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_fullscanrequestbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_fullscanrequestbuilder_build(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_fullscanrequestbuilder_inspect_spks_for_all_keychains(void * ptr, void * inspector, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_fullscanscriptinspector(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_fullscanscriptinspector(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_init_callback_vtable_fullscanscriptinspector(UniffiVTableCallbackInterfaceFullScanScriptInspector const * vtable
);
void uniffi_bdk_fn_method_fullscanscriptinspector_inspect(void * ptr, RustBuffer keychain, int32_t index, void * script, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_hashableoutpoint(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_hashableoutpoint(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_hashableoutpoint_new(RustBuffer outpoint, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_hashableoutpoint_outpoint(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_debug(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_hashableoutpoint_uniffi_trait_hash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_ipaddress(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_ipaddress(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_ipaddress_from_ipv4(int8_t q1, int8_t q2, int8_t q3, int8_t q4, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_ipaddress_from_ipv6(int16_t a, int16_t b, int16_t c, int16_t d, int16_t e, int16_t f, int16_t g, int16_t h, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_mnemonic(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_mnemonic(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_mnemonic_from_entropy(RustBuffer entropy, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_mnemonic_from_string(RustBuffer mnemonic, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_mnemonic_new(RustBuffer wordCount, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_mnemonic_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_persistence(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_persistence(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_init_callback_vtable_persistence(UniffiVTableCallbackInterfacePersistence const * vtable
);
void * uniffi_bdk_fn_method_persistence_initialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_persistence_persist(void * ptr, void * changeset, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_persister(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_persister(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_persister_custom(void * persistence, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_persister_new_in_memory(UniffiRustCallStatus *_Nonnull out_status
    
);
void * uniffi_bdk_fn_constructor_persister_new_sqlite(RustBuffer path, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_policy(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_policy(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_policy_as_string(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_policy_contribution(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_policy_id(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_policy_item(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_policy_requires_path(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_policy_satisfaction(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_psbt(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_psbt(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_psbt_from_file(RustBuffer path, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_psbt_from_unsigned_tx(void * tx, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_psbt_new(RustBuffer psbtBase64, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_psbt_combine(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_psbt_extract_tx(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_psbt_fee(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_psbt_finalize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_psbt_input(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_psbt_json_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_psbt_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_psbt_spend_utxo(void * ptr, int64_t inputIndex, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_psbt_write_to_file(void * ptr, RustBuffer path, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_script(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_script(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_script_new(RustBuffer rawOutputScript, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_script_to_bytes(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_script_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_syncrequest(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_syncrequest(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_syncrequestbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_syncrequestbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_syncrequestbuilder_build(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_syncrequestbuilder_inspect_spks(void * ptr, void * inspector, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_syncscriptinspector(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_syncscriptinspector(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_init_callback_vtable_syncscriptinspector(UniffiVTableCallbackInterfaceSyncScriptInspector const * vtable
);
void uniffi_bdk_fn_method_syncscriptinspector_inspect(void * ptr, void * script, int64_t total, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_transaction(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_transaction(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_transaction_new(RustBuffer transactionBytes, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_transaction_compute_txid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_transaction_compute_wtxid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_transaction_input(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_transaction_is_coinbase(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_transaction_is_explicitly_rbf(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_transaction_is_lock_time_enabled(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int32_t uniffi_bdk_fn_method_transaction_lock_time(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_transaction_output(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_transaction_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_transaction_total_size(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int32_t uniffi_bdk_fn_method_transaction_version(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_transaction_vsize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_transaction_weight(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_transaction_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_transaction_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_transaction_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_txbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_txbuilder(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_txbuilder_new(UniffiRustCallStatus *_Nonnull out_status
    
);
void * uniffi_bdk_fn_method_txbuilder_add_data(void * ptr, RustBuffer data, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_add_global_xpubs(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_add_recipient(void * ptr, void * script, void * amount, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_add_unspendable(void * ptr, RustBuffer unspendable, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_add_utxo(void * ptr, RustBuffer outpoint, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_add_utxos(void * ptr, RustBuffer outpoints, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_allow_dust(void * ptr, int8_t allowDust, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_change_policy(void * ptr, RustBuffer changePolicy, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_current_height(void * ptr, int32_t height, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_do_not_spend_change(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_drain_to(void * ptr, void * script, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_drain_wallet(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_exclude_below_confirmations(void * ptr, int32_t minConfirms, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_exclude_unconfirmed(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_fee_absolute(void * ptr, void * feeAmount, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_fee_rate(void * ptr, void * feeRate, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_finish(void * ptr, void * wallet, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_manually_selected_only(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_nlocktime(void * ptr, RustBuffer locktime, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_only_spend_change(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_policy_path(void * ptr, RustBuffer policyPath, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_set_exact_sequence(void * ptr, int32_t nsequence, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_set_recipients(void * ptr, RustBuffer recipients, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_unspendable(void * ptr, RustBuffer unspendable, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_txbuilder_version(void * ptr, int32_t version, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_txmerklenode(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_txmerklenode(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_txmerklenode_from_bytes(RustBuffer bytes, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_txmerklenode_from_string(RustBuffer hex, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_txmerklenode_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_txmerklenode_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_txmerklenode_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_txmerklenode_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_txmerklenode_uniffi_trait_hash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_txid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_txid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_txid_from_bytes(RustBuffer bytes, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_txid_from_string(RustBuffer hex, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_txid_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_txid_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_txid_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_txid_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_txid_uniffi_trait_hash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_update(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_update(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_wallet(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_wallet(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wallet_create_from_two_path_descriptor(void * twoPathDescriptor, RustBuffer network, void * persister, int32_t lookahead, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wallet_create_single(void * descriptor, RustBuffer network, void * persister, int32_t lookahead, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wallet_load(void * descriptor, void * changeDescriptor, void * persister, int32_t lookahead, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wallet_load_single(void * descriptor, void * persister, int32_t lookahead, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wallet_new(void * descriptor, void * changeDescriptor, RustBuffer network, void * persister, int32_t lookahead, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_wallet_apply_evicted_txs(void * ptr, RustBuffer evictedTxs, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_wallet_apply_unconfirmed_txs(void * ptr, RustBuffer unconfirmedTxs, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_wallet_apply_update(void * ptr, void * update, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_balance(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_wallet_calculate_fee(void * ptr, void * tx, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_wallet_calculate_fee_rate(void * ptr, void * tx, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_wallet_cancel_tx(void * ptr, void * tx, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_derivation_index(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_derivation_of_spk(void * ptr, void * spk, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_descriptor_checksum(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wallet_finalize_psbt(void * ptr, void * psbt, RustBuffer signOptions, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_get_tx(void * ptr, void * txid, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_get_utxo(void * ptr, RustBuffer op, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_method_wallet_insert_txout(void * ptr, RustBuffer outpoint, RustBuffer txout, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wallet_is_mine(void * ptr, void * script, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_latest_checkpoint(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_list_output(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_list_unspent(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_list_unused_addresses(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wallet_mark_used(void * ptr, RustBuffer keychain, int32_t index, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_network(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int32_t uniffi_bdk_fn_method_wallet_next_derivation_index(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_next_unused_address(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_peek_address(void * ptr, RustBuffer keychain, int32_t index, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wallet_persist(void * ptr, void * persister, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_policies(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_public_descriptor(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_reveal_addresses_to(void * ptr, RustBuffer keychain, int32_t index, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_reveal_next_address(void * ptr, RustBuffer keychain, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_sent_and_received(void * ptr, void * tx, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wallet_sign(void * ptr, void * psbt, RustBuffer signOptions, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_staged(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_wallet_start_full_scan(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_method_wallet_start_sync_with_revealed_spks(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_take_staged(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_transactions(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wallet_tx_details(void * ptr, void * txid, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wallet_unmark_used(void * ptr, RustBuffer keychain, int32_t index, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_clone_wtxid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void uniffi_bdk_fn_free_wtxid(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wtxid_from_bytes(RustBuffer bytes, UniffiRustCallStatus *_Nonnull out_status
);
void * uniffi_bdk_fn_constructor_wtxid_from_string(RustBuffer hex, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wtxid_serialize(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer uniffi_bdk_fn_method_wtxid_uniffi_trait_display(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wtxid_uniffi_trait_eq_eq(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int8_t uniffi_bdk_fn_method_wtxid_uniffi_trait_eq_ne(void * ptr, void * other, UniffiRustCallStatus *_Nonnull out_status
);
int64_t uniffi_bdk_fn_method_wtxid_uniffi_trait_hash(void * ptr, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer ffi_bdk_rustbuffer_alloc(int64_t size, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer ffi_bdk_rustbuffer_from_bytes(ForeignBytes bytes, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rustbuffer_free(RustBuffer buf, UniffiRustCallStatus *_Nonnull out_status
);
RustBuffer ffi_bdk_rustbuffer_reserve(RustBuffer buf, int64_t additional, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_u8(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_u8(int64_t handle
);
void ffi_bdk_rust_future_free_u8(int64_t handle
);
int8_t ffi_bdk_rust_future_complete_u8(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_i8(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_i8(int64_t handle
);
void ffi_bdk_rust_future_free_i8(int64_t handle
);
int8_t ffi_bdk_rust_future_complete_i8(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_u16(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_u16(int64_t handle
);
void ffi_bdk_rust_future_free_u16(int64_t handle
);
int16_t ffi_bdk_rust_future_complete_u16(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_i16(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_i16(int64_t handle
);
void ffi_bdk_rust_future_free_i16(int64_t handle
);
int16_t ffi_bdk_rust_future_complete_i16(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_u32(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_u32(int64_t handle
);
void ffi_bdk_rust_future_free_u32(int64_t handle
);
int32_t ffi_bdk_rust_future_complete_u32(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_i32(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_i32(int64_t handle
);
void ffi_bdk_rust_future_free_i32(int64_t handle
);
int32_t ffi_bdk_rust_future_complete_i32(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_u64(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_u64(int64_t handle
);
void ffi_bdk_rust_future_free_u64(int64_t handle
);
int64_t ffi_bdk_rust_future_complete_u64(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_i64(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_i64(int64_t handle
);
void ffi_bdk_rust_future_free_i64(int64_t handle
);
int64_t ffi_bdk_rust_future_complete_i64(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_f32(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_f32(int64_t handle
);
void ffi_bdk_rust_future_free_f32(int64_t handle
);
float ffi_bdk_rust_future_complete_f32(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_f64(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_f64(int64_t handle
);
void ffi_bdk_rust_future_free_f64(int64_t handle
);
double ffi_bdk_rust_future_complete_f64(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_pointer(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_pointer(int64_t handle
);
void ffi_bdk_rust_future_free_pointer(int64_t handle
);
void * ffi_bdk_rust_future_complete_pointer(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_rust_buffer(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_rust_buffer(int64_t handle
);
void ffi_bdk_rust_future_free_rust_buffer(int64_t handle
);
RustBuffer ffi_bdk_rust_future_complete_rust_buffer(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
void ffi_bdk_rust_future_poll_void(int64_t handle, UniffiRustFutureContinuationCallback callback, int64_t callbackData
);
void ffi_bdk_rust_future_cancel_void(int64_t handle
);
void ffi_bdk_rust_future_free_void(int64_t handle
);
void ffi_bdk_rust_future_complete_void(int64_t handle, UniffiRustCallStatus *_Nonnull out_status
);
int16_t uniffi_bdk_checksum_method_address_is_valid_for_network(void
    
);
int16_t uniffi_bdk_checksum_method_address_script_pubkey(void
    
);
int16_t uniffi_bdk_checksum_method_address_to_address_data(void
    
);
int16_t uniffi_bdk_checksum_method_address_to_qr_uri(void
    
);
int16_t uniffi_bdk_checksum_method_amount_to_btc(void
    
);
int16_t uniffi_bdk_checksum_method_amount_to_sat(void
    
);
int16_t uniffi_bdk_checksum_method_blockhash_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_bumpfeetxbuilder_allow_dust(void
    
);
int16_t uniffi_bdk_checksum_method_bumpfeetxbuilder_current_height(void
    
);
int16_t uniffi_bdk_checksum_method_bumpfeetxbuilder_finish(void
    
);
int16_t uniffi_bdk_checksum_method_bumpfeetxbuilder_nlocktime(void
    
);
int16_t uniffi_bdk_checksum_method_bumpfeetxbuilder_set_exact_sequence(void
    
);
int16_t uniffi_bdk_checksum_method_bumpfeetxbuilder_version(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_build(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_configure_timeout_millis(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_connections(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_data_dir(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_peers(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_scan_type(void
    
);
int16_t uniffi_bdk_checksum_method_cbfbuilder_socks5_proxy(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_average_fee_rate(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_broadcast(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_connect(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_is_running(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_lookup_host(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_min_broadcast_feerate(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_next_info(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_next_warning(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_shutdown(void
    
);
int16_t uniffi_bdk_checksum_method_cbfclient_update(void
    
);
int16_t uniffi_bdk_checksum_method_cbfnode_run(void
    
);
int16_t uniffi_bdk_checksum_method_changeset_change_descriptor(void
    
);
int16_t uniffi_bdk_checksum_method_changeset_descriptor(void
    
);
int16_t uniffi_bdk_checksum_method_changeset_indexer_changeset(void
    
);
int16_t uniffi_bdk_checksum_method_changeset_localchain_changeset(void
    
);
int16_t uniffi_bdk_checksum_method_changeset_network(void
    
);
int16_t uniffi_bdk_checksum_method_changeset_tx_graph_changeset(void
    
);
int16_t uniffi_bdk_checksum_method_derivationpath_is_empty(void
    
);
int16_t uniffi_bdk_checksum_method_derivationpath_is_master(void
    
);
int16_t uniffi_bdk_checksum_method_derivationpath_len(void
    
);
int16_t uniffi_bdk_checksum_method_descriptor_desc_type(void
    
);
int16_t uniffi_bdk_checksum_method_descriptor_descriptor_id(void
    
);
int16_t uniffi_bdk_checksum_method_descriptor_is_multipath(void
    
);
int16_t uniffi_bdk_checksum_method_descriptor_max_weight_to_satisfy(void
    
);
int16_t uniffi_bdk_checksum_method_descriptor_to_single_descriptors(void
    
);
int16_t uniffi_bdk_checksum_method_descriptor_to_string_with_secret(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorid_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorpublickey_derive(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorpublickey_extend(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorpublickey_is_multipath(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorpublickey_master_fingerprint(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorsecretkey_as_public(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorsecretkey_derive(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorsecretkey_extend(void
    
);
int16_t uniffi_bdk_checksum_method_descriptorsecretkey_secret_bytes(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_block_headers_subscribe(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_estimate_fee(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_full_scan(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_ping(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_server_features(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_sync(void
    
);
int16_t uniffi_bdk_checksum_method_electrumclient_transaction_broadcast(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_broadcast(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_full_scan(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_get_block_hash(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_get_fee_estimates(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_get_height(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_get_tx(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_get_tx_info(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_get_tx_status(void
    
);
int16_t uniffi_bdk_checksum_method_esploraclient_sync(void
    
);
int16_t uniffi_bdk_checksum_method_feerate_to_sat_per_kwu(void
    
);
int16_t uniffi_bdk_checksum_method_feerate_to_sat_per_vb_ceil(void
    
);
int16_t uniffi_bdk_checksum_method_feerate_to_sat_per_vb_floor(void
    
);
int16_t uniffi_bdk_checksum_method_fullscanrequestbuilder_build(void
    
);
int16_t uniffi_bdk_checksum_method_fullscanrequestbuilder_inspect_spks_for_all_keychains(void
    
);
int16_t uniffi_bdk_checksum_method_fullscanscriptinspector_inspect(void
    
);
int16_t uniffi_bdk_checksum_method_hashableoutpoint_outpoint(void
    
);
int16_t uniffi_bdk_checksum_method_persistence_initialize(void
    
);
int16_t uniffi_bdk_checksum_method_persistence_persist(void
    
);
int16_t uniffi_bdk_checksum_method_policy_as_string(void
    
);
int16_t uniffi_bdk_checksum_method_policy_contribution(void
    
);
int16_t uniffi_bdk_checksum_method_policy_id(void
    
);
int16_t uniffi_bdk_checksum_method_policy_item(void
    
);
int16_t uniffi_bdk_checksum_method_policy_requires_path(void
    
);
int16_t uniffi_bdk_checksum_method_policy_satisfaction(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_combine(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_extract_tx(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_fee(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_finalize(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_input(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_json_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_spend_utxo(void
    
);
int16_t uniffi_bdk_checksum_method_psbt_write_to_file(void
    
);
int16_t uniffi_bdk_checksum_method_script_to_bytes(void
    
);
int16_t uniffi_bdk_checksum_method_syncrequestbuilder_build(void
    
);
int16_t uniffi_bdk_checksum_method_syncrequestbuilder_inspect_spks(void
    
);
int16_t uniffi_bdk_checksum_method_syncscriptinspector_inspect(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_compute_txid(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_compute_wtxid(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_input(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_is_coinbase(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_is_explicitly_rbf(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_is_lock_time_enabled(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_lock_time(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_output(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_total_size(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_version(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_vsize(void
    
);
int16_t uniffi_bdk_checksum_method_transaction_weight(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_add_data(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_add_global_xpubs(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_add_recipient(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_add_unspendable(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_add_utxo(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_add_utxos(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_allow_dust(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_change_policy(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_current_height(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_do_not_spend_change(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_drain_to(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_drain_wallet(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_exclude_below_confirmations(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_exclude_unconfirmed(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_fee_absolute(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_fee_rate(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_finish(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_manually_selected_only(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_nlocktime(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_only_spend_change(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_policy_path(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_set_exact_sequence(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_set_recipients(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_unspendable(void
    
);
int16_t uniffi_bdk_checksum_method_txbuilder_version(void
    
);
int16_t uniffi_bdk_checksum_method_txmerklenode_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_txid_serialize(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_apply_evicted_txs(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_apply_unconfirmed_txs(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_apply_update(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_balance(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_calculate_fee(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_calculate_fee_rate(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_cancel_tx(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_derivation_index(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_derivation_of_spk(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_descriptor_checksum(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_finalize_psbt(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_get_tx(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_get_utxo(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_insert_txout(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_is_mine(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_latest_checkpoint(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_list_output(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_list_unspent(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_list_unused_addresses(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_mark_used(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_network(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_next_derivation_index(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_next_unused_address(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_peek_address(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_persist(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_policies(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_public_descriptor(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_reveal_addresses_to(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_reveal_next_address(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_sent_and_received(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_sign(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_staged(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_start_full_scan(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_start_sync_with_revealed_spks(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_take_staged(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_transactions(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_tx_details(void
    
);
int16_t uniffi_bdk_checksum_method_wallet_unmark_used(void
    
);
int16_t uniffi_bdk_checksum_method_wtxid_serialize(void
    
);
int16_t uniffi_bdk_checksum_constructor_address_from_script(void
    
);
int16_t uniffi_bdk_checksum_constructor_address_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_amount_from_btc(void
    
);
int16_t uniffi_bdk_checksum_constructor_amount_from_sat(void
    
);
int16_t uniffi_bdk_checksum_constructor_blockhash_from_bytes(void
    
);
int16_t uniffi_bdk_checksum_constructor_blockhash_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_bumpfeetxbuilder_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_cbfbuilder_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_from_aggregate(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_from_descriptor_and_network(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_from_indexer_changeset(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_from_local_chain_changes(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_from_merge(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_from_tx_graph_changeset(void
    
);
int16_t uniffi_bdk_checksum_constructor_changeset_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_derivationpath_master(void
    
);
int16_t uniffi_bdk_checksum_constructor_derivationpath_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip44(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip44_public(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip49(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip49_public(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip84(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip84_public(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip86(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptor_new_bip86_public(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptorid_from_bytes(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptorid_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptorpublickey_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptorsecretkey_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_descriptorsecretkey_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_electrumclient_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_esploraclient_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_feerate_from_sat_per_kwu(void
    
);
int16_t uniffi_bdk_checksum_constructor_feerate_from_sat_per_vb(void
    
);
int16_t uniffi_bdk_checksum_constructor_hashableoutpoint_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_ipaddress_from_ipv4(void
    
);
int16_t uniffi_bdk_checksum_constructor_ipaddress_from_ipv6(void
    
);
int16_t uniffi_bdk_checksum_constructor_mnemonic_from_entropy(void
    
);
int16_t uniffi_bdk_checksum_constructor_mnemonic_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_mnemonic_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_persister_custom(void
    
);
int16_t uniffi_bdk_checksum_constructor_persister_new_in_memory(void
    
);
int16_t uniffi_bdk_checksum_constructor_persister_new_sqlite(void
    
);
int16_t uniffi_bdk_checksum_constructor_psbt_from_file(void
    
);
int16_t uniffi_bdk_checksum_constructor_psbt_from_unsigned_tx(void
    
);
int16_t uniffi_bdk_checksum_constructor_psbt_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_script_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_transaction_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_txbuilder_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_txmerklenode_from_bytes(void
    
);
int16_t uniffi_bdk_checksum_constructor_txmerklenode_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_txid_from_bytes(void
    
);
int16_t uniffi_bdk_checksum_constructor_txid_from_string(void
    
);
int16_t uniffi_bdk_checksum_constructor_wallet_create_from_two_path_descriptor(void
    
);
int16_t uniffi_bdk_checksum_constructor_wallet_create_single(void
    
);
int16_t uniffi_bdk_checksum_constructor_wallet_load(void
    
);
int16_t uniffi_bdk_checksum_constructor_wallet_load_single(void
    
);
int16_t uniffi_bdk_checksum_constructor_wallet_new(void
    
);
int16_t uniffi_bdk_checksum_constructor_wtxid_from_bytes(void
    
);
int16_t uniffi_bdk_checksum_constructor_wtxid_from_string(void
    
);
int32_t ffi_bdk_uniffi_contract_version(void
    
);