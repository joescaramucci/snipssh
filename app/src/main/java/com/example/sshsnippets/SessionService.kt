package com.example.sshsnippets

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Servizio in primo piano attivo finché c'è almeno una sessione aperta. Con la notifica fissa
 * Android non congela l'app in background, quindi le connessioni SSH e SFTP restano vive.
 * Non contiene le connessioni: quelle restano in [OpenSessions], nello stesso processo.
 */
class SessionService : Service() {

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_COUNT = "count"
        private const val ACTION_DISCONNECT_ALL = "com.example.sshsnippets.DISCONNECT_ALL"

        /** Avvia, aggiorna o ferma il servizio in base al numero di sessioni aperte. */
        fun update(context: Context, count: Int) {
            val intent = Intent(context, SessionService::class.java).putExtra(EXTRA_COUNT, count)
            if (count > 0) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            // onStartCommand gira sul thread principale, come le altre operazioni su OpenSessions.
            OpenSessions.list.toList().forEach { OpenSessions.close(it) }
            stopSelf()
            return START_NOT_STICKY
        }
        val count = intent?.getIntExtra(EXTRA_COUNT, 1) ?: 1
        val notification = buildNotification(count)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Se il processo viene chiuso le connessioni sono perse comunque: non ha senso far ripartire il servizio.
        return START_NOT_STICKY
    }

    private fun buildNotification(count: Int): android.app.Notification {
        val context = localizedContext(this, Store(this).appLanguage())
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, SessionService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SnipSSH")
            .setContentText(context.getString(R.string.notif_sessions, count))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_disconnect_all), disconnect)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
