package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstanceGroupRepository @Inject constructor(
    private val dao: ExperienceDao,
) {
    fun getGroupsFlow(): Flow<List<SubstanceGroup>> =
        dao.getSubstanceGroupsFlow().flowOn(Dispatchers.IO).conflate()

    fun getGroupsWithItemsFlow(): Flow<List<SubstanceGroupWithItems>> =
        dao.getSubstanceGroupsWithItemsFlow().flowOn(Dispatchers.IO).conflate()

    fun getWithItemsFlow(id: Int): Flow<SubstanceGroupWithItems?> =
        dao.getSubstanceGroupWithItemsFlow(id).flowOn(Dispatchers.IO).conflate()

    suspend fun getWithItems(id: Int): SubstanceGroupWithItems? =
        dao.getSubstanceGroupWithItems(id)

    suspend fun getWithItemsByName(name: String): SubstanceGroupWithItems? =
        dao.getSubstanceGroupWithItemsByName(name)

    suspend fun create(group: SubstanceGroup, items: List<SubstanceGroupItem> = emptyList()): Int =
        dao.insertSubstanceGroupWithItems(group, items)

    suspend fun update(group: SubstanceGroup) = dao.update(group)

    suspend fun delete(group: SubstanceGroup) = dao.delete(group)

    suspend fun replaceItems(groupId: Int, items: List<SubstanceGroupItem>) =
        dao.replaceSubstanceGroupItems(groupId, items)

    suspend fun appendItem(groupId: Int, item: SubstanceGroupItem) {
        item.groupId = groupId
        dao.insert(item)
    }

    suspend fun deleteItem(item: SubstanceGroupItem) = dao.delete(item)
}
