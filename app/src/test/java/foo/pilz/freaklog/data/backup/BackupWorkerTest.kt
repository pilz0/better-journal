package foo.pilz.freaklog.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class BackupWorkerTest {

    private fun name(day: Int) =
        BackupWorker.backupFileName(Instant.parse("2026-01-%02dT08:30:00Z".format(day)), ZoneOffset.UTC)

    @Test
    fun `file name carries a sortable timestamp`() {
        assertEquals("journal-backup-2026-01-05-0830.enc", name(5))
    }

    @Test
    fun `nothing is pruned while at or below the keep count`() {
        val names = (1..BackupWorker.KEEP_COUNT).map(::name)
        assertTrue(BackupWorker.backupsToPrune(names).isEmpty())
    }

    @Test
    fun `only the oldest backups beyond the keep count are pruned`() {
        val names = (1..10).map(::name).shuffled()
        assertEquals(listOf(name(3), name(2), name(1)), BackupWorker.backupsToPrune(names))
    }

    @Test
    fun `the newest backups are kept`() {
        val names = (1..12).map(::name).shuffled()
        val kept = names - BackupWorker.backupsToPrune(names).toSet()
        assertEquals((6..12).map(::name).toSet(), kept.toSet())
        assertEquals(BackupWorker.KEEP_COUNT, kept.size)
    }

    @Test
    fun `nothing is pruned from an empty or small folder`() {
        assertTrue(BackupWorker.backupsToPrune(emptyList()).isEmpty())
        assertTrue(BackupWorker.backupsToPrune(listOf(name(1), "holiday.jpg")).isEmpty())
    }

    @Test
    fun `timestamps order across times of day, months and years`() {
        val times = listOf(
            "2025-12-31T23:59:00Z",
            "2026-01-01T00:00:00Z",
            "2026-01-01T09:05:00Z",
            "2026-01-01T10:00:00Z",
            "2026-02-01T00:00:00Z",
            "2026-09-30T12:00:00Z",
            "2026-10-01T12:00:00Z",
            "2026-11-15T07:45:00Z",
            "2027-01-01T00:00:00Z",
        ).map { BackupWorker.backupFileName(Instant.parse(it), ZoneOffset.UTC) }
        assertEquals(times.take(2).reversed(), BackupWorker.backupsToPrune(times.shuffled()))
    }

    @Test
    fun `files that are not backups are never pruned`() {
        val names = (1..10).map(::name) + listOf("holiday.jpg", "journal-backup-notes.txt", "Journal 01 Jan 2026.enc")
        val pruned = BackupWorker.backupsToPrune(names)
        assertEquals(3, pruned.size)
        assertTrue(pruned.all { it.startsWith("journal-backup-2026") })
    }
}
