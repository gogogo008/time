package com.example.pixeldiet.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.MainActivity
import com.example.pixeldiet.R
import com.example.pixeldiet.data.AppDatabase
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val viewModel by viewModels<SharedViewModel>() // ✅ ViewModel 초기화
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var backupManager: BackupManager

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(Exception::class.java)
                val idToken = account.idToken
                    ?: throw Exception("idToken is null")

                lifecycleScope.launch {
                    try {
                        // 1️⃣ Firebase Google 로그인
                        backupManager.signInWithGoogle(idToken)

                        // 2️⃣ Firebase UID 사용 (중요)
                        val firebaseUser = FirebaseAuth.getInstance().currentUser
                            ?: throw Exception("Firebase user is null")

                        val uid = firebaseUser.uid

                        // 3️⃣ UID 저장
                        getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("uid", uid)
                            .apply()

                        // 4️⃣ Firestore → Room 동기화
                        backupManager.syncFromFirestore(viewModel)

                        Toast.makeText(
                            this@LoginActivity,
                            "구글 로그인 완료",
                            Toast.LENGTH_SHORT
                        ).show()

                        goToMain()

                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Google login flow failed", e)
                        Toast.makeText(
                            this@LoginActivity,
                            e.message ?: "구글 로그인 실패",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("LoginActivity", "Google sign-in failed", e)
                Toast.makeText(
                    this@LoginActivity,
                    "구글 로그인 실패",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val db = AppDatabase.getInstance(applicationContext)
        backupManager = BackupManager(
            userDao = db.userProfileDao(),
            trackedAppDao = db.trackedAppDao(),
            dailyUsageDao = db.dailyUsageDao(),
            groupDao = db.groupDao(),
            friendDao = db.friendDao()
        )

        val btnGuest = findViewById<Button>(R.id.btnGuest)
        val btnGoogle = findViewById<Button>(R.id.btnGoogle)

        // Google Sign-In 옵션
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 게스트 로그인 (권장: Firebase anonymous)
        btnGuest.setOnClickListener {
            lifecycleScope.launch {
                try {
                    backupManager.initUser() // 내부에서 signInAnonymously 권장

                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                        ?: "guest_${System.currentTimeMillis()}"

                    getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("uid", uid)
                        .apply()

                    Toast.makeText(this@LoginActivity, "게스트 로그인 완료", Toast.LENGTH_SHORT).show()
                    goToMain()

                } catch (e: Exception) {
                    Log.e("LoginActivity", "Guest login failed", e)
                    Toast.makeText(this@LoginActivity, "로그인 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 구글 로그인 버튼
        btnGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
