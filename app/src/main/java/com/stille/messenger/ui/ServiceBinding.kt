package com.stille.messenger.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.stille.messenger.service.StilleService
import kotlinx.coroutines.flow.StateFlow

@Composable
fun rememberStilleService(): StilleService? {
    val context = LocalContext.current
    var service by remember { mutableStateOf<StilleService?>(null) }
    DisposableEffect(context) {
        // The UI may bind before the activity's startup effect; explicitly start the
        // foreground service so BIND_AUTO_CREATE is not relied on for lifecycle work.
        StilleService.start(context)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? StilleService.LocalBinder)?.service
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
        val bound = context.bindService(Intent(context, StilleService::class.java), connection, Context.BIND_AUTO_CREATE)
        onDispose { if (bound) context.unbindService(connection) }
    }
    return service
}

@Composable
fun <T> StateFlow<T>.collectStarted(): State<T> {
    val owner = LocalLifecycleOwner.current
    val result = remember(this) { mutableStateOf(value) }
    LaunchedEffect(this, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { collect { result.value = it } }
    }
    return result
}
