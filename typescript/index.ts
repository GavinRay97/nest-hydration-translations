type IProperty = Property | PropertyArray | PropertyObject

interface Property {
  name: string
  type: "COLUMN"
  isId?: boolean
  column?: string
}

interface PropertyArray {
  name: string
  type: "MANY"
  properties: IProperty[]
}

interface PropertyObject {
  name: string
  type: "ONE"
  properties: IProperty[]
}

interface Row {
  [key: string]: any
}

interface Entry {
  [key: string]: any
}

interface Result {
  [key: string]: any
}

function nest(dataset: Row[], properties: IProperty[]): Result[] {
  const result: Result[] = []
  const primaryIdColumns = properties.filter((it) => "isId" in it && it.isId) as Property[]

  for (const row of dataset) {
    const mappedEntry = buildEntry(primaryIdColumns, row, result)
    if (mappedEntry === null) continue

    for (const property of properties) {
      switch (property.type) {
        case "COLUMN":
          if (property.isId) continue
          extract(property, row, mappedEntry)
          break
        case "ONE":
          extractObject(property, row, mappedEntry)
          break
        case "MANY":
          extractArray(property, row, mappedEntry)
          break
      }
    }
  }

  return result
}

function extract(property: Property, row: Row, mappedEntry: Result) {
  mappedEntry[property.name] = row[property.column!!]
}

function extractObject(propertyObject: PropertyObject, row: Row, mappedEntry: Result) {
  const newEntry: Result = {}

  for (const property of propertyObject.properties) {
    switch (property.type) {
      case "COLUMN":
        if (property.isId && row[property.column!!] === null) {
          mappedEntry[propertyObject.name] = null
          return
        }
        extract(property, row, newEntry)
        break
      case "ONE":
        extractObject(property, row, newEntry)
        break
      case "MANY":
        extractArray(property, row, newEntry)
        break
    }
  }

  mappedEntry[propertyObject.name] = newEntry
}

function extractArray(propertyArray: PropertyArray, row: Row, mappedEntry: Result) {
  const primaryIdColumns = propertyArray.properties.filter(
    (it) => "isId" in it && it.isId
  ) as Property[]
  const entryExists = mappedEntry.hasOwnProperty(propertyArray.name)

  const list = entryExists ? (mappedEntry[propertyArray.name] as Result[]) : []

  const mapped = buildEntry(primaryIdColumns, row, list)
  if (mapped === null) {
    mappedEntry[propertyArray.name] = null
    return
  }

  for (const property of propertyArray.properties) {
    switch (property.type) {
      case "COLUMN":
        if (property.isId) continue
        extract(property, row, mapped)
        break
      case "ONE":
        extractObject(property, row, mapped)
        break
      case "MANY":
        extractArray(property, row, mapped)
        break
    }
  }

  if (entryExists) return
  mappedEntry[propertyArray.name] = list
}

function buildEntry(primaryIdColumns: Property[], row: Row, result: Result[]): Result | null {
  if (primaryIdColumns.some((it) => row[it.column!!] === null)) return null

  const existingEntry = result.find((it) => {
    return primaryIdColumns.every((pkCol) => it[pkCol.name] === row[pkCol.column!!])
  })

  if (existingEntry !== undefined) return existingEntry

  const newEntry: Result = {}
  for (const property of primaryIdColumns) {
    newEntry[property.name] = row[property.column!!]
  }

  result.push(newEntry)
  return newEntry
}

function main() {
  // Album, Artist, Tracks
  const exampleDataset = [
    {
      id: 1,
      name: "Album 1",
      artist_id: 1,
      artist_name: "Artist 1",
      track_id: 1,
      track_title: "Track 1",
    },
    {
      id: 1,
      name: "Album 1",
      artist_id: 1,
      artist_name: "Artist 1",
      track_id: 2,
      track_title: "Track 2",
    },
    {
      id: 2,
      name: "Album 2",
      artist_id: 1,
      artist_name: "Artist 1",
      track_id: 3,
      track_title: "Track 3",
    },
  ]
  const exampleDefinition: IProperty[] = [
    {
      name: "id",
      column: "id",
      type: "COLUMN",
      isId: true,
    },
    {
      name: "name",
      column: "name",
      type: "COLUMN",
    },
    {
      name: "artist",
      type: "ONE",
      properties: [
        {
          name: "id",
          column: "artist_id",
          type: "COLUMN",
          isId: true,
        },
        {
          name: "name",
          column: "artist_name",
          type: "COLUMN",
        },
      ],
    },
    {
      name: "tracks",
      type: "MANY",
      properties: [
        {
          name: "id",
          column: "track_id",
          type: "COLUMN",
          isId: true,
        },
        {
          name: "title",
          column: "track_title",
          type: "COLUMN",
        },
      ],
    },
  ]

  // [
  //   {
  //     id: 1,
  //     name: 'Album 1',
  //     artist: { id: 1, name: 'Artist 1' },
  //     tracks: [ { id: 1, title: 'Track 1' }, { id: 2, title: 'Track 2' } ]
  //   },
  //   {
  //     id: 2,
  //     name: 'Album 2',
  //     artist: { id: 1, name: 'Artist 1' },
  //     tracks: [ { id: 3, title: 'Track 3' } ]
  //   }
  // ]
  const result = nest(exampleDataset, exampleDefinition)
  console.dir(result, { depth: Infinity })
}

main()
