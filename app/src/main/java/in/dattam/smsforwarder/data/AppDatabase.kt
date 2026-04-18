package `in`.dattam.smsforwarder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class EnumConverters {
    @TypeConverter fun fromMode(v: PatternMode) = v.name
    @TypeConverter fun toMode(v: String) = PatternMode.valueOf(v)
}

@Database(entities = [ForwardingRule::class, EventLog::class], version = 3, exportSchema = false)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS event_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ts INTEGER NOT NULL,
                        stage TEXT NOT NULL,
                        sender TEXT NOT NULL,
                        bodyPreview TEXT NOT NULL,
                        ruleId INTEGER,
                        ruleName TEXT,
                        httpCode INTEGER,
                        attempt INTEGER,
                        detail TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE forwarding_rules ADD COLUMN bodyTemplate TEXT")
                db.execSQL(
                    "ALTER TABLE forwarding_rules ADD COLUMN bodyContentType TEXT NOT NULL DEFAULT 'application/json; charset=utf-8'"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "smsforwarder.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
