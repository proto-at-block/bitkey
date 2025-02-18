#!/bin/bash

openssl ecparam -name prime256v1 -genkey -noout -out wsm-staging-noise-private-key.pem
openssl ec -in wsm-staging-noise-private-key.pem -pubout -out wsm-staging-noise-public-key.pem
openssl ec -in wsm-staging-noise-private-key.pem -pubout -text
