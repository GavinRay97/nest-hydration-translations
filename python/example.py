from typing import Any, Dict, List, Optional


class Property:
    def __init__(self, name: str, column: str, is_id: bool = False):
        self.type = "COLUMN"
        self.name = name
        self.column = column
        self.is_id = is_id


class PropertyArray:
    def __init__(self, name: str, properties: List[Property]):
        self.type = "MANY"
        self.name = name
        self.properties = properties


class PropertyObject:
    def __init__(self, name: str, properties: List[Property]):
        self.type = "ONE"
        self.name = name
        self.properties = properties


class Row:
    def __init__(self, data: Dict[str, Any]):
        self.data = data


class Entry:
    def __init__(self, data: Dict[str, Any]):
        self.data = data


class Result:
    def __init__(self, data: Dict[str, Any]):
        self.data = data


def nest(dataset: List[Row], properties: List[Property]) -> List[Result]:
    result: List[Result] = []
    # Every property that has an "is_id" property and it's value is true
    primary_id_columns = [p for p in properties if hasattr(p, "is_id") and p.is_id]

    for row in dataset:
        mapped_entry = build_entry(primary_id_columns, row, result)
        if mapped_entry is None:
            continue

        for property in properties:
            if property.type == "COLUMN":
                if property.is_id:
                    continue
                extract(property, row, mapped_entry)
            elif property.type == "ONE":
                extract_object(property, row, mapped_entry)
            elif property.type == "MANY":
                extract_array(property, row, mapped_entry)
            else:
                raise Exception("Unknown property type")

    return result


def extract(property: Property, row: Row, mapped_entry: Result):
    mapped_entry.data[property.name] = row.data[property.column]


def extract_object(property_object: PropertyObject, row: Row, mapped_entry: Result):
    new_entry: Result = Result({})

    for property in property_object.properties:
        if property.type == "COLUMN":
            if property.is_id and row.data[property.column] is None:
                mapped_entry.data[property_object.name] = None
                return
            extract(property, row, new_entry)
        elif property.type == "ONE":
            extract_object(property, row, new_entry)
        elif property.type == "MANY":
            extract_array(property, row, new_entry)
        else:
            raise Exception("Unknown property type")

    mapped_entry.data[property_object.name] = new_entry


def extract_array(property_array: PropertyArray, row: Row, mapped_entry: Result):
    primary_id_columns = [p for p in property_array.properties if p.is_id]
    entry_exists = property_array.name in mapped_entry.data

    list = mapped_entry.data[property_array.name] if entry_exists else []

    mapped = build_entry(primary_id_columns, row, list)
    if mapped is None:
        mapped_entry.data[property_array.name] = None
        return

    for property in property_array.properties:
        if property.type == "COLUMN":
            if property.is_id:
                continue
            extract(property, row, mapped)
        elif property.type == "ONE":
            extract_object(property, row, mapped)
        elif property.type == "MANY":
            extract_array(property, row, mapped)
        else:
            raise Exception("Unknown property type")

    if entry_exists:
        return
    mapped_entry.data[property_array.name] = list


def build_entry(
    primary_id_columns: List[Property], row: Row, result: List[Result]
) -> Optional[Result]:
    if any(row.data[p.column] is None for p in primary_id_columns):
        return None

    existing_entry = next(
        (
            it
            for it in result
            if all(it.data[p.name] == row.data[p.column] for p in primary_id_columns)
        ),
        None,
    )

    if existing_entry is not None:
        return existing_entry

    new_entry: Result = Result({})
    for property in primary_id_columns:
        new_entry.data[property.name] = row.data[property.column]

    result.append(new_entry)
    return new_entry


def main():
    # Album, Artist, Tracks
    example_dataset = [
        Row(
            {
                "id": 1,
                "name": "Album 1",
                "artist_id": 1,
                "artist_name": "Artist 1",
                "track_id": 1,
                "track_title": "Track 1",
            }
        ),
        Row(
            {
                "id": 1,
                "name": "Album 1",
                "artist_id": 1,
                "artist_name": "Artist 1",
                "track_id": 2,
                "track_title": "Track 2",
            }
        ),
        Row(
            {
                "id": 2,
                "name": "Album 2",
                "artist_id": 1,
                "artist_name": "Artist 1",
                "track_id": 3,
                "track_title": "Track 3",
            }
        ),
    ]
    example_definition: List[Property] = [
        Property("id", "id", True),
        Property("name", "name"),
        PropertyObject(
            "artist",
            [
                Property("id", "artist_id", True),
                Property("name", "artist_name"),
            ],
        ),
        PropertyArray(
            "tracks",
            [
                Property("id", "track_id", True),
                Property("title", "track_title"),
            ],
        ),
    ]

    result = nest(example_dataset, example_definition)
    for r in result:
        print(r.data)


main()
