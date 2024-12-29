package com.dailystudio.gemini

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import com.dailystudio.devbricksx.development.Logger

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }


    override fun onResume() {
        super.onResume()

        val topBar: Toolbar? = findViewById(R.id.topAppBar)
        Logger.debug("topBar: $topBar")

        topBar?.let {
            setSupportActionBar(it)
        }

    }

}
