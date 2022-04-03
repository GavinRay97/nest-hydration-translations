data IProperty = Property { name :: String, type' :: String, isId :: Maybe Bool, column :: Maybe String }
                | PropertyArray { name :: String, type' :: String, properties :: [IProperty] }
                | PropertyObject { name :: String, type' :: String, properties :: [IProperty] }

type Row = [(String, String)]
type Result = [(String, String)]

nest :: [Row] -> [IProperty] -> [Result]
nest dataset properties = result
    where
    result = []
    primaryIdColumns = filter (\it -> isJust (isId it)) properties

    for row of dataset
        mappedEntry = buildEntry primaryIdColumns row result
        if mappedEntry == null
        continue

        for property of properties
        switch property.type
            case "COLUMN"
            if property.isId
                continue
            extract property row mappedEntry
            case "ONE"
            extractObject property row mappedEntry
            case "MANY"
            extractArray property row mappedEntry

extract :: Property -> Row -> Result -> Result
extract property row mappedEntry = mappedEntry ++ [(property.name, row[property.column!!])]

extractObject :: PropertyObject -> Row -> Result -> Result
extractObject propertyObject row mappedEntry = mappedEntry ++ [(propertyObject.name, newEntry)]
    where
    newEntry = []

    for property of propertyObject.properties
        switch property.type
        case "COLUMN"
            if property.isId && row[property.column!!] == null
            mappedEntry[propertyObject.name] = null
            return
            extract property row newEntry
        case "ONE"
            extractObject property row newEntry
        case "MANY"
            extractArray property row newEntry

extractArray :: PropertyArray -> Row -> Result -> Result
extractArray propertyArray row mappedEntry = mappedEntry ++ [(propertyArray.name, list)]
    where
    primaryIdColumns = filter (\it -> isJust (isId it)) propertyArray.properties
    entryExists = propertyArray.name `elem` mappedEntry

    list = if entryExists
        then mappedEntry[propertyArray.name]
        else []

    mapped = buildEntry primaryIdColumns row list
    if mapped == null
        mappedEntry[propertyArray.name] = null
        return

    for property of propertyArray.properties
        switch property.type
        case "COLUMN"
            if property.isId
            continue
            extract property row mapped
        case "ONE"
            extractObject property row mapped
        case "MANY"
            extractArray property row mapped

    if entryExists
        return
    mappedEntry[propertyArray.name] = list

buildEntry :: [Property] -> Row -> Result -> Result | null
buildEntry primaryIdColumns row result = if any (\it -> row[it.column!!] == null) primaryIdColumns
    then null
    else existingEntry
    where
    existingEntry = find (\it -> all (\pkCol -> it[pkCol.name] == row[pkCol.column!!]) primaryIdColumns) result

    if existingEntry != undefined
        return existingEntry

    newEntry = []
    for property of primaryIdColumns
        newEntry ++ [(property.name, row[property.column!!])]

    result ++ [newEntry]
    return newEntry