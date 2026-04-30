# PhoneInfo Project Context

## Project Overview
PhoneInfo is an Android application built using modern Android development practices. Its primary purpose is to retrieve and display detailed hardware and software information about the user's device. 

The application provides a "Device Overview" (Home Screen) and an "Advanced Details" screen, surfacing metrics such as RAM usage, storage availability, CPU details, battery health, Wi-Fi status, and mobile network information.

## Tech Stack & Architecture
*   **Language:** Kotlin (`v2.0.21`)
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Architecture Pattern:** MVVM (Model-View-ViewModel)
*   **State Management:** Kotlin Coroutines & StateFlow (`MutableStateFlow`)
*   **Navigation:** Navigation Compose
*   **Build System:** Gradle (Kotlin DSL) with Version Catalogs (`libs.versions.toml`)
*   **Minimum SDK:** 24
*   **Target SDK:** 36

### Key Components
*   `MainActivity.kt`: The entry point of the application. It handles initial permission requests (e.g., location, phone state) and sets up the Compose Navigation host.
*   `PhoneInfoViewModel.kt`: The core data engine. It runs a coroutine loop to continuously poll device sensors, system services (BatteryManager, TelephonyManager, WifiManager), and system files (`/proc/cpuinfo`) to update the `DeviceInfo` state object.
*   `HomeScreen.kt`: Displays high-level device information (RAM, Storage) with a polished gradient and glassmorphism UI.
*   `DetailScreen.kt`: A comprehensive list showing detailed metrics organized by categories (System, CPU, Battery, Display, Sensors, etc.).

## Development Conventions
*   **UI Declarations:** All UI is declarative, using Compose components. Avoid using XML layouts.
*   **Asynchronous Operations:** Coroutines are used for background polling and data fetching within the ViewModel.
*   **State Observation:** The UI observes state changes via `collectAsState()` from the ViewModel's `StateFlow`.
*   **Dependency Management:** Dependencies are strictly managed centrally in the `gradle/libs.versions.toml` file. Do not add raw dependency strings directly to `build.gradle.kts` files.

## Building and Running

You can build and test the application using the standard Gradle wrapper commands:

*   **Build Debug APK:** `./gradlew assembleDebug`
*   **Run Tests:** `./gradlew test`
*   **Run Android Tests (Instrumentation):** `./gradlew connectedAndroidTest`

*Note: The app requires physical hardware testing or a fully configured emulator to accurately display sensor, battery, and telephony information.*