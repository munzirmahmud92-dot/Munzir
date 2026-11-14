package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.di.StudioContainer
import com.example.firebase.configureAppCheck
import com.example.ui.StudioApp

class MainActivity : ComponentActivity() {
    private lateinit var studioContainer: StudioContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        studioContainer = StudioContainer(applicationContext)
        enableEdgeToEdge()

        setContent {
            StudioApp(container = studioContainer)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::studioContainer.isInitialized) {
            studioContainer.release()
        }
    }
}
