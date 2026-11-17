package app.clubcheck

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.clubcheck.upload.PurgeOriginalsWorker
import java.util.concurrent.TimeUnit

class ClubCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Original photos never leave the phone and are deleted after seven days.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "purge-originals", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<PurgeOriginalsWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
