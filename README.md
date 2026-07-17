# ArcKey

ArcKey is an Android archive utility for ZIP, RAR, 7z, and other formats supported by 7-Zip-JBinding-4Android. It can test manual passwords, a selected plain text password book, and bounded brute-force candidates. Dictionary and brute-force candidates can run across multiple worker threads. When a password is found, it is appended to the selected password book.

## Build Requirements

- JDK 17 or newer.
- Android SDK Platform 37 and Build Tools 36.0.0 or newer.
- Gradle wrapper distribution 9.5.0 or newer.
- Network access for first dependency sync.

This workspace currently ships source and Gradle configuration for `compileSdk 37`, `targetSdk 36`, and `minSdk 26`.

## Security Notes

- Only use password cracking on archives you own or are authorized to recover.
- Password books are UTF-8 `.txt` files with one password per line. They are intentionally plain text because the requested v1 behavior prioritizes interoperability.
- The last selected password book is remembered and preselected when the app opens again.
- The app never writes the recovered password to logs or notifications.
- Extraction writes into a folder named after the archive under the selected output tree and rejects absolute paths, drive paths, `..`, and other traversal attempts.
- The app defaults output to the archive's parent folder when Android can infer it from the document URI. Android still requires folder write permission, so first use opens the folder picker at that parent directory for authorization.
- Archive files can be opened from other Android apps through "Open with" / "Share" intents. The received archive is preselected in the app.

## Key Dependencies

- Android Gradle Plugin 9.3.0.
- Kotlin 2.4.10.
- Jetpack Compose BOM 2026.06.01.
- `com.sorrowblue.sevenzipjbinding:7-Zip-JBinding-4Android:16.02-2.4`.

## Useful Commands

```bash
./gradlew test
./gradlew assembleDebug
```

If the local machine lacks JDK 17 or Android SDK 36, install those first or open the project in a current Android Studio release and let it install the missing components.
