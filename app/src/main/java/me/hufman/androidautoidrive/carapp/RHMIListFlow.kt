@file:OptIn(ExperimentalCoroutinesApi::class)

package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import java.lang.IllegalArgumentException
import kotlin.math.max
import kotlin.math.min

fun <T> Flow<List<Flow<T>>>.rhmiDataTableFlow(rowMap: (T) -> Array<Any>): Flow<BMWRemoting.RHMIDataTable> {
	return this.flatMapLatest { tableRows ->
		tableRows.rhmiDataTableFlow(rowMap)
	}
}
fun <T> List<Flow<T>>.rhmiDataTableFlow(rowMap: (T) -> Array<Any>): Flow<BMWRemoting.RHMIDataTable> {
	val tableRows = this
	val rowCount = tableRows.count()
	return tableRows.mapIndexed { rowIndex, rowFlow: Flow<T> ->
		rowFlow.map { row ->
			val output = rowMap(row)
			BMWRemoting.RHMIDataTable(arrayOf(output), false, rowIndex, 1, rowCount, 0, output.size, output.size)
		}
	}.merge()
}

val BMWRemoting.RHMIDataTable.lastRow: Int
	get() = fromRow + numRows - 1
fun BMWRemoting.RHMIDataTable.includes(row: Int): Boolean =
	row in fromRow..lastRow
fun BMWRemoting.RHMIDataTable.abuts(table: BMWRemoting.RHMIDataTable): Boolean {
	return (table.fromRow <= this.fromRow - 1 && table.lastRow >= this.fromRow - 1) ||
			(table.fromRow >= this.fromRow - 1 && table.fromRow <= this.lastRow + 1)
}
fun BMWRemoting.RHMIDataTable.merge(table: BMWRemoting.RHMIDataTable) {
	if (totalRows != table.totalRows || totalColumns != table.totalColumns) {
		throw IllegalArgumentException("Additional RHMIDataTable must be the same size")
	}
	if (!abuts(table)) {
		throw IllegalArgumentException("Additional RHMIDataTable must abut this table")
	}
	val fullyWithin = fromRow <= table.fromRow && lastRow >= table.lastRow
	if (fullyWithin) {
		val destOffset = table.fromRow - fromRow
		table.data.forEachIndexed { index, row ->
			data[destOffset + index] = row
		}
	} else {
		val range = min(fromRow, table.fromRow)..max(lastRow, table.lastRow)
		val srcOffset = table.fromRow - range.first      // newData[srcOffset] == table.data[0]
		val destOffset = fromRow - range.first          // newData[destOffset] == this.data[0]
		val newData = Array<Array<Any>>(range.count()) {
			table.data.getOrNull(it - srcOffset) ?: data[it - destOffset]
		}
		data = newData
		fromRow = range.first
		numRows = newData.size
	}
}

class RHMIDataTableNeighbors() {
	val regions = ArrayList<BMWRemoting.RHMIDataTable>()

	private fun findInsertionIndex(row: BMWRemoting.RHMIDataTable): Int {
		var insertIndex = 0
		while (insertIndex < regions.size) {
			if (regions[insertIndex].abuts(row)) {
				return insertIndex
			}
			if (row.fromRow < regions[insertIndex].fromRow) {
				return insertIndex
			}
			insertIndex += 1
		}
		return regions.size
	}
	fun merge(row: BMWRemoting.RHMIDataTable) {
		val insertIndex = findInsertionIndex(row)
		if (regions.getOrNull(insertIndex)?.abuts(row) == true) {
			regions[insertIndex].merge(row)
		} else {
			regions.add(insertIndex, row)
		}

		var index = 0
		while (index < regions.size - 1) {
			if (regions[index].abuts(regions[index+1])) {
				regions[index].merge(regions[index+1])
				regions.removeAt(index+1)
				index -= 1
			}
			index += 1
		}
	}
}

// Based on debounce/sample
fun Flow<BMWRemoting.RHMIDataTable>.batchDataTables(debounceMs: Long = 100L): Flow<BMWRemoting.RHMIDataTable> = flow {
	val downstream = this
	coroutineScope {
		// a channel of upstream values to select on
		val upstream = produce {
			collect { send(it) }
		}
		// state
		var running = true
		var startTime = System.currentTimeMillis()
		var pending: RHMIDataTableNeighbors? = null
		while (running) {
			select<Unit> {
				// if we have a value, configure select to timeout and forward the value
				if (pending != null) {
					val elapsedTime = System.currentTimeMillis() - startTime
					onTimeout(debounceMs - elapsedTime) {
						val result = pending
						pending = null
						result?.let { neighbors ->
							neighbors.regions.forEach { region -> downstream.emit(region) }
						}
					}
				}
				// configure select to handle upstream values
				upstream.onReceiveCatching { result -> result
					.onSuccess {
						if (pending == null) {
							startTime = System.currentTimeMillis()
						}
						pending = (pending ?: RHMIDataTableNeighbors()).apply {
							merge(it)
						}
					}
					.onFailure {
						it?.let { throw it }
						running = false
						pending?.let { neighbors ->
							neighbors.regions.forEach { region -> downstream.emit(region) }
						}
						pending = null
					}
				}
			}
		}
	}
}