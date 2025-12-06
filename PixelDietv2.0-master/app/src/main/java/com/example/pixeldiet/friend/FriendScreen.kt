package com.example.pixeldiet.ui.friend

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.FriendRepository
import com.example.pixeldiet.friend.FriendViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job


@Composable
fun FriendScreen(
    context: Context = LocalContext.current
) {
    val db = DatabaseProvider.getDatabase(context)
    val repository = FriendRepository(
        db.friendDao(),
        FirebaseFirestore.getInstance(),
        FirebaseAuth.getInstance()
    )
    val viewModel: FriendViewModel = viewModel(
        factory = FriendViewModelFactory(repository)
    )

    FriendScreenContent(viewModel)   // ← 기존 UI 코드
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreenContent(viewModel: FriendViewModel) {

    val friendList by viewModel.friendList.collectAsState()
    val friendRequestsReceived by viewModel.friendRequestsReceived.collectAsState()
    val friendRequestsSent by viewModel.friendRequestsSent.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ------------------------------------
        // 1. 친구 리스트
        // ------------------------------------
        item {
            Text("내 친구들", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (friendList.isEmpty()) {
            item {
                Text(
                    "아직 친구가 없습니다.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            items(friendList) { friend ->
                FriendCard(
                    friend = friend,
                    onRemove = { viewModel.removeFriend(friend.uid) }
                )
            }
        }

        // ------------------------------------
        // 2. 친구 추가 버튼
        // ------------------------------------
        item {
            Button(
                onClick = { viewModel.openAddDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("친구 추가")
            }
        }

        // ------------------------------------
        // 3. 받은 친구 요청
        // ------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {

                    Text(
                        "받은 친구 요청",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (friendRequestsReceived.isEmpty()) {
                        Text(
                            "받은 요청이 없습니다.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        friendRequestsReceived.forEach { request ->

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(request.name, fontSize = 16.sp)

                                Row {
                                    // 수락 버튼 → Repository.acceptFriendRequest 호출할 함수 필요함
                                    Button(
                                        onClick = {
                                            // Repository에서 acceptFriendRequest 구현 후 ViewModel 래핑 필요
                                            // 예: viewModel.acceptFriendRequest(request.uid)
                                            viewModel.addFriend(request)
                                            viewModel.removeFriendRequestReceived(request.uid)
                                        }
                                    ) {
                                        Text("수락")
                                    }
                                }
                            }

                            Divider()
                        }
                    }
                }
            }
        }

        // ------------------------------------
        // 4. 보낸 친구 요청
        // ------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {

                    Text(
                        "보낸 친구 요청",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (friendRequestsSent.isEmpty()) {
                        Text(
                            "보낸 요청이 없습니다.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        friendRequestsSent.forEach { request ->

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(request.name, fontSize = 16.sp)

                                Button(
                                    onClick = {
                                        viewModel.removeFriendRequestSent(request.uid)
                                    }
                                ) {
                                    Text("취소")
                                }
                            }

                            Divider()
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------
    // 5. 친구 추가 Dialog
    // ------------------------------------
    if (showAddDialog) {
        AddFriendDialog(
            onConfirm = { uid ->
                // ViewModel에 sendFriendRequest 메소드 추가했으면 이렇게 사용
                // viewModel.sendFriendRequest(uid)

                viewModel.closeAddDialog()
            },
            onDismiss = { viewModel.closeAddDialog() }
        )
    }
}

@Composable
fun FriendCard(friend: FriendRecord, onRemove: () -> Unit) {  // Job이 아니라 Unit 사용
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                if (!friend.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = friend.photoUrl,
                        contentDescription = "친구 프로필",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "기본 프로필",
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(friend.name, fontSize = 16.sp)
        }

        TextButton(onClick = onRemove) {
            Text("삭제")
        }
    }
    Divider()
}


