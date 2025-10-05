package cl.andres.semana4

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(tableName = "ranges")
data class RangeConfig(
    @PrimaryKey val id: Int = 1,
    val minC: Double,
    val maxC: Double
)

@Dao
interface RangeDao {
    @Query("SELECT * FROM ranges WHERE id = 1")
    suspend fun get(): RangeConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(cfg: RangeConfig)
}

@Database(entities = [RangeConfig::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): RangeDao
    companion object {
        @Volatile private var I: AppDb? = null
        fun get(ctx: Context): AppDb =
            I ?: synchronized(this) {
                I ?: Room.databaseBuilder(ctx, AppDb::class.java, "ranges.db").build().also { I = it }
            }
    }
}

class RangesViewModel(private val ctx: Context): ViewModel() {
    private val dao = AppDb.get(ctx).dao()

    fun getRanges(cb: (Double, Double) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val cfg = dao.get() ?: RangeConfig(minC = -5.0, maxC = 4.0).also { dao.save(it) }
            cb(cfg.minC, cfg.maxC)
        }
    }

    fun setRanges(minC: Double, maxC: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.save(RangeConfig(minC = minC, maxC = maxC))
        }
    }

    companion object {
        val factory = { ctx: Context ->
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RangesViewModel(ctx.applicationContext) as T
            }
        }
    }
}