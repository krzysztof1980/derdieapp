package pl.kwitukiewicz.handygerman.artikel.core

/**
 * In-memory [LearningRepository] mirroring the SQLite store's contract: every
 * mutation is a single-row upsert ([delete] aside), `graduate` stamps level
 * [QUIZ_GRADUATION_LEVEL] plus the timestamp, and [putLevel] clears
 * [LearningState.graduatedAt]. [statesMap] is exposed for assertions.
 */
class FakeLearningRepository : LearningRepository {

    val statesMap = mutableMapOf<WordIdentity, LearningState>()

    override fun states(): Map<WordIdentity, LearningState> = statesMap.toMap()

    override fun putLevel(identity: WordIdentity, level: Int) {
        statesMap[identity] = LearningState(level, graduatedAt = null)
    }

    override fun graduate(identity: WordIdentity, atMillis: Long) {
        statesMap[identity] = LearningState(QUIZ_GRADUATION_LEVEL, atMillis)
    }

    override fun requeue(identity: WordIdentity) {
        statesMap[identity] = LearningState(level = 0, graduatedAt = null)
    }

    override fun delete(identity: WordIdentity) {
        statesMap.remove(identity)
    }

    override fun learnedCount(): Int = statesMap.values.count { it.graduatedAt != null }
}
