package pl.kwitukiewicz.handygerman.artikel

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine
import pl.kwitukiewicz.handygerman.artikel.core.SavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.data.SavedWordsDb
import pl.kwitukiewicz.handygerman.artikel.data.SqliteWordRepository
import pl.kwitukiewicz.handygerman.artikel.data.WordsDb
import pl.kwitukiewicz.handygerman.artikel.ui.SearchScreen
import pl.kwitukiewicz.handygerman.artikel.ui.theme.HandyGermanTheme

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
        // The app carries no manifest theme, and the platform default has an action bar —
        // it surfaced as a dark gray header band (the launcher label) above the Compose
        // content once the window canvas went white (it was always there, formerly invisible
        // against the dark DayNight background). Must precede setContent.
        requestWindowFeature(Window.FEATURE_NO_TITLE)
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
        // Single-appearance: the platform window default is a DayNight background, which
        // in the phone's dark mode showed through the transparent bars as a dark gray
        // strip above the (transparent, blank-state) wash. Pin the window's own canvas to
        // white — the Compose scheme is always-light, so the window must be too.
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        val appContext: Context = applicationContext
        setContent {
            HandyGermanTheme {
                SearchScreen(
                    engineLoader = { Dictionary.open(appContext).first },
                    savedLoader = { Dictionary.openSaved(appContext) },
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
    private var saved: SavedWordsRepository? = null

    fun open(context: Context): Pair<LookupEngine, Map<String, String>> = synchronized(lock) {
        opened ?: run {
            val repository = SqliteWordRepository(WordsDb(context).open())
            Pair(LookupEngine(repository), repository.meta()).also { opened = it }
        }
    }

    /** The saved store, opened once per process the same way — it is a second,
     * independent small database, never part of the copy-on-first-call dictionary. */
    fun openSaved(context: Context): SavedWordsRepository = synchronized(lock) {
        saved ?: SavedWordsDb(context).repository().also { saved = it }
    }
}
