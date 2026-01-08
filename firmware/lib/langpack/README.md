# Language Pack

## Overview

This library implements a thin wrapper around TLV to provide a system for
handling localized strings on target. The user is able to update the
global internal language pack to support multiple languages for UI.

## Example Usage

### Loading the Pre-Compiled Language Pack

```
langpack_load_default();
```

### Switching Language Packs

```
const uint8_t en_langpack[];
const uint8_t jp_langpack[];

void on_language_changed(language_id_t lang) {
  switch (lang) {
    case LANGUAGE_ID_EN:
      langpack_load(en_langpack, sizeof(en_langpack));
      break;
    case LANGUAGE_ID_JP:
      langpack_load(jp_langpack, sizeof(jp_langpack));
      break;
    default:
      ASSERT(false);
  }
}
```

## Meson

The `meson` build for this library outputs the following:

| Name                    |    Type    | Description                                                        |
| :---------------------- | :--------: | :----------------------------------------------------------------- |
| `langpack_dep`          | Dependency | Language agnostic dependency (no internal language pack).          |
| `langpack_deps[<LANG>]` | Dependency | Language specific dependency (pre-compiled with specified `LANG`). |


## Building

### Adding new Language Packs

To add a new language pack, one must:

1. Add a `<LANG>.yml` file to the `resources` sub-directory.
2. Add the `<LANG>` as a string to the `languages` array in the `meson.build`.

### Adding a new String

For each language pack in the `resources` folder, add a new key with an
associated string.

```
MyNewString: Hello World!
```

Then add a corresponding enumeration value to the `langpack_ids.h`.

```
LANGPACK_ID_MY_NEW_STRING = 0xDEADBEEFu
```

## Format

All strings are TLV encoded in the language pack with the key being the ID
value found in the `langpack_ids.h`. To support changes in format, each
language pack begins with a header. Every header starts with a version
number and a header length value.

### Version 1

|    Field   | Offset |    Size   | Notes                           |
| :--------: | :----: | :-------: | :------------------------------ |
|  `version` |  `0`   | `uint8_t` | This is always `1` for V1.      |
| `hdr_size` |  `1`   | `uint8_t` | This is `3` for a V1 header.    |
|   `type`   |  `2`   | `uint8_t` | Identifies the string encoding. |
