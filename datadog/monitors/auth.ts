import { Construct } from "constructs";
import { Environment } from "./common/environments";
import { Monitor } from "./common/monitor";
import { getErrorRecipients } from "./recipients";

export class AuthMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `authentication_${environment}`);

    const errorRecipients = getErrorRecipients(environment);

    // Configuration for threshold-based metric monitors
    let threshold_alert_config = {
        recipients: errorRecipients,
        type: "metric alert",
        monitorThresholds: {
            critical: "99",   // Trigger when below 99%
        },
        alertCondition: "below",           
    };
    
    // Configuration for anomaly detection monitors
    let anomaly_alert_config = {
        recipients: errorRecipients,
        type: "query alert",
        monitorThresholds: {
            critical: "1",
        },
    };
    
    // Monitor 1: Authentication Success Rate Breach
    new Monitor(this, "auth-success-rate-breach", {
        query: `
            sum(last_15m):(
                100 * (
                    sum:bitkey.http.response{router_name:authn_authz,env:${environment},status:2*}.as_count()
                ) / (
                    sum:bitkey.http.response{router_name:authn_authz AND env:${environment} AND ( status:4* OR status:5*)}.as_count()
                )
            ) < ${threshold_alert_config.monitorThresholds.critical}
        `,
        name: `[Auth] Authentication Success Rate Breach in env: ${environment}`,
        message: `
            The authentication success rate has fallen below ${threshold_alert_config.monitorThresholds.critical}% for at least 15 minutes in the *${environment}* environment.
            This could indicate an issue with the authentication service.
    
            See runbook: https://docs.wallet.build/runbooks/auth/#breached-sla-for-authentication-endpoints
        `,
        tags: [`auth_success_rate_breach_${environment}`],
        ...threshold_alert_config
    });
    
    // Monitor 2: Successful Authentication Volume Anomaly
    new Monitor(this, "auth-success-volume-anomaly", {
        query: `
            avg(last_4h):anomalies(sum:bitkey.http.response{router_name:authn_authz,env:${environment},status:2*}.as_count(),'agile',2,direction='both',interval=60,alert_window='last_15m',count_default_zero='true') >= ${anomaly_alert_config.monitorThresholds.critical}
        `,
        name: `[Auth] Successful Authentication Volume Anomaly Detected - ${environment}`,
        message: `
            Authentication Volume Anomaly Detected
    
            *Environment:* ${environment}
            *Status:* Authentication 2xx volume has deviated significantly from normal patterns for >15 minutes
    
            *Potential Impact:*
            • Unexpected spike or drop in authentication attempts
            • Could indicate service issues or unusual user behavior
            • Possible security incident if spike is unexpected
    
            *Investigation Steps:*
            1. Check auth service logs in Datadog
            2. Review error rates in auth service metrics
            3. Check for any recent deployments or changes
    
            See runbook: https://docs.wallet.build/runbooks/auth/#anomoly-detection-in-successful-or-unsuccessful-authentication-attempts
        `,
        tags: [`auth_success_volume_anomaly_${environment}`],
        ...anomaly_alert_config
    });
    
    // Monitor 3: Failed Authentication Volume Anomaly
    new Monitor(this, "auth-failure-volume-anomaly", {
        query: `
            avg(last_4h):anomalies(sum:bitkey.http.response{router_name:authn_authz AND env:${environment} AND (status:4* OR status:5*)}.as_count(),'agile',2,direction='both',interval=60,alert_window='last_15m',count_default_zero='true') >= ${anomaly_alert_config.monitorThresholds.critical}
        `,
        name: `[Auth] Failed Authentication Volume Anomaly Detected - ${environment}`,
        message: `
            Failed Authentication Volume Anomaly Detected
    
            *Environment:* ${environment}
            *Status:* Failed authentication volume (4xx/5xx errors) has deviated significantly from normal patterns for >15 minutes
    
            *Potential Impact:*
            • Unexpected spike or drop in failed authentication attempts
            • Could indicate brute force attempts or credential stuffing attacks
            • May suggest API integration issues or client misconfiguration
            • Possible security incident if spike is unexpected
    
            *Investigation Steps:*
            1. Check auth service logs in Datadog for specific error codes
            2. Review failed authentication patterns (IPs, user agents, endpoints)
            3. Check for any recent deployments or client integrations
            4. Consider enabling additional security measures if attack suspected
    
            See runbook: https://docs.wallet.build/runbooks/auth/#anomoly-detection-in-successful-or-unsuccessful-authentication-attempts
        `,
        tags: [`auth_failure_volume_anomaly_${environment}`],
        ...anomaly_alert_config
    });
  }
}
