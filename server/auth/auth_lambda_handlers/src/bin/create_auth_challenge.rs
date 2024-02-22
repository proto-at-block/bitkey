use base64::{Engine as _, engine::general_purpose::STANDARD as BASE64};
use lambda_runtime::{service_fn, Error, LambdaEvent};
use rand::Rng;
use serde_json::{json, Value};


#[tokio::main]
async fn main() -> Result<(), Error> {
    tracing_subscriber::fmt::init();

    let handler = service_fn(handler);
    lambda_runtime::run(handler).await?;
    Ok(())
}

async fn handler(event: LambdaEvent<Value>) -> Result<Value, Error> {
    // Request and response scheme are here: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-lambda-create-auth-challenge.html

    let mut challenge_bytes: [u8; 64] = [0;64];
    rand::thread_rng().fill(&mut challenge_bytes);
    let challenge_str = BASE64.encode(challenge_bytes);

    // cognito needs us to send the whole event back, which includes the request, response, and some header information
    // so we're going to take the incoming event, add in the response, and then send it back
    let mut payload = event.payload.clone().as_object().ok_or(Error::from("could not deserialze cognito payload as map"))?.to_owned();
    let response = json!({
        "publicChallengeParameters": {"challenge": challenge_str}, // the public challenge parameters are what the client will see.
        "privateChallengeParameters": {"challenge": challenge_str}, // private challenge parameters are not passed to the client, but will be made available to `verify_auth_challenge`
        "challengeMetadata": ""});
    payload.insert("response".to_string(), response);
    Ok(Value::Object(payload))
}