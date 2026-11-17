# VaultView

An unofficial Android front end for [Vimm's Lair](https://vimm.net/vault), built for handhelds like the Retroid Pocket 6 as well as phones.

- Browse by maker (Nintendo, PlayStation, Sega, Xbox, Atari, NEC, Philips) and system, with box art and screenshots
- Works with a controller: D-pad to move, A to select, B to go back; landscape layout with a side menu
- Game pages show details and a **Download** button; the site's own page loads in the background for its human check
- Downloads go to a folder you pick, sorted into ES-DE style folders (`snes`, `psx`, `gba`…), and `.zip`/`.7z` files are unpacked automatically
- Saved games, a download list, and a "Recently downloaded" shelf

Everything (lists, images, game pages and files) comes from vimm.net. This app only presents it differently.

## Installing

1. On your device, open the [latest release](https://github.com/ipromiseitsokay/vaultview/releases/latest) and download `VaultView.apk`.
2. Open it and allow your browser or file manager to install apps when Android asks.
3. After that, updates arrive inside the app: an **Update ready** banner appears when a new release is out.

## Updates

Every push to `main` runs [.github/workflows/release.yml](.github/workflows/release.yml), which tests, builds and signs the APK and publishes it as a release. The app checks the latest release on launch and offers to install it (Settings → Updates).

The workflow needs three repository secrets: `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD` and `SIGNING_KEY_PASSWORD`. Keep the keystore safe: an APK signed with a different key can't update the installed app.

## Building locally

Requires JDK 17 and the Android SDK (platform 34).

```bash
./gradlew assembleRelease
```

Without the signing environment variables the release build falls back to the debug key.
