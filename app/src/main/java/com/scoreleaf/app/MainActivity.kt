package com.scoreleaf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.scoreleaf.app.ui.ScoreleafApp
import com.scoreleaf.app.ui.ScoreleafTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScoreleafTheme { ScoreleafApp(initialPdf = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data) } }
    }
}
