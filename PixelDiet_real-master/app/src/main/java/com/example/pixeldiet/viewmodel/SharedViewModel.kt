package com.example.pixeldiet.viewmodel

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.model.*
import com.example.pixeldiet.repository.SyncRepository
import com.example.pixeldiet.repository.UsageRepository
import com.github.mikephil.charting.data.Entry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class UserProfile(
    val uid: String,
    val name: String,
    val imageUrl: String,
    val friendCode: String
)

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    // ------------------- User/Profile -------------------
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val trackedPrefs by lazy {
        application.getSharedPreferences("tracked_apps_prefs", Context.MODE_PRIVATE)
    }
    private val goalPrefs by lazy {
        application.getSharedPreferences("goal_prefs", Context.MODE_PRIVATE)
    }

    private val _trackedPackages = MutableStateFlow<Set<String>>(emptySet())
    val trackedPackagesFlow: StateFlow<Set<String>> = _trackedPackages

    private val _trackedApps = MutableStateFlow<List<TrackedAppEntity>>(emptyList())
    val trackedAppsFlow: StateFlow<List<TrackedAppEntity>> = _trackedApps

    // ✅ 로딩 게이트: 기본은 false (동기화/초기 로딩 끝나면 true)
    private val _isDataReady = MutableStateFlow(true)
    val isDataReady: StateFlow<Boolean> = _isDataReady

    private val _dailyDetailFlow = MutableStateFlow<List<Pair<AppUsage, Int>>>(emptyList())
    val dailyDetailFlow: StateFlow<List<Pair<AppUsage, Int>>> get() = _dailyDetailFlow

    private val _overallGoalMinutes = MutableStateFlow<Int?>(null)
    val overallGoalFlow: StateFlow<Int?> = _overallGoalMinutes

    // ------------------- Firebase Auth -------------------
    private val auth = FirebaseAuth.getInstance()
    private val _userName = MutableStateFlow(getUserName())
    val userName: StateFlow<String> = _userName
    val isGoogleUser = MutableStateFlow(isGoogleLogin())

    private val authListener = FirebaseAuth.AuthStateListener {
        _userName.value = getUserName()
        isGoogleUser.value = isGoogleLogin()
    }

    private fun getCurrentUserUid(): String? = auth.currentUser?.uid

    // ------------------- Repositories -------------------
    private val repository = UsageRepository
    private val syncRepository = SyncRepository(application.applicationContext)

    // ------------------- App usage (UI DTO) -------------------
    private val _appUsageList = MutableStateFlow<List<AppUsage>>(emptyList())
    val appUsageListFlow: StateFlow<List<AppUsage>> get() = _appUsageList

    private val context = getApplication<Application>().applicationContext

    val dailyUsageListFlow: StateFlow<List<DailyUsage>> = repository.dailyUsageListFlow
    val notificationSettingsFlow: StateFlow<NotificationSettings?> = repository.notificationSettingsFlow
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    fun onUserLoggedIn(uid: String) {
        _userId.value = uid
    }
    // ------------------- Total usage (calendar/stat) -------------------
    val totalUsageFlow: StateFlow<Pair<Int, Int>> =
        dailyUsageListFlow.combine(trackedPackagesFlow) { dailies, tracked ->
            val totalUsed = dailies.flatMap { it.appUsages.entries }
                .filter { tracked.isEmpty() || it.key in tracked }
                .sumOf { it.value }
            totalUsed
        }.combine(_overallGoalMinutes) { used, goal ->
            used to (goal ?: 0)
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0 to (_overallGoalMinutes.value ?: 0))

    // ------------------- Filters -------------------
    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilterTextFlow: StateFlow<String> =
        combine(_selectedFilter, appUsageListFlow) { pkg, apps ->
            if (pkg == null) "전체" else apps.find { it.packageName == pkg }?.appLabel ?: "전체"
        }.stateIn(viewModelScope, SharingStarted.Lazily, "전체")

    // ------------------- Selected month -------------------
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonthFlow: StateFlow<Int> = _selectedMonth

    // ------------------- Initial load -------------------
    private var hasSyncedOnce = false

    init {
        auth.addAuthStateListener(authListener)

        // ✅ init에서는 직접 syncRepository를 부르지 말고, 아래 "내부 함수"를 통해 1회 동기화 + 데이터 구성까지 끝낸다.
        viewModelScope.launch(Dispatchers.IO) {
            val uid = getCurrentUserUid()
            if (uid == null) {
                // 로그인 전 상태라면 UI가 영원히 로딩에 갇히지 않게 풀어줌
                _isDataReady.value = true
                return@launch
            }

        }
    }

    fun markDataReady() {
        _isDataReady.value = true
    }

    // ------------------- SharedPreferences / Room 업데이트 -------------------
    fun updateTrackedPackages(newSet: Set<String>) {
        _trackedPackages.value = newSet
        trackedPrefs.edit().putStringSet("tracked_packages", newSet).apply()
    }

    fun setOverallGoal(minutes: Int?) {
        _overallGoalMinutes.value = minutes
        goalPrefs.edit().apply {
            if (minutes == null) remove("overall_goal_minutes") else putInt("overall_goal_minutes", minutes)
        }.apply()
    }

    // ------------------- Firebase 인증 관련 -------------------
    private fun getUserName(): String {
        val user = auth.currentUser
        return if (user != null && !user.isAnonymous) {
            "${user.displayName ?: "사용자"}님 환영합니다"
        } else "게스트 로그인 중입니다"
    }

    private fun isGoogleLogin(): Boolean {
        val user = auth.currentUser
        return user != null && !user.isAnonymous
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.getDatabase(getApplication())
            db.userProfileDao().clearAll()
            db.trackedAppDao().clearAll()
            db.dailyUsageDao().clearAll()
            db.groupDao().clearAll()
            db.friendDao().clearAll()

            trackedPrefs.edit().clear().apply()
            goalPrefs.edit().clear().apply()

            auth.signOut()

            _userProfile.value = null
            _trackedApps.value = emptyList()
            _trackedPackages.value = emptySet()
            _overallGoalMinutes.value = null

            // 로그아웃 직후에는 다시 로딩상태로 돌려도 되지만,
            // 현재 화면 네비게이션에서 Login으로 이동할 것이므로 true로 풀어둠
            _isDataReady.value = true
            hasSyncedOnce = false
        }
    }

    fun loadDailyAppUsage(uid: String, date: String) {
        viewModelScope.launch {
            Log.d("AppUsageList", "UID: $uid, Date: $date")

            val usageMap = repository.getDailyAppUsage(uid, date)
            Log.d("AppUsageList", "UsageMap from DB: $usageMap")

            val usageList = usageMap.map { (pkg, mins) ->
                AppUsage(pkg, appLabel = pkg, icon = null, currentUsage = mins, goalTime = 0, streak = 0)
            }
            _appUsageList.value = usageList
            Log.d("AppUsageList", "Loaded: $usageList")
        }
    }

    fun onGoogleLoginSuccess(idToken: String) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()

                _userProfile.value = null
                _trackedApps.value = emptyList()
                _trackedPackages.value = emptySet()
                _overallGoalMinutes.value = null

                initUserProfile()

                // ✅ 계정 바뀌었으면 동기화 1회 다시 수행
                val uid = getCurrentUserUid() ?: return@launch
                viewModelScope.launch(Dispatchers.IO) {
                    hasSyncedOnce = false
                    syncFromFirestoreInternal(uid, force = true)
                }
            } catch (e: Exception) {
                Log.e("GoogleLogin", "Firebase sign in failed: $e")
            }
        }
    }

    // ------------------- 캘린더 관련 -------------------
    fun setCalendarFilter(packageName: String?) { _selectedFilter.value = packageName }
    fun setSelectedMonth(year: Int, month: Int) { _selectedMonth.value = month }

    val calendarGoalTimeFlow: StateFlow<Int> = combine(
        _overallGoalMinutes, appUsageListFlow, trackedPackagesFlow
    ) { overallGoal, apps, tracked ->
        if (overallGoal != null) overallGoal
        else apps.filter { tracked.isEmpty() || it.packageName in tracked }.sumOf { it.goalTime }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val calendarDecoratorDataFlow: StateFlow<List<CalendarDecoratorData>> = combine(
        dailyUsageListFlow, appUsageListFlow, _selectedFilter, trackedPackagesFlow, _overallGoalMinutes
    ) { dailies, apps, filterPkg, tracked, overallGoal ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val decorators = mutableListOf<CalendarDecoratorData>()
        for (daily in dailies) {
            val date = sdf.parse(daily.date) ?: continue
            val cal = Calendar.getInstance().apply { time = date }
            val calDay = CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

            val (usage, goal) = if (filterPkg == null) {
                val dayUsage = daily.appUsages.filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }.values.sum()
                val autoGoal = apps.filter { tracked.isEmpty() || it.packageName in tracked }.sumOf { it.goalTime }
                dayUsage to (overallGoal ?: autoGoal)
            } else {
                val dayUsage = daily.appUsages[filterPkg] ?: 0
                val appGoal = apps.find { it.packageName == filterPkg }?.goalTime ?: 0
                dayUsage to appGoal
            }

            if (goal <= 0) continue

            val status = when {
                usage > goal -> DayStatus.FAIL
                usage > goal * 0.7 -> DayStatus.WARNING
                else -> DayStatus.SUCCESS
            }
            decorators.add(CalendarDecoratorData(calDay, status))
        }
        decorators
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val calendarStatsTextFlow: StateFlow<String> = combine(
        calendarDecoratorDataFlow, selectedMonthFlow, _selectedFilter
    ) { decorators, month, _ ->
        val successDays = decorators.count { it.date.month == month && (it.status == DayStatus.SUCCESS || it.status == DayStatus.WARNING) }
        "${month}월 목표 성공일: 총 ${successDays}일!"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    private fun calculateOverallStreak(dailies: List<DailyUsage>, apps: List<AppUsage>, tracked: Set<String>, goal: Int): Int {
        if (goal <= 0) return 0
        val sortedDays = dailies.sortedByDescending { it.date }
        var wasSuccess: Boolean? = null
        var streakCount = 0
        for (day in sortedDays) {
            val dayUsage = day.appUsages.filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }.values.sum()
            val success = dayUsage <= goal
            if (wasSuccess == null) wasSuccess = success
            if (success == wasSuccess) streakCount++ else break
        }
        return if (wasSuccess == true) streakCount else -streakCount
    }

    val streakTextFlow: StateFlow<String> = combine(
        appUsageListFlow, dailyUsageListFlow, _selectedFilter, trackedPackagesFlow, _overallGoalMinutes
    ) { apps, dailies, filterPkg, tracked, overallGoal ->
        val streak = if (filterPkg == null) calculateOverallStreak(dailies, apps, tracked, overallGoal ?: 0)
        else apps.find { it.packageName == filterPkg }?.streak ?: 0
        val appName = if (filterPkg == null) "전체" else apps.find { it.packageName == filterPkg }?.appLabel ?: "알 수 없음"
        val days = kotlin.math.abs(streak)
        val emoji = if (streak >= 0) "🔥" else "💀"
        "$appName: $emoji$days"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    val chartDataFlow: StateFlow<List<Entry>> = combine(
        dailyUsageListFlow, _selectedFilter, trackedPackagesFlow, selectedMonthFlow
    ) { dailies, filterPkg, tracked, month ->
        dailies.filter { it.date.substring(5, 7).toInt() == month }.map { daily ->
            val dayOfMonth = daily.date.substring(8, 10).toFloat()
            val usage = if (filterPkg == null) daily.appUsages.filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }.values.sum()
            else daily.appUsages[filterPkg] ?: 0
            Entry(dayOfMonth, usage.toFloat())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun calculateRealtimeUsage(): Map<String, Int> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: emptyList()
        return stats.associate { it.packageName to (it.totalTimeInForeground / 1000 / 60).toInt() }
    }

    // ========================== 날짜 선택시 앱 사용기록 불러오기 =============================
    fun loadDailyDetail(selectedDate: CalendarDay, context: Context) = viewModelScope.launch {
        val uid = getCurrentUserUid() ?: return@launch
        val dateStr = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.month, selectedDate.day)

        try {
            val (goals, usages) = syncRepository.fetchGoalAndUsageForDate(uid, dateStr)

            val pm = context.packageManager
            val list = goals.map { (pkg, goalTime) ->
                val usage = usages[pkg] ?: 0
                val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                val icon = appInfo?.let { pm.getApplicationIcon(it) }

                AppUsage(
                    packageName = pkg,
                    appLabel = label,
                    icon = icon,
                    currentUsage = usage,
                    goalTime = goalTime,
                    streak = 0
                ) to goalTime
            }

            _dailyDetailFlow.value = list
        } catch (e: Exception) {
            Log.e("CalendarDetail", "Failed to load daily detail: $e")
            _dailyDetailFlow.value = emptyList()
        }
    }

    // ------------------- 데이터 로딩(내부) -------------------
    private suspend fun refreshDataInternal(uid: String) {
        val backupToday = syncRepository.loadBackupToday(uid)
        val realtimeUsage = calculateRealtimeUsage()
        val trackedList = UsageRepository.getAllTrackedOnce()

        _trackedApps.value = trackedList
        _trackedPackages.value = trackedList.map { it.packageName }.toSet()

        // ✅ backupToday 키도 포함해서 목록을 만든다 (백업만 있는 앱도 표시)
        val allPackages = (trackedList.map { it.packageName } + backupToday.keys + realtimeUsage.keys).distinct()

        val mergedList = allPackages.map { pkg ->
            val label = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }

            val icon = try { context.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

            val initialUsage = backupToday[pkg] ?: 0
            val realtime = realtimeUsage[pkg]
            // ✅ 중복 합산 방지: realtime이 있으면 realtime, 없으면 backupToday
            val usage = realtime ?: initialUsage

            val goal = trackedList.find { it.packageName == pkg }?.goalTime ?: 0
            AppUsage(pkg, label, icon, usage, goal, streak = 0)
        }.sortedBy { it.appLabel.lowercase() }

        _appUsageList.value = mergedList
        _overallGoalMinutes.value = trackedList.sumOf { it.goalTime }
    }

    // ------------------- 데이터 로딩(외부 호출) -------------------
    fun refreshData() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        refreshDataInternal(uid)
    }

    fun saveNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        repository.updateNotificationSettings(settings)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    // ------------------- Firestore -> Room 동기화 -------------------
    private suspend fun syncFromFirestoreInternal(uid: String, force: Boolean) {
        if (!force && hasSyncedOnce) {
            // 이미 1회 동기화/초기 로딩 끝났으면, 이후에는 데이터만 새로 고치기
            refreshDataInternal(uid)
            _isDataReady.value = true
            return
        }

        _isDataReady.value = false
        try {
            syncRepository.syncFromFirestore(uid)
            hasSyncedOnce = true

            // 동기화 끝났으니 화면 데이터 재계산
            refreshDataInternal(uid)
        } catch (e: Exception) {
            Log.e("SharedViewModel", "syncFromFirestore failed: $e")
        } finally {
            _isDataReady.value = true
        }
    }

    /** 수동 동기화(설정화면 버튼 등에서 호출) */
    fun syncFromFirestore() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        syncFromFirestoreInternal(uid, force = true)
    }

    // ------------------- 트래킹 앱 업데이트 -------------------
    fun saveTrackedAppsWithGoals(selectedAppsWithGoals: Map<String, Int>) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTracked = UsageRepository.getAllTrackedOnce()
                val toDelete = currentTracked.filter { it.packageName !in selectedAppsWithGoals.keys }
                toDelete.forEach { UsageRepository.deleteTrackedApp(it.packageName) }

                val toSave = selectedAppsWithGoals.map { (pkg, goal) ->
                    TrackedAppEntity(packageName = pkg, goalTime = goal)
                }

                UsageRepository.updateTrackedApps(toSave)
                _trackedPackages.value = selectedAppsWithGoals.keys

                repository.updateGoalTimes(selectedAppsWithGoals)

                val latestTracked = UsageRepository.getAllTrackedOnce()
                _trackedApps.value = latestTracked.map { tracked ->
                    val goal = selectedAppsWithGoals[tracked.packageName] ?: tracked.goalTime
                    tracked.copy(goalTime = goal)
                }

                val totalGoal = _trackedApps.value.sumOf { it.goalTime }
                _overallGoalMinutes.value = totalGoal

                _trackedApps.value.forEach {
                    Log.d("DBCheck", "Saved Package: ${it.packageName}, GoalTime: ${it.goalTime}")
                }

                // 저장 후 UI도 갱신
                val uid = getCurrentUserUid()
                if (uid != null) refreshDataInternal(uid)
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Failed to save tracked apps with goals: $e")
            }
        }

    // =================== 백그라운드 이동시 일일 사용기록 백업 ==================
    fun uploadDailyUsageToFirebase() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        val appUsagesMap = appUsageListFlow.value.associate { it.packageName to it.currentUsage }
        syncRepository.uploadDailyUsage(uid, appUsagesMap)
    }

    fun uploadDailyGoalToFirebase(newGoals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        syncRepository.uploadDailyGoal(uid, newGoals)
    }

    fun initUserProfile() = viewModelScope.launch(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@launch
        try {
            val entity = syncRepository.initUserProfileIfNeeded(
                uid = uid,
                displayName = auth.currentUser?.displayName
            )

            _userProfile.value = UserProfile(
                uid = entity.uid,
                name = entity.name,
                imageUrl = entity.imageUrl,
                friendCode = entity.friendCode
            )
        } catch (e: Exception) {
            Log.e("UserProfile", "Failed to init user profile: $e")
        }
    }

    fun insertTestDataForUid(uid: String) = CoroutineScope(Dispatchers.IO).launch {
        syncRepository.insertTestDataForUid(uid)
    }
}
