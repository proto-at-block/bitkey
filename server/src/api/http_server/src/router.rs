use axum::Router;

pub trait RouterBuilder {
    fn unauthed_router(&self) -> Router {
        Router::new()
    }

    fn account_authed_router(&self) -> Router {
        Router::new()
    }

    fn recovery_authed_router(&self) -> Router {
        Router::new()
    }

    fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
    }

    fn basic_validation_router(&self) -> Router {
        Router::new()
    }
}