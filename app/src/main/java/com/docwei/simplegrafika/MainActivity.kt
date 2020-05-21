package com.docwei.simplegrafika

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.playingmovie).setOnClickListener {
            var intent = Intent(this@MainActivity, PlayingMovieActivity::class.java)
            startActivity(intent)
        }

    }
}
