use urn::Urn;

#[derive(Debug, thiserror::Error)]
#[error("could not encode external ID")]
pub struct Error(#[from] urn::Error);

pub trait ExternalIdentifier<T: ToString>: Sized + From<Urn> {
    fn namespace() -> &'static str;

    fn new(id: T) -> Result<Self, Error> {
        let nss = format!("wallet-{}", Self::namespace());
        let urn = urn::UrnBuilder::new(&nss, &id.to_string()).build()?;

        Ok(Self::from(urn))
    }
}
