package com.arpit.myapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        count = savedInstanceState?.getInt("count") ?: 0
        val counterText = findViewById<TextView>(R.id.counterText)
        counterText.text = count.toString()

        findViewById<Button>(R.id.tapButton).setOnClickListener {
            count++
            counterText.text = count.toString()
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            count = 0
            counterText.text = "0"
        }

        findViewById<Button>(R.id.webButton).setOnClickListener {
            startActivity(Intent(this, WebActivity::class.java))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("count", count)
    }
}
