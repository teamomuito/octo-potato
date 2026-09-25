package io.github.teamomuito.octopotato.data

object TidyPlan {
    /** Temporary screenshots that have been around long enough to go. */
    fun due(db: ShotDb, settings: TidySettings, now: Long = System.currentTimeMillis()): List<Shot> {
        if (!settings.enabled) return emptyList()
        return db.temporaries().filter { settings.covers(it.kind) && Expiry.at(it.taken, it.found, settings.days) <= now }
    }
}
