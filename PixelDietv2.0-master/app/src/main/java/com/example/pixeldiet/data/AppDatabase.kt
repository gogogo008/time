package com.example.pixeldiet.data

import androidx.room.Database
import androidx.room.RoomDatabase


import com.example.pixeldiet.friend.FriendRequestReceived
import com.example.pixeldiet.friend.FriendRequestSent
import com.example.pixeldiet.friend.FriendRecord

@Database(
    entities = [UserProfileEntity::class,  FriendRecord::class,   FriendRequestReceived::class, FriendRequestSent::class,  DailyUsageEntity::class, AppUsageEntity::class,TrackedAppEntity::class, NotificationSettingsEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun notificationSettingsDao(): NotificationSettingsDao

    abstract fun friendDao(): FriendDao
}
