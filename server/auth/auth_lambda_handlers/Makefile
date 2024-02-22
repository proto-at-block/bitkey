PLATFORM := $(shell uname)
ifeq  ($(PLATFORM),Linux)
	TARGET = --target $(shell uname -m)-unknown-linux-musl
else
	TARGET =
endif
CARGO = cargo build $(TARGET) --release --locked

.PHONY: buildlambda
buildlambda:
	$(CARGO) \
		--bin create_auth_challenge \
		--bin define_auth_challenge \
		--bin verify_auth_challenge \
		--bin pre_token_generation

.PHONY: clean
clean:
	cargo clean
