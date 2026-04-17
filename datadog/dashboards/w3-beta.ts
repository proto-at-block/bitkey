import { Construct } from "constructs";
import { Dashboard } from "@cdktf/provider-datadog/lib/dashboard";

/**
 * W3 Beta dashboard for monitoring the user experience of W3 hardware
 * beta testers using the team app (world.bitkey.team).
 *
 * Layout: triage-first (what's broken?), then user journeys, then general health.
 * All widgets respect the dashboard time picker — no fixed liveSpan.
 * Key charts use groupBy @usr.firmware_version to show W1/W3 split without
 * excluding events that lack the attribute (e.g. pre-pairing).
 *
 * Sections:
 *  1.  Health Summary (KPIs)
 *  2.  NFC Health
 *  3.  Firmware Update (FWUP) Health
 *  4.  Emerging Patterns & Known Bugs
 *  5.  W3 Upgrade Funnel & Sweep (W1 → W3)
 *  6.  Fresh W3 Onboarding (new account)
 *  7.  App Errors & Crashes
 *  8.  API & Network Health
 *  9.  Per-User Drill-Down
 *  10. Beta Population
 */
export class W3BetaDashboard extends Construct {
  constructor(scope: Construct) {
    super(scope, "w3_beta_dashboard");

    const team = "service:world.bitkey.team env:team";
    // FWUP APM span resource names (includes W3 two-tap confirmation path)
    // Note: getConfirmationResult is excluded because it's shared across all
    // two-tap flows (signing, mobile pay, etc.), not just FWUP. Errors from
    // FWUP confirmation taps propagate to the parent nfcTransaction-fwup-confirmation span.
    const fwupSpans =
      "resource_name:(nfcTransaction-fwup OR nfcTransaction-fwup-confirmation OR fwupFinish OR fwupTransfer OR fwupStart)";

    new Dashboard(this, "w3_beta", {
      title: "W3 Beta — Team App Health",
      description:
        "Monitors the W3 hardware beta experience for team app (world.bitkey.team) users. Triage-first layout: health KPIs, NFC, FWUP, known issues, then user journeys and general health. Key charts group by firmware version to show W1/W3 split.",
      layoutType: "ordered",
      reflowType: "auto",
      widget: [
        // =====================================================================
        // Section 1: Health Summary (KPIs)
        // =====================================================================
        {
          groupDefinition: {
            title: "Health Summary",
            layoutType: "ordered",
            widget: [
              {
                queryValueDefinition: {
                  title: "Active Users",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@usr.app_installation_id",
                        },
                        searchQuery: `@type:session ${team}`,
                        groupBy: [],
                      },
                    },
                  ],
                  precision: 0,
                },
              },
              {
                queryValueDefinition: {
                  title: "NFC Failure Views",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_* ${team}`,
                        groupBy: [],
                      },
                    },
                  ],
                  precision: 0,
                },
              },
              {
                queryValueDefinition: {
                  title: "W3 Upgrade Errors",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:W3_UPGRADE_ERROR ${team}`,
                        groupBy: [],
                      },
                    },
                  ],
                  precision: 0,
                },
              },
              {
                queryValueDefinition: {
                  title: "Error Logs",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error`,
                        groupBy: [],
                      },
                    },
                  ],
                  precision: 0,
                },
              },
              {
                queryValueDefinition: {
                  title: "RUM Crashes",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:error @error.category:crash ${team}`,
                        groupBy: [],
                      },
                    },
                  ],
                  precision: 0,
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 2: NFC Health
        // =====================================================================
        {
          groupDefinition: {
            title: "NFC Health",
            layoutType: "ordered",
            widget: [
              // Overview: is NFC working?
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "NFC Success vs Failure",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_SUCCESS_* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // Which firmware is failing?
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "NFC Failures by Firmware Version",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_* ${team}`,
                        groupBy: [
                          {
                            facet: "@usr.firmware_version",
                            limit: 10,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                      displayType: "bars",
                    },
                  ],
                },
              },
              // Transaction signing success vs failure
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Transaction Signing: Success vs Failure",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:(NFC_SUCCESS_SIGN_TRANSACTION OR NFC_SUCCESS_SIGN_ACTION_PROOF) ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:(NFC_FAILURE_SIGN_TRANSACTION OR NFC_FAILURE_SIGN_ACTION_PROOF) ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // NFC failures by flow context
              {
                toplistDefinition: {
                  title: "NFC Failures by Flow Context",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_* ${team}`,
                        groupBy: [
                          {
                            facet: "@view.name",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              // NFC error types from APM spans
              {
                toplistDefinition: {
                  title: "NFC Error Types (APM spans)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query:
                                "service:world.bitkey.team resource_name:nfcTransaction* status:error",
                            },
                            groupBy: [
                              {
                                facet: "error.type",
                                limit: 15,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                    },
                  ],
                },
              },
              // NFC span errors by firmware version
              {
                toplistDefinition: {
                  title: "NFC Span Errors by Firmware Version",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query:
                                "service:world.bitkey.team resource_name:nfcTransaction* status:error",
                            },
                            groupBy: [
                              {
                                facet: "@usr.firmware_version",
                                limit: 10,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                    },
                  ],
                },
              },
              // NFC transaction errors over time by operation
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "NFC Transaction Errors Over Time (by operation)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query:
                                "service:world.bitkey.team resource_name:nfcTransaction* status:error",
                            },
                            groupBy: [
                              {
                                facet: "resource_name",
                                limit: 10,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // Two-tap confirmation events
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Two-Tap: Confirmation Pending vs Denied",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_CONFIRMATION_PENDING* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "cool" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_CONFIRMATION_DENIED* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // Recent NFC error logs
              {
                logStreamDefinition: {
                  title: "Recent NFC Error Logs",
                  query: `${team} status:error (firmware.CommandError.* OR "Tag response error" OR "System resource unavailable" OR "Session invalidated" OR NfcException.*)`,
                  columns: [
                    "timestamp",
                    "@usr.app_installation_id",
                    "@usr.firmware_version",
                    "message",
                  ],
                  showDateColumn: true,
                  showMessageColumn: true,
                  messageDisplay: "expanded-md",
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 3: Firmware Update (FWUP) Health
        // =====================================================================
        {
          groupDefinition: {
            title: "Firmware Update (FWUP) Health",
            layoutType: "ordered",
            widget: [
              // FWUP success vs views-with-errors
              // Note: errors often stay on NFC_INITIATE_FWUP / NFC_UPDATE_IN_PROGRESS_FWUP
              // rather than transitioning to NFC_FAILURE_FWUP, so we use error.count > 0.
              // firmware_version here reflects the pre-update firmware, not the target.
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "FWUP: Success vs Views with Errors",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_SUCCESS_FWUP ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:(*FWUP* OR *fwup*) @view.error.count:>0 ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // FWUP errors by firmware version (the "decline to zero" chart)
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "FWUP Errors by Firmware Version (decline to zero)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query: `service:world.bitkey.team ${fwupSpans} status:error`,
                            },
                            groupBy: [
                              {
                                facet: "@usr.firmware_version",
                                limit: 10,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                      displayType: "bars",
                    },
                  ],
                },
              },
              // FWUP error types
              {
                toplistDefinition: {
                  title: "FWUP Error Types (APM spans)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query: `service:world.bitkey.team ${fwupSpans} status:error`,
                            },
                            groupBy: [
                              {
                                facet: "error.type",
                                limit: 15,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                    },
                  ],
                },
              },
              // FWUP span errors over time by firmware version
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "FWUP Span Errors Over Time (by firmware version)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query: `service:world.bitkey.team ${fwupSpans} status:error`,
                            },
                            groupBy: [
                              {
                                facet: "@usr.firmware_version",
                                limit: 10,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                      displayType: "bars",
                    },
                  ],
                },
              },
              // FWUP errors by app version
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "FWUP Errors by App Version",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query: `service:world.bitkey.team ${fwupSpans} status:error`,
                            },
                            groupBy: [
                              {
                                facet: "@version",
                                limit: 10,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                      displayType: "bars",
                    },
                  ],
                },
              },
              // Recent FWUP error logs
              {
                logStreamDefinition: {
                  title: "Recent FWUP Error Logs",
                  query: `${team} status:error (*fwup* OR *FWUP* OR "firmware update")`,
                  columns: [
                    "timestamp",
                    "@usr.app_installation_id",
                    "@usr.firmware_version",
                    "message",
                  ],
                  showDateColumn: true,
                  showMessageColumn: true,
                  messageDisplay: "expanded-md",
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 4: Emerging Patterns & Known Bugs
        // =====================================================================
        {
          groupDefinition: {
            title: "Emerging Patterns & Known Bugs",
            layoutType: "ordered",
            widget: [
              // Emerging: auto-clustered error log patterns
              {
                listStreamDefinition: {
                  title: "Emerging Error Patterns (auto-clustered)",
                  request: [
                    {
                      responseFormat: "event_list",
                      query: {
                        dataSource: "logs_pattern_stream",
                        queryString: `${team} status:error`,
                        clusteringPatternFieldPath: "message",
                        groupBy: [{ facet: "service" }],
                        storage: "hot",
                      },
                      columns: [
                        { field: "status_line", width: "auto" },
                        { field: "matches", width: "auto" },
                        { field: "volume", width: "auto" },
                        { field: "service", width: "auto" },
                        { field: "message", width: "auto" },
                      ],
                    },
                  ],
                },
              },
              // Known: hw-signature-w3-placeholder
              {
                queryValueDefinition: {
                  title: "hw-signature-w3-placeholder Errors",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error hw-signature-w3-placeholder`,
                        groupBy: [],
                      },
                    },
                  ],
                  precision: 0,
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "hw-signature-w3-placeholder Errors Over Time",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error hw-signature-w3-placeholder`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // Firmware errors over time (hardcoded known types)
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Firmware / NFC Errors Over Time (known types)",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error "firmware.CommandError.CorruptResponseEnvelope"`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "orange" },
                    },
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error "firmware.CommandError.GeneralCommandError"`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error "Tag response error"`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "grey" },
                    },
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error "System resource unavailable"`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "purple" },
                    },
                  ],
                },
              },
              // Dynamic catch-all: surfaces new firmware/NFC error types
              {
                toplistDefinition: {
                  title:
                    "All Firmware / NFC Errors by Message (catch-all for new issues)",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error (firmware.CommandError.* OR "Tag response error" OR "System resource unavailable" OR "Session invalidated" OR NfcException.*)`,
                        groupBy: [
                          {
                            facet: "message",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 5: W3 Upgrade Funnel & Sweep (W1 → W3)
        // =====================================================================
        {
          groupDefinition: {
            title: "W3 Upgrade Funnel & Sweep (W1 → W3)",
            layoutType: "ordered",
            widget: [
              {
                toplistDefinition: {
                  title: "W3 Upgrade Screens (funnel steps)",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:W3_UPGRADE_* ${team}`,
                        groupBy: [
                          {
                            facet: "@view.name",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "W3 Upgrade: Complete vs Error",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:W3_UPGRADE_COMPLETE ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:W3_UPGRADE_ERROR ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // Sweep errors (final step of upgrade)
              {
                toplistDefinition: {
                  title: "Post-Upgrade Sweep Error Types",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error (*sweep* OR *broadcast* OR BdkError* OR "FailedToGenerateDestinationAddress" OR "No descriptor backup exists" OR "InsufficientFunds")`,
                        groupBy: [
                          {
                            facet: "message",
                            limit: 15,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Transaction Broadcast & Sweep Failures Over Time",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error ("Error broadcasting" OR BdkError OR *sweep* OR "FailedToGenerateDestinationAddress")`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 6: Fresh W3 Onboarding (new account path)
        // =====================================================================
        {
          groupDefinition: {
            title: "Fresh W3 Onboarding (new account path)",
            layoutType: "ordered",
            widget: [
              {
                toplistDefinition: {
                  title: "Onboarding Screens Hit",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:(*ONBOARDING* OR NEW_ACCOUNT_* OR HW_PAIR* OR HW_ACTIVATION* OR HW_SAVE_FINGERPRINT* OR FINGERPRINT_ENROLLMENT* OR BUILD_HARDWARE_DESCRIPTOR* OR CREATE_ACCOUNT*) ${team}`,
                        groupBy: [
                          {
                            facet: "@view.name",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "Onboarding / Account Creation Errors",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:(*FAILURE* OR *ERROR*) @view.name:(*ONBOARDING* OR NEW_ACCOUNT_* OR HW_* OR FINGERPRINT_* OR CREATE_ACCOUNT*) ${team}`,
                        groupBy: [
                          {
                            facet: "@view.name",
                            limit: 15,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Pairing NFC: Success vs Failure",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_SUCCESS_PAIR_NEW_HW_* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_PAIR_NEW_HW_* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title:
                    "Account Setup Errors (touchpoints, cloud backup, notifications)",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error ("Failed to add touchpoint" OR "Canceling account creation" OR "No local backup found" OR "Keychain error")`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 7: App Errors & Crashes
        // =====================================================================
        {
          groupDefinition: {
            title: "App Errors & Crashes",
            layoutType: "ordered",
            widget: [
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Sessions with Errors vs Total Sessions",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@session.id",
                        },
                        searchQuery: `@type:session ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@session.id",
                        },
                        searchQuery: `@type:session @session.error.count:>0 ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "NFC Sessions: Total vs With Failures",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@session.id",
                        },
                        searchQuery: `@type:view @view.name:NFC_* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "green" },
                    },
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@session.id",
                        },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_* ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              // What are the errors?
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "RUM Errors Over Time",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:error ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "Top Error Messages (excl. socrec certificate noise)",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error -socrec_key_certificate_verification_failure`,
                        groupBy: [
                          {
                            facet: "message",
                            limit: 15,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "Top Screens with Errors",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.error.count:>0 ${team}`,
                        groupBy: [
                          {
                            facet: "@view.name",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              // Which firmware is affected?
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Error Logs by Firmware Version",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error`,
                        groupBy: [
                          {
                            facet: "@usr.firmware_version",
                            limit: 10,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                      displayType: "bars",
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "RUM Errors by Firmware Version",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:error ${team}`,
                        groupBy: [
                          {
                            facet: "@usr.firmware_version",
                            limit: 10,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              // Recent error logs
              {
                logStreamDefinition: {
                  title: "Recent Error Logs",
                  query: `${team} status:error -socrec_key_certificate_verification_failure`,
                  columns: [
                    "timestamp",
                    "@usr.app_installation_id",
                    "@usr.firmware_version",
                    "message",
                  ],
                  showDateColumn: true,
                  showMessageColumn: true,
                  messageDisplay: "expanded-md",
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 8: API & Network Health
        // =====================================================================
        {
          groupDefinition: {
            title: "API & Network Health",
            layoutType: "ordered",
            widget: [
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "API Errors Over Time (4xx/5xx)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query:
                                "service:world.bitkey.team @http.status_code:>=400",
                            },
                            groupBy: [],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "Top Failing API Endpoints",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [{ aggregation: "count" }],
                            search: {
                              query:
                                "service:world.bitkey.team @http.status_code:>=400",
                            },
                            groupBy: [
                              {
                                facet: "resource_name",
                                limit: 15,
                                sort: { aggregation: "count", order: "desc" },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "API Latency p95 (top endpoints, excl. NFC)",
                  request: [
                    {
                      query: [
                        {
                          eventQuery: {
                            dataSource: "spans",
                            name: "q1",
                            compute: [
                              { aggregation: "pc95", metric: "@duration" },
                            ],
                            search: {
                              query:
                                "service:world.bitkey.team @http.status_code:* -resource_name:nfcTransaction*",
                            },
                            groupBy: [
                              {
                                facet: "resource_name",
                                limit: 10,
                                sort: {
                                  aggregation: "pc95",
                                  order: "desc",
                                },
                              },
                            ],
                          },
                        },
                      ],
                      formula: [{ formulaExpression: "q1" }],
                      displayType: "line",
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Network Errors (Offline / Timeout / DNS)",
                  request: [
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error OfflineOperationException`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "cool" },
                    },
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error SocketTimeoutException`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "warm" },
                    },
                    {
                      logQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `${team} status:error UnknownHostException`,
                        groupBy: [],
                      },
                      displayType: "bars",
                      style: { palette: "grey" },
                    },
                  ],
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 9: Per-User Drill-Down
        // =====================================================================
        {
          groupDefinition: {
            title: "Per-User Drill-Down",
            layoutType: "ordered",
            widget: [
              {
                toplistDefinition: {
                  title: "RUM Errors by User",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:error ${team}`,
                        groupBy: [
                          {
                            facet: "@usr.app_installation_id",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "NFC Failures by User",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:view @view.name:NFC_FAILURE_* ${team}`,
                        groupBy: [
                          {
                            facet: "@usr.app_installation_id",
                            limit: 20,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Error Events by User Over Time",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: { aggregation: "count" },
                        searchQuery: `@type:error ${team}`,
                        groupBy: [
                          {
                            facet: "@usr.app_installation_id",
                            limit: 10,
                            sortQuery: {
                              aggregation: "count",
                              order: "desc",
                            },
                          },
                        ],
                      },
                      displayType: "bars",
                    },
                  ],
                },
              },
            ],
          },
        },

        // =====================================================================
        // Section 10: Beta Population
        // =====================================================================
        {
          groupDefinition: {
            title: "Beta Population",
            layoutType: "ordered",
            widget: [
              {
                timeseriesDefinition: {
                  showLegend: true,
                  legendLayout: "auto",
                  title: "Unique Active Users Over Time",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@usr.app_installation_id",
                        },
                        searchQuery: `@type:session ${team}`,
                        groupBy: [],
                      },
                      displayType: "bars",
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "Device Models",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@session.id",
                        },
                        searchQuery: `@type:session ${team}`,
                        groupBy: [
                          {
                            facet: "@device.model",
                            limit: 10,
                            sortQuery: {
                              aggregation: "cardinality",
                              facet: "@session.id",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
              {
                toplistDefinition: {
                  title: "OS Versions",
                  request: [
                    {
                      rumQuery: {
                        index: "*",
                        computeQuery: {
                          aggregation: "cardinality",
                          facet: "@usr.app_installation_id",
                        },
                        searchQuery: `@type:session ${team}`,
                        groupBy: [
                          {
                            facet: "@os.version",
                            limit: 10,
                            sortQuery: {
                              aggregation: "cardinality",
                              facet: "@usr.app_installation_id",
                              order: "desc",
                            },
                          },
                        ],
                      },
                    },
                  ],
                },
              },
            ],
          },
        },
      ],
    });
  }
}
