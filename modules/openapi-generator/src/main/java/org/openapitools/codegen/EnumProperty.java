package org.openapitools.codegen;

import java.util.Objects;

public class EnumProperty implements Cloneable {

    public String name;
    public String value;
    public boolean isString;
    public boolean isNullable;
    public boolean enumUnknownDefaultCase;

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }


    public boolean getIsString() {
        return this.isString;
    }

    public void setIsString(boolean isString) {
        this.isString = isString;
    }

    public boolean getIsNullable() {
        return this.isNullable;
    }

    public void setIsNullable(boolean IsNullable) {
        this.isNullable = IsNullable;
    }

    public void setIsEnumUnknownDefaultCase(boolean enumUnknownDefaultCase) {
        this.enumUnknownDefaultCase = enumUnknownDefaultCase;
    }

    public boolean getIsEnumUnknownDefaultCase() {
        return this.enumUnknownDefaultCase;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnumProperty)) return false;
        EnumProperty that = (EnumProperty) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(value, that.value) &&
                isNullable == that.isNullable &&
                isString == that.isString &&
                enumUnknownDefaultCase == that.enumUnknownDefaultCase;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getValue(), isString, isNullable, enumUnknownDefaultCase);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EnumProperty {");
        sb.append("name = '").append(name).append('\'');
        sb.append(", value = '").append(value).append('\'');
        sb.append(", isString = '").append(isString).append('\'');
        sb.append(", isNullable = '").append(isNullable).append('\'');
        sb.append(", enumUnknownDefaultCase = '").append(enumUnknownDefaultCase).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
