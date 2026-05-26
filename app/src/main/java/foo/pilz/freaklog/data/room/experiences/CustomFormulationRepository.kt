package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomFormulationRepository @Inject constructor(
    private val customFormulationDao: CustomFormulationDao
) {
    fun getAll(): Flow<List<CustomFormulation>> = customFormulationDao.getAll()

    suspend fun insert(customFormulation: CustomFormulation): Long {
        return customFormulationDao.insert(customFormulation)
    }

    suspend fun update(customFormulation: CustomFormulation) {
        customFormulationDao.update(customFormulation)
    }

    suspend fun delete(customFormulation: CustomFormulation) {
        customFormulationDao.delete(customFormulation)
    }
}
