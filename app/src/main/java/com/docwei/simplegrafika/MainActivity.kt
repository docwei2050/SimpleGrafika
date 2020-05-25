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
            var intent = Intent(this@MainActivity, PlayMovieActivity::class.java)
            startActivity(intent)
        }
        ContentManager.initialize(this)
        val cm: ContentManager = ContentManager.instance!!
        if (!cm.isContentCreated(this)) {
            cm.createAll(this)
        }
    }
}
