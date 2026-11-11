package io.github.isramorante.brava

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.isramorante.brava.ui.BravaApp
import io.github.isramorante.brava.ui.BravaTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BravaTheme {
                BravaApp(vm)
            }
        }
    }
}
