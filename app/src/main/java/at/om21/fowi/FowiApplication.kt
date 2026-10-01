package at.om21.fowi

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class FowiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Night mode is not persisted by AppCompat, so apply the stored choice before any activity starts.
        AppCompatDelegate.setDefaultNightMode(AppSettings.themeMode(this).nightMode)
    }
}
