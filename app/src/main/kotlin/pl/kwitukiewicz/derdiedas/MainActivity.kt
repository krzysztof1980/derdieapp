package pl.kwitukiewicz.derdiedas

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import pl.kwitukiewicz.derdiedas.core.LookupEngine
import pl.kwitukiewicz.derdiedas.data.SqliteWordRepository
import pl.kwitukiewicz.derdiedas.data.WordsDb
import pl.kwitukiewicz.derdiedas.ui.SearchScreen
import pl.kwitukiewicz.derdiedas.ui.theme.DerdiedasTheme

/**
 * Host of the search screen. The screen receives a remembered *loader*, not an engine:
 * opening the database copies the ~93 MiB asset on first call, so [WordsDb.open] must run
 * on the IO dispatcher inside `SearchScreen` — constructing the engine in composition
 * would copy it on the UI thread. Remembered per process, the loader is also what keeps
 * that copy to exactly one.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext: Context = applicationContext
        setContent {
            DerdiedasTheme {
                val engineLoader = remember(appContext) {
                    val load: suspend () -> LookupEngine = {
                        LookupEngine(SqliteWordRepository(WordsDb(appContext).open()))
                    }
                    load
                }
                SearchScreen(engineLoader = engineLoader)
            }
        }
    }
}
