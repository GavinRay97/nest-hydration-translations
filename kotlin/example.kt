sealed interface IProperty {
    val name: String
}

data class Definition(val properties: List<IProperty>)

data class Property(override val name: String, val isId: Boolean = false) : IProperty {
    val column: String = name
}

data class PropertyArray(override val name: String, val properties: List<IProperty>) : IProperty

data class PropertyObject(override val name: String, val properties: List<IProperty>) : IProperty

typealias Row = MutableMap<String, Any?>
typealias Result = MutableMap<String, Any?>

object Hydrator {

    fun nest(dataset: List<Row>, properties: List<IProperty>): List<Result> {
        val result = mutableListOf<Result>()
        val primaryIdColumns = properties.filter { it is Property && it.isId } as List<Property>

        for (row in dataset) {
            val mappedEntry = buildEntry(primaryIdColumns, row, result) ?: continue

            for (property in properties) {
                when (property) {
                    is Property -> {
                        if (property.isId) continue
                        extract(property, row, mappedEntry)
                    }
                    is PropertyObject -> extractObject(property, row, mappedEntry)
                    is PropertyArray -> extractArray(property, row, mappedEntry)
                }
            }
        }

        return result
    }

    fun extract(property: Property, row: Row, mappedEntry: Result) {
        mappedEntry[property.name] = row[property.column]
    }

    fun extractObject(propertyObject: PropertyObject, row: Row, mappedEntry: Result) {
        val newEntry = mutableMapOf<String, Any?>()

        for (property in propertyObject.properties) {
            when (property) {
                is Property -> {
                    if (property.isId && row[property.column] == null) {
                        mappedEntry[propertyObject.name] = null
                        return
                    }
                    extract(property, row, newEntry)
                }
                is PropertyObject -> extractObject(property, row, newEntry)
                is PropertyArray -> extractArray(property, row, newEntry)
            }
        }

        mappedEntry[propertyObject.name] = newEntry
    }

    fun extractArray(propertyArray: PropertyArray, row: Row, mappedEntry: Result) {
        val primaryIdColumns = propertyArray.properties.filter { it is Property && it.isId } as List<Property>
        val entryExists = mappedEntry.containsKey(propertyArray.name)

        val list = if (entryExists) mappedEntry[propertyArray.name] as MutableList<Result> else mutableListOf()
        val mapped = buildEntry(primaryIdColumns, row, list) ?: return

        for (property in propertyArray.properties) {
            when (property) {
                is Property -> {
                    if (property.isId) continue
                    extract(property, row, mapped)
                }
                is PropertyObject -> extractObject(property, row, mapped)
                is PropertyArray -> extractArray(property, row, mapped)
            }
        }

        if (entryExists) return
        mappedEntry[propertyArray.name] = list
    }

    fun buildEntry(primaryIdColumns: List<Property>, row: Row, result: MutableList<Result>): Result? {
        if (primaryIdColumns.any { row[it.column] == null }) return null

        val existingEntry = result.find {
            primaryIdColumns.all { pkCol -> it[pkCol.name] == row[pkCol.column] }
        }

        if (existingEntry != null) return existingEntry

        val newEntry = mutableMapOf<String, Any?>()
        for (property in primaryIdColumns) {
            newEntry[property.name] = row[property.column]
        }

        result.add(newEntry)
        return newEntry
    }
}
