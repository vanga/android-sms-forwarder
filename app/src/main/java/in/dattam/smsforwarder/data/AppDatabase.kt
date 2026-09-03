@file:Suppress("DEPRECATION")

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

@Database(
    entities = [ForwardingRule::class, EventLog::class, WebhookDelivery::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun webhookDeliveryDao(): WebhookDeliveryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE event_log (
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE webhook_deliveries (
                        id TEXT NOT NULL PRIMARY KEY,
                        smsKey TEXT NOT NULL,
                        ruleId INTEGER NOT NULL,
                        ruleName TEXT NOT NULL,
                        sender TEXT NOT NULL,
                        body TEXT NOT NULL,
                        sim INTEGER NOT NULL,
                        sentTs INTEGER NOT NULL,
                        recvTs INTEGER NOT NULL,
                        maxAttempts INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        httpCode INTEGER,
                        detail TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX index_webhook_deliveries_smsKey_ruleId " +
                        "ON webhook_deliveries (smsKey, ruleId)"
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
