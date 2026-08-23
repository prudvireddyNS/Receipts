package com.prudvi.trackbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.prudvi.trackbudget.ui.TrackBudgetApp
import com.prudvi.trackbudget.ui.TrackBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as TrackBudgetApplication).repository
        setContent {
            TrackBudgetTheme {
                TrackBudgetApp(repository)
            }
        }
    }
}
