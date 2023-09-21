package me.grey.picquery.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import me.grey.picquery.theme.PicQueryThemeM3
import me.grey.picquery.ui.display.DisplayScreen
import me.grey.picquery.ui.display.DisplayViewModel

class DisplayActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DisplayActivity"
    }

    private val displayViewModel: DisplayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val index = intent.getIntExtra("index", 0)
        val photoId = intent.getLongExtra("id", 0)

        displayViewModel.findPhotoById(photoId)

        Log.d(TAG, index.toString())
        setContent {
            PicQueryThemeM3 {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DisplayScreen(index)
                }
            }
        }
    }
}