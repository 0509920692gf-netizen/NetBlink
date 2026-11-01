package com.example.netblink

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var grant: Button
    private lateinit var cutBtn: Button
    private lateinit var autoSwitch: Switch

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { refresh() }
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        status = findViewById(R.id.status)
        grant = findViewById(R.id.grant)
        cutBtn = findViewById(R.id.cut)
        autoSwitch = findViewById(R.id.auto)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(deadListener)

        grant.setOnClickListener {
            when {
                !NetController.shizukuRunning() -> openShizuku()
                else -> try {
                    Shizuku.requestPermission(1)
                } catch (_: Throwable) {
                    openShizuku()
                }
            }
        }

        cutBtn.setOnClickListener {
            if (!NetController.shizukuReady()) {
                Toast.makeText(this, R.string.need_shizuku, Toast.LENGTH_SHORT).show()
            } else {
                cutBtn.isEnabled = false
                NetController.cut(this) { runOnUiThread { refresh() } }
                refresh()
            }
        }

        autoSwitch.isChecked = prefs.auto
        autoSwitch.setOnCheckedChangeListener { _, on ->
            prefs.auto = on
            if (on) MonitorService.start(this) else MonitorService.stop(this)
        }

        NetController.recover(this)
        if (prefs.auto) MonitorService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onDestroy()
    }

    private fun refresh() {
        val ready = NetController.shizukuReady()
        status.setText(
            when {
                ready -> R.string.shizuku_ready
                NetController.shizukuRunning() -> R.string.shizuku_no_perm
                else -> R.string.shizuku_off
            }
        )
        grant.visibility = if (ready) android.view.View.GONE else android.view.View.VISIBLE
        cutBtn.isEnabled = ready && !NetController.busy.get()
        cutBtn.setText(if (NetController.busy.get()) R.string.cutting else R.string.cut_now)
        autoSwitch.isEnabled = true
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
    }
}
