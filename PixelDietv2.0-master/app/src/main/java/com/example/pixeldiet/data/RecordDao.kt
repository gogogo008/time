package com.example.pixeldiet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pixeldiet.friend.FriendRecord

import kotlinx.coroutines.flow.Flow


@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage")
    fun getAllAppUsages(): Flow<List<AppUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<AppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: AppUsageEntity)
}

// -------------------- DailyUsageDao --------------------
@Dao
interface DailyUsageDao {

    // 1️⃣ UI용: 특정 범위 데이터를 Flow로 가져오기
    @Query("SELECT * FROM daily_usage WHERE uid = :uid AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun getUsageInRange(uid: String, from: String, to: String): Flow<List<DailyUsageEntity>>

    // 2️⃣ 백업/동기화용: 전체 데이터 가져오기
    @Query("SELECT * FROM daily_usage ORDER BY date ASC")
    suspend fun getAll(): List<DailyUsageEntity>

    // 3️⃣ 데이터 삽입
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<DailyUsageEntity>)

    // 4️⃣ 단일 기록 삽입/업데이트
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: DailyUsageEntity)

    // 5️⃣ 특정 날짜 기록 삭제
    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun deleteByDate(date: String)

    // 6️⃣ 전체 삭제
    @Query("DELETE FROM daily_usage")
    suspend fun deleteAll()
}

// -------------------- UserProfileDao --------------------
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    fun getUserProfile(uid: String): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Update
    suspend fun update(profile: UserProfileEntity)
}

// -------------------- NotificationSettingsDao --------------------
@Dao
interface NotificationSettingsDao {
    @Query("SELECT * FROM notification_settings LIMIT 1")
    suspend fun getSettings(): NotificationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: NotificationSettingsEntity)

    @Update
    suspend fun update(settings: NotificationSettingsEntity)
}

@Dao
interface FriendDao {

    // 친구
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriend(friend: FriendRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriends(friends: List<FriendRecord>)

    @Query("SELECT * FROM friend_record")
    suspend fun getAllFriends(): List<FriendRecord>

    @Query("DELETE FROM friend_record WHERE uid = :friendUid")
    suspend fun removeFriend(friendUid: String)

    // 요청받은 친구
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestReceived(request: FriendRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestsReceived(requests: List<FriendRecord>)

    @Query("SELECT * FROM friend_request_received")
    suspend fun getFriendRequestsReceived(): List<FriendRecord>

    @Query("DELETE FROM friend_request_received WHERE uid = :uid")
    suspend fun removeFriendRequestReceived(uid: String)

    // 보낸 요청
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestSent(request: FriendRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestsSent(requests: List<FriendRecord>)

    @Query("SELECT * FROM friend_request_sent")
    suspend fun getFriendRequestsSent(): List<FriendRecord>

    @Query("DELETE FROM friend_request_sent WHERE uid = :uid")
    suspend fun removeFriendRequestSent(uid: String)
}

@Dao
interface TrackedAppDao {

    @Query("SELECT * FROM tracked_apps")
    fun getAllTrackedApps(): Flow<List<TrackedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: TrackedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(apps: List<TrackedAppEntity>)

    @Delete
    suspend fun delete(app: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps")
    suspend fun deleteAll()

    @Query("SELECT * FROM tracked_apps")
    suspend fun getAllTrackedAppsOnce(): List<TrackedAppEntity>

    @Query("DELETE FROM tracked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}
