package pl.kwitukiewicz.handygerman.artikel.data

import android.database.sqlite.SQLiteDatabase
import pl.kwitukiewicz.handygerman.core.foldForm

/**
 * The debug-build corpus self-check: the exporter wrote `forms.form_folded` with its copy
 * of [foldForm]; this app queries with its own. If the two ever drift — changed vectors,
 * diverging runtimes — every drifted shipped form becomes unfindable, so debug builds scan
 * the shipped corpus and refuse to hide the fact. The failure message names the offending
 * triple: the form, the key stored in the db, and the key this app computes.
 */
fun verifyFoldConsistency(db: SQLiteDatabase) {
    db.rawQuery("SELECT form, form_folded FROM forms", null).use { cursor ->
        while (cursor.moveToNext()) {
            val form = cursor.getString(0)
            val stored = cursor.getString(1)
            val computed = foldForm(form)
            check(stored == computed) { "fold drift at '$form' -> db:'$stored' app:'$computed'" }
        }
    }
}
