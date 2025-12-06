package com.example.pixeldiet.friend

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_request_sent")
data class FriendRequestSent(
    @PrimaryKey val uid: String,
    val name: String,
    val photoUrl: String? = null
)