use anyhow::Result;
use rustify::{blocking::client::Client, errors::ClientError, Endpoint, MiddleWare};

use crate::{entities::AuthenticationToken, nfc::SafeTransactor, signers::Authentication};

use super::middleware::{AuthenticationMiddleware, ContentTypeMiddleware, Keyproof};

pub(crate) trait EndpointExt {
    type Response;

    fn exec_unauthenticated(self, client: &impl Client) -> Result<Self::Response>;
    fn exec_authenticated(
        self,
        client: &impl Client,
        token: &AuthenticationToken,
    ) -> Result<Self::Response>;
    fn exec_keyproofed(
        self,
        client: &impl Client,
        token: &AuthenticationToken,
        application: Option<&impl Authentication>, // TODO: None::<...> is ugly AF
        hardware: Option<&impl Authentication>,    // TODO: None::<...> is ugly AF
        context: &SafeTransactor,
    ) -> Result<Self::Response>;
    fn exec_middleware(
        self,
        client: &impl Client,
        middleware: &impl MiddleWare,
    ) -> Result<Self::Response>;
}

impl<T: Endpoint> EndpointExt for T {
    type Response = T::Response;

    fn exec_unauthenticated(self, client: &impl Client) -> Result<Self::Response> {
        self.exec_middleware(client, &ContentTypeMiddleware)
    }

    fn exec_authenticated(
        self,
        client: &impl Client,
        token: &AuthenticationToken,
    ) -> Result<Self::Response> {
        self.exec_middleware(client, &AuthenticationMiddleware(token))
    }

    fn exec_keyproofed(
        self,
        client: &impl Client,
        token: &AuthenticationToken,
        application: Option<&impl Authentication>,
        hardware: Option<&impl Authentication>,
        context: &SafeTransactor,
    ) -> Result<Self::Response> {
        let application = application
            .map(|signer| Keyproof::header_value(token, signer, context))
            .transpose()?;
        let hardware = hardware
            .map(|signer| Keyproof::header_value(token, signer, context))
            .transpose()?;
        self.exec_middleware(client, &Keyproof::new(token, application, hardware)?)
    }

    fn exec_middleware(
        self,
        client: &impl Client,
        middleware: &impl MiddleWare,
    ) -> Result<Self::Response> {
        (|| self.with_middleware(middleware).exec_block(client)?.parse())()
            .map_err(helpful_client_error)
    }
}

fn helpful_client_error(error: ClientError) -> anyhow::Error {
    let context = match &error {
        ClientError::ServerResponseError { code: 401, content } => {
            let message = match content {
                Some(c) if !c.is_empty() => format!(" ({c})"),
                _ => " ".to_string(),
            };
            Some(format!(
                "Unauthorized!{message}Run `wallet authenticate` and try again."
            ))
        }
        ClientError::ServerResponseError {
            code: 404,
            content: _,
        } => Some("Endpoint not found! Has the server API changed?".to_string()),
        _ => Some(format!("{error:#?}")),
    };

    let error = anyhow::Error::new(error);

    match context {
        Some(c) => error.context(c),
        None => error,
    }
}
