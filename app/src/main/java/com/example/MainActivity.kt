package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.ScoreboardApp
import com.example.ui.theme.ScoreboardTheme

class MainActivity : ComponentActivity() {

    // Lazy instantiation of Offline-first Room Database and Repository
    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "malayalam_scoreboard_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val repository by lazy { AppRepository(db) }

    // Setup Main ViewModel
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreboardTheme {
                ScoreboardApp(viewModel = viewModel)
            }
        }
    }
}
