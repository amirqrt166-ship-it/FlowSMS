package app.flowsms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var p: Palette
    private lateinit var list: ListView
    private lateinit var search: EditText
    private lateinit var banner: TextView
    private lateinit var empty: TextView

    private var all = listOf<Conv>()
    private var shown = listOf<Conv>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { load() }
    }

    private fun granted(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val want = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) want.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = want.filter { !granted(it) }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Palette(this)
        styleBars(p)
        setContentView(buildUi())
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        Store.clearNames()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        load()
    }

    override fun onPause() {
        contentResolver.unregisterContentObserver(observer)
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) = load()

    // ───────── UI ─────────

    private fun buildUi(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }

        col.addView(TextView(this).apply {
            text = getString(R.string.app_name); textSize = 28f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(p.text)
            setPadding(dp(20), dp(14), dp(20), dp(4))
        })

        search = EditText(this).apply {
            hint = getString(R.string.search_hint); textSize = 15f
            setTextColor(p.text); setHintTextColor(p.muted); setSingleLine()
            setPadding(dp(18), 0, dp(18), 0)
            background = shape(p.surface, dp(22).toFloat())
            onChange { filter() }
        }
        col.addView(search, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(16), dp(6), dp(16), dp(8)) })

        banner = TextView(this).apply {
            text = getString(R.string.set_default); textSize = 14f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = shape(p.accent, dp(16).toFloat())
            visibility = View.GONE
            setOnClickListener { askDefaultSms() }
        }
        col.addView(banner, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(8)) })

        list = ListView(this).apply {
            divider = null; dividerHeight = 0
            setSelector(android.R.color.transparent)
            clipToPadding = false; setPadding(0, 0, 0, dp(90))
            adapter = ad
            setOnItemClickListener { _, _, pos, _ ->
                val c = shown[pos]
                startActivity(Intent(this@MainActivity, ThreadActivity::class.java)
                    .putExtra("address", c.address).putExtra("thread", c.thread))
            }
            setOnItemLongClickListener { _, _, pos, _ -> rowMenu(shown[pos]); true }
        }

        empty = TextView(this).apply {
            textSize = 15f; setTextColor(p.muted); gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setOnClickListener { if (!granted(Manifest.permission.READ_SMS)) askPermissions() }
        }

        val fab = TextView(this).apply {
            text = "+"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = circle(p.accent); elevation = dp(6).toFloat()
            setOnClickListener { startActivity(Intent(this@MainActivity, ThreadActivity::class.java)) }
        }

        val body = FrameLayout(this)
        body.addView(list, FrameLayout.LayoutParams(-1, -1))
        body.addView(empty, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        body.addView(fab, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM or Gravity.END)
            .apply { setMargins(dp(20), dp(20), dp(20), dp(24)) })
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return col
    }

    private val ad = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(i: Int) = shown[i]
        override fun getItemId(i: Int) = shown[i].thread
        override fun getView(i: Int, v: View?, parent: ViewGroup): View {
            val row = (v as? ConvRow) ?: ConvRow()
            row.bind(shown[i])
            return row
        }
    }

    inner class ConvRow : LinearLayout(this@MainActivity) {
        private val av = TextView(context).apply {
            gravity = Gravity.CENTER; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        private val nameT = TextView(context).apply {
            textSize = 16f; setSingleLine(); ellipsize = TextUtils.TruncateAt.END
        }
        private val timeT = TextView(context).apply { textSize = 12f; setTextColor(p.muted) }
        private val snip = TextView(context).apply {
            textSize = 14f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        private val badge = TextView(context).apply {
            textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = shape(p.accent, dp(11).toFloat()); setPadding(dp(7), 0, dp(7), 0); minWidth = dp(22)
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundResource(rippleRes())
            addView(av, LayoutParams(dp(48), dp(48)))
            val col = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(14), 0, dp(10), 0) }
            val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(nameT, LayoutParams(0, -2, 1f))
            top.addView(timeT, LayoutParams(-2, -2))
            col.addView(top)
            col.addView(snip)
            addView(col, LayoutParams(0, -2, 1f))
            addView(badge, LayoutParams(-2, dp(22)))
        }

        fun bind(c: Conv) {
            avatarize(av, c.name, p)
            nameT.text = c.name
            nameT.setTextColor(p.text)
            nameT.typeface = if (c.unread > 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            snip.text = c.snippet
            snip.setTextColor(if (c.unread > 0) p.text else p.muted)
            timeT.text = fmtTime(c.date)
            badge.visibility = if (c.unread > 0) VISIBLE else GONE
            badge.text = if (c.unread > 99) "99+" else c.unread.toString()
        }
    }

    // ───────── داده ─────────

    private fun load() {
        if (!granted(Manifest.permission.READ_SMS)) { all = emptyList(); filter(); return }
        Thread {
            val r = Store.conversations(this)
            runOnUiThread { all = r; filter() }
        }.start()
    }

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        shown = if (q.isEmpty()) all else all.filter {
            it.name.lowercase().contains(q) || it.address.contains(q) || it.snippet.lowercase().contains(q)
        }
        ad.notifyDataSetChanged()
        val perm = granted(Manifest.permission.READ_SMS)
        empty.setText(if (perm) R.string.empty else R.string.need_perm)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        banner.visibility = if (Store.isDefault(this)) View.GONE else View.VISIBLE
    }

    private fun rowMenu(c: Conv) {
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(arrayOf(getString(R.string.mark_read), getString(R.string.delete))) { _, which ->
                if (which == 0) Thread { Store.markRead(this, c.thread); runOnUiThread { load() } }.start()
                else AlertDialog.Builder(this)
                    .setMessage(R.string.delete_confirm)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Thread { Store.deleteThread(this, c.thread); runOnUiThread { load() } }.start()
                    }
                    .setNegativeButton(R.string.cancel, null).show()
            }.show()
    }
}
