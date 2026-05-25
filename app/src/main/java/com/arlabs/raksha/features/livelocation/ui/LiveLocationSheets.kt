package com.arlabs.raksha.features.livelocation.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arlabs.raksha.services.LiveLocationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareChoiceSheet(
    onDismiss: () -> Unit,
    onCurrentLocationSelected: () -> Unit,
    onLiveLocationSelected: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2D1B3D)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).padding(bottom = 24.dp)
        ) {
            Text("Share Location", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Choose how you want to share your location with others.", color = Color.LightGray)
            Spacer(modifier = Modifier.height(24.dp))
            
            ListItem(
                headlineContent = { Text("Share Current Location", color = Color.White) },
                supportingContent = { Text("Send a static Google Maps link of where you are right now.", color = Color.Gray) },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF00E5FF)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onCurrentLocationSelected() }
            )
            Divider(color = Color.White.copy(alpha = 0.1f))
            ListItem(
                headlineContent = { Text("Share Live Location", color = Color.White, fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Share a link that tracks your movement in real-time.", color = Color(0xFFFF9800)) },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFf40c5c)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onLiveLocationSelected() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationDurationSheet(
    context: Context,
    onDismiss: () -> Unit,
    viewModel: LiveLocationViewModel = hiltViewModel()
) {
    val durations = listOf(
        Pair("15 Minutes", 15),
        Pair("1 Hour", 60),
        Pair("2 Hours", 120),
        Pair("4 Hours", 240),
        Pair("8 Hours", 480)
    )

    var isLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2D1B3D)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).padding(bottom = 24.dp)
        ) {
            Text("Live Location Duration", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("How long would you like to share your live location?", color = Color.LightGray)
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFf40c5c))
                }
            } else {
                durations.forEach { (label, minutes) ->
                    ListItem(
                        headlineContent = { Text(label, color = Color.White) },
                        leadingContent = { Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFf40c5c)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            isLoading = true
                            viewModel.startLiveLocationSession(minutes) { sessionId, expiresAt ->
                                LiveLocationService.start(context, sessionId, expiresAt)
                                val shareLink = "https://raksha-97818.web.app/live/$sessionId"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "My Live Location \u2014 Raksha")
                                    putExtra(Intent.EXTRA_TEXT, "I am sharing my live location with you securely on Raksha. Track me in real-time here:\n$shareLink")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Live Location via"))
                                onDismiss()
                            }
                        }
                    )
                    Divider(color = Color.White.copy(alpha = 0.1f))
                }
            }
        }
    }
}
