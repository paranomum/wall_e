package org.openapitools.codegen;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class EnumProperty implements Cloneable {

    public String name;
    public String value;
    public boolean isString = false;
    public boolean isInteger;
    public boolean isLong;
    public boolean isNumber = false;
    public boolean isNumeric = false;
    public boolean isFloat = false;
    public boolean isDouble = false;
    public boolean isDate = false;
    public boolean isDateTime = false;
    public boolean isDecimal = false;
    public boolean isShort = false;
    public boolean isUnboundedInteger = false;
    public boolean isPrimitiveType = false;
    public boolean isBoolean = false;
    public boolean isFreeFormObject;
    public boolean withXml = false;
    public boolean isNullable = false;
    public boolean enumUnknownDefaultCase = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnumProperty)) return false;
        EnumProperty that = (EnumProperty) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(value, that.value) &&
                isString == that.isString &&
                isInteger == that.isInteger &&
                isLong == that.isLong &&
                isNumber == that.isNumber &&
                isNumeric == that.isNumeric &&
                isFloat == that.isFloat &&
                isDouble == that.isDouble &&
                isDate == that.isDate &&
                isDateTime == that.isDateTime &&
                isDecimal == that.isDecimal &&
                isShort == that.isShort &&
                isUnboundedInteger == that.isUnboundedInteger &&
                isPrimitiveType == that.isPrimitiveType &&
                isBoolean == that.isBoolean &&
                isFreeFormObject == that.isFreeFormObject &&
                withXml == that.withXml &&
                isNullable == that.isNullable &&
                enumUnknownDefaultCase == that.enumUnknownDefaultCase;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getValue(), isString,
                isInteger, isLong, isNumber, isNumeric, isFloat, isDouble, isDate, isDateTime, isDecimal, isShort,
                isUnboundedInteger, isPrimitiveType, isBoolean, isFreeFormObject, withXml,
                isNullable, enumUnknownDefaultCase);
    }

    @Override
    public String toString() {
        String sb = "EnumProperty {" + "name = '" + name + '\'' +
                ", value = '" + value + '\'' +
                ", isString = '" + isString + '\'' +
                ", isInteger = '" + isInteger + '\'' +
                ", isLong = '" + isLong + '\'' +
                ", isNumber = '" + isNumber + '\'' +
                ", isNumeric = '" + isNumeric + '\'' +
                ", isFloat = '" + isFloat + '\'' +
                ", isDouble = '" + isDouble + '\'' +
                ", isDate = '" + isDate + '\'' +
                ", isDateTime = '" + isDateTime + '\'' +
                ", isDecimal = '" + isDecimal + '\'' +
                ", isShort = '" + isShort + '\'' +
                ", isUnboundedInteger = '" + isUnboundedInteger + '\'' +
                ", isPrimitiveType = '" + isPrimitiveType + '\'' +
                ", isBoolean = '" + isBoolean + '\'' +
                ", isFreeFormObject = '" + isFreeFormObject + '\'' +
                ", withXml = '" + withXml + '\'' +
                ", isNullable = '" + isNullable + '\'' +
                ", enumUnknownDefaultCase = '" + enumUnknownDefaultCase + '\'' +
                '}';
        return sb;
    }
}
