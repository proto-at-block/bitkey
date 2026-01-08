use bdk_kyoto::bip157::lookup_host;
use bdk_kyoto::bip157::tokio;
use bdk_kyoto::bip157::AddrV2;
use bdk_kyoto::bip157::Network;
use bdk_kyoto::bip157::Node;
use bdk_kyoto::bip157::ServiceFlags;
use bdk_kyoto::builder::Builder as BDKCbfBuilder;
use bdk_kyoto::builder::BuilderExt;
use bdk_kyoto::HeaderCheckpoint;
use bdk_kyoto::LightClient as BDKLightClient;
use bdk_kyoto::Receiver;
use bdk_kyoto::RejectReason;
use bdk_kyoto::Requester;
use bdk_kyoto::TrustedPeer;
use bdk_kyoto::UnboundedReceiver;
use bdk_kyoto::UpdateSubscriber;
use bdk_kyoto::Warning as Warn;

use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use tokio::sync::Mutex;

use crate::bitcoin::BlockHash;
use crate::bitcoin::Transaction;
use crate::bitcoin::Wtxid;
use crate::error::CbfError;
use crate::types::Update;
use crate::wallet::Wallet;
use crate::FeeRate;

const DEFAULT_CONNECTIONS: u8 = 2;
const CWD_PATH: &str = ".";
const TCP_HANDSHAKE_TIMEOUT: Duration = Duration::from_secs(2);
const MESSAGE_RESPONSE_TIMEOUT: Duration = Duration::from_secs(5);

/// Receive a [`CbfClient`] and [`CbfNode`].
#[derive(Debug, uniffi::Record)]
pub struct CbfComponents {
    /// Publish events to the node, like broadcasting transactions or adding scripts.
    pub client: Arc<CbfClient>,
    /// The node to run and fetch transactions for a [`Wallet`].
    pub node: Arc<CbfNode>,
}

/// A [`CbfClient`] handles wallet updates from a [`CbfNode`].
#[derive(Debug, uniffi::Object)]
pub struct CbfClient {
    sender: Arc<Requester>,
    info_rx: Mutex<Receiver<bdk_kyoto::Info>>,
    warning_rx: Mutex<UnboundedReceiver<bdk_kyoto::Warning>>,
    update_rx: Mutex<UpdateSubscriber>,
}

/// A [`CbfNode`] gathers transactions for a [`Wallet`].
/// To receive [`Update`] for [`Wallet`], refer to the
/// [`CbfClient`]. The [`CbfNode`] will run until instructed
/// to stop.
#[derive(Debug, uniffi::Object)]
pub struct CbfNode {
    node: std::sync::Mutex<Option<Node>>,
}

#[uniffi::export]
impl CbfNode {
    /// Start the node on a detached OS thread and immediately return.
    pub fn run(self: Arc<Self>) {
        let mut lock = self.node.lock().unwrap();
        let node = lock.take().expect("cannot call run more than once");
        std::thread::spawn(|| {
            tokio::runtime::Builder::new_multi_thread()
                .enable_all()
                .build()
                .unwrap()
                .block_on(async move {
                    let _ = node.run().await;
                })
        });
    }
}

/// Build a BIP 157/158 light client to fetch transactions for a `Wallet`.
///
/// Options:
/// * List of `Peer`: Bitcoin full-nodes for the light client to connect to. May be empty.
/// * `connections`: The number of connections for the light client to maintain.
/// * `scan_type`: Sync, recover, or start a new wallet. For more information see [`ScanType`].
/// * `data_dir`: Optional directory to store block headers and peers.
///
/// A note on recovering wallets. Developers should allow users to provide an
/// approximate recovery height and an estimated number of transactions for the
/// wallet. When determining how many scripts to check filters for, the `Wallet`
/// `lookahead` value will be used. To ensure all transactions are recovered, the
/// `lookahead` should be roughly the number of transactions in the wallet history.
#[derive(Clone, uniffi::Object)]
pub struct CbfBuilder {
    connections: u8,
    handshake_timeout: Duration,
    response_timeout: Duration,
    data_dir: Option<String>,
    scan_type: ScanType,
    socks5_proxy: Option<Socks5Proxy>,
    peers: Vec<Peer>,
}

#[allow(clippy::new_without_default)]
#[uniffi::export]
impl CbfBuilder {
    /// Start a new [`CbfBuilder`]
    #[uniffi::constructor]
    pub fn new() -> Self {
        CbfBuilder {
            connections: DEFAULT_CONNECTIONS,
            handshake_timeout: TCP_HANDSHAKE_TIMEOUT,
            response_timeout: MESSAGE_RESPONSE_TIMEOUT,
            data_dir: None,
            scan_type: ScanType::default(),
            socks5_proxy: None,
            peers: Vec::new(),
        }
    }

    /// The number of connections for the light client to maintain. Default is two.
    pub fn connections(&self, connections: u8) -> Arc<Self> {
        Arc::new(CbfBuilder {
            connections,
            ..self.clone()
        })
    }

    /// Directory to store block headers and peers. If none is provided, the current
    /// working directory will be used.
    pub fn data_dir(&self, data_dir: String) -> Arc<Self> {
        Arc::new(CbfBuilder {
            data_dir: Some(data_dir),
            ..self.clone()
        })
    }

    /// Select between syncing, recovering, or scanning for new wallets.
    pub fn scan_type(&self, scan_type: ScanType) -> Arc<Self> {
        Arc::new(CbfBuilder {
            scan_type,
            ..self.clone()
        })
    }

    /// Bitcoin full-nodes to attempt a connection with.
    pub fn peers(&self, peers: Vec<Peer>) -> Arc<Self> {
        Arc::new(CbfBuilder {
            peers,
            ..self.clone()
        })
    }

    /// Configure the time in milliseconds that a node has to:
    /// 1. Respond to the initial connection
    /// 2. Respond to a request
    pub fn configure_timeout_millis(&self, handshake: u64, response: u64) -> Arc<Self> {
        Arc::new(CbfBuilder {
            handshake_timeout: Duration::from_millis(handshake),
            response_timeout: Duration::from_millis(response),
            ..self.clone()
        })
    }

    /// Configure connections to be established through a `Socks5 proxy. The vast majority of the
    /// time, the connection is to a local Tor daemon, which is typically exposed at
    /// `127.0.0.1:9050`.
    pub fn socks5_proxy(&self, proxy: Socks5Proxy) -> Arc<Self> {
        Arc::new(CbfBuilder {
            socks5_proxy: Some(proxy),
            ..self.clone()
        })
    }

    /// Construct a [`CbfComponents`] for a [`Wallet`].
    pub fn build(&self, wallet: &Wallet) -> CbfComponents {
        let wallet = wallet.get_wallet();

        let mut trusted_peers = Vec::new();
        for peer in self.peers.iter() {
            trusted_peers.push(peer.clone().into());
        }

        let scan_type = match self.scan_type {
            ScanType::Sync => bdk_kyoto::ScanType::Sync,
            ScanType::Recovery {
                used_script_index,
                checkpoint,
            } => {
                let network = wallet.network();
                // Any other network has taproot and segwit baked in since the genesis block.
                if !matches!(network, Network::Bitcoin) {
                    bdk_kyoto::ScanType::Recovery {
                        used_script_index,
                        checkpoint: HeaderCheckpoint::from_genesis(network),
                    }
                } else {
                    match checkpoint {
                        RecoveryPoint::GenesisBlock => bdk_kyoto::ScanType::Recovery {
                            used_script_index,
                            checkpoint: HeaderCheckpoint::from_genesis(wallet.network()),
                        },
                        RecoveryPoint::SegwitActivation => bdk_kyoto::ScanType::Recovery {
                            used_script_index,
                            checkpoint: HeaderCheckpoint::segwit_activation(),
                        },
                        RecoveryPoint::TaprootActivation => bdk_kyoto::ScanType::Recovery {
                            used_script_index,
                            checkpoint: HeaderCheckpoint::taproot_activation(),
                        },
                    }
                }
            }
        };

        let path_buf = self
            .data_dir
            .clone()
            .map(|path| PathBuf::from(&path))
            .unwrap_or(PathBuf::from(CWD_PATH));

        let mut builder = BDKCbfBuilder::new(wallet.network())
            .required_peers(self.connections)
            .data_dir(path_buf)
            .handshake_timeout(self.handshake_timeout)
            .response_timeout(self.response_timeout)
            .add_peers(trusted_peers);

        if let Some(proxy) = &self.socks5_proxy {
            let port = proxy.port;
            let addr = proxy.address.inner;
            builder = builder.socks5_proxy((addr, port));
        }

        let BDKLightClient {
            requester,
            info_subscriber,
            warning_subscriber,
            update_subscriber,
            node,
        } = builder
            .build_with_wallet(&wallet, scan_type)
            .expect("networks match by definition");

        let node = CbfNode {
            node: std::sync::Mutex::new(Some(node)),
        };

        let client = CbfClient {
            sender: Arc::new(requester),
            info_rx: Mutex::new(info_subscriber),
            warning_rx: Mutex::new(warning_subscriber),
            update_rx: Mutex::new(update_subscriber),
        };

        CbfComponents {
            client: Arc::new(client),
            node: Arc::new(node),
        }
    }
}

#[uniffi::export]
impl CbfClient {
    /// Return the next available info message from a node. If none is returned, the node has stopped.
    pub async fn next_info(&self) -> Result<Info, CbfError> {
        let mut info_rx = self.info_rx.lock().await;
        info_rx
            .recv()
            .await
            .map(|e| e.into())
            .ok_or(CbfError::NodeStopped)
    }

    /// Return the next available warning message from a node. If none is returned, the node has stopped.
    pub async fn next_warning(&self) -> Result<Warning, CbfError> {
        let mut warn_rx = self.warning_rx.lock().await;
        warn_rx
            .recv()
            .await
            .map(|warn| warn.into())
            .ok_or(CbfError::NodeStopped)
    }

    /// Return an [`Update`]. This is method returns once the node syncs to the rest of
    /// the network or a new block has been gossiped.
    pub async fn update(&self) -> Result<Update, CbfError> {
        let update = self
            .update_rx
            .lock()
            .await
            .update()
            .await
            .map_err(|_| CbfError::NodeStopped)?;
        Ok(Update(update))
    }

    /// Broadcast a transaction to the network, erroring if the node has stopped running.
    pub async fn broadcast(&self, transaction: &Transaction) -> Result<Arc<Wtxid>, CbfError> {
        let tx = transaction.into();
        self.sender
            .broadcast_random(tx)
            .await
            .map_err(From::from)
            .map(|wtxid| Arc::new(Wtxid(wtxid)))
    }

    /// The minimum fee rate required to broadcast a transcation to all connected peers.
    pub async fn min_broadcast_feerate(&self) -> Result<Arc<FeeRate>, CbfError> {
        self.sender
            .broadcast_min_feerate()
            .await
            .map_err(|_| CbfError::NodeStopped)
            .map(|fee| Arc::new(FeeRate(fee)))
    }

    /// Fetch the average fee rate for a block by requesting it from a peer. Not recommend for
    /// resource-limited devices.
    pub async fn average_fee_rate(
        &self,
        blockhash: Arc<BlockHash>,
    ) -> Result<Arc<FeeRate>, CbfError> {
        let fee_rate = self
            .sender
            .average_fee_rate(blockhash.0)
            .await
            .map_err(|_| CbfError::NodeStopped)?;
        Ok(Arc::new(fee_rate.into()))
    }

    /// Add another [`Peer`] to attempt a connection with.
    pub fn connect(&self, peer: Peer) -> Result<(), CbfError> {
        self.sender
            .add_peer(peer)
            .map_err(|_| CbfError::NodeStopped)
    }

    /// Query a Bitcoin DNS seeder using the configured resolver.
    ///
    /// This is **not** a generic DNS implementation. Host names are prefixed with a `x849` to filter
    /// for compact block filter nodes from the seeder. For example `dns.myseeder.com` will be queried
    /// as `x849.dns.myseeder.com`. This has no guarantee to return any `IpAddr`.
    pub fn lookup_host(&self, hostname: String) -> Vec<Arc<IpAddress>> {
        let nodes = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap()
            .block_on(lookup_host(hostname));
        nodes
            .into_iter()
            .map(|ip| Arc::new(IpAddress { inner: ip }))
            .collect()
    }
    /// Check if the node is still running in the background.
    pub fn is_running(&self) -> bool {
        self.sender.is_running()
    }

    /// Stop the [`CbfNode`]. Errors if the node is already stopped.
    pub fn shutdown(&self) -> Result<(), CbfError> {
        self.sender.shutdown().map_err(From::from)
    }
}

/// A log message from the node.
#[derive(Debug, uniffi::Enum)]
pub enum Info {
    /// All the required connections have been met. This is subject to change.
    ConnectionsMet,
    /// The node was able to successfully connect to a remote peer.
    SuccessfulHandshake,
    /// A percentage value of filters that have been scanned.
    Progress {
        /// The height of the local block chain.
        chain_height: u32,
        /// The percent of filters downloaded.
        filters_downloaded_percent: f32,
    },
    /// A relevant block was downloaded from a peer.
    BlockReceived(String),
}

impl From<bdk_kyoto::Info> for Info {
    fn from(value: bdk_kyoto::Info) -> Info {
        match value {
            bdk_kyoto::Info::ConnectionsMet => Info::ConnectionsMet,
            bdk_kyoto::Info::SuccessfulHandshake => Info::SuccessfulHandshake,
            bdk_kyoto::Info::Progress(progress) => Info::Progress {
                filters_downloaded_percent: progress.percentage_complete(),
                chain_height: progress.chain_height(),
            },
            bdk_kyoto::Info::BlockReceived(block) => Info::BlockReceived(block.to_string()),
        }
    }
}

/// Warnings a node may issue while running.
#[derive(Debug, uniffi::Enum)]
pub enum Warning {
    /// The node is looking for connections to peers.
    NeedConnections,
    /// A connection to a peer timed out.
    PeerTimedOut,
    /// The node was unable to connect to a peer in the database.
    CouldNotConnect,
    /// A connection was maintained, but the peer does not signal for compact block filers.
    NoCompactFilters,
    /// The node has been waiting for new inv and will find new peers to avoid block withholding.
    PotentialStaleTip,
    /// A peer sent us a peer-to-peer message the node did not request.
    UnsolicitedMessage,
    /// A transaction got rejected, likely for being an insufficient fee or non-standard transaction.
    TransactionRejected {
        wtxid: String,
        reason: Option<String>,
    },
    /// The peer sent us a potential fork.
    EvaluatingFork,
    /// An unexpected error occurred processing a peer-to-peer message.
    UnexpectedSyncError { warning: String },
    /// The node failed to respond to a message sent from the client.
    RequestFailed,
}

impl From<Warn> for Warning {
    fn from(value: Warn) -> Warning {
        match value {
            Warn::NeedConnections {
                connected: _,
                required: _,
            } => Warning::NeedConnections,
            Warn::PeerTimedOut => Warning::PeerTimedOut,
            Warn::CouldNotConnect => Warning::CouldNotConnect,
            Warn::NoCompactFilters => Warning::NoCompactFilters,
            Warn::PotentialStaleTip => Warning::PotentialStaleTip,
            Warn::UnsolicitedMessage => Warning::UnsolicitedMessage,
            Warn::TransactionRejected { payload } => {
                let reason = payload.reason.map(|r| r.into_string());
                Warning::TransactionRejected {
                    wtxid: payload.wtxid.to_string(),
                    reason,
                }
            }
            Warn::EvaluatingFork => Warning::EvaluatingFork,
            Warn::UnexpectedSyncError { warning } => Warning::UnexpectedSyncError { warning },
            Warn::ChannelDropped => Warning::RequestFailed,
        }
    }
}

/// Sync a wallet from the last known block hash or recover a wallet from a specified recovery
/// point.
#[derive(Debug, Clone, Copy, Default, uniffi::Enum)]
pub enum ScanType {
    /// Sync an existing wallet from the last stored chain checkpoint.
    #[default]
    Sync,
    /// Recover an existing wallet by scanning from the specified height.
    Recovery {
        /// The estimated number of scripts the user has revealed for the wallet being recovered.
        /// If unknown, a conservative estimate, say 1,000, could be used.
        used_script_index: u32,
        /// A relevant starting point or soft fork to start the sync.
        checkpoint: RecoveryPoint,
    },
}

#[derive(Debug, Clone, Copy, Default, uniffi::Enum)]
pub enum RecoveryPoint {
    GenesisBlock,
    #[default]
    SegwitActivation,
    TaprootActivation,
}

/// A peer to connect to over the Bitcoin peer-to-peer network.
#[derive(Clone, uniffi::Record)]
pub struct Peer {
    /// The IP address to reach the node.
    pub address: Arc<IpAddress>,
    /// The port to reach the node. If none is provided, the default
    /// port for the selected network will be used.
    pub port: Option<u16>,
    /// Does the remote node offer encrypted peer-to-peer connection.
    pub v2_transport: bool,
}

/// An IP address to connect to over TCP.
#[derive(Debug, uniffi::Object)]
pub struct IpAddress {
    inner: IpAddr,
}

#[uniffi::export]
impl IpAddress {
    /// Build an IPv4 address.
    #[uniffi::constructor]
    pub fn from_ipv4(q1: u8, q2: u8, q3: u8, q4: u8) -> Self {
        Self {
            inner: IpAddr::V4(Ipv4Addr::new(q1, q2, q3, q4)),
        }
    }

    /// Build an IPv6 address.
    #[allow(clippy::too_many_arguments)]
    #[uniffi::constructor]
    pub fn from_ipv6(a: u16, b: u16, c: u16, d: u16, e: u16, f: u16, g: u16, h: u16) -> Self {
        Self {
            inner: IpAddr::V6(Ipv6Addr::new(a, b, c, d, e, f, g, h)),
        }
    }
}

/// A proxy to route network traffic, most likely through a Tor daemon. Normally this proxy is
/// exposed at 127.0.0.1:9050.
#[derive(Debug, Clone, uniffi::Record)]
pub struct Socks5Proxy {
    /// The IP address, likely `127.0.0.1`
    pub address: Arc<IpAddress>,
    /// The listening port, likely `9050`
    pub port: u16,
}

impl From<Peer> for TrustedPeer {
    fn from(peer: Peer) -> Self {
        let services = if peer.v2_transport {
            let mut services = ServiceFlags::P2P_V2;
            services.add(ServiceFlags::NETWORK);
            services.add(ServiceFlags::COMPACT_FILTERS);
            services
        } else {
            let mut services = ServiceFlags::COMPACT_FILTERS;
            services.add(ServiceFlags::NETWORK);
            services
        };
        let addr_v2 = match peer.address.inner {
            IpAddr::V4(ipv4_addr) => AddrV2::Ipv4(ipv4_addr),
            IpAddr::V6(ipv6_addr) => AddrV2::Ipv6(ipv6_addr),
        };
        TrustedPeer::new(addr_v2, peer.port, services)
    }
}

trait DisplayExt {
    fn into_string(self) -> String;
}

impl DisplayExt for RejectReason {
    fn into_string(self) -> String {
        let message = match self {
            RejectReason::Malformed => "Message could not be decoded.",
            RejectReason::Invalid => "Transaction was invalid for some reason.",
            RejectReason::Obsolete => "Client version is no longer supported.",
            RejectReason::Duplicate => "Duplicate version message received.",
            RejectReason::NonStandard => "Transaction was nonstandard.",
            RejectReason::Dust => "One or more outputs are below the dust threshold.",
            RejectReason::Fee => "Transaction does not have enough fee to be mined.",
            RejectReason::Checkpoint => "Inconsistent with compiled checkpoint.",
        };
        message.into()
    }
}
