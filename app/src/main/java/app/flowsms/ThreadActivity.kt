package app.flowsms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class ThreadActivity : Activity() {

    private lateinit var p: Palette
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var to: EditText
    private lateinit var toRow: View
    private lateinit var sendBtn: TextView
    private lateinit var title: TextView
    private lateinit var av: TextView
    private lateinit var call: TextView

    private var address: String? = null
    private var thread = 0L
    private var msgs = listOf<Msg>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { load() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Palette(this)
        styleBars(p)
        resolve(intent)
        setContentView(buildUi())
        intent.getStringExtra("sms_body")?.let { input.setText(it) }
        refreshHeader()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i == null) return
        setIntent(i)
        msgs = emptyList(); ad.notifyDataSetChanged()
        resolve(i); refreshHeader(); load()
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        address?.let { getSystemService(NotificationManager::class.java).cancel(it.hashCode()) }
        load()
    }

    override fun onPause() {
        contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    private fun resolve(i: Intent) {
        thread = i.getLongExtra("thread", 0L)
        address = i.getStringExtra("address")
            ?: i.data?.schemeSpecificPart?.substringBefore('?')?.split(';', ',')?.firstOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ───────── UI ─────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }

        // هدر
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = backGlyph(); textSize = 32f; gravity = Gravity.CENTER; setTextColor(p.text)
            setBackgroundResource(rippleRes()); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        av = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(av, LinearLayout.LayoutParams(dp(38), dp(38)))
        title = TextView(this).apply {
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(p.text); setSingleLine()
            setPadding(dp(12), 0, dp(8), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        call = TextView(this).apply {
            text = "📞"; textSize = 20f; gravity = Gravity.CENTER
            setBackgroundResource(rippleRes())
            setOnClickListener { address?.let { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(it)))) } }
        }
        header.addView(call, LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(header)

        // گیرنده (فقط برای پیام جدید)
        to = EditText(this).apply {
            hint = getString(R.string.to_hint); textSize = 15f
            setTextColor(p.text); setHintTextColor(p.muted); setSingleLine()
            inputType = InputType.TYPE_CLASS_PHONE
            setPadding(dp(18), 0, dp(18), 0)
            background = shape(p.surface, dp(22).toFloat())
        }
        val pick = TextView(this).apply {
            text = "👤"; textSize = 20f; gravity = Gravity.CENTER
            background = circle(p.surface)
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 5)
            }
        }
        toRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
            addView(to, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(pick, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) })
        }
        root.addView(toRow)

        // پیام‌ها
        list = ListView(this).apply {
            divider = null; dividerHeight = 0
            setSelector(android.R.color.transparent)
            stackFromBottom = true
            transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL
            clipToPadding = false; setPadding(0, dp(6), 0, dp(6))
            adapter = ad
            setOnItemLongClickListener { _, _, pos, _ -> msgMenu(msgs[pos]); true }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        // نوار نوشتن
        input = EditText(this).apply {
            hint = getString(R.string.type_message); textSize = 16f
            setTextColor(p.text); setHintTextColor(p.muted)
            background = shape(p.surface, dp(24).toFloat())
            setPadding(dp(18), dp(10), dp(18), dp(10)); minHeight = dp(46); maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            onChange { updateSend() }
        }
        sendBtn = TextView(this).apply {
            text = "↑"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = circle(p.accent); setOnClickListener { send() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            setPadding(dp(10), dp(6), dp(10), dp(8))
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(sendBtn, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) })
        }
        root.addView(bar)
        updateSend()
        return root
    }

    private fun updateSend() { sendBtn.alpha = if (input.text.isBlank()) 0.4f else 1f }

    private fun refreshHeader() {
        val a = address
        toRow.visibility = if (a == null) View.VISIBLE else View.GONE
        av.visibility = if (a == null) View.GONE else View.VISIBLE
        call.visibility = if (a == null) View.GONE else View.VISIBLE
        if (a == null) { title.setText(R.string.new_message); return }
        title.text = a
        Thread {
            val n = Store.name(this, a)
            runOnUiThread { title.text = n; avatarize(av, n, p) }
        }.start()
    }

    // ───────── لیست پیام‌ها ─────────

    private val ad = object : BaseAdapter() {
        override fun getCount() = msgs.size
        override fun getItem(i: Int) = msgs[i]
        override fun getItemId(i: Int) = msgs[i].id
        override fun getView(i: Int, v: View?, parent: ViewGroup): View {
            val row = (v as? Bubble) ?: Bubble()
            row.bind(msgs[i])
            return row
        }
    }

    private fun bubbleShape(out: Boolean): GradientDrawable {
        val r = dp(20).toFloat(); val s = dp(5).toFloat()
        val tailRight = out != isRtl()
        val br = if (tailRight) s else r
        val bl = if (tailRight) r else s
        return GradientDrawable().apply {
            setColor(if (out) p.accent else p.inBubble)
            cornerRadii = floatArrayOf(r, r, r, r, br, br, bl, bl)
        }
    }

    inner class Bubble : LinearLayout(this@ThreadActivity) {
        private val tv = TextView(context).apply {
            textSize = 16f; setPadding(dp(14), dp(9), dp(14), dp(9))
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        }
        private val meta = TextView(context).apply { textSize = 11f; setPadding(dp(6), dp(2), dp(6), 0) }

        init {
            orientation = VERTICAL
            setPadding(dp(12), dp(3), dp(12), dp(3))
            addView(tv, LayoutParams(-2, -2))
            addView(meta, LayoutParams(-2, -2))
        }

        fun bind(m: Msg) {
            val out = m.type != 1
            val g = if (out) Gravity.END else Gravity.START
            (tv.layoutParams as LayoutParams).gravity = g
            (meta.layoutParams as LayoutParams).gravity = g
            tv.text = m.body
            tv.setTextColor(if (out) Color.WHITE else p.text)
            tv.background = bubbleShape(out)
            var t = fmtTime(m.date)
            when (m.type) {
                5 -> t += " · " + getString(R.string.failed)
                4, 6 -> t += " · " + getString(R.string.sending)
            }
            meta.text = t
            meta.setTextColor(if (m.type == 5) p.error else p.muted)
        }
    }

    // ───────── منطق ─────────

    private fun load() {
        val a = address
        Thread {
            if (thread == 0L && a != null) thread = Store.threadOf(this, a)
            val t = thread
            val r = if (t != 0L) Store.messages(this, t) else emptyList()
            runOnUiThread { msgs = r; ad.notifyDataSetChanged() }
            if (r.any { it.type == 1 && !it.read }) Store.markRead(this, t)
        }.start()
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val dest = address ?: to.text.toString().trim()
        if (dest.isEmpty()) { Toast.makeText(this, R.string.enter_number, Toast.LENGTH_SHORT).show(); return }
        if (!Store.isDefault(this)) {
            Toast.makeText(this, R.string.default_needed, Toast.LENGTH_LONG).show()
            askDefaultSms(); return
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 2); return
        }
        input.setText("")
        Thread {
            val ok = Store.send(this, dest, text)
            val t = Store.threadOf(this, dest)
            runOnUiThread {
                if (!ok) Toast.makeText(this, R.string.send_failed, Toast.LENGTH_SHORT).show()
                if (address == null) { address = dest; thread = t; refreshHeader() }
                load()
            }
        }.start()
    }

    private fun msgMenu(m: Msg) {
        val items = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        items.add(getString(R.string.copy))
        acts.add {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("sms", m.body))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        val a = address
        if (m.type == 5 && a != null) {
            items.add(getString(R.string.resend))
            acts.add {
                Thread { Store.send(this, a, m.body, Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, m.id.toString())) }.start()
            }
        }
        items.add(getString(R.string.delete))
        acts.add {
            Thread {
                try { contentResolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, m.id.toString()), null, null) }
                catch (_: Exception) {}
            }.start()
        }
        AlertDialog.Builder(this).setItems(items.toTypedArray()) { _, i -> acts[i]() }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 5 && res == RESULT_OK) {
            data?.data?.let { u ->
                contentResolver.query(u, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
                    ?.use { if (it.moveToFirst()) to.setText(it.getString(0)) }
            }
        }
    }
}
