# Serato Database V2 Binary Format

Technical reference for the `_Serato_/database V2` file format.

## Overview

The Database V2 file stores Serato DJ's main track library index. It uses a binary Tag-Length-Value (TLV) architecture.

- **Endianness**: Big-Endian (Network Byte Order)
- **String Encoding**: UTF-16BE (2 bytes per character)
- **File Location**: `_Serato_/database V2`

---

## File Structure

### Header Block (`vrsn`)

```text
[vrsn]  4 bytes marker
        2 bytes padding (00 00)
        2 bytes length (Int16)
        N bytes version string (UTF-16BE)
```

**Example**: `"2.0/Serato Scratch LIVE Database"`

### Track Records (`otrk`)

Each track in the library is stored as an `otrk` block:

```text
[otrk]  4 bytes marker
        4 bytes length (Int32)
        N bytes record data (TLV fields)
```

---

## Field Reference

### Text Fields (`t` prefix) - UTF-16BE Strings

| Tag    | Name         | Description                              |
|--------|--------------|------------------------------------------|
| `ttyp` | Type         | File type (e.g., "mp3", "quicktime")     |
| `pfil` | Path File    | Absolute path to media file              |
| `tsng` | Song Title   | Track title                              |
| `tart` | Artist       | Artist name                              |
| `talb` | Album        | Album name                               |
| `tgen` | Genre        | Genre tag                                |
| `tlen` | Length       | Duration string (e.g., "04:35.98")       |
| `tsiz` | Size         | File size string (e.g., "6.3MB")         |
| `tbit` | Bitrate      | Bitrate string (e.g., "192.0kbps")       |
| `tsmp` | Sample Rate  | Sample rate string (e.g., "44.1k")       |
| `tbpm` | BPM          | Tempo string (e.g., "126.00")            |
| `tcom` | Comment      | Comment/key field (e.g., "8A - 5")       |
| `tgrp` | Grouping     | Grouping tag                             |
| `tcmp` | Composer     | Composer name                            |
| `ttyr` | Year         | Release year (e.g., "2012")              |
| `tkey` | Key          | Musical key (e.g., "Am", "Em")           |
| `tlbl` | Label        | Record label                             |
| `tadd` | Added        | Date added timestamp (string)            |

### Unsigned Integer Fields (`u` prefix) - Int32

| Tag    | Name         | Description                              |
|--------|--------------|------------------------------------------|
| `uadd` | Added Time   | Unix timestamp when added                |
| `ulbl` | Label Color  | Color value (hex, e.g., 0xFF9999)        |
| `utme` | Modified     | Unix timestamp last modified             |
| `ufsb` | File Size    | File size in bytes (binary)              |
| `udsc` | Disc Number  | Disc number                              |
| `utkn` | Track Number | Track number                             |
| `utpc` | Play Count   | Play count                               |

### Boolean Flags (`b` prefix) - Int8

| Tag    | Name         | Description                              |
|--------|--------------|------------------------------------------|
| `bhrt` | Has Art      | Has album artwork                        |
| `bmis` | Missing      | File is missing/offline                  |
| `bply` | Played       | Has been played                          |
| `blop` | Looped       | Is looped                                |
| `bitu` | iTunes       | Is from iTunes                           |
| `bovc` | Override     | Has overridden values                    |
| `bcrt` | Corrupt      | File is corrupt                          |
| `biro` | Read Only    | Is read-only                             |
| `bwlb` | White Label  | Is white label                           |
| `bwll` | Whitelisted  | Is whitelisted                           |
| `buns` | Unseen       | Is unseen/new                            |
| `bbgl` | Beatgrid Lck | Beatgrid locked                          |
| `bkrk` | Key Lock     | Key lock enabled                         |
| `bstm` | Streaming    | Is streaming track                       |

### Version Info (`sbav`)

| Tag    | Name         | Description                              |
|--------|--------------|------------------------------------------|
| `sbav` | Save Version | Database save version (2 bytes)          |

---

## Field Structure

Each field inside `otrk` follows this pattern:

```text
[TAG ]  4 bytes ASCII tag name
[LEN ]  4 bytes Big-Endian Int32
[DATA]  N bytes (length specified by LEN)
```

### Example: Reading a Track Record

```java
// Read tag and length
String tag = new String(data, pos, 4);       // e.g., "pfil"
int len = readInt(data, pos + 4);            // e.g., 140
byte[] value = Arrays.copyOfRange(data, pos + 8, pos + 8 + len);

if (tag.equals("pfil")) {
    String path = new String(value, StandardCharsets.UTF_16BE);
    // path = "/Volumes/Music/Song.mp3"
}
```

---

## Platform-Specific Encoding

> [!IMPORTANT]
> Path encoding differs between operating systems. Cross-platform tools must normalize paths before comparison.

| Platform | Unicode Form | Notes                              |
|----------|--------------|-------------------------------------|
| macOS    | NFD          | Decomposed (matches HFS+/APFS)      |
| Windows  | NFC          | Precomposed (matches NTFS)          |

---

## Example: Full File Layout

```text
[Header]
  vrsn (64 bytes)
  "2.0/Serato Scratch LIVE Database"

[Track Record 1]
  otrk (length: 754)
    ttyp (6)   "mp3"
    pfil (140) "/Volumes/Music/Artist - Song.mp3"
    tsng (32)  "Song Title"
    tart (28)  "Artist Name"
    talb (60)  "Album Name"
    tgen (10)  "Other"
    tlen (16)  "04:35.98"
    tsiz (10)  "6.3MB"
    tbit (18)  "192.0kbps"
    tsmp (10)  "44.1k"
    tbpm (10)  "96.92"
    tkey (4)   "Am"
    uadd (4)   0x58156F3A (timestamp)
    ulbl (4)   0x00FF9999 (color)
    utme (4)   0x5E4710D3 (timestamp)
    ufsb (4)   0x00E57DB3 (15039923 bytes)
    sbav (2)   0x0201
    bhrt (1)   0x01 (true)
    bmis (1)   0x00 (false)
    ...
```

---

## Parsing Notes

- **Unknown tags**: Skip using their length field
- **Text sizes**: Some fields like `tsiz` store values as text, not binary
- **Path normalization**: Always normalize to NFC before comparing paths

## Related

- [Session File Format](session-fixer/README.md#session-file-format-reference) - History session binary format
- [ser-sync-pro](README.md) - Main Serato crate sync tool
