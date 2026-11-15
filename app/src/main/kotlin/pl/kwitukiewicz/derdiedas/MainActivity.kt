package pl.kwitukiewicz.derdiedas

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.kwitukiewicz.derdiedas.core.LookupEngine
import pl.kwitukiewicz.derdiedas.data.SqliteWordRepository
import pl.kwitukiewicz.derdiedas.data.WordsDb
import pl.kwitukiewicz.derdiedas.ui.SearchScreen
import pl.kwitukiewicz.derdiedas.ui.theme.DerdiedasTheme

/**
 * Host of the search screen. The screen receives *loaders*, not an engine: opening the
 * database copies the ~93 MiB asset on first call, so [WordsDb.open] must run on an IO
 * worker inside `SearchScreen` — constructing the engine in composition would copy it on
 * the UI thread. Both loaders only ever ask [Dictionary], which keeps that copy and the
 * handle down to exactly one per process.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge: content (the wash) draws under transparent system bars. Both
        // styles are pinned to `light` — dark icons, transparent scrims — because the
        // app is single-appearance (WashPalette.appLooksDark): the bar treatment must
        // not follow the system either. SearchScreen insets its content via
        // statusBars/navigationBars padding, which is what keeps the field tappable
        // below the status strip.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val appContext: Context = applicationContext
        setContent {
            DerdiedasTheme {
                SearchScreen(
                    engineLoader = { Dictionary.open(appContext).first },
                    metaProvider = { Dictionary.open(appContext).second },
                )
            }
        }
    }
}

/**
 * The process-wide database handle: the engine and the meta stamps are opened once, on
 * whichever IO thread asks first, and every later caller — including every Activity
 * recreation, since a composition-`remember`ed loader would otherwise re-open the handle
 * on each rotation — gets the cached pair back under one lock. A failed open is never
 * cached: the dictionary is simply refused, and the next launch or composition retries.
 */
private object Dictionary {

    private val lock = Any()
    private var opened: Pair<LookupEngine, Map<String, String>>? = null

    fun open(context: Context): Pair<LookupEngine, Map<String, String>> = synchronized(lock) {
        opened ?: run {
            val repository = SqliteWordRepository(WordsDb(context).open())
            Pair(LookupEngine(repository), repository.meta()).also { opened = it }
        }
    }
}
