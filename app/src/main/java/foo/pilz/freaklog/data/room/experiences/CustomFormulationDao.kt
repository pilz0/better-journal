package foo.pilz.freaklog.data.room.experiences

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFormulationDao {
    @Query("SELECT * FROM custom_formulation")
    fun getAll(): Flow<List<CustomFormulation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customFormulation: CustomFormulation): Long

    @Update
    suspend fun update(customFormulation: CustomFormulation)

    @Delete
    suspend fun delete(customFormulation: CustomFormulation)
}
