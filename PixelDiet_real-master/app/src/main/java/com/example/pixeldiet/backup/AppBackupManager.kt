package com.example.pixeldiet.backup

import com.example.pixeldiet.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow

object AppBackupManager {
    lateinit var instance: BackupManager
    val isDataReady = MutableStateFlow(true)

    fun init(db: AppDatabase) {
        instance = BackupManager(
            userDao = db.userProfileDao(),
            trackedAppDao = db.trackedAppDao(),
            dailyUsageDao = db.dailyUsageDao(),
            groupDao = db.groupDao(),
            friendDao = db.friendDao()
        )
    }
}