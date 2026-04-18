package `in`.dattam.smsforwarder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class EnumConverters {
    @TypeConverter fun fromMode(v: PatternMode) = v.name
    @TypeConverter fun toMode(v: String) = PatternMode.valueOf(v)
}

@Database(entities = [ForwardingRule::class], version = 1, exportSchema = false)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "smsforwarder.db"
            ).build().also { instance = it }
        }
    }
}
