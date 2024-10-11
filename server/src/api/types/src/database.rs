pub trait Upsertable {
    const KEY_PROPERTIES: &'static [&'static str];
    const IF_NOT_EXISTS_PROPERTIES: &'static [&'static str];
}
