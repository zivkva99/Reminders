package com.ziv.reminders

import android.app.Application
import com.ziv.reminders.data.AppContainer

class RemindersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
