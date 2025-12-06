package com.example.pixeldiet.backup

// BackupManager.kt
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class BackupManager {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** 현재 UID 가져오기 */
    fun currentUserId(): String = auth.currentUser?.uid ?: "anonymous"

    /** 익명 로그인 (앱 최초 실행 시) */
    suspend fun initUser() {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Google 로그인 + 익명 데이터 병합 */
    suspend fun signInWithGoogle(idToken: String) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 백업 여부 확인 */
    suspend fun hasBackupData(): Boolean {
        val uid = currentUserId()
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("dailyRecords")
            .get()
            .await()
        return snapshot.documents.isNotEmpty()
    }

    /** DailyUsage 업로드 */
    suspend fun uploadDailyUsage(appUsages: Map<String, Int>, date: String) {
        val uid = currentUserId()
        val data = mapOf(
            "date" to date,
            "appUsages" to appUsages
        )
        try {
            firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .document(date)
                .set(data)
                .await()
            Log.d("BackupManager", "Daily usage uploaded for $date")
        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to upload daily usage", e)
        }
    }

    /** DailyUsage 다운로드 */
    suspend fun downloadDailyUsage(): Map<String, Map<String, Int>> {
        val uid = currentUserId()
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .get()
                .await()

            snapshot.documents.associate { doc ->
                val date = doc.getString("date") ?: ""
                val appUsages = doc.get("appUsages") as? Map<String, Long> ?: emptyMap()
                date to appUsages.mapValues { it.value.toInt() }
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to download daily usage", e)
            emptyMap()
        }
    }
}
