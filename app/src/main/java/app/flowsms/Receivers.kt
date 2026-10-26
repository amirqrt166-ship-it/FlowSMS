package app.flowsms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony

/** دریافت پیامک (فقط وقتی برنامه‌ی پیش‌فرض است) */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
        if (msgs.isEmpty()) return
        val byAddr = LinkedHashMap<String, StringBuilder>()
        for (m in msgs) {
            val a = m.originatingAddress ?: continue
            byAddr.getOrPut(a) { StringBuilder() }.append(m.messageBody ?: "")
        }
        for ((addr, sb) in byAddr) {
            val body = sb.toString()
            try {
                c.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                    put("address", addr); put("body", body)
                    put("date", System.currentTimeMillis()); put("date_sent", msgs[0].timestampMillis)
                    put("read", 0); put("seen", 0)
                })
            } catch (_: Exception) {}
            Store.notifyIncoming(c, addr, body)
        }
    }
}

/** MMS فعلاً پشتیبانی نمی‌شود؛ گیرنده فقط برای واجد شرایط بودن به‌عنوان برنامه‌ی پیش‌فرض است */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {}
}

/** نتیجه‌ی ارسال: sent یا failed */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val uri = i.data ?: return
        val type = if (resultCode == Activity.RESULT_OK) 2 else 5
        try { c.contentResolver.update(uri, ContentValues().apply { put("type", type) }, null, null) }
        catch (_: Exception) {}
    }
}

/** «پاسخ با پیام» هنگام رد کردن تماس */
class RespondService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val to = i?.data?.schemeSpecificPart?.substringBefore('?')?.trim()
        val text = i?.getStringExtra(Intent.EXTRA_TEXT)
        if (!to.isNullOrEmpty() && !text.isNullOrBlank()) Thread { Store.send(this, to, text) }.start()
        stopSelf()
        return START_NOT_STICKY
    }
}
