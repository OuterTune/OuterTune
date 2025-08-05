package com.google.android.music

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // only proceed if intent is specifically sent to this, else the main app will handle it
        if (intent?.getPackage() != "com.google.android.music") return

        val proxyIntent = Intent(intent.action).apply {
            setPackage("com.dd3boh.outertune")
            putExtras(intent)
        }

        try {
            startActivity(proxyIntent)
        } catch (e: Exception) {
            Log.d("OuterTune Proxy", "failed on main, retrying on debug app")
            proxyIntent.setPackage("com.dd3boh.outertune.debug")
            startActivity(proxyIntent)
        } catch (e: Exception) {
            Log.d("OuterTune Proxy", "failed on main and debug app")
            Toast.makeText(this, "Could not start OuterTune", Toast.LENGTH_LONG).show()
        } finally {
            finishAndRemoveTask()
        }
    }
}