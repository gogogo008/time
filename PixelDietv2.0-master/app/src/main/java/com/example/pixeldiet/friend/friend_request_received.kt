package com.example.pixeldiet.friend

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_request_received")
data class FriendRequestReceived(
    @PrimaryKey val uid: String,
    val name: String,
    val photoUrl: String? = null
)
