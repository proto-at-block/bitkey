package build.wallet.sqldelight

import build.wallet.sqldelight.DatabaseContents.TableContents
import com.jakewharton.picnic.TextAlignment.MiddleCenter
import com.jakewharton.picnic.TextBorder
import com.jakewharton.picnic.renderText
import com.jakewharton.picnic.table

/**
 * Simply render each individual table.
 */
fun DatabaseContents.renderText(): String {
  return buildString {
    tables.forEachIndexed { index, table ->
      append(table.renderText())
      // Insert newline between each table.
      if (index < tables.lastIndex) {
        appendLine()
      }
    }
  }
}

/**
 * Render table contents as a nicely formatted table of contents.
 */
fun TableContents.renderText(): String {
  // Naive way to count how many rows there are in the table.
  val rowCount = rowValues.values.firstOrNull()?.size ?: 0

  return table {
    cellStyle {
      border = true
      alignment = MiddleCenter
    }
    header {
      row {
        cell(tableName) {
          columnSpan = columnNames.size
        }
      }
      // Render row with all column names.
      row(*columnNames.toTypedArray())
    }

    // Render each row and its values for each column.
    if (rowCount > 0) {
      repeat(rowCount) { rowIndex ->
        row {
          columnNames.forEach { column ->
            val rowValues = rowValues[column]
            val value = requireNotNull(rowValues)[rowIndex]
            cell(value)
          }
        }
      }
    } else {
      row {
        cell("Empty") {
          columnSpan = columnNames.size
        }
      }
    }
  }.renderText(border = TextBorder.DEFAULT)
}
