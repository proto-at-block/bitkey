use std::collections::HashMap;

use aws_lambda_events::cognito::{
    ClaimsAndScopeOverrideDetailsV2, CognitoAccessTokenGenerationV2,
    CognitoEventUserPoolsPreTokenGenResponseV2, CognitoEventUserPoolsPreTokenGenV2,
};
use lambda_runtime::{service_fn, Error, LambdaEvent};

#[tokio::main]
async fn main() -> Result<(), Error> {
    tracing_subscriber::fmt::init();

    lambda_runtime::run(service_fn(handler)).await
}

async fn handler(
    event: LambdaEvent<CognitoEventUserPoolsPreTokenGenV2>,
) -> Result<CognitoEventUserPoolsPreTokenGenV2, Error> {
    let mut payload = event.payload.clone();
    let access_token_generation = Some(CognitoAccessTokenGenerationV2 {
        claims_to_add_or_override: HashMap::new(),
        claims_to_suppress: vec![],
        scopes_to_add: vec![],
        scopes_to_suppress: vec![String::from("aws.cognito.signin.user.admin")],
    });

    let ovr = ClaimsAndScopeOverrideDetailsV2 {
        access_token_generation,
        ..Default::default()
    };
    payload.response = CognitoEventUserPoolsPreTokenGenResponseV2 {
        claims_and_scope_override_details: Some(ovr),
    };

    Ok(payload)
}
