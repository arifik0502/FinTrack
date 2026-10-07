package com.example.fintrack.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.example.fintrack.ai.learnTx
import com.example.fintrack.data.AppDb
import com.example.fintrack.data.Cats
import com.example.fintrack.data.Tx
import com.example.fintrack.ui.palette
import com.example.fintrack.util.money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppState { @Volatile var foreground = false }

/** Category picker drawn over any app. Needs "Display over other apps" permission. */
object Overlay {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<Tx>()
    private var view: View? = null

    fun show(c: Context, t: Tx) {
        val app = c.applicationContext
        main.post { if (view != null) queue.add(t) else render(app, t) }
    }

    private fun dismiss(c: Context) {
        view?.let { runCatching { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } }
        view = null
        queue.removeFirstOrNull()?.let { render(c, it) }
    }

    private fun render(c: Context, t: Tx) {
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) 0xFF24282E.toInt() else Color.WHITE
        val fg = if (dark) Color.WHITE else 0xFF1B1B1F.toInt()
        val d = c.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(24).toFloat() }
            elevation = dp(8).toFloat()
        }
        card.addView(TextView(c).apply { text = "Choose a category"; setTextColor(fg); textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
        card.addView(TextView(c).apply {
            text = "${t.merchant.ifBlank { t.app }} \u2022 ${money(t.amount)}"; setTextColor(fg); alpha = 0.7f; textSize = 13f; setPadding(0, dp(2), 0, dp(8))
        })
        Cats.all.chunked(2).forEach { pair ->
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { cat ->
                row.addView(Button(c).apply {
                    text = cat; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
                    minHeight = 0; minimumHeight = dp(40)
                    background = GradientDrawable().apply { setColor(palette[Cats.all.indexOf(cat) % palette.size].toArgb()); cornerRadius = dp(20).toFloat() }
                    setOnClickListener { save(c, t, cat); dismiss(c) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            card.addView(row)
        }
        card.addView(TextView(c).apply {
            text = "Skip"; gravity = Gravity.CENTER; setTextColor(fg); setPadding(0, dp(10), 0, dp(4))
            setOnClickListener { scope.launch { AppDb.get(c).dao().putTx(t.copy(pending = false)) }; dismiss(c) }
        })
        val root = FrameLayout(c).apply { setPadding(dp(12), 0, dp(12), 0); addView(card) }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = dp(48) }
        try {
            wm.addView(root, lp)
            view = root
            // Auto-hide; transaction stays pending and is asked again inside the app.
            main.postDelayed({ if (view === root) dismiss(c) }, 25_000)
        } catch (e: Exception) { Notifier.pick(c, t) }
    }

    private fun save(c: Context, t: Tx, cat: String) {
        scope.launch {
            val d = AppDb.get(c).dao()
            d.putTx(t.copy(category = cat, pending = false, conf = 1f))
            learnTx(d, t, cat)
        }
    }
}
