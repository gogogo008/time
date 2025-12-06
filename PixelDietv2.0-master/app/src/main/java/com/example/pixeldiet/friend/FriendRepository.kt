

package com.example.pixeldiet.friend

import com.example.pixeldiet.data.FriendDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FriendRepository(
    private val dao: FriendDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ---------------------------
    // 친구 목록
    // ---------------------------
    suspend fun loadFriends() {
        val uid = auth.currentUser?.uid ?: return
        // Firebase에서 친구 목록 가져오기
        val firebaseFriends = firestore.collection("users")
            .document(uid)
            .collection("friends")
            .get()
            .await()
            .map { doc ->
                FriendRecord(
                    uid = doc.getString("uid") ?: "",
                    name = doc.getString("name") ?: "친구",
                    photoUrl = doc.getString("photoUrl")
                )
            }
        // Room에 저장
        dao.addFriends(firebaseFriends)
    }

    suspend fun getAllFriends(): List<FriendRecord> = dao.getAllFriends()

    suspend fun addFriend(friend: FriendRecord) {
        dao.addFriend(friend)
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(uid)
            .collection("friends")
            .document(friend.uid)
            .set(friend.toMap())
            .await()
    }

    suspend fun removeFriend(uid: String) {
        dao.removeFriend(uid)
        val currentUid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(currentUid)
            .collection("friends")
            .document(uid)
            .delete()
            .await()
    }

    // ---------------------------
    // 받은 친구 요청
    // ---------------------------
    suspend fun loadFriendRequestsReceived() {
        val uid = auth.currentUser?.uid ?: return
        val requests = firestore.collection("users")
            .document(uid)
            .collection("friendRequestsReceived")
            .get()
            .await()
            .map { doc ->
                FriendRecord(
                    uid = doc.getString("uid") ?: "",
                    name = doc.getString("name") ?: "친구",
                    photoUrl = doc.getString("photoUrl")
                )
            }
        dao.addFriendRequestsReceived(requests)
    }

    suspend fun getFriendRequestsReceived(): List<FriendRecord> =
        dao.getFriendRequestsReceived()

    suspend fun removeFriendRequestReceived(uid: String) {
        dao.removeFriendRequestReceived(uid)
        val currentUid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(currentUid)
            .collection("friendRequestsReceived")
            .document(uid)
            .delete()
            .await()
    }

    // ---------------------------
    // 보낸 친구 요청
    // ---------------------------
    suspend fun loadFriendRequestsSent() {
        val uid = auth.currentUser?.uid ?: return
        val requests = firestore.collection("users")
            .document(uid)
            .collection("friendRequestsSent")
            .get()
            .await()
            .map { doc ->
                FriendRecord(
                    uid = doc.getString("uid") ?: "",
                    name = doc.getString("name") ?: "친구",
                    photoUrl = doc.getString("photoUrl")
                )
            }
        dao.addFriendRequestsSent(requests)
    }

    suspend fun getFriendRequestsSent(): List<FriendRecord> =
        dao.getFriendRequestsSent()

    suspend fun removeFriendRequestSent(uid: String) {
        dao.removeFriendRequestSent(uid)
        val currentUid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(currentUid)
            .collection("friendRequestsSent")
            .document(uid)
            .delete()
            .await()
    }

    // ---------------------------
    // FriendRecord -> Map 변환
    // ---------------------------
    private fun FriendRecord.toMap() = mapOf(
        "uid" to uid,
        "name" to name,
        "photoUrl" to photoUrl
    )
}

