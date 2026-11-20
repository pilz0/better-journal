package foo.pilz.freaklog.ui.tabs.settings.funny

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UltraFunSkinControllerTest {
  private data class A(val n: Int) : UltraFunSkin

  private data class B(val n: Int) : UltraFunSkin

  @Test
  fun `showing a new skin stops the previous one from overwriting it`() =
      runTest(UnconfinedTestDispatcher()) {
        val controller = UltraFunSkinController()
        val first = MutableStateFlow(A(1))
        val second = MutableStateFlow(B(1))
        controller.show(backgroundScope, first)
        controller.show(backgroundScope, second)
        first.value = A(2)
        assertThat(controller.state.value).isEqualTo(B(1))
        second.value = B(2)
        assertThat(controller.state.value).isEqualTo(B(2))
      }

  @Test
  fun `a dismissed skin stays dismissed when its data changes`() =
      runTest(UnconfinedTestDispatcher()) {
        val controller = UltraFunSkinController()
        val data = MutableStateFlow(A(1))
        controller.show(backgroundScope, data)
        controller.dismiss()
        data.value = A(2)
        assertThat(controller.state.value).isNull()
      }

  @Test
  fun `dismissIfShowing only closes the matching skin`() =
      runTest(UnconfinedTestDispatcher()) {
        val controller = UltraFunSkinController()
        controller.show(backgroundScope, MutableStateFlow(B(1)))
        controller.dismissIfShowing<A>()
        assertThat(controller.state.value).isEqualTo(B(1))
        controller.dismissIfShowing<B>()
        assertThat(controller.state.value).isNull()
      }
}
