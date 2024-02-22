use lambda_runtime::{Error, LambdaEvent, service_fn};
use serde_json::{json, Value};

const CUSTOM_CHALLENGE_STR: &str = "CUSTOM_CHALLENGE"; // defined by cognito. needs to be this literal

#[tokio::main]
async fn main() -> Result<(), Error> {
    tracing_subscriber::fmt::init();

    let handler = service_fn(handler);
    lambda_runtime::run(handler).await?;
    Ok(())
}

async fn handler(event: LambdaEvent<Value>) -> Result<Value, Error> {
    // Request and response scheme are here: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-lambda-define-auth-challenge.html
    println!("Got event: {}", event.payload.to_string());

    let request = event.payload.get("request")
        .ok_or(Error::from("'request' not found from cognito"))?
        .as_object()
        .ok_or(Error::from("could not deserialize request from cognito"))?;

    if let Some(user_not_found_val) = request.get("userNotFound") {
        if let Some(user_not_found) = user_not_found_val.as_bool() {
            if user_not_found {
                return Err(Error::from("user not found"));
            }
        }
    }

    // cognito needs us to send the whole event back, which includes the request, response, and some header information
    // so we're going to take the incoming event, add in the response, and then send it back
    let mut payload = event.payload.clone().as_object().ok_or(Error::from("could not deserialze cognito payload as map"))?.to_owned();

    let response = match request.get("session").and_then(|session| {
        session.as_array().and_then(|session| {
            session.last().and_then(|last_session| {
                last_session.as_object().and_then(|last_session| {
                    last_session.get("challengeResult").and_then(|challenge_result| challenge_result.as_bool().and_then(|challenge_result| {
                        if challenge_result == false {
                            Some(json!({"issueTokens": false, "failAuthentication": true}))
                        } else {
                            last_session.get("challengeName").and_then(|challenge_name| challenge_name.as_str().and_then(|challenge_name| {
                                if challenge_name == CUSTOM_CHALLENGE_STR && challenge_result {
                                    Some(json!({"issueTokens": true, "failAuthentication": false}))
                                } else {
                                    None
                                }
                            }))
                        }
                    }))
                })
            })
        })
    }) {
        None => json!({"issueTokens": false, "failAuthentication": false, "challengeName": CUSTOM_CHALLENGE_STR}),
        Some(resp) => resp
    };

    payload.insert("response".to_string(), response);
    Ok(Value::Object(payload))
}