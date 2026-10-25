# Sensor-activated-timer

An Android clock app with Timer, Stopwatch, and Settings tabs. The goal is to let users start a countdown using device sensors.

## Planned sensor triggers

1. **Proximity:** start the configured countdown when the proximity sensor becomes covered.
2. **Accelerometer:** start the countdown when a shake is detected.
3. **Microphone:** detect three tap-like acoustic peaks within 1.5 seconds. Audio cannot reliably prove that a tap came from the back of the phone, so detection will need tuning and user control.
4. **Camera:** detect visual movement while camera access is explicitly enabled.

## Project structure

- `MainActivity.kt` — Android entry point.
- `ui/ClockApp.kt` — Compose UI and timer/stopwatch presentation.
- `sensor/ProximitySensorController.kt` — proximity sensor lifecycle and edge-triggered detection.

Sensor controllers are kept separate from UI code so each trigger can be implemented and tested independently.

## Build

Open this repository's root folder in Android Studio and let Gradle sync. The project uses Kotlin 1.9.25, Android Gradle Plugin 8.5.2, and Jetpack Compose.

The initial implementation is a development build; sensor behavior and compilation should be verified on a physical Android device before release.
