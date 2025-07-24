use axum::body::Bytes;
use errors::ApiError;
use hmac::{Hmac, Mac};
use sha2::Sha256;
use tracing::instrument;

use crate::{
    entities::{IssueSlaBreached, IssueUpdate, Label, Webhook},
    metrics,
};

#[instrument(err, skip(webhook_secret, signature_header, body_bytes))]
pub(crate) fn validate_and_parse_webhook(
    webhook_secret: String,
    signature_header: linear_signature::Header,
    body_bytes: Bytes,
) -> Result<Webhook, ApiError> {
    let mut mac = Hmac::<Sha256>::new_from_slice(webhook_secret.as_bytes()).map_err(|_| {
        ApiError::GenericInternalApplicationError("invalid webhook secret".to_string())
    })?;

    mac.update(&body_bytes);

    let signature_header = signature_header
        .0
        .to_str()
        .map_err(|_| ApiError::GenericBadRequest("invalid signature header".to_string()))?;

    let signature_bytes = hex::decode(signature_header)
        .map_err(|_| ApiError::GenericBadRequest("failed to decode signature".to_string()))?;

    mac.verify_slice(&signature_bytes)
        .map_err(|_| ApiError::GenericUnauthorized("invalid signature".to_string()))?;

    let webhook: Webhook = serde_json::from_slice(&body_bytes)
        .map_err(|_| ApiError::GenericBadRequest("failed to parse webhook".to_string()))?;

    Ok(webhook)
}

#[instrument(skip(issue_update))]
pub(crate) fn process_issue_update(issue_update: IssueUpdate) {
    // Only look at CS Escalation tickets
    if !has_cs_escalation_label(issue_update.data.labels) {
        return;
    }

    // For CS Escalation tickets, the system removes the sla when the state is changed from Triage (or similar)
    let (None, Some(updated_at), None, Some(sla_breaches_at)) = (
        issue_update.actor,
        issue_update.data.updated_at,
        issue_update.data.sla_breaches_at,
        issue_update.updated_from.sla_breaches_at,
    ) else {
        return;
    };

    // If SLA wasn't met, we already emitted a metric when it breached via an IssueSLA breached webhook
    if updated_at >= sla_breaches_at {
        return;
    }

    metrics::CS_ESCALATION_SLA_MET.add(1, &[]);
}

#[instrument(skip(issue_sla_breached))]
pub(crate) fn process_issue_sla_breached(issue_sla_breached: IssueSlaBreached) {
    // Only look at CS Escalation tickets
    if !has_cs_escalation_label(issue_sla_breached.issue_data.labels) {
        return;
    }

    metrics::CS_ESCALATION_SLA_BREACHED.add(1, &[]);
}

fn has_cs_escalation_label(labels: Option<Vec<Label>>) -> bool {
    labels.is_some_and(|ls| ls.contains(&Label::CsEscalation {}))
}

pub(crate) mod linear_signature {
    static NAME: axum::http::HeaderName = axum::http::HeaderName::from_static("linear-signature");

    #[derive(Debug)]
    pub struct Header(pub axum::http::HeaderValue);

    impl axum_extra::headers::Header for Header {
        fn name() -> &'static axum::http::HeaderName {
            &NAME
        }

        fn decode<'i, I>(values: &mut I) -> Result<Self, axum_extra::headers::Error>
        where
            Self: Sized,
            I: Iterator<Item = &'i axum::http::HeaderValue>,
        {
            values
                .next()
                .cloned()
                .ok_or_else(axum_extra::headers::Error::invalid)
                .map(Header)
        }

        fn encode<E: Extend<axum::http::HeaderValue>>(&self, values: &mut E) {
            values.extend(::std::iter::once((&self.0).into()));
        }
    }
}
