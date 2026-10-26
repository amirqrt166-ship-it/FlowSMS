package app.flowsms

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.TextView
import java.util.Date

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.isRtl() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
fun Context.backGlyph() = if (isRtl()) "›" else "‹"

fun Context.rippleRes(): Int {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    return tv.resourceId
}

fun Context.fmtTime(date: Long): String =
    if (DateUtils.isToday(date)) DateFormat.getTimeFormat(this).format(Date(date))
    else DateUtils.formatDateTime(this, date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)

class Palette(c: Context) {
    val dark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private fun p(l: String, d: String) = Color.parseColor(if (dark) d else l)
    val bg = p("#FFFFFF", "#101113")
    val surface = p("#F2F3F6", "#1D1F23")
    val text = p("#17181B", "#ECEDEF")
    val muted = p("#6C7077", "#8D9199")
    val accent = p("#4F46E5", "#7C75FF")
    val inBubble = p("#EEF0F4", "#24272C")
    val error = p("#D93025", "#F28B82")
}

fun shape(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
fun circle(color: Int) = GradientDrawable().apply { setColor(color); shape = GradientDrawable.OVAL }

fun Activity.styleBars(p: Palette) {
    window.setBackgroundDrawable(ColorDrawable(p.bg))
    @Suppress("DEPRECATION")
    window.statusBarColor = p.bg
    @Suppress("DEPRECATION")
    window.navigationBarColor = p.bg
    if (!p.dark) {
        var f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = f
    }
}

fun Context.avatarize(tv: TextView, name: String, p: Palette) {
    tv.text = name.firstOrNull { it.isLetter() }?.uppercase() ?: "#"
    val hue = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
    tv.background = circle(Color.HSVToColor(floatArrayOf(hue, 0.5f, if (p.dark) 0.62f else 0.72f)))
}

fun EditText.onChange(f: () -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun afterTextChanged(s: Editable?) = f()
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
})

/** درخواست پیش‌فرض شدن به‌عنوان برنامه‌ی پیامک */
@Suppress("DEPRECATION")
fun Activity.askDefaultSms() {
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS))
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 77)
    } else {
        startActivityForResult(
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName), 77
        )
    }
}
