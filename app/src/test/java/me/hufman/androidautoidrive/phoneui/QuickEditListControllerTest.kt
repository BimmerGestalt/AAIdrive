package me.hufman.androidautoidrive.phoneui

import android.view.View
import android.view.ViewGroup
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.phoneui.controllers.QuickEditListController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickEditListControllerTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	val items = ArrayList<String>()
	val itemTouchHelper = mock<ItemTouchHelper>()
	val adapter = mock<RecyclerView.Adapter<RecyclerView.ViewHolder>>()
	val controller = QuickEditListController(items, itemTouchHelper).also {
		it.adapter = adapter
	}

	@Test
	fun testAddEmptyItem() {
		controller.addItem()
		assertEquals(0, items.size)
		verifyZeroInteractions(adapter)
		verifyZeroInteractions(itemTouchHelper)
	}

	@Test
	fun testAddItem() {
		controller.currentInput.value = "Test"
		controller.addItem()
		assertEquals(1, items.size)
		assertEquals("Test", items[0])
		verify(adapter).notifyItemInserted(0)
		verifyZeroInteractions(itemTouchHelper)
	}

	@Test
	fun testDragMissing() {
		val intermediate = mock<ViewGroup> {
			on { parent } doReturn null
		}
		val view = mock<View> {
			on { parent } doReturn intermediate
		}
		controller.startDrag(view)
		verifyZeroInteractions(itemTouchHelper)
	}

	@Test
	fun testDrag() {
		val viewHolder = mock<RecyclerView.ViewHolder>()
		val recyclerView = mock<RecyclerView> {
			on { getChildViewHolder(any()) } doReturn viewHolder
		}
		val intermediate = mock<ViewGroup> {
			on { parent } doReturn recyclerView
		}
		val view = mock<View> {
			on { parent } doReturn intermediate
		}
		controller.startDrag(view)

		verify(recyclerView).getChildViewHolder(intermediate)
		verify(itemTouchHelper).startDrag(viewHolder)
	}
}