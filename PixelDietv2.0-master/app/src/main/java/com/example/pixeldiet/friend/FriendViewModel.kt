package com.example.pixeldiet.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FriendViewModel(private val repository: FriendRepository) : ViewModel() {

    private val _friendList = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friendList: StateFlow<List<FriendRecord>> = _friendList

    private val _friendRequestsReceived = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friendRequestsReceived: StateFlow<List<FriendRecord>> = _friendRequestsReceived

    private val _friendRequestsSent = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friendRequestsSent: StateFlow<List<FriendRecord>> = _friendRequestsSent

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    init {
        refreshAll()
    }

    fun refreshAll() = viewModelScope.launch {
        repository.loadFriends()
        repository.loadFriendRequestsReceived()
        repository.loadFriendRequestsSent()

        _friendList.value = repository.getAllFriends()
        _friendRequestsReceived.value = repository.getFriendRequestsReceived()
        _friendRequestsSent.value = repository.getFriendRequestsSent()
    }

    fun addFriend(friend: FriendRecord) = viewModelScope.launch {
        repository.addFriend(friend)
        refreshAll()
    }

    fun removeFriend(uid: String) = viewModelScope.launch {
        repository.removeFriend(uid)
        refreshAll()
    }

    fun removeFriendRequestReceived(uid: String) = viewModelScope.launch {
        repository.removeFriendRequestReceived(uid)
        refreshAll()
    }

    fun removeFriendRequestSent(uid: String) = viewModelScope.launch {
        repository.removeFriendRequestSent(uid)
        refreshAll()
    }

    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }
}