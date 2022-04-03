import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NestHydration {

    public static void main(String[] args) {
        List<Map<String, Object>> exampleDataset = List.of(
                Map.of(
                        "id", 1,
                        "name", "Album 1",
                        "artist_id", 1,
                        "artist_name", "Artist 1",
                        "track_id", 1,
                        "track_title", "Track 1"),
                Map.of(
                        "id", 1,
                        "name", "Album 1",
                        "artist_id", 1,
                        "artist_name", "Artist 1",
                        "track_id", 2,
                        "track_title", "Track 2"),
                Map.of(
                        "id", 2,
                        "name", "Album 2",
                        "artist_id", 2,
                        "artist_name", "Artist 2",
                        "track_id", 3,
                        "track_title", "Track 3"));

        List<IProperty> properties = List.of(
                new Property("a", "id", true),
                new Property("b", "name", false),
                new PropertyObject("artist", List.of(
                        new Property("id", "artist_id", true),
                        new Property("name", "artist_name", false))),
                new PropertyArray("tracks", List.of(
                        new Property("id", "track_id", true),
                        new Property("title", "track_title", false))));

        List<Map<String, Object>> result = nest(exampleDataset, properties);
        for (Map<String, Object> row : result) {
            System.out.println(Arrays.toString(row.entrySet().toArray()));
        }
    }

    public static class IProperty {
        public String name;
        public String type;
    }

    public static class Property extends IProperty {
        public final boolean isId;
        public final String column;

        public Property(String name, String column, boolean isId) {
            this.name = name;
            this.type = "COLUMN";
            this.isId = isId;
            this.column = column;
        }
    }

    public static class PropertyArray extends IProperty {
        public final List<IProperty> properties;

        public PropertyArray(String name, List<IProperty> properties) {
            this.name = name;
            this.type = "MANY";
            this.properties = properties;
        }
    }

    public static class PropertyObject extends IProperty {
        public final List<IProperty> properties;

        public PropertyObject(String name, List<IProperty> properties) {
            this.name = name;
            this.type = "ONE";
            this.properties = properties;
        }
    }

    public static List<Map<String, Object>> nest(List<Map<String, Object>> dataset, List<IProperty> properties) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Property> primaryIdColumns = properties.stream()
                .filter(it -> it instanceof Property p && p.isId)
                .map(Property.class::cast)
                .toList();

        for (Map<String, Object> row : dataset) {
            Map<String, Object> mappedEntry = buildEntry(primaryIdColumns, row, result);
            if (mappedEntry == null)
                continue;

            for (IProperty property : properties) {
                if (property instanceof Property p) {
                    if (p.isId)
                        continue;
                    extract(p, row, mappedEntry);
                }
                if (property instanceof PropertyObject po) {
                    extractObject(po, row, mappedEntry);
                }
                if (property instanceof PropertyArray pa) {
                    extractArray(pa, row, mappedEntry);
                }
            }
        }

        return result;
    }

    private static void extract(Property property, Map<String, Object> row, Map<String, Object> mappedEntry) {
        mappedEntry.put(property.name, row.get(property.column));
    }

    private static void extractObject(PropertyObject propertyObject, Map<String, Object> row,
            Map<String, Object> mappedEntry) {
        Map<String, Object> newEntry = new HashMap<>();

        for (IProperty property : propertyObject.properties) {
            if (property instanceof Property p) {
                if (p.isId && row.get(p.column) == null) {
                    mappedEntry.put(propertyObject.name, null);
                    return;
                }
                extract(p, row, newEntry);
            }
            if (property instanceof PropertyObject po) {
                extractObject(po, row, newEntry);
            }
            if (property instanceof PropertyArray pa) {
                extractArray(pa, row, newEntry);
            }
        }

        mappedEntry.put(propertyObject.name, newEntry);
    }

    private static void extractArray(PropertyArray propertyArray, Map<String, Object> row,
            Map<String, Object> mappedEntry) {
        List<Property> primaryIdColumns = propertyArray.properties.stream()
                .filter(it -> it instanceof Property p && p.isId)
                .map(Property.class::cast)
                .toList();

        boolean entryExists = mappedEntry.containsKey(propertyArray.name);

        List<Map<String, Object>> list = entryExists
                ? (List<Map<String, Object>>) mappedEntry.get(propertyArray.name)
                : new ArrayList<>();

        Map<String, Object> mapped = buildEntry(primaryIdColumns, row, list);
        if (mapped == null) {
            mappedEntry.put(propertyArray.name, null);
            return;
        }

        for (IProperty property : propertyArray.properties) {
            if (property instanceof Property p) {
                if (p.isId)
                    continue;
                extract(p, row, mapped);
            }
            if (property instanceof PropertyObject po) {
                extractObject(po, row, mapped);
            }
            if (property instanceof PropertyArray pa) {
                extractArray(pa, row, mapped);
            }
        }

        if (entryExists)
            return;

        mappedEntry.put(propertyArray.name, list);
    }

    private static Map<String, Object> buildEntry(
            List<Property> primaryIdColumns,
            Map<String, Object> row,
            List<Map<String, Object>> result) {
        if (primaryIdColumns.stream().anyMatch(it -> row.get(it.column) == null))
            return null;

        Map<String, Object> existingEntry = result.stream()
                .filter(it -> primaryIdColumns.stream()
                        .allMatch(pkCol -> it.get(pkCol.name).equals(row.get(pkCol.column))))
                .findFirst()
                .orElse(null);

        if (existingEntry != null)
            return existingEntry;

        Map<String, Object> newEntry = new HashMap<>();
        for (Property property : primaryIdColumns) {
            newEntry.put(property.name, row.get(property.column));
        }

        result.add(newEntry);
        return newEntry;
    }

}
