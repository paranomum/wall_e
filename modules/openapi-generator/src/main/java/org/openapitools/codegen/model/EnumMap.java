package org.openapitools.codegen.model;

import org.openapitools.codegen.CodegenEnum;
import org.openapitools.codegen.CodegenModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnumMap extends HashMap<String, Object>{

    public EnumMap() {

    }

    public EnumMap(Map<String, Object> init) {
        putAll(init);
    }

    public void setEnum(CodegenEnum _enum) {
        put("enum", _enum);
    }

    public CodegenEnum getEnum() {
        return (CodegenEnum) get("enum");
    }

    /**
     * Convert a list of ModelMap to map of CodegenModel.
     *
     * @param  allModels list of model map
     * @return           map of Codegen Model
     */
    static public HashMap<String, CodegenEnum> toCodegenEnumMap(List<EnumMap> allModels) {
        HashMap<String, CodegenEnum> enumMaps = new HashMap<>();

        for (EnumMap modelMap : allModels) {
            CodegenEnum m = modelMap.getEnum();
            enumMaps.put(m.classname, m);
        }

        return enumMaps;
    }
}
