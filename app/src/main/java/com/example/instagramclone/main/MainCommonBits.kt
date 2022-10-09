package com.example.instagramclone.main

import android.app.Notification
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.instagramclone.DestinationScreens
import com.example.instagramclone.IgViewModel

@Composable
fun NotificationMessage(vm: IgViewModel) {
    val notifState = vm.popupNotification.value
    val notifMessage = notifState?.getContentOrNull()

    if (notifMessage != null) {
        Toast.makeText(LocalContext.current, notifMessage, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun CommonProgressSpinner() {
    Row(
        modifier = Modifier
            .alpha(0.5f)
            .background(Color.LightGray)
            .clickable(enabled = false) { }
            .fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
    }
}

fun navigateTo(navController: NavController, dest: DestinationScreens){
    navController.navigate(dest.route){

        // the reason for having these parameters is that , we may have a user that kind of jumps back and forth between multiple screens
        // and we don't want to open the same screen multiple times,
        // so we just need to pop up the back stack to that particular screen and launch it a single time
        // In other words we pop up to that screen but we don't launch a new screen but just use the existing one
        popUpTo(dest.route)
        launchSingleTop = true
    }
}