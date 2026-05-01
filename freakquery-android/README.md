# FreakQuery Kotlin

Android-friendly Kotlin port of the FreakQuery Python package.

## Use in Android Studio

Copy this `freakquery-android` folder into your Android project and include it as a module:

```kotlin
include(":freakquery-android")
```

Then add it to your app module:

```kotlin
dependencies {
    implementation(project(":freakquery-android"))
}
```

## Basic API

```kotlin
import com.ndm4.freakquery.FreakQuery

val logs = FreakQuery.loadLogs(jsonString)
// Or: context.assets.open("logs.json").use { FreakQuery.loadLogs(it) }

val count = FreakQuery.query("month|count", logs)
val lastDose = FreakQuery.query("last|dose", logs)

val text = FreakQuery.render(
    "Last: {{last}} / Routes:\n{{ratio=route}}",
    logs
)
```

## Supported input

`loadLogs` supports:

- A normal JSON array of log rows.
- Journal-style exports with a top-level `experiences` array and nested `ingestions`.

The Kotlin port has no Python runtime dependency. It uses Android's built-in `org.json` classes.
