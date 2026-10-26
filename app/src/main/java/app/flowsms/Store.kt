package app.flowsms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import java.util.concurrent.ConcurrentHashMap

data class Conv(val thread: Long, val address: String, var name: String,
                val snippet: String, val date: Long, var unread: Int)

data class Msg(val id: Long, val body: String, val date: Long, val type: Int, val read: Boolean)

object Store {
    private const val CH = "msgs"
    private val names = ConcurrentHashMap<String, String>()

    fun clearNames() = names.clear()

    fun isDefault(c: Context) = Telephony.Sms.getDefaultSmsPackage(c) == c.packageName

    fun name(c: Context, addr: String): String {
        names[addr]?.let { return it }
        var n = addr
        if (addr.none { it.isLetter() } && addr.any { it.isDigit() }) {
            try {
                val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(addr))
                c.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) n = it.getString(0) ?: addr }
            } catch (_: Exception) {}
        }
        names[addr] = n
        return n
    }

    /** لیست گفتگوها: آخرین پیام هر thread */
    fun conversations(c: Context): List<Conv> {
        val out = LinkedHashMap<Long, Conv>()
        try {
            c.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("thread_id", "address", "body", "date", "read", "type"),
                null, null, "date DESC"
            )?.use { q ->
                var n = 0
                while (q.moveToNext() && n++ < 6000) {
                    val t = q.getLong(0)
                    val addr = q.getString(1) ?: continue
                    val unread = q.getInt(4) == 0 && q.getInt(5) == 1
                    val cur = out[t]
                    if (cur == null) out[t] = Conv(t, addr, addr, q.getString(2) ?: "", q.getLong(3), if (unread) 1 else 0)
                    else if (unread) cur.unread++
                }
            }
        } catch (_: SecurityException) {}
        out.values.forEach { it.name = name(c, it.address) }
        return out.values.toList()
    }

    fun messages(c: Context, thread: Long): List<Msg> {
        val out = ArrayList<Msg>()
        try {
            c.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf("_id", "body", "date", "type", "read"),
                "thread_id=?", arrayOf(thread.toString()), "date ASC"
            )?.use { q ->
                while (q.moveToNext())
                    out.add(Msg(q.getLong(0), q.getString(1) ?: "", q.getLong(2), q.getInt(3), q.getInt(4) == 1))
            }
        } catch (_: SecurityException) {}
        return out
    }

    fun threadOf(c: Context, addr: String): Long =
        try { Telephony.Threads.getOrCreateThreadId(c, addr) } catch (_: Exception) { 0L }

    fun markRead(c: Context, thread: Long) {
        if (thread == 0L) return
        try {
            c.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply { put("read", 1); put("seen", 1) },
                "thread_id=? AND read=0", arrayOf(thread.toString())
            )
        } catch (_: Exception) {}
    }

    fun deleteThread(c: Context, thread: Long) {
        try { c.contentResolver.delete(Telephony.Sms.CONTENT_URI, "thread_id=?", arrayOf(thread.toString())) }
        catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    fun send(c: Context, to: String, body: String, existing: Uri? = null): Boolean {
        var uri: Uri? = existing
        return try {
            if (uri == null) {
                uri = c.contentResolver.insert(Telephony.Sms.CONTENT_URI, ContentValues().apply {
                    put("address", to); put("body", body)
                    put("date", System.currentTimeMillis()); put("read", 1); put("type", 4)
                }) ?: return false
            } else {
                c.contentResolver.update(uri, ContentValues().apply { put("type", 4) }, null, null)
            }
            val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java)
                     else SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            val pi = PendingIntent.getBroadcast(
                c, (uri!!.lastPathSegment?.toIntOrNull() ?: 0),
                Intent(c, SentReceiver::class.java).setData(uri),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val pis = ArrayList<PendingIntent>().also { l -> parts.forEach { _ -> l.add(pi) } }
            sm.sendMultipartTextMessage(to, null, parts, pis, null)
            true
        } catch (_: Exception) {
            try { uri?.let { c.contentResolver.update(it, ContentValues().apply { put("type", 5) }, null, null) } }
            catch (_: Exception) {}
            false
        }
    }

    @Suppress("DEPRECATION")
    fun notifyIncoming(c: Context, addr: String, body: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(
                NotificationChannel(CH, c.getString(R.string.channel), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            c, addr.hashCode(),
            Intent(c, ThreadActivity::class.java).putExtra("address", addr)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CH) else Notification.Builder(c)
        b.setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(name(c, addr))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
        nm.notify(addr.hashCode(), b.build())
    }
}
