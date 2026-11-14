package pl.kwitukiewicz.derdiedas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import pl.kwitukiewicz.derdiedas.ui.theme.DerdiedasTheme

/** Placeholder host; the real search screen is mounted here in a later task. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DerdiedasTheme {
                Text("derdieapp")
            }
        }
    }
}
