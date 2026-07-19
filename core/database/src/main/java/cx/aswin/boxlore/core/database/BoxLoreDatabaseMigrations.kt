package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Extracted migration SQL so unit tests can verify table creation without Room schema JSON.
 */
object BoxLoreDatabaseMigrations {
    fun migrate29To30(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_sessions (
                sessionId TEXT NOT NULL PRIMARY KEY,
                episodeId TEXT NOT NULL,
                podcastId TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER NOT NULL,
                consumedMs INTEGER NOT NULL,
                completed INTEGER NOT NULL,
                localDay INTEGER NOT NULL,
                timeBucket INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_endedAt ON listening_sessions(endedAt)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_episodeId ON listening_sessions(episodeId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_podcastId ON listening_sessions(podcastId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_localDay ON listening_sessions(localDay)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_rollups (
                localDay INTEGER NOT NULL,
                episodeId TEXT NOT NULL,
                podcastId TEXT NOT NULL,
                consumedMs INTEGER NOT NULL,
                sessionCount INTEGER NOT NULL,
                completionCount INTEGER NOT NULL,
                lastListenedAt INTEGER NOT NULL,
                morningMs INTEGER NOT NULL,
                afternoonMs INTEGER NOT NULL,
                eveningMs INTEGER NOT NULL,
                nightMs INTEGER NOT NULL,
                PRIMARY KEY(localDay, episodeId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_rollups_podcastId ON listening_rollups(podcastId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_rollups_localDay ON listening_rollups(localDay)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_rollups_episodeId ON listening_rollups(episodeId)",
        )
    }
}
