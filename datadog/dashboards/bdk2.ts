import { Construct } from "constructs";
import { Dashboard } from "@cdktf/provider-datadog/lib/dashboard";

export class Bdk2Dashboard extends Construct {
  constructor(scope: Construct) {
    super(scope, "bdk2_dashboard");

    new Dashboard(this, "bdk2_monitors", {
      title: "BDK2 Monitors",
      layoutType: "ordered",
      widget: [
        {
          manageStatusDefinition: {
            title: "BDK2 Monitors - Production",
            query: "tag:bdk2_production",
            summaryType: "monitors",
            displayFormat: "countsAndList",
            showLastTriggered: true,
          },
        },
        {
          manageStatusDefinition: {
            title: "BDK2 Monitors - Staging",
            query: "tag:bdk2_staging",
            summaryType: "monitors",
            displayFormat: "countsAndList",
            showLastTriggered: true,
          },
        },
        {
          manageStatusDefinition: {
            title: "BDK2 Divergence Monitors - Production",
            query: "tag:bdk2_production bdk2_sync_duration_provider_divergence",
            summaryType: "monitors",
            displayFormat: "countsAndList",
            showLastTriggered: true,
          },
        },
        {
          manageStatusDefinition: {
            title: "BDK2 Divergence Monitors - Staging",
            query: "tag:bdk2_staging bdk2_sync_duration_provider_divergence",
            summaryType: "monitors",
            displayFormat: "countsAndList",
            showLastTriggered: true,
          },
        },
      ],
    });
  }
}
