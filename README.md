# LoveChain
<<<<<<< HEAD

LoveChain is a private romantic blockchain and future live trust map for a couple.

## Platform Layout

```text
ANDROID/
    Android app in Kotlin and Jetpack Compose.

IOS/
    Future iPhone app workspace.

TinyGO/
    Future firmware and shared low-level ring logic.
```

Android is the first working platform. iOS and TinyGO are prepared as separate work areas so platform code does not mix.

## Version 0.2

The current Android version contains:

* local LoveBlock models;
* SHA-256 block hashes;
* Genesis Block creation;
* LoveCoins;
* local SQLite storage;
* Android Keystore device signatures;
* confirmation statuses for signed and partner-confirmed blocks;
* JSON export and import;
* migration from the first SharedPreferences JSON store;
* Russian and English UI resources;
* Jetpack Compose UI;
* manual Together, Walk, Travel, and Gratitude blocks;
* LoveMap placeholder with mutual transparency text;
* placeholders for GPS, Bluetooth presence, motion detection, and event mining.

There are no ads, no public social feed, no negative scoring, and no jealousy mechanics.

## Architecture

```text
ANDROID/app/src/main/java/lovechain/core/
    LoveModels.kt
    LoveBlockHasher.kt
    LoveBlockSignatureVerifier.kt
    LoveChain.kt
    LoveEventServices.kt

ANDROID/app/src/main/java/lovechain/android/
    MainActivity.kt
    DeviceKeyStore.kt
    LoveBlockSQLiteStore.kt
    LoveChainJsonCodec.kt
```

The core package holds reusable relationship-chain logic. The Android package holds UI, device signing, SQLite persistence, JSON transfer, and first-version migration.

## Future Versions

```text
0.3 LoveMap
    couple registration
    partner location
    battery and motion status
    server relay

0.4 Presence
    Bluetooth confirmation
    Near Block
    Together Block

0.5 Event Mining
    walk detection
    travel detection
    return home detection
    block candidates confirmed by both partners
```

## Build

Open `ANDROID/` in Android Studio and run the `app` module.

The project is intentionally local-first. Background location, Bluetooth scanning, and server synchronization are not enabled yet.
=======
Blockchain for lovers

<img width="100%" height="100%" alt="lovechain" src="https://github.com/user-attachments/assets/62b8031b-d155-4382-91ef-2eb0fa0e92e4" />
>>>>>>> 36634ee5b3496e362bf21af33b649ebf1d7e4788
