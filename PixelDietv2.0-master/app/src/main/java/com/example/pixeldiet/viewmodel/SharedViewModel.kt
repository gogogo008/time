package com.example.pixeldiet.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.model.*
import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.repository.UsageRepository.loadTrackedApps
import com.github.mikephil.charting.data.Entry
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.firestore
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class UserProfile(
    val uid: String,
    val name: String,
    val imageUrl: String,
    val friendCode: String
)

class SharedViewModel(application: Application) : AndroidViewModel(application) {
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile


    // ------------------- SharedPreferences -------------------
    private val trackedPrefs by lazy { application.getSharedPreferences("tracked_apps_prefs", Context.MODE_PRIVATE) }
    private val goalPrefs by lazy { application.getSharedPreferences("goal_prefs", Context.MODE_PRIVATE) }

    private val _trackedPackages = MutableStateFlow<Set<String>>(emptySet())
    val trackedPackagesFlow: StateFlow<Set<String>> = _trackedPackages

    private val _trackedApps = MutableStateFlow<List<TrackedAppEntity>>(emptyList())
    val trackedAppsFlow: StateFlow<List<TrackedAppEntity>> = _trackedApps



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

    // ------------------- 앱 사용 데이터 -------------------
    private val repository = UsageRepository

    val appUsageListFlow: StateFlow<List<AppUsage>> = repository.appUsageListFlow
    val dailyUsageListFlow: StateFlow<List<DailyUsage>> = repository.dailyUsageListFlow
    val notificationSettingsFlow: StateFlow<NotificationSettings?> = repository.notificationSettingsFlow
    private val _dailyUsageList = MutableStateFlow<List<DailyUsage>>(emptyList())


    val totalUsageFlow: StateFlow<Pair<Int, Int>> =
        dailyUsageListFlow.combine(trackedPackagesFlow) { dailies, tracked ->
            val totalUsed = dailies.flatMap { it.appUsages.entries }
                .filter { tracked.isEmpty() || it.key in tracked }
                .sumOf { it.value }

            totalUsed
        }.combine(_overallGoalMinutes) { used, goal ->
            used to (goal ?: 0)
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0 to (_overallGoalMinutes.value ?: 0))

    // ------------------- 선택된 필터 -------------------
    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilterTextFlow: StateFlow<String> = combine(_selectedFilter, appUsageListFlow) { pkg, apps ->
        if (pkg == null) "전체" else apps.find { it.packageName == pkg }?.appLabel ?: "전체"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "전체")

    // ------------------- 선택된 달 -------------------
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonthFlow: StateFlow<Int> = _selectedMonth

    // ------------------- 초기화 -------------------
    init {
        auth.addAuthStateListener(authListener)

        viewModelScope.launch(Dispatchers.IO) {
            loadTrackedAppsSync()

            val uid = auth.currentUser?.uid ?: return@launch
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
            val today = sdf.format(Date())

            // repository의 dailyUsageListFlow를 업데이트
            val dailyUsages = UsageRepository.getDailyUsageList(uid, today, today)
            UsageRepository.updateDailyUsageList(dailyUsages)
        }

        loadOverallGoal()
        refreshData()
    }

    // ------------------- SharedPreferences 로드 -------------------


    private fun loadOverallGoal() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1️⃣ DB에서 앱별 목표시간 불러오기
                val appsFromDb = UsageRepository.getAllTrackedOnce()
                _trackedApps.value = appsFromDb
                _trackedPackages.value = appsFromDb.map { it.packageName }.toSet()

                // 2️⃣ 총 목표시간 계산
                val totalGoal = appsFromDb.sumOf { it.goalTime }
                _overallGoalMinutes.value = totalGoal

                // 3️⃣ 필요하다면 SharedPreferences에도 저장 (선택)
                goalPrefs.edit().putInt("overall_goal_minutes", totalGoal).apply()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Failed to load overall goal: $e")
            }
        }
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

    fun setGoalTimes(goals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateGoalTimes(goals)
        refreshData()
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

    private fun getCurrentUserUid(): String? = auth.currentUser?.uid

    fun logout() = auth.signOut()

    fun onGoogleLoginSuccess(idToken: String) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
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

    // ------------------- 데이터 로딩 -------------------
    fun refreshData() {
        val uid = getCurrentUserUid() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.loadRealData(getApplication(), uid)
        }
    }

    fun saveNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        repository.updateNotificationSettings(settings)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    // ------------------- 트래킹 앱 업데이트 -------------------UsageRepository.getAllTrackedOnce()
    suspend fun loadTrackedAppsSync() {
        val apps = UsageRepository.getAllTrackedOnce() // suspend 아님
        _trackedApps.value = apps
    }
    fun saveTrackedAppsWithGoals(selectedAppsWithGoals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1️⃣ 기존 DB에 저장된 모든 앱 가져오기
            val currentTracked = UsageRepository.getAllTrackedOnce()

            // 2️⃣ 삭제할 앱 = 기존 DB에 있는데 선택 목록에 없는 앱
            val toDelete = currentTracked.filter { it.packageName !in selectedAppsWithGoals.keys }
            toDelete.forEach { UsageRepository.deleteTrackedApp(it.packageName) }

            // 3️⃣ 추가/업데이트할 앱 = 선택된 앱 + 목표시간
            val toSave = selectedAppsWithGoals.map { (pkg, goal) ->
                TrackedAppEntity(packageName = pkg, goalTime = goal)
            }

            // 4️⃣ 저장 (업데이트/삽입)
            UsageRepository.updateTrackedApps(toSave)
            _trackedPackages.value = selectedAppsWithGoals.keys

            // 5️⃣ Repository의 currentGoals 갱신
            repository.updateGoalTimes(selectedAppsWithGoals)

            // 6️⃣ 최신 데이터 불러오기 & Flow 직접 emit
            val latestTracked = UsageRepository.getAllTrackedOnce()
            _trackedApps.value = latestTracked.map { tracked ->
                val goal = selectedAppsWithGoals[tracked.packageName] ?: tracked.goalTime
                tracked.copy(goalTime = goal)
            }

            // 🔹 전체 목표시간 항상 앱별 합계로 갱신
            val totalGoal = _trackedApps.value.sumOf { it.goalTime }
            _overallGoalMinutes.value = totalGoal

            // 7️⃣ 로그
            _trackedApps.value.forEach {
                Log.d("DBCheck", "Saved Package: ${it.packageName}, GoalTime: ${it.goalTime}")
            }

        } catch (e: Exception) {
            Log.e("SharedViewModel", "Failed to save tracked apps with goals: $e")
        }
    }


    fun uploadDailyUsageToFirebase() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val today = sdf.format(Date())

        val appUsagesMap = _trackedApps.value.associate { it.packageName to it.goalTime }

        val data = mapOf(
            "date" to today,
            "appUsages" to appUsagesMap
        )

        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("dailyRecords")
            .document(today)
            .set(data)
    }

    // ------------------- 개별 앱 목표 시간 업데이트 -------------------

    fun dumpGoalTimesToLog() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val allTracked = UsageRepository.getAllTrackedOnce() // suspend 함수이므로 launch 안에서 호출
            allTracked.forEach {
                Log.d("DBCheck", "Package: ${it.packageName}, GoalTime: ${it.goalTime}")
            }
        } catch (e: Exception) {
            Log.e("DBCheck", "Failed to fetch tracked apps: $e")
        }
    }

}
