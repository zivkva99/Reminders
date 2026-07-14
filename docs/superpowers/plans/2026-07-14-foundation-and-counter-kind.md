# Foundation + Counter Kind Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Reminders Android app from scratch — Gradle scaffold, the
`HabitKind`/`HabitInstance` generic-engine schema, and the Counter-with-goal kind
(the "Exercise" habit) built end-to-end from Room through a working on-device UI —
proving the shared scaffolding (schema, alarm self-heal, dashboard rendering) before
the Timer and Schedule-cursor kinds build on top of it in later plans.

**Architecture:** One Room database (`AppDatabase`, v1) with a `habit_instance` table
(kind discriminator + per-kind config columns) and a kind-specific
`counter_daily_progress` table. A `HabitEngine` dispatch layer exposes the two calls
every kind can answer generically — `todayStatus()` and `currentStreak()` — routed by
`instance.kind` to `CounterHabitRepository`. Per the design doc's resolution of the
dispatch-layer risk: **only the read path is unified** across kinds; each kind's own
progress-marking action (Counter's `increment()`, later Timer's `start()`/`stop()`,
ScheduleCursor's `markRead()`) stays a method on that kind's own repository, called
directly by that kind's own UI — forcing "mark progress" into one fake generic verb
would leak kind-specific semantics into a shared signature that doesn't actually fit
a timed session or an ordered cursor. `AlarmManager.setWindow()` (inexact) drives
reminders, generalized to take a `habitInstanceId` so one receiver serves every
instance regardless of kind. Compose UI, MVVM with a manual `AppContainer` for DI
(no framework), matching ReadBook's proven patterns throughout.

**Tech Stack:** Kotlin 2.3.0, Jetpack Compose (Material 3, dynamic color), Room 2.7.1
(KSP), AndroidX, JUnit4 + Robolectric 4.16.1 for anything touching Room/AlarmManager,
`kotlinx-coroutines-test` with `StandardTestDispatcher`.

## Global Constraints

- Package / application ID: `com.ziv.reminders`.
- `minSdk = 35`, `targetSdk = 36`, `compileSdk` release 36 (minor API level 1) —
  matching ReadBook's real, proven build config exactly (not the design doc's
  approximate "API 34" — this plan is the confirmation the design doc deferred).
- Scheduling is **inexact only**: `AlarmManager.setWindow()`, never `setExact()` /
  `setExactAndAllowWhileIdle()`. No `SCHEDULE_EXACT_ALARM` permission anywhere in this
  plan.
- No data migration from Shape or ReadBook. Fresh database, `habit_instance` seeded
  at runtime (idempotent insert-if-absent), not imported from either source app.
- No in-app "add habit" UI in this plan or any later one currently scoped — new
  `HabitInstance` rows are inserted via seed code, not a screen.
- Every Room schema change ships with a real `Migration` object; never
  `fallbackToDestructiveMigration()`. This plan is schema v1 (nothing to migrate
  from yet — the rule takes effect starting with whichever later plan adds Timer's
  or Schedule-cursor's config columns as v1→v2).
- TDD for all pure logic and repository/dispatch code; Robolectric
  (`@Config(sdk = [35])`) for anything touching Room or `AlarmManager`; every commit
  after a task leaves `./gradlew :app:testDebugUnitTest` green.
- "Enabled," wherever it appears in this plan (dashboard filtering), means the
  enabled-days bitmask evaluated against today's date — there is no separate
  instance-level on/off flag.

---

## File Structure

```
Reminders/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  gradle/libs.versions.toml, gradle/wrapper/*
  gradlew, gradlew.bat
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/...                                   (Task 1)
  app/src/main/java/com/ziv/reminders/
    RemindersApp.kt                                       (Task 9)
    MainActivity.kt                                       (Task 11)
    data/
      EnabledDays.kt                                       (Task 2)
      HabitKind.kt                                          (Task 3)
      HabitInstance.kt                                      (Task 3)
      HabitInstanceDao.kt                                   (Task 3)
      CounterDailyProgress.kt                               (Task 3)
      CounterDailyProgressDao.kt                            (Task 3)
      AppDatabase.kt                                        (Task 3)
      HabitSeeding.kt                                       (Task 3)
      HabitStatus.kt                                        (Task 4)
      CounterHabitRepository.kt                             (Task 4)
      AppContainer.kt                                       (Task 9)
    engine/
      HabitEngine.kt                                        (Task 5)
    scheduling/
      HabitScheduler.kt                                     (Task 6)
      HabitReminderReceiver.kt                               (Task 7)
      RolloverReceiver.kt                                    (Task 8)
      BootReceiver.kt                                        (Task 8)
    notifications/
      HabitNotifications.kt                                 (Task 6)
    ui/
      theme/RemindersTheme.kt                               (Task 1)
      dashboard/DashboardUiState.kt                          (Task 10)
      dashboard/DashboardViewModel.kt                        (Task 10)
      dashboard/DashboardScreen.kt                           (Task 11)
  app/src/test/java/com/ziv/reminders/
    data/EnabledDaysTest.kt                                 (Task 2)
    data/HabitInstanceDaoTest.kt                            (Task 3)
    data/CounterDailyProgressDaoTest.kt                     (Task 3)
    data/CounterHabitRepositoryTest.kt                       (Task 4)
    engine/HabitEngineTest.kt                                (Task 5)
    scheduling/HabitSchedulerTest.kt                         (Task 6)
    scheduling/HabitReminderReceiverTest.kt                  (Task 7)
    scheduling/RolloverReceiverTest.kt                       (Task 8)
    scheduling/BootReceiverTest.kt                           (Task 8)
    ui/dashboard/DashboardViewModelTest.kt                   (Task 10)
```

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`, `values/colors.xml`, `values/themes.xml`, `values-night/themes.xml`
- Create: `app/src/main/res/xml/backup_rules.xml`, `xml/data_extraction_rules.xml`
- Create: `app/src/main/java/com/ziv/reminders/ui/theme/RemindersTheme.kt`
- Create: `app/src/main/java/com/ziv/reminders/MainActivity.kt` (placeholder, replaced in Task 11)
- Copy (binary, via shell, not hand-written): `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, and the `mipmap-*`/`drawable` launcher icon assets — these are generic Android build tooling and default launcher icons, not app logic or habit data, so reusing them from ReadBook is standard practice, not the kind of migration Global Constraints rules out.

**Interfaces:**
- Produces: a buildable, installable empty Compose app shell that later tasks add real code to. `RemindersTheme` (Composable, dynamic color, no params) is consumed by Task 11.

- [ ] **Step 1: Create Gradle project files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Reminders"
include(":app")
```

`build.gradle.kts` (root):
```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
kotlin.code.style=official
# KSP (Room's annotation processor) is not yet compatible with AGP's built-in
# Kotlin support — fall back to the classic separate Kotlin Android plugin,
# which in turn requires AGP's legacy (pre-9.0) DSL.
android.builtInKotlin=false
android.newDsl=false
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "9.2.1"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
appcompat = "1.6.1"
material = "1.10.0"
kotlin = "2.3.0"
ksp = "2.3.0"
room = "2.7.1"
sqlite = "2.5.1"
coroutines = "1.9.0"
robolectric = "4.16.1"
androidxTestCore = "1.6.1"
composeBom = "2026.06.01"
activityCompose = "1.13.0"
lifecycleCompose = "2.10.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
sqlite-bundled = { group = "androidx.sqlite", name = "sqlite-bundled", version.ref = "sqlite" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleCompose" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ziv.reminders"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ziv.reminders"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("debug") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.room.testing)
    testImplementation(libs.sqlite.bundled)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
```

- [ ] **Step 2: Copy build-tooling boilerplate from ReadBook**

Run:
```bash
cp "D:\Users\zivk\Documents\GitHub\ReadBook\gradlew" "D:\Users\zivk\Documents\GitHub\Reminders\gradlew"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\gradlew.bat" "D:\Users\zivk\Documents\GitHub\Reminders\gradlew.bat"
mkdir -p "D:\Users\zivk\Documents\GitHub\Reminders\gradle\wrapper"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\gradle\wrapper\gradle-wrapper.jar" "D:\Users\zivk\Documents\GitHub\Reminders\gradle\wrapper\gradle-wrapper.jar"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\gradle\wrapper\gradle-wrapper.properties" "D:\Users\zivk\Documents\GitHub\Reminders\gradle\wrapper\gradle-wrapper.properties"
mkdir -p "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-anydpi" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-hdpi" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-mdpi" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-xhdpi" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-xxhdpi" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-xxxhdpi" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\drawable"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\mipmap-anydpi\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-anydpi\"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\mipmap-hdpi\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-hdpi\"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\mipmap-mdpi\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-mdpi\"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\mipmap-xhdpi\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-xhdpi\"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\mipmap-xxhdpi\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-xxhdpi\"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\mipmap-xxxhdpi\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\mipmap-xxxhdpi\"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\res\drawable\"* "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\res\drawable\"
chmod +x "D:\Users\zivk\Documents\GitHub\Reminders\gradlew"
```

- [ ] **Step 3: Create manifest, resource values, and theme**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".RemindersApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Reminders">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".scheduling.HabitReminderReceiver"
            android:exported="false" />

        <receiver
            android:name=".scheduling.RolloverReceiver"
            android:exported="false" />

        <receiver
            android:name=".scheduling.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Reminders</string>
</resources>
```

`app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Reminders" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/teal_200</item>
        <item name="colorSecondaryVariant">@color/teal_700</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
    </style>
</resources>
```

`app/src/main/res/values-night/themes.xml`:
```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Reminders" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/purple_200</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/black</item>
        <item name="colorSecondary">@color/teal_200</item>
        <item name="colorSecondaryVariant">@color/teal_200</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
    </style>
</resources>
```

`app/src/main/res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
</full-backup-content>
```

`app/src/main/res/xml/data_extraction_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
    </cloud-backup>
</data-extraction-rules>
```

`app/src/main/java/com/ziv/reminders/ui/theme/RemindersTheme.kt`:
```kotlin
package com.ziv.reminders.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material You / dynamic color — no custom brand to design or maintain, per the design doc. */
@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

`app/src/main/java/com/ziv/reminders/MainActivity.kt` (placeholder — replaced in Task 11):
```kotlin
package com.ziv.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.ziv.reminders.ui.theme.RemindersTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemindersTheme {
                Text("Reminders")
            }
        }
    }
}
```

- [ ] **Step 4: Verify the scaffold builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle app gradlew gradlew.bat
git commit -m "Scaffold Reminders Android project"
```

---

### Task 2: `EnabledDays` pure utility

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/EnabledDays.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/EnabledDaysTest.kt`

**Interfaces:**
- Produces: `fun isEnabledDay(date: LocalDate, enabledDaysMask: Int): Boolean` — consumed
  by `HabitScheduler` (Task 6). Bitmask convention: Sun=1, Mon=2, Tue=4, Wed=8, Thu=16,
  Fri=32, Sat=64.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnabledDaysTest {
    @Test
    fun isEnabledDay_sundayWithFullMask_isTrue() {
        val sunday = LocalDate.of(2026, 7, 12) // a known Sunday
        assertTrue(isEnabledDay(sunday, enabledDaysMask = 0b1111111))
    }

    @Test
    fun isEnabledDay_saturdayWithSunThuMask_isFalse() {
        val saturday = LocalDate.of(2026, 7, 18) // a known Saturday
        assertFalse(isEnabledDay(saturday, enabledDaysMask = 0b0011111)) // Sun-Thu
    }

    @Test
    fun isEnabledDay_thursdayWithSunThuMask_isTrue() {
        val thursday = LocalDate.of(2026, 7, 16) // a known Thursday
        assertTrue(isEnabledDay(thursday, enabledDaysMask = 0b0011111)) // Sun-Thu
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.EnabledDaysTest"`
Expected: FAIL — `isEnabledDay` is not defined (compile error).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.ziv.reminders.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Sun=1, Mon=2, Tue=4, Wed=8, Thu=16, Fri=32, Sat=64. */
private fun bitFor(day: DayOfWeek): Int = when (day) {
    DayOfWeek.SUNDAY -> 0b0000001
    DayOfWeek.MONDAY -> 0b0000010
    DayOfWeek.TUESDAY -> 0b0000100
    DayOfWeek.WEDNESDAY -> 0b0001000
    DayOfWeek.THURSDAY -> 0b0010000
    DayOfWeek.FRIDAY -> 0b0100000
    DayOfWeek.SATURDAY -> 0b1000000
}

fun isEnabledDay(date: LocalDate, enabledDaysMask: Int): Boolean =
    (enabledDaysMask and bitFor(date.dayOfWeek)) != 0
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.EnabledDaysTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/EnabledDays.kt app/src/test/java/com/ziv/reminders/data/EnabledDaysTest.kt
git commit -m "Add isEnabledDay pure utility"
```

---

### Task 3: Room schema — `HabitKind`, `HabitInstance`, `CounterDailyProgress`, DAOs, `AppDatabase`, seeding

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/HabitKind.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/HabitInstance.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/HabitInstanceDao.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/CounterDailyProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/CounterDailyProgressDao.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/HabitInstanceDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/CounterDailyProgressDaoTest.kt`

**Interfaces:**
- Produces:
  - `enum class HabitKind { COUNTER }`
  - `data class HabitInstance(id: Long, kind: String, name: String, enabledDaysMask: Int, notificationTitle: String, notificationBody: String, counterGoal: Int?)`
  - `data class CounterDailyProgress(habitInstanceId: Long, date: String, count: Int, completed: Boolean)`
  - `interface HabitInstanceDao { suspend fun getAll(): List<HabitInstance>; suspend fun getById(id: Long): HabitInstance?; suspend fun insertIfAbsent(instance: HabitInstance) }`
  - `interface CounterDailyProgressDao { suspend fun getByDate(habitInstanceId: Long, date: String): CounterDailyProgress?; suspend fun upsert(progress: CounterDailyProgress); suspend fun getCompletedDates(habitInstanceId: Long): List<String> }`
  - `const val EXERCISE_HABIT_INSTANCE_ID = 1L`
  - `suspend fun ensureHabitsSeeded(dao: HabitInstanceDao)`
  - Consumed by: `CounterHabitRepository` (Task 4), `HabitEngine` (Task 5), `HabitScheduler`/receivers (Tasks 6-8), `RemindersApp` (Task 9), `DashboardViewModel` (Task 10).

- [ ] **Step 1: Write the failing DAO tests**

`app/src/test/java/com/ziv/reminders/data/HabitInstanceDaoTest.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitInstanceDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getById_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.habitInstanceDao().getById(1L))
        db.close()
    }

    @Test
    fun insertIfAbsent_thenGetById_returnsTheRow() = runTest {
        val db = newDb()
        val instance = HabitInstance(
            id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders", notificationBody = "Don't forget your exercises today!",
            counterGoal = 5,
        )
        db.habitInstanceDao().insertIfAbsent(instance)

        val loaded = db.habitInstanceDao().getById(1L)
        assertEquals(instance, loaded)
        db.close()
    }

    @Test
    fun insertIfAbsent_rowAlreadyExists_doesNotOverwrite() = runTest {
        val db = newDb()
        val original = HabitInstance(
            id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders", notificationBody = "original", counterGoal = 5,
        )
        db.habitInstanceDao().insertIfAbsent(original)
        db.habitInstanceDao().insertIfAbsent(original.copy(notificationBody = "changed"))

        assertEquals("original", db.habitInstanceDao().getById(1L)?.notificationBody)
        db.close()
    }

    @Test
    fun getAll_returnsEveryInsertedInstance() = runTest {
        val db = newDb()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        assertEquals(1, db.habitInstanceDao().getAll().size)
        db.close()
    }
}
```

`app/src/test/java/com/ziv/reminders/data/CounterDailyProgressDaoTest.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CounterDailyProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.counterDailyProgressDao().getByDate(1L, "2026-07-14"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val progress = CounterDailyProgress(habitInstanceId = 1L, date = "2026-07-14", count = 3, completed = false)
        db.counterDailyProgressDao().upsert(progress)

        assertEquals(progress, db.counterDailyProgressDao().getByDate(1L, "2026-07-14"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-14", 1, false))
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-14", 5, true))

        val loaded = db.counterDailyProgressDao().getByDate(1L, "2026-07-14")
        assertEquals(5, loaded?.count)
        assertEquals(true, loaded?.completed)
        db.close()
    }

    @Test
    fun getCompletedDates_returnsOnlyCompletedRowsForThatInstance() = runTest {
        val db = newDb()
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-12", 5, true))
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-13", 2, false))
        db.counterDailyProgressDao().upsert(CounterDailyProgress(2L, "2026-07-12", 5, true)) // different instance

        assertEquals(listOf("2026-07-12"), db.counterDailyProgressDao().getCompletedDates(1L))
        db.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitInstanceDaoTest" --tests "com.ziv.reminders.data.CounterDailyProgressDaoTest"`
Expected: FAIL — `AppDatabase`, `HabitInstance`, `CounterDailyProgress`, and the DAOs don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitKind.kt`:
```kotlin
package com.ziv.reminders.data

/**
 * The extensibility primitive: adding a new instance of an existing kind needs only a
 * HabitInstance row (see HabitSeeding.kt), zero new Kotlin classes. A genuinely new kind
 * still needs a new enum case, HabitStatus variant, repository, and HabitEngine branch —
 * TIMER and SCHEDULE_CURSOR are added by later plans, each as a real Room migration.
 */
enum class HabitKind {
    COUNTER,
}
```

`app/src/main/java/com/ziv/reminders/data/HabitInstance.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * kind is stored as a plain String (HabitKind.name), not a Room-mapped enum column — so a
 * future kind's migration only needs a data INSERT, never a schema change to this column.
 * counterGoal is nullable because non-Counter kinds (added by later plans) won't use it;
 * later plans add their own nullable per-kind config columns the same way.
 */
@Entity(tableName = "habit_instance")
data class HabitInstance(
    @PrimaryKey val id: Long,
    val kind: String,
    val name: String,
    val enabledDaysMask: Int,
    val notificationTitle: String,
    val notificationBody: String,
    val counterGoal: Int?,
)
```

`app/src/main/java/com/ziv/reminders/data/HabitInstanceDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HabitInstanceDao {
    @Query("SELECT * FROM habit_instance")
    suspend fun getAll(): List<HabitInstance>

    @Query("SELECT * FROM habit_instance WHERE id = :id")
    suspend fun getById(id: Long): HabitInstance?

    // IGNORE on primary-key conflict: calling this every app startup is idempotent seeding,
    // not an update path — see ensureHabitsSeeded in HabitSeeding.kt.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(instance: HabitInstance)
}
```

`app/src/main/java/com/ziv/reminders/data/CounterDailyProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity

@Entity(tableName = "counter_daily_progress", primaryKeys = ["habitInstanceId", "date"])
data class CounterDailyProgress(
    val habitInstanceId: Long,
    val date: String,
    val count: Int,
    val completed: Boolean,
)
```

`app/src/main/java/com/ziv/reminders/data/CounterDailyProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CounterDailyProgressDao {
    @Query("SELECT * FROM counter_daily_progress WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): CounterDailyProgress?

    @Upsert
    suspend fun upsert(progress: CounterDailyProgress)

    @Query("SELECT date FROM counter_daily_progress WHERE habitInstanceId = :habitInstanceId AND completed = 1")
    suspend fun getCompletedDates(habitInstanceId: Long): List<String>
}
```

`app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Schema v1 — nothing to migrate from yet. Starting with whichever later plan adds Timer's or
// Schedule-cursor's config columns (v1->v2), every change ships a real Migration object;
// never fallbackToDestructiveMigration() — see Global Constraints.
@Database(
    entities = [HabitInstance::class, CounterDailyProgress::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
}
```

`app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`:
```kotlin
package com.ziv.reminders.data

const val EXERCISE_HABIT_INSTANCE_ID = 1L

/**
 * Idempotent — safe to call on every app startup (RemindersApp.onCreate). insertIfAbsent's
 * IGNORE conflict strategy means a row already present is left untouched, so this is how a
 * fourth HabitInstance gets added in the future too: one more insertIfAbsent call here, no UI.
 */
suspend fun ensureHabitsSeeded(dao: HabitInstanceDao) {
    dao.insertIfAbsent(
        HabitInstance(
            id = EXERCISE_HABIT_INSTANCE_ID,
            kind = HabitKind.COUNTER.name,
            name = "Exercise",
            enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders",
            notificationBody = "Don't forget your exercises today!",
            counterGoal = 5,
        )
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitInstanceDaoTest" --tests "com.ziv.reminders.data.CounterDailyProgressDaoTest"`
Expected: PASS (8 tests total)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data app/src/test/java/com/ziv/reminders/data
git commit -m "Add Room schema for HabitInstance and CounterDailyProgress"
```

---

### Task 4: `CounterHabitRepository`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt`

**Interfaces:**
- Consumes: `HabitInstance`, `CounterDailyProgress`, `CounterDailyProgressDao` (Task 3).
- Produces: `class CounterHabitRepository(dao: CounterDailyProgressDao) { suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.CounterStatus; suspend fun increment(instance: HabitInstance, today: LocalDate); suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int }` — consumed by `HabitEngine` (Task 5) and `DashboardViewModel` (Task 10). Also produces `HabitStatus` (sealed interface, `CounterStatus` variant), created in Step 3 below since this repository's return type needs it — `HabitEngine` (Task 5) extends this same file's usage, not its declaration.

This test uses a hand-written fake `CounterDailyProgressDao` (pure JUnit, no Robolectric) —
DAO correctness is already covered by Task 3's Robolectric tests; this task only needs to
verify the repository's status/streak/increment logic against an in-memory fake.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()

    override suspend fun getByDate(habitInstanceId: Long, date: String): CounterDailyProgress? =
        rows[habitInstanceId to date]

    override suspend fun upsert(progress: CounterDailyProgress) {
        rows[progress.habitInstanceId to progress.date] = progress
    }

    override suspend fun getCompletedDates(habitInstanceId: Long): List<String> =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

class CounterHabitRepositoryTest {

    private val instance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun todayStatus_noRowYet_isZeroOfGoal_notCompleted() = runTest {
        val repo = CounterHabitRepository(FakeCounterDailyProgressDao())

        val status = repo.todayStatus(instance, today)

        assertEquals(0, status.current)
        assertEquals(5, status.goal)
        assertFalse(status.completed)
    }

    @Test
    fun increment_fourTimes_belowGoal_notCompleted() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)

        repeat(4) { repo.increment(instance, today) }
        val status = repo.todayStatus(instance, today)

        assertEquals(4, status.current)
        assertFalse(status.completed)
    }

    @Test
    fun increment_reachingGoal_marksCompleted() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)

        repeat(5) { repo.increment(instance, today) }
        val status = repo.todayStatus(instance, today)

        assertEquals(5, status.current)
        assertTrue(status.completed)
    }

    @Test
    fun currentStreak_todayNotDoneYet_countsThroughYesterday() = runTest {
        val dao = FakeCounterDailyProgressDao()
        dao.rows[1L to "2026-07-12"] = CounterDailyProgress(1L, "2026-07-12", 5, true)
        dao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val repo = CounterHabitRepository(dao)

        assertEquals(2, repo.currentStreak(instance, today))
    }

    @Test
    fun currentStreak_gapBreaksIt() = runTest {
        val dao = FakeCounterDailyProgressDao()
        dao.rows[1L to "2026-07-10"] = CounterDailyProgress(1L, "2026-07-10", 5, true)
        // 07-11 missing — gap
        dao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val repo = CounterHabitRepository(dao)

        assertEquals(1, repo.currentStreak(instance, today)) // only 07-13 counts back from today
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.CounterHabitRepositoryTest"`
Expected: FAIL — `CounterHabitRepository` and `HabitStatus` don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

First, `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt` (needed as the repository's
return type; this is the same file Task 5 extends with the `HabitEngine` dispatch — creating
it here is not a boundary violation, it's the natural order since the repository is the first
consumer):
```kotlin
package com.ziv.reminders.data

/**
 * The one type unified across every kind — see HabitEngine (engine/HabitEngine.kt) for why
 * only the read path (todayStatus/currentStreak) is generic; each kind's own progress-marking
 * action stays a method on that kind's own repository.
 */
sealed interface HabitStatus {
    data class CounterStatus(val current: Int, val goal: Int, val completed: Boolean) : HabitStatus
}
```

`app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

class CounterHabitRepository(private val dao: CounterDailyProgressDao) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.CounterStatus {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        return HabitStatus.CounterStatus(current = current, goal = goal, completed = current >= goal)
    }

    suspend fun increment(instance: HabitInstance, today: LocalDate) {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        val newCount = current + 1
        dao.upsert(
            CounterDailyProgress(
                habitInstanceId = instance.id,
                date = today.toString(),
                count = newCount,
                completed = newCount >= goal,
            )
        )
    }

    // Mirrors Shape's TrainingStats.currentStreak anchor logic: if today isn't done yet, the
    // day isn't over — the streak counts through yesterday and isn't broken until midnight
    // passes without today being hit.
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        val anchor = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        var cursor = anchor
        while (cursor in completedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.CounterHabitRepositoryTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStatus.kt app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt
git commit -m "Add CounterHabitRepository and HabitStatus"
```

---

### Task 5: `HabitEngine` dispatch layer

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`
- Test: `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`

**Interfaces:**
- Consumes: `HabitInstance`, `HabitStatus`, `CounterHabitRepository` (Tasks 3-4).
- Produces: `class HabitEngine(counterRepository: CounterHabitRepository) { suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus; suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int }` — consumed by `HabitReminderReceiver` (Task 7) and `DashboardViewModel` (Task 10). This is the spike the design doc flagged as the highest technical risk: proof that a single dispatch signature can route to any kind's read-path without leaking kind-specific details to callers. Its resolution (stated in this plan's Architecture section) is that only `todayStatus`/`currentStreak` are unified; write actions are not.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

class HabitEngineTest {

    private val counterInstance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun todayStatus_counterKind_dispatchesToCounterRepository() = runTest {
        val engine = HabitEngine(CounterHabitRepository(FakeCounterDailyProgressDao()))

        val status = engine.todayStatus(counterInstance, today)

        assertEquals(HabitStatus.CounterStatus(current = 0, goal = 5, completed = false), status)
    }

    @Test
    fun currentStreak_counterKind_dispatchesToCounterRepository() = runTest {
        val dao = FakeCounterDailyProgressDao()
        dao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val engine = HabitEngine(CounterHabitRepository(dao))

        assertEquals(1, engine.currentStreak(counterInstance, today))
    }

    @Test
    fun todayStatus_unknownKind_throws() = runTest {
        val engine = HabitEngine(CounterHabitRepository(FakeCounterDailyProgressDao()))
        val unknown = counterInstance.copy(kind = "SOMETHING_ELSE")

        assertFailsWith<IllegalArgumentException> { engine.todayStatus(unknown, today) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: FAIL — `HabitEngine` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.HabitStatus
import java.time.LocalDate

/**
 * Dispatches the two calls every kind can answer generically. Write actions (Counter's
 * increment, later Timer's start/stop, ScheduleCursor's markRead) deliberately stay on each
 * kind's own repository, not here — see this plan's Architecture section for why.
 */
class HabitEngine(private val counterRepository: CounterHabitRepository) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.todayStatus(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.currentStreak(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/engine app/src/test/java/com/ziv/reminders/engine
git commit -m "Add HabitEngine dispatch layer"
```

---

### Task 6: `HabitScheduler` and `HabitNotifications`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/scheduling/HabitScheduler.kt`
- Create: `app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt`
- Test: `app/src/test/java/com/ziv/reminders/scheduling/HabitSchedulerTest.kt`

**Interfaces:**
- Consumes: `HabitInstance`, `isEnabledDay` (Tasks 2-3).
- Produces:
  - `class HabitScheduler(context: Context) { fun scheduleRemindersForToday(date: LocalDate, instance: HabitInstance); fun scheduleRollover(from: LocalDate) }`, plus `companion object { val REMINDER_HOURS; const val WINDOW_LENGTH_MS; const val ACTION_REMINDER; const val ACTION_ROLLOVER; const val EXTRA_HABIT_INSTANCE_ID }` — consumed by `HabitReminderReceiver`/`RolloverReceiver`/`BootReceiver` (Tasks 7-8) and `RemindersApp` (Task 9).
  - `object HabitNotifications { fun channelId(instance: HabitInstance): String; fun notificationId(instance: HabitInstance): Int; fun createChannel(context: Context, instance: HabitInstance); fun buildReminderNotification(context: Context, instance: HabitInstance): Notification }` — consumed by `HabitReminderReceiver` (Task 7).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.HabitInstance
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun clearAnyPreExistingAlarms() {
        shadowOf(alarmManager).getScheduledAlarms().forEach { it.operation?.let(alarmManager::cancel) }
    }

    private val instance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )

    @Test
    fun scheduleRemindersForToday_enabledDay_schedulesFiveAlarms() {
        val scheduler = HabitScheduler(context)

        // A date far enough in the past that every reminder hour is still "in the future"
        // relative to itself is impossible to construct without a clock seam here (unlike the
        // receivers, HabitScheduler has no injectable clock — it always compares against real
        // "now"), so this test uses tomorrow, which guarantees every hour is in the future.
        val tomorrow = LocalDate.now().plusDays(1)
        scheduler.scheduleRemindersForToday(tomorrow, instance)

        assertEquals(5, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleRemindersForToday_nonEnabledDay_schedulesNothing() {
        val scheduler = HabitScheduler(context)
        val neverEnabled = instance.copy(enabledDaysMask = 0)

        scheduler.scheduleRemindersForToday(LocalDate.now().plusDays(1), neverEnabled)

        assertTrue(shadowOf(alarmManager).getScheduledAlarms().isEmpty())
    }

    @Test
    fun scheduleRollover_schedulesOneAlarm() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleRollover(from = LocalDate.now())

        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitSchedulerTest"`
Expected: FAIL — `HabitScheduler` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/scheduling/HabitScheduler.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.isEnabledDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wraps AlarmManager, generalized to take a habitInstanceId so one receiver serves every
 * habit instance regardless of kind. Always inexact (setWindow, never setExact...) — see
 * Global Constraints.
 */
class HabitScheduler(private val context: Context) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleRemindersForToday(date: LocalDate, instance: HabitInstance) {
        if (!isEnabledDay(date, instance.enabledDaysMask)) return
        for (hour in REMINDER_HOURS) {
            val triggerAt = epochMillisAt(date, hour)
            if (triggerAt <= System.currentTimeMillis()) continue
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS, reminderPendingIntent(instance.id, hour)
            )
        }
    }

    fun scheduleRollover(from: LocalDate) {
        val nextMidnight = epochMillisAt(from.plusDays(1), hour = 0, minute = 1)
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, nextMidnight, WINDOW_LENGTH_MS, rolloverPendingIntent())
    }

    private fun epochMillisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun reminderPendingIntent(habitInstanceId: Long, hour: Int): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        // Unique request code per (instance, hour) so different instances/hours never collide.
        val requestCode = (habitInstanceId * 100 + hour).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rolloverPendingIntent(): PendingIntent {
        val intent = Intent(context, RolloverReceiver::class.java).setAction(ACTION_ROLLOVER)
        return PendingIntent.getBroadcast(
            context, ROLLOVER_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        val REMINDER_HOURS = listOf(9, 10, 11, 12, 13)
        const val WINDOW_LENGTH_MS = 15 * 60 * 1000L
        // Negative, guaranteed never to collide with a (habitInstanceId * 100 + hour) request code.
        const val ROLLOVER_REQUEST_CODE = -1
        const val ACTION_REMINDER = "com.ziv.reminders.action.REMINDER"
        const val ACTION_ROLLOVER = "com.ziv.reminders.action.ROLLOVER"
        const val EXTRA_HABIT_INSTANCE_ID = "com.ziv.reminders.extra.HABIT_INSTANCE_ID"
    }
}
```

`app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt`:
```kotlin
package com.ziv.reminders.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ziv.reminders.MainActivity
import com.ziv.reminders.R
import com.ziv.reminders.data.HabitInstance

/** One channel per HabitInstance (not per-kind or shared) — see Global Constraints. */
object HabitNotifications {
    fun channelId(instance: HabitInstance): String = "habit_${instance.id}"
    fun notificationId(instance: HabitInstance): Int = instance.id.toInt()

    fun createChannel(context: Context, instance: HabitInstance) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId(instance), instance.name, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun buildReminderNotification(context: Context, instance: HabitInstance): Notification {
        // CLEAR_TOP + SINGLE_TOP so tapping resumes an existing MainActivity instead of
        // stacking a duplicate one, matching ReadBook's precedent for notification-tap intents.
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, instance.id.toInt(), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId(instance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText(instance.notificationBody)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitSchedulerTest"`
Expected: PASS (3 tests). Note: `HabitReminderReceiver` and `RolloverReceiver` are referenced
by class literal in `HabitScheduler` but not implemented until Tasks 7-8 — create empty
placeholder classes now so this task compiles:

`app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt` (placeholder, replaced in Task 7):
```kotlin
package com.ziv.reminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
```

`app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt` (placeholder, replaced in Task 8):
```kotlin
package com.ziv.reminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RolloverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/scheduling app/src/main/java/com/ziv/reminders/notifications app/src/test/java/com/ziv/reminders/scheduling/HabitSchedulerTest.kt
git commit -m "Add HabitScheduler and HabitNotifications"
```

---

### Task 7: `HabitReminderReceiver`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt` (replace Task 6's placeholder)
- Test: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `HabitInstanceDao.getById` (Task 3), `HabitEngine.todayStatus` (Task 5),
  `HabitNotifications` (Task 6), `HabitScheduler.ACTION_REMINDER`/`EXTRA_HABIT_INSTANCE_ID` (Task 6).
- Produces: testable seams `internal var today`, `internal var habitInstanceDaoOverride`,
  `internal var habitEngineOverride`, `internal var scopeOverride` — mirrors ReadBook's
  `NudgeReceiver` seam pattern exactly, consumed by this task's own test.
- Depends on `RemindersApp.container` (Task 9) for its real (non-test) code path — Task 9
  is implemented after this task; until then, `(context.applicationContext as RemindersApp)`
  will not compile. Create a minimal `RemindersApp` placeholder now (see Step 3) that Task 9
  replaces.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitReminderReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "Reminders", notificationBody = "Don't forget your exercises today!",
        counterGoal = 5,
    )

    // goAsync() only works via the real broadcast mechanism — register + sendBroadcast + idle,
    // not receiver.onReceive() directly (mirrors ReadBook's NudgeReceiverTest precedent).
    private fun dispatch(receiver: HabitReminderReceiver, habitInstanceId: Long) {
        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_REMINDER), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(
            Intent(HabitScheduler.ACTION_REMINDER).putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun onReceive_todayNotCompleted_postsAReminderNotification() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(instance)

        val receiver = HabitReminderReceiver()
        receiver.today = { LocalDate.of(2026, 7, 14) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitEngineOverride = HabitEngine(CounterHabitRepository(db.counterDailyProgressDao()))
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        dispatch(receiver, habitInstanceId = 1L)
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(instance) }
        assertEquals(HabitNotifications.channelId(instance), notification?.notification?.channelId)

        db.close()
    }

    @Test
    fun onReceive_todayAlreadyCompleted_postsNothing() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(instance)
        repeat(5) {
            db.counterDailyProgressDao().upsert(
                com.ziv.reminders.data.CounterDailyProgress(1L, "2026-07-14", it + 1, it + 1 >= 5)
            )
        }

        val receiver = HabitReminderReceiver()
        receiver.today = { LocalDate.of(2026, 7, 14) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitEngineOverride = HabitEngine(CounterHabitRepository(db.counterDailyProgressDao()))
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        dispatch(receiver, habitInstanceId = 1L)
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(instance) }
        assertNull(notification)

        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitReminderReceiverTest"`
Expected: FAIL — the placeholder `HabitReminderReceiver` has no seams and does nothing.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/ziv/reminders/RemindersApp.kt` as a minimal placeholder (Task 9
replaces this with the real DI wiring):
```kotlin
package com.ziv.reminders

import android.app.Application
import com.ziv.reminders.data.AppContainer

class RemindersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
```

Create `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` as a minimal placeholder
(Task 9 replaces this with the full wiring):
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db").build()
    }

    val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    val habitEngine: HabitEngine by lazy { HabitEngine(counterHabitRepository) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}
```

Replace `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fired by AlarmManager at each hourly reminder time. A no-op if today is already completed. */
class HabitReminderReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitEngineOverride: HabitEngine? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        val pendingResult = goAsync()
        // Each override is checked via ?: BEFORE the RemindersApp cast, not a separate eager
        // `val container = (context.applicationContext as RemindersApp)...` line — that would
        // evaluate the cast unconditionally, throwing ClassCastException in every test even
        // when overrides are provided (Robolectric's application context isn't a RemindersApp
        // instance — see robolectric.properties from Task 3). Mirrors ReadBook's NudgeReceiver
        // exactly, which relies on this same short-circuiting for the same reason.
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val engine = habitEngineOverride
            ?: (context.applicationContext as RemindersApp).container.habitEngine
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val instance = dao.getById(habitInstanceId) ?: return@launch
                val status = engine.todayStatus(instance, today())
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                }
                if (!completed) {
                    HabitNotifications.createChannel(context, instance)
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.notify(HabitNotifications.notificationId(instance), HabitNotifications.buildReminderNotification(context, instance))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitReminderReceiverTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/RemindersApp.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt
git commit -m "Implement HabitReminderReceiver"
```

---

### Task 8: `RolloverReceiver` and `BootReceiver`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt` (replace Task 6's placeholder)
- Create: `app/src/main/java/com/ziv/reminders/scheduling/BootReceiver.kt`
- Test: `app/src/test/java/com/ziv/reminders/scheduling/RolloverReceiverTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/scheduling/BootReceiverTest.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add the `BootReceiver` entry with the
  `BOOT_COMPLETED` intent filter (already present from Task 1's manifest content, since it
  was written with this task's receiver in mind — verify it's there, no change needed if so).

**Interfaces:**
- Consumes: `HabitInstanceDao.getAll` (Task 3), `HabitScheduler.scheduleRemindersForToday`/`scheduleRollover` (Task 6).
- Produces: same `internal var` testable-seam pattern as `HabitReminderReceiver` (Task 7),
  consumed by this task's own tests.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RolloverReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun clearAnyPreExistingAlarms() {
        shadowOf(alarmManager).getScheduledAlarms().forEach { it.operation?.let(alarmManager::cancel) }
    }

    @Test
    fun onReceive_schedulesTodaysRemindersForEveryInstance_andReschedulesItself() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )

        val receiver = RolloverReceiver()
        receiver.newDay = { LocalDate.now().plusDays(1) } // guarantee every reminder hour is in the future
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitSchedulerOverride = HabitScheduler(context)
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_ROLLOVER), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(HabitScheduler.ACTION_ROLLOVER))
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        // 5 reminder alarms for the one instance + 1 rollover self-reschedule = 6.
        assertEquals(6, shadowOf(alarmManager).getScheduledAlarms().size)

        db.close()
    }
}
```

```kotlin
package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun clearAnyPreExistingAlarms() {
        shadowOf(alarmManager).getScheduledAlarms().forEach { it.operation?.let(alarmManager::cancel) }
    }

    @Test
    fun onReceive_bootCompleted_schedulesTodaysRemindersForEveryInstance_andRollover() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )

        val receiver = BootReceiver()
        receiver.today = { LocalDate.now().plusDays(1) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitSchedulerOverride = HabitScheduler(context)
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        // BootReceiver calls goAsync(), which needs the real broadcast dispatch mechanism just
        // like HabitReminderReceiver/RolloverReceiver — direct onReceive() would NPE on the
        // PendingResult. android:exported="false" in the manifest is fine for real devices
        // (the system still delivers BOOT_COMPLETED to non-exported receivers registered in the
        // manifest); it only affects registerReceiver's RECEIVER_NOT_EXPORTED flag choice here.
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BOOT_COMPLETED), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        assertEquals(6, shadowOf(alarmManager).getScheduledAlarms().size)

        db.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.RolloverReceiverTest" --tests "com.ziv.reminders.scheduling.BootReceiverTest"`
Expected: FAIL — the placeholder `RolloverReceiver` has no seams/logic, and `BootReceiver` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Replace `app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.HabitInstanceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires nightly at 00:01: schedules the new day's reminders for every habit (if enabled) and
 * reschedules itself. */
class RolloverReceiver : BroadcastReceiver() {

    internal var newDay: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitSchedulerOverride: HabitScheduler? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        // Lazy — each override is checked via ?: before the RemindersApp cast, not a shared
        // eager `val container = ...` line, for the same ClassCastException reason documented
        // in HabitReminderReceiver (Task 7).
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val scheduler = habitSchedulerOverride
            ?: (context.applicationContext as RemindersApp).container.habitScheduler
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val today = newDay()
                for (instance in dao.getAll()) {
                    scheduler.scheduleRemindersForToday(today, instance)
                }
                scheduler.scheduleRollover(from = today)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

Create `app/src/main/java/com/ziv/reminders/scheduling/BootReceiver.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.HabitInstanceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Alarms don't survive reboots — re-establish today's reminders and the rollover chain.
 * Wrapped so a boot-time exception never silently bricks the day's reminders; worst case,
 * nothing gets (re)scheduled here and the next app-open self-heal covers it. */
class BootReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitSchedulerOverride: HabitScheduler? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        // Lazy — same reason as HabitReminderReceiver (Task 7) and RolloverReceiver above.
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val scheduler = habitSchedulerOverride
            ?: (context.applicationContext as RemindersApp).container.habitScheduler
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val date = today()
                for (instance in dao.getAll()) {
                    scheduler.scheduleRemindersForToday(date, instance)
                }
                scheduler.scheduleRollover(from = date)
            } catch (e: Exception) {
                // Never let a boot-time failure crash the receiver — next app-open self-heals.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.RolloverReceiverTest" --tests "com.ziv.reminders.scheduling.BootReceiverTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt app/src/main/java/com/ziv/reminders/scheduling/BootReceiver.kt app/src/test/java/com/ziv/reminders/scheduling/RolloverReceiverTest.kt app/src/test/java/com/ziv/reminders/scheduling/BootReceiverTest.kt
git commit -m "Implement RolloverReceiver and BootReceiver"
```

---

### Task 9: `AppContainer` and `RemindersApp` — real wiring

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (replace Task 7's placeholder)
- Modify: `app/src/main/java/com/ziv/reminders/RemindersApp.kt` (replace Task 7's placeholder)

**Interfaces:**
- Consumes: everything from Tasks 3-8.
- Produces: `AppContainer` fully wired (adds nothing new to its public shape from Task 7's
  placeholder — this task's job is behavior, not new surface). Consumed by `MainActivity`
  (Task 11) and `DashboardViewModel` (Task 10).

This task has no new pure logic to unit-test — its correctness is "does the app boot and
self-heal scheduling," which Task 12's on-device verification checklist covers. No new test
file.

- [ ] **Step 1: Replace `AppContainer` and `RemindersApp` with the real wiring**

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (same as Task 7's placeholder —
no change needed; confirm it matches this exact content):
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        // No .addMigrations(...) yet — schema v1, first release. Later kinds add real
        // Migration objects here; never fallbackToDestructiveMigration() — Global Constraints.
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db").build()
    }

    val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    val habitEngine: HabitEngine by lazy { HabitEngine(counterHabitRepository) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}
```

`app/src/main/java/com/ziv/reminders/RemindersApp.kt`:
```kotlin
package com.ziv.reminders

import android.app.Application
import com.ziv.reminders.data.AppContainer
import com.ziv.reminders.data.ensureHabitsSeeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class RemindersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Self-heal on every app open: seeds the known habit instances on first launch, then
        // ensures today's reminders and the rollover chain are scheduled even if the
        // midnight/boot jobs never got to run (OEM battery killers, a missed boot receiver,
        // etc.) — not solely reliant on any single scheduling path.
        appScope.launch {
            ensureHabitsSeeded(container.habitInstanceDao)
            val today = LocalDate.now()
            for (instance in container.habitInstanceDao.getAll()) {
                container.habitScheduler.scheduleRemindersForToday(today, instance)
            }
            container.habitScheduler.scheduleRollover(from = today)
        }
    }
}
```

- [ ] **Step 2: Verify the project still builds and all tests still pass**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests from Tasks 2-8 still pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/main/java/com/ziv/reminders/RemindersApp.kt
git commit -m "Wire AppContainer and RemindersApp startup self-heal"
```

---

### Task 10: `DashboardUiState` and `DashboardViewModel`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `AppContainer` (Task 9), `HabitEngine`, `HabitStatus`, `CounterHabitRepository` (Tasks 4-5).
- Produces:
  - `data class HabitRowUiState(instanceId: Long, name: String, statusText: String, completed: Boolean, streak: Int)`
  - `data class DashboardUiState(habits: List<HabitRowUiState> = emptyList(), isLoaded: Boolean = false)`
  - `interface DashboardDataSource { val habitInstanceDao: HabitInstanceDao; val counterHabitRepository: CounterHabitRepository; val habitEngine: HabitEngine }` — extracted from `AppContainer` in Step 3 below so this ViewModel is testable without a real `Context`/Room instance; `AppContainer` implements it.
  - `class DashboardViewModel(dataSource: DashboardDataSource) : ViewModel() { val uiState: StateFlow<DashboardUiState>; fun refresh(); fun onIncrement(instanceId: Long) }` plus `companion object { fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory }` — consumed by `DashboardScreen`/`MainActivity` (Task 11), which pass the real `AppContainer` (from `RemindersApp.container`) as the `DashboardDataSource` argument.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardViewModelTest {

    // DB is created inside each runTest block below, not a shared helper outside it — its
    // setQueryCoroutineContext must reference the enclosing runTest's own testScheduler, or the
    // DB's dispatcher won't advance in lockstep with the test's virtual clock and
    // advanceUntilIdle() won't flush its queued work (same reason ReadBook's NudgeReceiverTest
    // constructs its db inline per-test rather than via a shared no-argument helper).

    @Test
    fun refresh_oneHabitNotYetDone_populatesOneRow() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(1, state.habits.size)
        assertEquals("0/5", state.habits[0].statusText)
        assertEquals(false, state.habits[0].completed)

        db.close()
    }

    @Test
    fun onIncrement_updatesStatusTextAndCompletion() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        repeat(5) {
            viewModel.onIncrement(1L)
            testScheduler.advanceUntilIdle()
        }

        val state = viewModel.uiState.value
        assertEquals("5/5", state.habits[0].statusText)
        assertEquals(true, state.habits[0].completed)

        db.close()
    }
}
```

`app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt` (test-only helper —
implements the same public members `DashboardViewModel` reads off `AppContainer`, backed by a
given test `AppDatabase` instead of a real on-device one):
```kotlin
package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase) {
    val habitInstanceDao = db.habitInstanceDao()
    val counterDailyProgressDao = db.counterDailyProgressDao()
    val counterHabitRepository = CounterHabitRepository(counterDailyProgressDao)
    val habitEngine = HabitEngine(counterHabitRepository)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel` doesn't exist yet, and it doesn't accept a
`TestAppContainer` (compile error). Note: this drives a design change in Step 3 — see below.

- [ ] **Step 3: Write the implementation**

`DashboardViewModelTest`'s use of `TestAppContainer` instead of the real `AppContainer` means
`DashboardViewModel` must depend on a narrow interface, not the concrete `AppContainer` class
(which requires a real `Context`/Room instance the test doesn't have). Extract that interface:

Add to `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (append, don't replace the
existing class — `AppContainer` now implements this new interface):
```kotlin
interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

Change `AppContainer`'s class declaration line to:
```kotlin
class AppContainer(context: Context) : DashboardDataSource {
```

(No other change to `AppContainer` — its existing `val` properties already satisfy this
interface's members since they use the same names and types.)

Update `TestAppContainer` from Step 1 to implement it too:
```kotlin
package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase) : DashboardDataSource {
    override val habitInstanceDao = db.habitInstanceDao()
    override val counterHabitRepository = CounterHabitRepository(db.counterDailyProgressDao())
    override val habitEngine = HabitEngine(counterHabitRepository)
}
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

data class HabitRowUiState(
    val instanceId: Long,
    val name: String,
    val statusText: String,
    val completed: Boolean,
    val streak: Int,
)

data class DashboardUiState(
    val habits: List<HabitRowUiState> = emptyList(),
    val isLoaded: Boolean = false,
)
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instances = dataSource.habitInstanceDao.getAll()
            val rows = instances.map { instance ->
                val status = dataSource.habitEngine.todayStatus(instance, today)
                val streak = dataSource.habitEngine.currentStreak(instance, today)
                val (statusText, completed) = when (status) {
                    is HabitStatus.CounterStatus -> "${status.current}/${status.goal}" to status.completed
                }
                HabitRowUiState(instance.id, instance.name, statusText, completed, streak)
            }
            _uiState.value = DashboardUiState(habits = rows, isLoaded = true)
        }
    }

    fun onIncrement(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.counterHabitRepository.increment(instance, LocalDate.now())
            refresh()
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = DashboardViewModel(dataSource) as T
            }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Run the full test suite to confirm nothing else broke**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests from Tasks 2-10 pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/main/java/com/ziv/reminders/ui/dashboard app/src/test/java/com/ziv/reminders/ui/dashboard
git commit -m "Add DashboardUiState and DashboardViewModel"
```

---

### Task 11: `DashboardScreen` and real `MainActivity`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt` (replace Task 1's placeholder)

**Interfaces:**
- Consumes: `DashboardViewModel`, `DashboardUiState`, `HabitRowUiState` (Task 10),
  `RemindersTheme` (Task 1), `RemindersApp.container` (Task 9).
- Produces: the on-device UI. No new unit-testable surface — Compose UI in this codebase's
  established pattern (matching Shape/ReadBook) is verified manually on-device (Task 12), not
  unit-tested; the state it renders (`DashboardUiState`) is already covered by Task 10.

- [ ] **Step 1: Write `DashboardScreen`**

```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (!uiState.isLoaded) return@Column
        uiState.habits.forEach { habit ->
            HabitRow(habit = habit, onIncrement = { viewModel.onIncrement(habit.instanceId) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HabitRow(habit: HabitRowUiState, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onIncrement),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = if (habit.completed) "✓ ${habit.statusText}" else habit.statusText,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
```

- [ ] **Step 2: Replace `MainActivity`**

```kotlin
package com.ziv.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziv.reminders.ui.dashboard.DashboardScreen
import com.ziv.reminders.ui.dashboard.DashboardViewModel
import com.ziv.reminders.ui.theme.RemindersTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val container = (application as RemindersApp).container
        setContent {
            RemindersTheme {
                val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
                DashboardScreen(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
```

- [ ] **Step 3: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/main/java/com/ziv/reminders/MainActivity.kt
git commit -m "Add DashboardScreen and wire MainActivity"
```

---

### Task 12: On-device verification

**Files:** none (verification only).

**Interfaces:** none — terminal task for this plan.

- [ ] **Step 1: Run the full test suite one more time**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, every test from Tasks 2-10 passes.

- [ ] **Step 2: Install on-device**

Run: `./gradlew :app:installDebug` (with the phone connected and USB debugging enabled)
Expected: install succeeds; "Reminders" appears in the app drawer.

- [ ] **Step 3: Manual QA checklist**

Open the app. Confirm:
- The `POST_NOTIFICATIONS` permission prompt appears on first launch.
- The dashboard shows exactly one row: "Exercise", streak "0d", status "0/5".
- Tapping the row increments the count each time: 1/5, 2/5, 3/5, 4/5, 5/5.
- On reaching 5/5, the row shows "✓ 5/5".
- Force-stop the app (Settings → Apps → Reminders → Force stop), then reboot the phone (or at
  minimum re-open the app) — confirm the dashboard still shows 5/5 for today (Room persistence
  survives process death, matching every Room-backed screen in Shape/ReadBook).
- Wait for (or manually trigger by adjusting the phone's clock, then reopening the app to
  re-trigger self-heal) a reminder hour while the count is below 5 — confirm a notification
  appears with title "Reminders" and body "Don't forget your exercises today!", and tapping it
  opens the app.
- With the count already at 5/5, confirm no reminder notification appears at a reminder hour
  (the completed-day no-op path).

- [ ] **Step 4: Record the result**

If all checks pass, this plan is complete — the Timer kind (Plan 2) and Schedule-cursor kind
(Plan 3) can now be planned against a proven, working engine. If any check fails, treat it as
a bug in the already-committed code from this plan (fix forward with its own commit) rather
than carrying it into the next plan.
