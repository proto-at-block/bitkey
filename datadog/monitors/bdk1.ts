import { Construct } from "constructs";
import { Environment } from "./common/environments";
import { Monitor } from "./common/monitor";
import { getErrorRecipients } from "./recipients";

export class Bdk1Monitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `bdk1_${environment}`);

    const errorRecipients = getErrorRecipients(environment);
    const tags = [`bdk1_${environment}`];
    const rumBaseQuery = `@type:resource @context.bdk_version:1 service:world.bitkey.app env:${environment}`;
    const mempoolRumQuery = `${rumBaseQuery} @resource.url:"ssl://bitkey.mempool.space:50002"`;
    const blockstreamRumQuery = `${rumBaseQuery} @resource.url:"ssl://electrum.blockstream.info:50002"`;
    const rumLatencyQuery = (name: string, query: string) => ({
      dataSource: "rum",
      name,
      indexes: ["*"],
      compute: [{ aggregation: "pc95", metric: "@duration" }],
      groupBy: [],
      search: {
        query,
      },
    });

    const mempoolSyncVolumeLow = new Monitor(
      this,
      "bdk1_mempool_sync_volume_low",
      {
        query: `rum("${mempoolRumQuery}").rollup("count").last("15m") < 20`,
        name: `[Legacy BDK] Mempool sync volume low on env:${environment}`,
        message:
          "Mempool legacy BDK sync volume below 20 in the last 15 minutes (gating).",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "20",
        },
        recipients: [],
      }
    );

    const blockstreamSyncVolumeLow = new Monitor(
      this,
      "bdk1_blockstream_sync_volume_low",
      {
        query: `rum("${blockstreamRumQuery}").rollup("count").last("15m") < 20`,
        name: `[Legacy BDK] Blockstream sync volume low on env:${environment}`,
        message:
          "Blockstream legacy BDK sync volume below 20 in the last 15 minutes (gating).",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "20",
        },
        recipients: [],
      }
    );

    const mempoolSyncDurationHistoricalDivergence = new Monitor(
      this,
      "bdk1_mempool_sync_duration_historical_divergence",
      {
        query:
          `formula("moving_rollup(mempool, 900, 'avg') / moving_rollup(mempool, 86400, 'avg')")` +
          `.last("15m") > 1.25`,
        name: `[Legacy BDK] Mempool sync p95 divergence on env:${environment}`,
        message:
          "Mempool legacy BDK sync p95 is >25% higher than its 24h mean.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.25",
        },
        recipients: [],
        variables: {
          eventQuery: [rumLatencyQuery("mempool", mempoolRumQuery)],
        },
      }
    );

    const mempoolSyncDurationProviderRatio = new Monitor(
      this,
      "bdk1_mempool_sync_duration_provider_ratio_high",
      {
        query:
          `formula("moving_rollup(mempool, 900, 'avg') / moving_rollup(blockstream, 900, 'avg')")` +
          `.last("15m") > 1.5`,
        name: `[Legacy BDK] Mempool sync p95 vs Blockstream on env:${environment}`,
        message:
          "Mempool legacy BDK sync p95 is >50% higher than Blockstream.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.5",
        },
        recipients: [],
        variables: {
          eventQuery: [
            rumLatencyQuery("mempool", mempoolRumQuery),
            rumLatencyQuery("blockstream", blockstreamRumQuery),
          ],
        },
      }
    );

    const blockstreamSyncDurationHistoricalDivergence = new Monitor(
      this,
      "bdk1_blockstream_sync_duration_historical_divergence",
      {
        query:
          `formula("moving_rollup(blockstream, 900, 'avg') / moving_rollup(blockstream, 86400, 'avg')")` +
          `.last("15m") > 1.25`,
        name: `[Legacy BDK] Blockstream sync p95 divergence on env:${environment}`,
        message:
          "Blockstream legacy BDK sync p95 is >25% higher than its 24h mean.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.25",
        },
        recipients: [],
        variables: {
          eventQuery: [rumLatencyQuery("blockstream", blockstreamRumQuery)],
        },
      }
    );

    const blockstreamSyncDurationProviderRatio = new Monitor(
      this,
      "bdk1_blockstream_sync_duration_provider_ratio_high",
      {
        query:
          `formula("moving_rollup(blockstream, 900, 'avg') / moving_rollup(mempool, 900, 'avg')")` +
          `.last("15m") > 1.5`,
        name: `[Legacy BDK] Blockstream sync p95 vs Mempool on env:${environment}`,
        message:
          "Blockstream legacy BDK sync p95 is >50% higher than Mempool.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.5",
        },
        recipients: [],
        variables: {
          eventQuery: [
            rumLatencyQuery("blockstream", blockstreamRumQuery),
            rumLatencyQuery("mempool", mempoolRumQuery),
          ],
        },
      }
    );

    new Monitor(this, "bdk1_sync_duration_provider_divergence", {
      query:
        `(${mempoolSyncDurationHistoricalDivergence.id} && ` +
        `${mempoolSyncDurationProviderRatio.id} && ` +
        `!${mempoolSyncVolumeLow.id}) || ` +
        `(${blockstreamSyncDurationHistoricalDivergence.id} && ` +
        `${blockstreamSyncDurationProviderRatio.id} && ` +
        `!${blockstreamSyncVolumeLow.id})`,
      name: `[Legacy BDK] Electrum provider sync p95 divergence on env:${environment}`,
      message:
        "Legacy BDK sync p95 is elevated for one provider vs its 24h mean and the other provider.",
      tags,
      type: "composite",
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk1_sync_duration_high", {
      query:
        `rum(` +
        `"@type:resource @resource.url:(\\"ssl://bitkey.mempool.space:50002\\" OR \\"ssl://electrum.blockstream.info:50002\\") ` +
        `@context.bdk_version:1 service:world.bitkey.app env:${environment}"` +
        `).rollup("avg","@duration").last("15m") > 10000`,
      name: `[Legacy BDK] Wallet sync duration high on env:${environment}`,
      message: "Legacy BDK wallet sync duration is elevated on mobile clients.",
      tags,
      type: "rum alert",
      monitorThresholds: {
        critical: "10000",
      },
      recipients: errorRecipients,
    });
  }
}
