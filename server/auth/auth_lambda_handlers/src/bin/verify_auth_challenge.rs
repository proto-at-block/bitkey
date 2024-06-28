use lambda_runtime::{service_fn, Error, LambdaEvent};
use secp256k1::ecdsa::Signature;
use secp256k1::hashes::sha256;
use secp256k1::{Message, PublicKey, Secp256k1};
use serde_json::{json, Value};
use std::str::FromStr;

#[tokio::main]
async fn main() -> Result<(), Error> {
    tracing_subscriber::fmt::init();

    let handler = service_fn(handler);
    lambda_runtime::run(handler).await?;
    Ok(())
}

//IMPORTANT: Update terraform files and Lambdas when changing these attributes
const PUBLIC_KEY_ATTRIBUTE: &str = "custom:publicKey";

// Lambda request/response parameters
const LAMBDA_REQUEST_PARAM: &str = "request";
const LAMBDA_PRIVATE_CHALLENGE_PARAMS_PARAM: &str = "privateChallengeParameters";
const LAMBDA_CHALLENGE_PARAM: &str = "challenge";
const LAMBDA_USER_ATTRIBUTES_PARAM: &str = "userAttributes";
const LAMBDA_CHALLENGE_ANSWER_PARAM: &str = "challengeAnswer";
const LAMBDA_RESPONSE_PARAM: &str = "response";
const LAMBDA_ANSWER_CORRECT_PARAM: &str = "answerCorrect";

async fn handler(event: LambdaEvent<Value>) -> Result<Value, Error> {
    // Request and response schema documented here: https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-lambda-verify-auth-challenge-response.html

    let request = event
        .payload
        .get(LAMBDA_REQUEST_PARAM)
        .ok_or(Error::from("'request' not found from cognito"))?
        .as_object()
        .ok_or(Error::from("could not deserialize request from cognito"))?;

    let private_challenge_parameters = request
        .get(LAMBDA_PRIVATE_CHALLENGE_PARAMS_PARAM)
        .ok_or(Error::from(
            "could not get private challenge parameters from cognito request",
        ))?
        .as_object()
        .ok_or(Error::from(
            "could not deserialize private challenge parameters",
        ))?;

    // get private challenge data (provided by `create_auth_challenge`)
    let challenge = private_challenge_parameters
        .get(LAMBDA_CHALLENGE_PARAM)
        .ok_or(Error::from("challenge not found in private parameters"))?
        .as_str()
        .ok_or(Error::from("could not deserialize challenge"))?;

    let user_attributes = request
        .get(LAMBDA_USER_ATTRIBUTES_PARAM)
        .ok_or("could not get user attributes from cognito request")?
        .as_object()
        .ok_or(Error::from("could not deserialize user attributes"))?;

    // get pubkeys associated with user
    let public_key_str = user_attributes.get(PUBLIC_KEY_ATTRIBUTE).map_or(
        Err(Error::from("Could not find publicKey attribute")),
        |pk| {
            pk.as_str()
                .ok_or(Error::from("Could not deserialize publicKey"))
        },
    )?;
    let public_key = PublicKey::from_str(public_key_str)
        .map_err(|err| Error::from(format!("Could not parse publicKey: {}", err)))?;

    // get challenge answer provided by the caller
    let challenge_answer = request
        .get(LAMBDA_CHALLENGE_ANSWER_PARAM)
        .ok_or_else(|| Error::from("challenge answer not provided"))?
        .as_str()
        .ok_or_else(|| Error::from("Could not parse challenge answer as string"))?
        .to_string();

    let secp = Secp256k1::new();
    let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_bytes());
    let answer_correct = if let Ok(signature) = Signature::from_str(&challenge_answer) {
        if secp.verify_ecdsa(&message, &signature, &public_key).is_ok() {
            true
        } else {
            println!(
                "Bad signature {} for message {}! Returning False",
                signature, message
            );
            false
        }
    } else {
        println!(
            "Can not parse signature from challenge answer: {}",
            challenge_answer
        );
        false
    };

    // cognito needs us to send the whole event back, which includes the request, response, and some header information
    // so we're going to take the incoming event, add in the response, and then send it back
    let mut payload = event
        .payload
        .clone()
        .as_object()
        .ok_or(Error::from("could not deserialze cognito payload as map"))?
        .to_owned();
    let response = json!({LAMBDA_ANSWER_CORRECT_PARAM: answer_correct});
    payload.insert(LAMBDA_RESPONSE_PARAM.to_string(), response);
    println!(
        "Here's the response we're going to send to cognito: {:?}",
        payload
    );
    Ok(Value::Object(payload))
}

#[cfg(test)]
mod test {
    use crate::{
        handler, LAMBDA_ANSWER_CORRECT_PARAM, LAMBDA_CHALLENGE_ANSWER_PARAM,
        LAMBDA_CHALLENGE_PARAM, LAMBDA_PRIVATE_CHALLENGE_PARAMS_PARAM, LAMBDA_REQUEST_PARAM,
        LAMBDA_RESPONSE_PARAM, LAMBDA_USER_ATTRIBUTES_PARAM, PUBLIC_KEY_ATTRIBUTE,
    };
    use bitcoin_hashes::{sha256, Hash};
    use lambda_runtime::{Context, LambdaEvent};
    use secp256k1::ecdsa::Signature;
    use secp256k1::{Message, PublicKey, Secp256k1};
    use serde_json::{json, Map, Value};
    use std::str::FromStr;

    const TEST_CHALLENGE: &str = "test_challenge";

    struct PublicKeyAndSignature(pub PublicKey, pub Signature);
    struct TestData {
        app: PublicKeyAndSignature,
        hw: PublicKeyAndSignature,
        recovery: PublicKeyAndSignature,
        msg: Message,
    }

    impl TestData {
        fn gen_user_attrs(&self) -> ((String, Value), (String, Value), (String, Value)) {
            let app = (
                PUBLIC_KEY_ATTRIBUTE.to_string(),
                Value::String(self.app.0.to_string()),
            );
            let hw = (
                PUBLIC_KEY_ATTRIBUTE.to_string(),
                Value::String(self.hw.0.to_string()),
            );
            let recovery = (
                PUBLIC_KEY_ATTRIBUTE.to_string(),
                Value::String(self.recovery.0.to_string()),
            );
            (app, hw, recovery)
        }
    }

    fn gen_test_data() -> TestData {
        // For updating these tests, the SecretKeys corresponding to the values below are:
        // app_seckey: 3569512692d20422b2342b0fcfc6f47ab94c855f15be78af3a9ecdb2296637d3
        // hw_seckey: c8a078a5a4b5f7b01344c506948fa44b84e22bbfa676d75a2c1dff2615ffcae6
        // recovery_seckey: 9e2f5eaff1b4db0359008b15fad1bf6d812825934c3966faf85c8a6e4b5b2ae3

        let hash = sha256::Hash::hash(TEST_CHALLENGE.as_bytes());
        let msg = Message::from_slice(&hash[..]).unwrap();
        let app_sig = Signature::from_str("3045022100b8c6b892beb23d354f146bf8d502a0733c2af274c71aaa12d5d31f6364ca43bb02205927249ea801f962bbbcf45400500650dcd3ddeed0204f5ad6a9ffdf9e358995").expect("Valid app signature");
        let hw_sig = Signature::from_str("3044022033b4ee615036e0ea6f0d6b3e34aa3176a7c7d9d40a2880fabf1733813c87e9d202203f19f28d9221c7f4e1885118f9e04f0e1ecac18ae7d1100e55d277c7b1e6667a").expect("Valid hw signature");
        let recovery_sig = Signature::from_str("30450221008e22459062c52191674add5e76b82eb5b4029f53333d70910052ae7955ed960f0220097782b3d789a93a6191214b65067888378193a2d26c01b3309831c5cfc8a9c8").expect("Valid recovery signature");

        let apk = PublicKey::from_str(
            "0370845f95afe375d0737814c9051215fff9bbfe3b23da220d51146d57fbb45acc",
        )
        .expect("Valid app pubkey");
        let hpk = PublicKey::from_str(
            "03a440d3494e1835b63afea122773137d939b11f6a53d9b358aa0621a89d05514a",
        )
        .expect("Valid hw pubkey");
        let rpk = PublicKey::from_str(
            "02975efeeb25c8b5b56bf6587dc7ec4cf175559e37cd6db1e1551e809e5da1063e",
        )
        .expect("Valid recovery pubkey");

        TestData {
            app: PublicKeyAndSignature(apk, app_sig),
            hw: PublicKeyAndSignature(hpk, hw_sig),
            recovery: PublicKeyAndSignature(rpk, recovery_sig),
            msg,
        }
    }

    #[tokio::test]
    async fn test_app_user() {
        let data = gen_test_data();
        let attrs = data.gen_user_attrs();

        let payload = json!({
            LAMBDA_REQUEST_PARAM: {
                LAMBDA_PRIVATE_CHALLENGE_PARAMS_PARAM: {
                    LAMBDA_CHALLENGE_PARAM: TEST_CHALLENGE
                },
                LAMBDA_USER_ATTRIBUTES_PARAM: {
                    attrs.0.0: attrs.0.1
                },
                LAMBDA_CHALLENGE_ANSWER_PARAM: data.app.1.to_string()
            }
        });

        let event = LambdaEvent {
            payload,
            context: Context::default(),
        };

        let result = handler(event).await.unwrap();
        assert_eq!(
            result
                .get(LAMBDA_RESPONSE_PARAM)
                .unwrap()
                .get(LAMBDA_ANSWER_CORRECT_PARAM)
                .unwrap(),
            &Value::Bool(true)
        );
    }

    #[tokio::test]
    async fn test_hw_user() {
        let data = gen_test_data();
        let attrs = data.gen_user_attrs();

        let payload = json!({
            LAMBDA_REQUEST_PARAM: {
                LAMBDA_PRIVATE_CHALLENGE_PARAMS_PARAM: {
                    LAMBDA_CHALLENGE_PARAM: TEST_CHALLENGE
                },
                LAMBDA_USER_ATTRIBUTES_PARAM: {
                    attrs.1.0: attrs.1.1
                },
                LAMBDA_CHALLENGE_ANSWER_PARAM: data.hw.1.to_string()
            }
        });

        let event = LambdaEvent {
            payload,
            context: Context::default(),
        };

        let result = handler(event).await.unwrap();
        assert_eq!(
            result
                .get(LAMBDA_RESPONSE_PARAM)
                .unwrap()
                .get(LAMBDA_ANSWER_CORRECT_PARAM)
                .unwrap(),
            &Value::Bool(true)
        );
    }

    #[tokio::test]
    async fn test_recovery_user() {
        let data = gen_test_data();
        let attrs = data.gen_user_attrs();

        let payload = json!({
            LAMBDA_REQUEST_PARAM: {
                LAMBDA_PRIVATE_CHALLENGE_PARAMS_PARAM: {
                    LAMBDA_CHALLENGE_PARAM: TEST_CHALLENGE
                },
                LAMBDA_USER_ATTRIBUTES_PARAM: {
                    attrs.2.0: attrs.2.1
                },
                LAMBDA_CHALLENGE_ANSWER_PARAM: data.recovery.1.to_string()
            }
        });

        let event = LambdaEvent {
            payload,
            context: Context::default(),
        };

        let result = handler(event).await.unwrap();
        assert_eq!(
            result
                .get(LAMBDA_RESPONSE_PARAM)
                .unwrap()
                .get(LAMBDA_ANSWER_CORRECT_PARAM)
                .unwrap(),
            &Value::Bool(true)
        );
    }
}
