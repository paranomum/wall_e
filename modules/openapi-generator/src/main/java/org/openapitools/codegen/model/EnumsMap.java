package org.openapitools.codegen.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnumsMap extends HashMap<String, Object> {

    public EnumsMap() {}

    public void setEnums(List<EnumMap> enumMaps) {
        put("enums", enumMaps);
    }

    @SuppressWarnings("unchecked")
    public List<EnumMap> getEnums() {
        return (List<EnumMap>) get("enums");
    }

    public void setImports(List<Map<String, String>> imports) {
        put("imports", imports);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getImports() {
        return (List<Map<String, String>>) get("imports");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getImportsOrEmpty() {
        return (List<Map<String, String>>) getOrDefault("imports", new ArrayList<>());
    }
}
