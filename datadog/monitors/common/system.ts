import { Monitor, MonitorConfig, MonitorMonitorThresholds } from "./monitor";
import { Construct } from "constructs";

type BaseConfig = Omit<MonitorConfig, "query" | "type">;

interface ContainerCpuUtilizationHighConfig extends BaseConfig {
    tags: string[],
    window?: string,
    monitorThresholds: MonitorMonitorThresholds,
}

const containerCpuUtilizationHighConfigDefaults: Partial<ContainerCpuUtilizationHighConfig> = {
    window: "10m",
}

/**
 * ContainerCpuUtilizationHighMonitor creates a monitor that alerts if the average CPU utilization
 * exceeds a threshold over a period of time
 */
export class ContainerCpuUtilizationHighMonitor extends Construct {
    constructor(scope: Construct, id: string, config: ContainerCpuUtilizationHighConfig) {
        super(scope, id);

        config = {
            ...containerCpuUtilizationHighConfigDefaults,
            ...config,
        };

        const {
            tags,
            window,
            ...monitorConfig
        } = config;

        new Monitor(this, 'cpu_utilization_high', {
            query:
                `avg(last_${window}):
           avg:container.cpu.usage{${tags.join(",")}}
           / avg:container.cpu.limit{${tags.join(",")}}
           > ${monitorConfig.monitorThresholds?.critical}`,
            ...monitorConfig,
            type: "query alert",
            tags: tags,
        });
    }
}

interface ContainerMemoryUtilizationHighConfig extends BaseConfig {
    tags: string[],
    window?: string,
    monitorThresholds: MonitorMonitorThresholds,
}

const containerMemoryUtilizationHighConfigDefaults: Partial<ContainerMemoryUtilizationHighConfig> = {
    window: "10m",
}

/**
* ContainerMemoryUtilizationHighMonitor creates a monitor that alerts if the average CPU utilization
* exceeds a threshold over a period of time
*/
export class ContainerMemoryUtilizationHighMonitor extends Construct {
    constructor(scope: Construct, id: string, config: ContainerMemoryUtilizationHighConfig) {
        super(scope, id);

        config = {
            ...containerMemoryUtilizationHighConfigDefaults,
            ...config,
        };

        const {
            tags,
            window,
            ...monitorConfig
        } = config;

        new Monitor(this, 'memory_utilization_high', {
            query:
                `avg(last_${window}):
             avg:container.memory.usage{${tags.join(",")}}
             / avg:container.memory.limit{${tags.join(",")}}
             > ${monitorConfig.monitorThresholds?.critical}`,
            ...monitorConfig,
            type: "query alert",
            tags: tags,
        });
    }
}

interface TokioBusyRatioHighConfig extends BaseConfig {
    tags: string[],
    window?: string,
    monitorThresholds: MonitorMonitorThresholds,
}

const tokioBusyRatioHighConfigDefaults: Partial<TokioBusyRatioHighConfig> = {
    window: "10m",
}

/**
* TokioBusyRatioHighMonitor creates a monitor that alerts if the average Tokio busy ratio
* exceeds a threshold over a period of time
*/
export class TokioBusyRatioHighMonitor extends Construct {
    constructor(scope: Construct, id: string, config: TokioBusyRatioHighConfig) {
        super(scope, id);

        config = {
            ...tokioBusyRatioHighConfigDefaults,
            ...config,
        };

        const {
            tags,
            window,
            ...monitorConfig
        } = config;

        new Monitor(this, 'tokio_busy_ratio_high', {
            query:
                `avg(last_${window}):
             avg:bitkey.tokio.busy_ratio{${tags.join(",")}}
             > ${monitorConfig.monitorThresholds?.critical}`,
            ...monitorConfig,
            type: "query alert",
            tags: tags,
        });
    }
}
