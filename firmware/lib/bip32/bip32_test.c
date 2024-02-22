#include "arithmetic.h"
#include "bip32.h"
#include "criterion_test_utils.h"
#include "ecc.h"
#include "hash.h"
#include "hex.h"
#include "libbase58.h"

#include <criterion/criterion.h>
#include <openssl/bn.h>

#include <stdio.h>

bool fixed_b58check_enc(char* b58c, size_t* b58c_sz, const void* data, size_t datasz) {
  uint8_t buf[datasz + sizeof(uint32_t)];
  uint8_t* hash = &buf[datasz];

  memcpy(&buf[0], data, datasz);
  if (!crypto_sha256d(hash, buf, datasz)) {
    *b58c_sz = 0;
    return false;
  }

  return b58enc(b58c, b58c_sz, buf, datasz + sizeof(uint32_t));
}

static void check_private_key(extended_key_t* child_priv, extended_key_t* parent_pub,
                              version_bytes_t version, uint32_t child_num, uint8_t depth,
                              char* b58_expected) {
  uint8_t serialized_priv_key[BIP32_SERIALIZED_EXT_KEY_SIZE] = {0};

  cr_assert(bip32_serialize_ext_key(child_priv, parent_pub, NULL, version, child_num, depth,
                                    serialized_priv_key, sizeof(serialized_priv_key)));
  char base58_encoded_priv_key[BIP32_SERIALIZED_B58_ENCODED_EXT_KEY_SIZE] = {0};

  size_t encoded_size = sizeof(base58_encoded_priv_key);
  cr_assert(fixed_b58check_enc(base58_encoded_priv_key, &encoded_size, serialized_priv_key,
                               sizeof(serialized_priv_key)));
  cr_util_cmp_buffers(base58_encoded_priv_key, b58_expected,
                      BIP32_SERIALIZED_B58_ENCODED_EXT_KEY_SIZE);
}

static void check_public_key(extended_key_t* child_priv, extended_key_t* child_pub,
                             extended_key_t* parent_pub, version_bytes_t version,
                             uint32_t child_num, uint8_t depth, char* b58_expected) {
  uint8_t serialized_pub_key[BIP32_SERIALIZED_EXT_KEY_SIZE] = {0};
  cr_assert(bip32_priv_to_pub(child_priv, child_pub));
  cr_assert(bip32_serialize_ext_key(child_pub, parent_pub, NULL, version, child_num, depth,
                                    serialized_pub_key, sizeof(serialized_pub_key)));
  char base58_encoded_pub_key[BIP32_SERIALIZED_B58_ENCODED_EXT_KEY_SIZE] = {0};

  size_t encoded_size = sizeof(base58_encoded_pub_key);
  cr_assert(fixed_b58check_enc(base58_encoded_pub_key, &encoded_size, serialized_pub_key,
                               sizeof(serialized_pub_key)));

  cr_util_cmp_buffers(base58_encoded_pub_key, b58_expected,
                      BIP32_SERIALIZED_B58_ENCODED_EXT_KEY_SIZE);
}

Test(bip32_test, vector_1) {
  extended_key_t root_priv;
  extended_key_t root_pub;
  extended_key_t parent_pub;
  extended_key_t child_priv;
  extended_key_t child_pub;
  uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE];

  crypto_ecc_secp256k1_init();

  uint8_t seed[] = {
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
  };

  // Chain m
  {
    cr_assert(bip32_derive_master_key(seed, sizeof(seed), &root_priv));

    check_private_key(
      &root_priv, NULL, MAINNET_PRIV, 0, 0,
      "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TG"
      "tR"
      "BeJgk33yuGBxrMPHi");

    check_public_key(
      &root_priv, &root_pub, NULL, MAINNET_PUB, 0, 0,
      "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TM"
      "g7"
      "usUDFdp6W1EGMcet8");

    parent_pub = root_pub;
  }

  // Chain m/0H
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    uint8_t expected_fingerprint[] = {0x34, 0x42, 0x19, 0x3e};
    cr_util_cmp_buffers(parent_fingerprint, expected_fingerprint, sizeof(parent_fingerprint));

    check_private_key(
      &child_priv, &parent_pub, MAINNET_PRIV, path.indices[0], 1,
      "xprv9uHRZZhk6KAJC1avXpDAp4MDc3sQKNxDiPvvkX8Br5ngLNv1TxvUxt4cV1rGL5hj6KCesnDYUhd7oWgT11eZG7Xn"
      "xHrnYeSvkzY7d2bhkJ7");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) ==
              false);  // Not valid for hardened derivation.

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[0], 1,
                     "xpub68Gmy5EdvgibQVfPdqkBBCHxA5htiqg55crXYuXoQRKfDBFA1WEjWgP6LHhwBZeNK1VTsfTFU"
                     "HCdrfp1bgwQ9xv5ski8PX9rL2dZXvgGDnw");

    parent_pub = child_pub;
  }

  // Chain m/0H/1
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT, 1};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    uint8_t expected_fingerprint[] = {0x5c, 0x1b, 0xd6, 0x48};
    cr_util_cmp_buffers(parent_fingerprint, expected_fingerprint, sizeof(parent_fingerprint));

    check_private_key(
      &child_priv, &parent_pub, MAINNET_PRIV, path.indices[1], 2,
      "xprv9wTYmMFdV23N2TdNG573QoEsfRrWKQgWeibmLntzniatZvR9BmLnvSxqu53Kw1UmYPxLgboyZQaXwTCg8MSY3H2E"
      "U4pWcQDnRnrVA1xe8fs");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) ==
              false);  // Not valid for hardened derivation.

    check_public_key(
      &child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[1], 2,
      "xpub6ASuArnXKPbfEwhqN6e3mwBcDTgzisQN1wXN9BJcM47sSikHjJf3UFHKkNAWbWMiGj7Wf5uMash7SyYq527Hqck2"
      "AxYysAA7xmALppuCkwQ");

    parent_pub = child_pub;
  }

  // Chain m/0H/1/2H
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT, 1, 2 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    uint8_t expected_fingerprint[] = {0xbe, 0xf5, 0xa2, 0xf9};
    cr_util_cmp_buffers(parent_fingerprint, expected_fingerprint, sizeof(parent_fingerprint));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[2], 3,
                      "xprv9z4pot5VBttmtdRTWfWQmoH1taj2axGVzFqSb8C9xaxKymcFzXBDptWmT7FwuEzG3ryjH4kt"
                      "ypQSAewRiNMjANTtpgP4mLTj34bhnZX7UiM");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) ==
              false);  // Not valid for hardened derivation.

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[2], 3,
                     "xpub6D4BDPcP2GT577Vvch3R8wDkScZWzQzMMUm3PWbmWvVJrZwQY4VUNgqFJPMM3No2dFDFGTsxx"
                     "pG5uJh7n7epu4trkrX7x7DogT5Uv6fcLW5");

    parent_pub = child_pub;
  }

  // Chain m/0H/1/2H/2
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT, 1, 2 | BIP32_HARDENED_BIT, 2};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    uint8_t expected_fingerprint[] = {0xee, 0x7a, 0xb9, 0x0c};
    cr_util_cmp_buffers(parent_fingerprint, expected_fingerprint, sizeof(parent_fingerprint));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[3], 4,
                      "xprvA2JDeKCSNNZky6uBCviVfJSKyQ1mDYahRjijr5idH2WwLsEd4Hsb2Tyh8RfQMuPh7f7RtyzT"
                      "tdrbdqqsunu5Mm3wDvUAKRHSC34sJ7in334");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) ==
              false);  // Not valid for hardened derivation.

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[3], 4,
                     "xpub6FHa3pjLCk84BayeJxFW2SP4XRrFd1JYnxeLeU8EqN3vDfZmbqBqaGJAyiLjTAwm6ZLRQUMv1"
                     "ZACTj37sR62cfN7fe5JnJ7dh8zL4fiyLHV");

    parent_pub = child_pub;
  }

  // Chain m/0H/1/2H/2/1000000000
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT, 1, 2 | BIP32_HARDENED_BIT, 2, 1000000000};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    uint8_t expected_fingerprint[] = {0xd8, 0x80, 0xd7, 0xd8};
    cr_util_cmp_buffers(parent_fingerprint, expected_fingerprint, sizeof(parent_fingerprint));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[4], 5,
                      "xprvA41z7zogVVwxVSgdKUHDy1SKmdb533PjDz7J6N6mV6uS3ze1ai8FHa8kmHScGpWmj4WggLyQ"
                      "jgPie1rFSruoUihUZREPSL39UNdE3BBDu76");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) ==
              false);  // Not valid for hardened derivation.

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[4], 5,
                     "xpub6H1LXWLaKsWFhvm6RVpEL9P4KfRZSW7abD2ttkWP3SSQvnyA8FSVqNTEcYFgJS2UaFcxupHiY"
                     "kro49S8yGasTvXEYBVPamhGW6cFJodrTHy");

    parent_pub = child_pub;
  }
}

Test(bip32_test, vector_2) {
  extended_key_t root_priv;
  extended_key_t root_pub;
  extended_key_t parent_pub;
  extended_key_t child_priv;
  extended_key_t child_pub;
  uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE];

  crypto_ecc_secp256k1_init();

  uint8_t seed[] = {0xff, 0xfc, 0xf9, 0xf6, 0xf3, 0xf0, 0xed, 0xea, 0xe7, 0xe4, 0xe1, 0xde, 0xdb,
                    0xd8, 0xd5, 0xd2, 0xcf, 0xcc, 0xc9, 0xc6, 0xc3, 0xc0, 0xbd, 0xba, 0xb7, 0xb4,
                    0xb1, 0xae, 0xab, 0xa8, 0xa5, 0xa2, 0x9f, 0x9c, 0x99, 0x96, 0x93, 0x90, 0x8d,
                    0x8a, 0x87, 0x84, 0x81, 0x7e, 0x7b, 0x78, 0x75, 0x72, 0x6f, 0x6c, 0x69, 0x66,
                    0x63, 0x60, 0x5d, 0x5a, 0x57, 0x54, 0x51, 0x4e, 0x4b, 0x48, 0x45, 0x42};

  // Chain m
  {
    cr_assert(bip32_derive_master_key(seed, sizeof(seed), &root_priv));

    check_private_key(&root_priv, NULL, MAINNET_PRIV, 0, 0,
                      "xprv9s21ZrQH143K31xYSDQpPDxsXRTUcvj2iNHm5NUtrGiGG5e2DtALGdso3pGz6ssrdK4PFmM8"
                      "NSpSBHNqPqm55Qn3LqFtT2emdEXVYsCzC2U");

    check_public_key(&root_priv, &root_pub, NULL, MAINNET_PUB, 0, 0,
                     "xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8"
                     "idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB");

    parent_pub = root_pub;
  }

  // Chain m/0
  {
    uint32_t indices[] = {0};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[0], 1,
                      "xprv9vHkqa6EV4sPZHYqZznhT2NPtPCjKuDKGY38FBWLvgaDx45zo9WQRUT3dKYnjwih2yJD9mkr"
                      "ocEZXo1ex8G81dwSM1fwqWpWkeS3v86pgKt");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path));

    extended_key_t expected_child_pub;
    cr_assert(bip32_priv_to_pub(&child_priv, &expected_child_pub));
    cr_util_cmp_buffers(&child_pub, &expected_child_pub, sizeof(expected_child_pub));

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[0], 1,
                     "xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjX"
                     "oUmmDznezpbZb7ap6r1D3tgFxHmwMkQTPH");

    parent_pub = child_pub;
  }

  // Chain m/0/2147483647H
  {
    uint32_t indices[] = {0, 2147483647 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[1], 2,
                      "xprv9wSp6B7kry3Vj9m1zSnLvN3xH8RdsPP1Mh7fAaR7aRLcQMKTR2vidYEeEg2mUCTAwCd6vnxV"
                      "rcjfy2kRgVsFawNzmjuHc2YmYRmagcEPdU9");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    cr_assert(bip32_priv_to_pub(&child_priv, &child_pub));

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[1], 2,
                     "xpub6ASAVgeehLbnwdqV6UKMHVzgqAG8Gr6riv3Fxxpj8ksbH9ebxaEyBLZ85ySDhKiLDBrQSARLq"
                     "1uNRts8RuJiHjaDMBU4Zn9h8LZNnBC5y4a");

    parent_pub = child_pub;
  }

  // Chain m/0/2147483647H/1
  {
    uint32_t indices[] = {0, 2147483647 | BIP32_HARDENED_BIT, 1};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[2], 3,
                      "xprv9zFnWC6h2cLgpmSA46vutJzBcfJ8yaJGg8cX1e5StJh45BBciYTRXSd25UEPVuesF9yog62t"
                      "GAQtHjXajPPdbRCHuWS6T8XA2ECKADdw4Ef");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    cr_assert(bip32_priv_to_pub(&child_priv, &child_pub));

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[2], 3,
                     "xpub6DF8uhdarytz3FWdA8TvFSvvAh8dP3283MY7p2V4SeE2wyWmG5mg5EwVvmdMVCQcoNJxGoWaU"
                     "9DCWh89LojfZ537wTfunKau47EL2dhHKon");

    parent_pub = child_pub;
  }

  // Chain m/0/2147483647H/1/2147483646H
  {
    uint32_t indices[] = {0, 2147483647 | BIP32_HARDENED_BIT, 1, 2147483646 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[3], 4,
                      "xprvA1RpRA33e1JQ7ifknakTFpgNXPmW2YvmhqLQYMmrj4xJXXWYpDPS3xz7iAxn8L39njGVyuos"
                      "eXzU6rcxFLJ8HFsTjSyQbLYnMpCqE2VbFWc");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    cr_assert(bip32_priv_to_pub(&child_priv, &child_pub));

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[3], 4,
                     "xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHW"
                     "kY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL");

    parent_pub = child_pub;
  }

  // Chain m/0/2147483647H/1/2147483646H/2
  {
    uint32_t indices[] = {0, 2147483647 | BIP32_HARDENED_BIT, 1, 2147483646 | BIP32_HARDENED_BIT,
                          2};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[4], 5,
                      "xprvA2nrNbFZABcdryreWet9Ea4LvTJcGsqrMzxHx98MMrotbir7yrKCEXw7nadnHM8Dq38EGfSh"
                      "6dqA9QWTyefMLEcBYJUuekgW4BYPJcr9E7j");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    cr_assert(bip32_priv_to_pub(&child_priv, &child_pub));

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[4], 5,
                     "xpub6FnCn6nSzZAw5Tw7cgR9bi15UV96gLZhjDstkXXxvCLsUXBGXPdSnLFbdpq8p9HmGsApME5hQ"
                     "TZ3emM2rnY5agb9rXpVGyy3bdW6EEgAtqt");

    parent_pub = child_pub;
  }
}

Test(bip32_test, vector_3) {
  extended_key_t root_priv;
  extended_key_t root_pub;
  extended_key_t parent_pub;
  extended_key_t child_priv;
  extended_key_t child_pub;
  uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE];

  crypto_ecc_secp256k1_init();

  uint8_t seed[] = {0x4b, 0x38, 0x15, 0x41, 0x58, 0x3b, 0xe4, 0x42, 0x33, 0x46, 0xc6, 0x43, 0x85,
                    0x0d, 0xa4, 0xb3, 0x20, 0xe4, 0x6a, 0x87, 0xae, 0x3d, 0x2a, 0x4e, 0x6d, 0xa1,
                    0x1e, 0xba, 0x81, 0x9c, 0xd4, 0xac, 0xba, 0x45, 0xd2, 0x39, 0x31, 0x9a, 0xc1,
                    0x4f, 0x86, 0x3b, 0x8d, 0x5a, 0xb5, 0xa0, 0xd0, 0xc6, 0x4d, 0x2e, 0x8a, 0x1e,
                    0x7d, 0x14, 0x57, 0xdf, 0x2e, 0x5a, 0x3c, 0x51, 0xc7, 0x32, 0x35, 0xbe};

  // Chain m
  {
    cr_assert(bip32_derive_master_key(seed, sizeof(seed), &root_priv));

    check_private_key(&root_priv, NULL, MAINNET_PRIV, 0, 0,
                      "xprv9s21ZrQH143K25QhxbucbDDuQ4naNntJRi4KUfWT7xo4EKsHt2QJDu7KXp1A3u7Bi1j8ph3E"
                      "GsZ9Xvz9dGuVrtHHs7pXeTzjuxBrCmmhgC6");

    check_public_key(&root_priv, &root_pub, NULL, MAINNET_PUB, 0, 0,
                     "xpub661MyMwAqRbcEZVB4dScxMAdx6d4nFc9nvyvH3v4gJL378CSRZiYmhRoP7mBy6gSPSCYk6SzX"
                     "PTf3ND1cZAceL7SfJ1Z3GC8vBgp2epUt13");

    parent_pub = root_pub;
  }

  // Chain m/0H
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[0], 1,
                      "xprv9uPDJpEQgRQfDcW7BkF7eTya6RPxXeJCqCJGHuCJ4GiRVLzkTXBAJMu2qaMWPrS7AANYqdq6"
                      "vcBcBUdJCVVFceUvJFjaPdGZ2y9WACViL4L");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[0], 1,
                     "xpub68NZiKmJWnxxS6aaHmn81bvJeTESw724CRDs6HbuccFQN9Ku14VQrADWgqbhhTHBaohPX4CjN"
                     "Lf9fq9MYo6oDaPPLPxSb7gwQN3ih19Zm4Y");

    parent_pub = child_pub;
  }
}

Test(bip32_test, vector_4) {
  extended_key_t root_priv;
  extended_key_t root_pub;
  extended_key_t parent_pub;
  extended_key_t child_priv;
  extended_key_t child_pub;
  uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE];

  crypto_ecc_secp256k1_init();

  uint8_t seed[] = {0x3d, 0xdd, 0x56, 0x02, 0x28, 0x58, 0x99, 0xa9, 0x46, 0x11, 0x45,
                    0x06, 0x15, 0x7c, 0x79, 0x97, 0xe5, 0x44, 0x45, 0x28, 0xf3, 0x00,
                    0x3f, 0x61, 0x34, 0x71, 0x21, 0x47, 0xdb, 0x19, 0xb6, 0x78};

  // Chain m
  {
    cr_assert(bip32_derive_master_key(seed, sizeof(seed), &root_priv));

    check_private_key(&root_priv, NULL, MAINNET_PRIV, 0, 0,
                      "xprv9s21ZrQH143K48vGoLGRPxgo2JNkJ3J3fqkirQC2zVdk5Dgd5w14S7fRDyHH4dWNHUgkvsvN"
                      "DCkvAwcSHNAQwhwgNMgZhLtQC63zxwhQmRv");

    check_public_key(&root_priv, &root_pub, NULL, MAINNET_PUB, 0, 0,
                     "xpub661MyMwAqRbcGczjuMoRm6dXaLDEhW1u34gKenbeYqAix21mdUKJyuyu5F1rzYGVxyL6tmgBU"
                     "AEPrEz92mBXjByMRiJdba9wpnN37RLLAXa");

    parent_pub = root_pub;
  }

  // Chain m/0H
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[0], 1,
                      "xprv9vB7xEWwNp9kh1wQRfCCQMnZUEG21LpbR9NPCNN1dwhiZkjjeGRnaALmPXCX7SgjFTiCTT6b"
                      "Xes17boXtjq3xLpcDjzEuGLQBM5ohqkao9G");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[0], 1,
                     "xpub69AUMk3qDBi3uW1sXgjCmVjJ2G6WQoYSnNHyzkmdCHEhSZ4tBok37xfFEqHd2AddP56Tqp4o5"
                     "6AePAgCjYdvpW2PU2jbUPFKsav5ut6Ch1m");

    parent_pub = child_pub;
  }

  // Chain m/0H/1H
  {
    uint32_t indices[] = {0 | BIP32_HARDENED_BIT, 1 | BIP32_HARDENED_BIT};
    derivation_path_t path = {
      .indices = indices,
      .num_indices = ARRAY_SIZE(indices),
    };

    cr_assert(bip32_derive_path_priv(&root_priv, &child_priv, parent_fingerprint, &path));

    check_private_key(&child_priv, &parent_pub, MAINNET_PRIV, path.indices[1], 2,
                      "xprv9xJocDuwtYCMNAo3Zw76WENQeAS6WGXQ55RCy7tDJ8oALr4FWkuVoHJeHVAcAqiZLE7Je3vZ"
                      "JHxspZdFHfnBEjHqU5hG1Jaj32dVoS6XLT1");

    cr_assert(bip32_derive_path_pub(&parent_pub, &child_pub, &path) == false);

    check_public_key(&child_priv, &child_pub, &parent_pub, MAINNET_PUB, path.indices[1], 2,
                     "xpub6BJA1jSqiukeaesWfxe6sNK9CCGaujFFSJLomWHprUL9DePQ4JDkM5d88n49sMGJxrhpjazuX"
                     "YWdMf17C9T5XnxkopaeS7jGk1GyyVziaMt");

    parent_pub = child_pub;
  }
}
