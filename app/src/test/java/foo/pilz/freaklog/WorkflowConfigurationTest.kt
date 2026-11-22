package foo.pilz.freaklog

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowConfigurationTest {

  @Test
  fun googlePlayReleaseTargetsInternalAndAlphaTracks() {
    val releaseWorkflowFile = listOf(
      File(".github/workflows/release.yml"),
      File("../.github/workflows/release.yml")
    ).firstOrNull { it.exists() }

    assertTrue("release.yml workflow file must exist", releaseWorkflowFile != null && releaseWorkflowFile.exists())
    val content = releaseWorkflowFile!!.readText()

    assertTrue(
      "Google Play upload in release.yml must deploy to both internal and alpha tracks",
      content.contains("tracks: internal,alpha")
    )
  }
}
