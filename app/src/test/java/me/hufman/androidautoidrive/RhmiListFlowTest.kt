package me.hufman.androidautoidrive

import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.hufman.androidautoidrive.carapp.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhmiListFlowTest {
	val sourceData = (0 until 5).map { index ->
		Channel<Int>(capacity=1).also {
			it.trySend(index)
		}
	}
	val sourceFlows = sourceData.map {it.consumeAsFlow()}

	fun disconnect() {
		sourceData.forEach {
			it.cancel()
		}
	}


	@Test
	fun testConvert() = runTest {
		val output = ArrayList<BMWRemoting.RHMIDataTable>()
		launch {
			sourceFlows.rhmiDataTableFlow { arrayOf(
				it, it*it
			) }.collect {
				output.add(it)
			}
		}
		advanceUntilIdle()

		assertEquals(5, output.size)
		output.forEachIndexed { index, it ->
			assertEquals(index, it.fromRow)
			assertEquals(1, it.numRows)
			assertEquals(5, it.totalRows)
			assertEquals(0, it.fromColumn)
			assertEquals(2, it.numColumns)
			assertEquals(2, it.totalColumns)

			assertEquals(1, it.data.size)
			assertEquals(2, it.data[0].size)

			assertEquals(index, it.data[0][0])
			assertEquals(index*index, it.data[0][1])
		}

		disconnect()
	}

	@Test
	fun testStream() = runTest {
		val output = ArrayList<BMWRemoting.RHMIDataTable>()
		launch {
			sourceFlows.rhmiDataTableFlow { arrayOf(
				it, it*it
			) }.collect {
				output.add(it)
			}
		}
		advanceUntilIdle()

		assertEquals(5, output.size)
		output.clear()
		sourceData[3].send(30)
		advanceUntilIdle()

		assertEquals(1, output.size)
		assertEquals(3, output[0].fromRow)
		assertEquals(900, output[0].data[0][1])

		disconnect()
	}

	@Test
	fun testRHMIDataTableUpdate() {
		val totalRows = 5
		val createRow: (Int) -> BMWRemoting.RHMIDataTable = {
			BMWRemoting.RHMIDataTable(arrayOf(arrayOf(it, it*it)),
				false, it, 1, totalRows, 0, 2, 2
			)
		}
		run {   // append to end
			val row1 = createRow(1)
			val row2 = createRow(2)
			assertTrue(row1.abuts(row2))
			assertTrue(row2.abuts(row1))
			row1.merge(row2)
			assertEquals(1, row1.fromRow)
			assertEquals(2, row1.numRows)
			assertEquals(2, row1.data.size)
			assertEquals(1, row1.data[0][0])
			assertEquals(2, row1.data[1][0])
		}
		run {   // prepend
			val row1 = createRow(1)
			val row2 = createRow(2)
			row2.merge(row1)
			assertEquals(1, row2.fromRow)
			assertEquals(2, row2.numRows)
			assertEquals(2, row2.data.size)
			assertEquals(1, row2.data[0][0])
			assertEquals(2, row2.data[1][0])
		}
		run {   // edit
			val table = createRow(1)
			table.merge(createRow(2))
			table.merge(createRow(3))
			val newRow = createRow(2)
			newRow.data[0][0] = 99
			table.merge(newRow)

			assertEquals(1, table.fromRow)
			assertEquals(3, table.numRows)
			assertEquals(3, table.data.size)
			assertEquals(1, table.data[0][0])
			assertEquals(99, table.data[1][0])
			assertEquals(3, table.data[2][0])
		}
	}

	@Test
	fun testNeighbors() {
		val totalRows = 5
		val createRow: (Int) -> BMWRemoting.RHMIDataTable = {
			BMWRemoting.RHMIDataTable(arrayOf(arrayOf(it, it*it)),
				false, it, 1, totalRows, 0, 2, 2
			)
		}
		val neighbors = RHMIDataTableNeighbors()
		neighbors.merge(createRow(1))
		neighbors.merge(createRow(2))
		assertEquals(1, neighbors.regions.size)
		assertEquals(1, neighbors.regions[0].fromRow)
		assertEquals(2, neighbors.regions[0].numRows)
		assertEquals(1, neighbors.regions[0].data[0][0])
		assertEquals(2, neighbors.regions[0].data[1][0])

		neighbors.merge(createRow(4))
		assertEquals(2, neighbors.regions.size)
		assertEquals(1, neighbors.regions[0].fromRow)
		assertEquals(2, neighbors.regions[0].numRows)
		assertEquals(4, neighbors.regions[1].fromRow)
		assertEquals(1, neighbors.regions[1].numRows)

		neighbors.merge(createRow(3))
		assertEquals(1, neighbors.regions.size)
		assertEquals(1, neighbors.regions[0].fromRow)
		assertEquals(4, neighbors.regions[0].numRows)
		assertEquals(1, neighbors.regions[0].data[0][0])
		assertEquals(2, neighbors.regions[0].data[1][0])
		assertEquals(3, neighbors.regions[0].data[2][0])
		assertEquals(4, neighbors.regions[0].data[3][0])

		neighbors.merge(createRow(0))
		assertEquals(1, neighbors.regions.size)
		assertEquals(0, neighbors.regions[0].fromRow)
		assertEquals(5, neighbors.regions[0].numRows)
		assertEquals(0, neighbors.regions[0].data[0][0])
		assertEquals(1, neighbors.regions[0].data[1][0])
		assertEquals(2, neighbors.regions[0].data[2][0])
		assertEquals(3, neighbors.regions[0].data[3][0])
		assertEquals(4, neighbors.regions[0].data[4][0])
	}

	@Test
	fun testCombine() = runTest {
		val output = ArrayList<BMWRemoting.RHMIDataTable>()
		launch {
			sourceFlows.rhmiDataTableFlow { arrayOf(
				it, it*it
			) }.batchDataTables(500).collect {
				output.add(it)
			}
		}
		delay(200)
		assertEquals(0, output.size)
		delay(200)
		assertEquals(0, output.size)
		delay(200)
		assertEquals(1, output.size)
		assertEquals(5, output[0].numRows)
		assertEquals(0, output[0].data[0][0])
		assertEquals(1, output[0].data[1][0])
		assertEquals(2, output[0].data[2][0])
		assertEquals(3, output[0].data[3][0])
		assertEquals(4, output[0].data[4][0])

		disconnect()
	}

}