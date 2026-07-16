# Battery-Optimization Exemption Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On every app launch, if Reminders isn't yet exempted from battery optimization,
launch the system's own exemption dialog — the last remaining item from the original
design doc's Next Steps (Xiaomi/MIUI Autostart is not applicable; the verified device is
Samsung).

**Architecture:** One new method on `MainActivity`, `requestBatteryOptimizationExemptionIfNeeded()`,
called from `onCreate()` alongside the existing `requestNotificationPermissionIfNeeded()`.
Checks `PowerManager.isIgnoringBatteryOptimizations(packageName)`; if false, starts
`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for that package. No persisted
state, no custom UI — mirrors the existing notification-permission check's shape exactly.

**Tech Stack:** Same as prior plans — Kotlin 2.3.0. No new dependencies.

## Global Constraints

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- No persisted "already asked" state and no denied-state banner — matches what's
  actually shipped for the notification-permission check today, not a new pattern.
- No unit test for this task — `MainActivity`'s existing notification-permission check
  has zero test coverage in this codebase (plain Activity glue code around standard
  Android system calls, not repository/dispatch logic); this follows that same
  established precedent.

---

## File Structure

```
Reminders/
  app/src/main/AndroidManifest.xml                       (Modify — Task 1)
  app/src/main/java/com/ziv/reminders/MainActivity.kt     (Modify — Task 1)
```

---

### Task 1: Add the battery-optimization exemption request

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`

**Interfaces:**
- Produces: `MainActivity.requestBatteryOptimizationExemptionIfNeeded()`, called from
  `onCreate()`. No new public interface consumed elsewhere.

This task has no failing-test-first step (per this plan's Global Constraints — no unit
test for this class of code, matching the existing, untested notification-permission
check it sits beside). Steps are: implement, build, manually verify, commit.

- [ ] **Step 1: Add the manifest permission**

Add to `app/src/main/AndroidManifest.xml`, alongside the existing `<uses-permission>` elements:
```xml
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

- [ ] **Step 2: Add the exemption request to `MainActivity`**

Full `app/src/main/java/com/ziv/reminders/MainActivity.kt`:
```kotlin
package com.ziv.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
        requestBatteryOptimizationExemptionIfNeeded()
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

    // No persisted "already asked" state, no denied-state banner — re-checks and
    // re-prompts every launch until actually granted, same shape as the notification
    // permission check above. Some OEMs (Samsung, Xiaomi) aggressively kill background
    // alarms/WorkManager without this exemption.
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }
}
```

- [ ] **Step 3: Build and run the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (no new tests, no regressions — this task adds no testable logic per the
Global Constraints)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/ziv/reminders/MainActivity.kt
git commit -m "Request battery-optimization exemption on launch"
```

---

### Task 2: On-device manual verification

Not a code task — no commit. This is the only way to confirm the system dialog actually
appears and behaves correctly; Robolectric doesn't simulate the real
`PowerManager`/Settings exemption flow.

- [ ] On a device where the exemption isn't yet granted, launching the app shows the
  system's battery-optimization exemption dialog (may appear as a permission-style
  system sheet, wording varies by OEM/Android version).
- [ ] Granting the exemption: relaunching the app no longer shows the dialog.
- [ ] Denying/dismissing the exemption: relaunching the app shows it again (no
  persisted "don't ask again" state, per this plan's Global Constraints — this is
  expected, not a bug).
- [ ] No crash, no regression to the existing notification-permission prompt firing
  alongside it in the same launch.
- [ ] Confirm via `adb shell dumpsys deviceidle whitelist` (or equivalent) that the
  package appears in the whitelist once granted, as an independent check beyond the
  in-app dialog closing.

Once all boxes are checked, update `.superpowers/sdd/progress.md` to record this plan's
completion, matching prior plans' precedent.
