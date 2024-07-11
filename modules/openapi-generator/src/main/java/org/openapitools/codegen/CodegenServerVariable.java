package org.openapitools.codegen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CodegenServerVariable {
    public String name;
    public String defaultValue;
    public String description;
    public List<String> enumValues;
    public String value;
    public Map<String, Object> vendorExtensions = new HashMap<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodegenServerVariable that = (CodegenServerVariable) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(defaultValue, that.defaultValue) &&
                Objects.equals(description, that.description) &&
                Objects.equals(enumValues, that.enumValues) &&
                Objects.equals(value, that.value) &&
                Objects.equals(vendorExtensions, that.vendorExtensions);
    }

    @Override
    public int hashCode() {

        return Objects.hash(name, defaultValue, description, enumValues, value, vendorExtensions);
    }

    @Override
    public String toString() {
        String sb = "CodegenServerVariable{" + "name='" + name + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                ", description='" + description + '\'' +
                ", enumValues=" + enumValues +
                ", value='" + value + '\'' +
                ", vendorExtensions=" + vendorExtensions +
                '}';
        return sb;
    }
}
