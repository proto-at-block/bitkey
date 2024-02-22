use std::marker::PhantomData;
use std::sync::Arc;

use launchdarkly_server_sdk::{Client, Context, ContextBuilder, FlagValue, Reason};

use crate::{service::Service, Error};

#[derive(Clone)]
pub struct Flag<T> {
    pub key: &'static str,
    pub phantom: PhantomData<T>,
}

impl<T> Flag<T>
where
    T: Clone,
{
    pub const fn new(key: &'static str) -> Self {
        Self {
            key,
            phantom: PhantomData,
        }
    }

    pub fn resolver(&self, service: &Service) -> Resolver<T> {
        self.with_context(service, "flag")
            .expect("context should always be valid")
    }

    fn with_context(&self, service: &Service, context: &str) -> Result<Resolver<T>, Error> {
        let context = ContextBuilder::new(context)
            .build()
            .map_err(Error::Context)?;
        Ok(Resolver {
            flag: self.clone(),
            service: service.clone(),
            context,
        })
    }
}

#[derive(Clone)]
pub struct Resolver<T> {
    service: Service,
    context: Context,
    flag: Flag<T>,
}

fn resolve<T: Into<FlagValue> + Clone>(
    client: Arc<Client>,
    context: &Context,
    key: &'static str,
    default: T,
) -> FlagValue {
    let detail = client.variation_detail(context, key, default);
    match detail.reason {
        Reason::Error { error } => {
            // We can only reach this state
            //
            // * if we use a flag before initializing the LaunchDarkly SDK. This is an invalid
            //   application state and should panic.
            // * if the flag itself is misconfigured, invalid, or malformed. These are
            //   programming errors or invalid states, and should panic.
            // * if we are in tests and the flag is not configured. We should panic and
            //   require that the test initializes the flag properly.
            panic!("flag {key}: {error:?}")
        }
        _ => detail
            .value
            .expect("value is always available when reason is not an error"),
    }
}

impl Resolver<bool> {
    pub fn resolve(&self) -> bool {
        if let Some(b) = self
            .service
            .override_flags
            .get(self.flag.key)
            .and_then(|f| f.parse().ok())
        {
            return b;
        }
        resolve(
            self.service.client.clone(),
            &self.context,
            self.flag.key,
            false,
        )
        .as_bool()
        .expect("flag should always be a bool")
    }
}

impl Resolver<&str> {
    pub fn resolve(&self) -> String {
        if let Some(f) = self.service.override_flags.get(self.flag.key) {
            return f.to_owned();
        }
        resolve(
            self.service.client.clone(),
            &self.context,
            self.flag.key,
            FlagValue::Str("".to_string()),
        )
        .as_string()
        .expect("flag should always be a string")
    }
}
