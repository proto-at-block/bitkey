use anyhow::Result;
use http::HeaderValue;

use rustify::{errors::ClientError, Endpoint, MiddleWare};
use tracing::debug;
use wca::pcsc::Transactor;

use crate::{entities::AuthenticationToken, signers::Authentication};

// TODO: Upstream to rustify, https://github.com/jmgilman/rustify/issues/15
pub struct ContentTypeMiddleware;

impl MiddleWare for ContentTypeMiddleware {
    fn request<E>(
        &self,
        _: &E,
        req: &mut http::request::Request<Vec<u8>>,
    ) -> Result<(), ClientError>
    where
        E: Endpoint,
    {
        req.headers_mut()
            .append("content-type", HeaderValue::from_static("application/json"));

        Ok(())
    }

    fn response<E>(
        &self,
        _: &E,
        _: &mut http::response::Response<Vec<u8>>,
    ) -> Result<(), ClientError>
    where
        E: Endpoint,
    {
        Ok(())
    }
}

pub(crate) struct AuthenticationMiddleware<'a>(pub(crate) &'a AuthenticationToken);

impl MiddleWare for AuthenticationMiddleware<'_> {
    fn request<E: Endpoint>(
        &self,
        endpoint: &E,
        req: &mut http::Request<Vec<u8>>,
    ) -> Result<(), ClientError> {
        ContentTypeMiddleware.request(endpoint, req)?;

        req.headers_mut().append(
            "Authorization",
            HeaderValue::from_str(&format!("Bearer {}", self.0 .0))
                .map_err(|e| ClientError::GenericError { source: e.into() })?,
        );

        Ok(())
    }

    fn response<E: Endpoint>(
        &self,
        endpoint: &E,
        resp: &mut http::Response<Vec<u8>>,
    ) -> Result<(), ClientError> {
        ContentTypeMiddleware.response(endpoint, resp)?;

        Ok(())
    }
}

pub struct Keyproof<'a> {
    inner_middleware: AuthenticationMiddleware<'a>,
    application: Option<HeaderValue>,
    hardware: Option<HeaderValue>,
}

impl<'a> Keyproof<'a> {
    pub(crate) fn header_value<S: Authentication>(
        authentication: &AuthenticationToken,
        signer: &S,
        context: &impl Transactor,
    ) -> Result<HeaderValue> {
        let token = authentication.0.as_bytes();
        let signature = signer.sign(token, context)?;
        Ok(HeaderValue::from_str(&signature.to_string())?)
    }

    pub(crate) fn new(
        authentication: &'a AuthenticationToken,
        application: Option<HeaderValue>,
        hardware: Option<HeaderValue>,
    ) -> Result<Keyproof<'a>> {
        Ok(Self {
            inner_middleware: AuthenticationMiddleware(authentication),
            application,
            hardware,
        })
    }
}

impl MiddleWare for Keyproof<'_> {
    fn request<E: Endpoint>(
        &self,
        endpoint: &E,
        req: &mut http::Request<Vec<u8>>,
    ) -> Result<(), ClientError> {
        self.inner_middleware.request(endpoint, req)?;

        if let Some(ref value) = self.application {
            debug!("X-App-Signature: {value:?}");
            req.headers_mut().append("X-App-Signature", value.clone());
        }

        if let Some(ref value) = self.hardware {
            debug!("X-Hw-Signature: {value:?}");
            req.headers_mut().append("X-Hw-Signature", value.clone());
        }

        Ok(())
    }

    fn response<E: Endpoint>(
        &self,
        endpoint: &E,
        resp: &mut http::Response<Vec<u8>>,
    ) -> Result<(), ClientError> {
        self.inner_middleware.response(endpoint, resp)?;

        Ok(())
    }
}
