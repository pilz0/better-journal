package foo.pilz.freaklog.data.room.experiences.relations

import androidx.room.Embedded
import androidx.room.Relation
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem

data class SubstanceGroupWithItems(
    @Embedded val group: SubstanceGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId",
    )
    val items: List<SubstanceGroupItem>,
) {
    val sortedItems: List<SubstanceGroupItem>
        get() = items.sortedBy { it.sortOrder }
}
