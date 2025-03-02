/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen;

import io.swagger.v3.oas.models.ExternalDocumentation;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * CodegenEnum represents a schema object in a OpenAPI document.
 */
public class CodegenEnum {

    public String name;

    public String classname;

    public String classFilename; // store the class file name, mainly used for import
    public String filePackage;

//    public List<String> importTo; // where to import enums (classFileName);

    public String description, dataType;

    public boolean isString, isInteger, isLong, isNumber, isNumeric, isFloat, isDouble, isDate, isDateTime,
            isDecimal, isShort, isUnboundedInteger, isPrimitiveType, isBoolean, isFreeFormObject;
    private boolean additionalPropertiesIsAnyType;
    public boolean isUri = false;

    public Set<EnumProperty> enumVars = new HashSet<>();
    public String datatypeWithEnum;
    public List<String> additionalEnumTypeAnnotations;
    public boolean useEnumCaseInsensitive;
    public boolean enumUnknownDefaultCase;
    public boolean jackson = false;
    public boolean gson = false;
    public boolean jsonb = false;

    public boolean hasVars, emptyVars, hasMoreModels, hasEnums, isEnum, hasValidation;
    /**
     * Indicates the OAS schema specifies "nullable: true".
     */
    public boolean isNullable;
    /**
     * Indicates the type has at least one required property.
     */
    public boolean hasRequired;
    /**
     * Indicates the OAS schema specifies "deprecated: true".
     */
    public boolean isDeprecated;
    /**
     * Indicates the type has at least one read-only property.
     */
    public boolean hasReadOnly;
    /**
     * Indicates the all properties of the type are read-only.
     */
    public boolean hasOnlyReadOnly = true;
    public ExternalDocumentation externalDocumentation;

    public Map<String, Object> vendorExtensions = new HashMap<>();
    private CodegenComposedSchemas composedSchemas;
    private boolean hasMultipleTypes = false;
    private String format;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }


    public Set<EnumProperty> getEnumVars() {
        return enumVars;
    }

    public void setEnumVars(Set<EnumProperty> enumVars) {
        this.enumVars = enumVars;
    }

    public String getClassFilename() {
        return classFilename;
    }

    public void setClassFilename(String classFilename) {
        this.classFilename = classFilename;
    }

    public String getFilePackage() {
        return filePackage;
    }

    public void setFilePackage(String filePackage) {
        this.filePackage = filePackage;
    }

    /**
     * Return true if the classname property is sanitized, false if it is the same as the OpenAPI schema name.
     * The OpenAPI schema name may be any valid JSON schema name, including non-ASCII characters.
     * The name of the class may have to be sanitized with character escaping.
     *
     * @return true if the classname property is sanitized
     */
    public boolean getIsClassnameSanitized() {
        return !StringUtils.equals(classname, name);
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname(String classname) {
        this.classname = classname;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDatatypeWithEnum() {
        return datatypeWithEnum;
    }

    public void setDatatypeWithEnum(String datatypeWithEnum) {
        this.datatypeWithEnum = datatypeWithEnum;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ExternalDocumentation getExternalDocumentation() {
        return externalDocumentation;
    }

    public void setExternalDocumentation(ExternalDocumentation externalDocumentation) {
        this.externalDocumentation = externalDocumentation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getIsDate() {
        return isDate;
    }

    public void setIsDate(boolean isDate) {
        this.isDate = isDate;
    }

    public boolean getIsDateTime() {
        return isDateTime;
    }

    public void setIsDateTime(boolean isDateTime) {
        this.isDateTime = isDateTime;
    }

    public boolean getIsShort() {
        return isShort;
    }

    public void setIsShort(boolean isShort) {
        this.isShort = isShort;
    }

    public boolean getIsBoolean() {
        return isBoolean;
    }

    public void setIsBoolean(boolean isBoolean) {
        this.isBoolean = isBoolean;
    }

    public boolean getIsUnboundedInteger() {
        return isUnboundedInteger;
    }

    public void setIsUnboundedInteger(boolean isUnboundedInteger) {
        this.isUnboundedInteger = isUnboundedInteger;
    }

    public boolean getIsPrimitiveType() {
        return isPrimitiveType;
    }

    public void setIsPrimitiveType(boolean isPrimitiveType) {
        this.isPrimitiveType = isPrimitiveType;
    }

    public boolean getIsUri() {
        return isUri;
    }

    public void setIsUri(boolean isUri) {
        this.isUri = isUri;
    }

    public boolean getHasValidation() {
        return hasValidation;
    }

    public void setHasValidation(boolean hasValidation) {
        this.hasValidation = hasValidation;
    }

    public Map<String, Object> getVendorExtensions() {
        return vendorExtensions;
    }

    public void setVendorExtensions(Map<String, Object> vendorExtensions) {
        this.vendorExtensions = vendorExtensions;
    }

    public boolean getAdditionalPropertiesIsAnyType() {
        return additionalPropertiesIsAnyType;
    }

    public void setAdditionalPropertiesIsAnyType(boolean additionalPropertiesIsAnyType) {
        this.additionalPropertiesIsAnyType = additionalPropertiesIsAnyType;
    }

    public boolean getHasVars() {
        return this.hasVars;
    }

    public void setHasVars(boolean hasVars) {
        this.hasVars = hasVars;
    }

    public boolean getHasRequired() {
        return this.hasRequired;
    }

    public void setHasRequired(boolean hasRequired) {
        this.hasRequired = hasRequired;
    }

    public boolean getIsString() {
        return isString;
    }

    public void setIsString(boolean isString) {
        this.isString = isString;
    }

    public boolean getIsNumber() {
        return isNumber;
    }

    public void setIsNumber(boolean isNumber) {
        this.isNumber = isNumber;
    }

    public boolean getIsFreeFormObject() {
        return isFreeFormObject;
    }

    public void setIsFreeFormObject(boolean isFreeFormObject) {
        this.isFreeFormObject = isFreeFormObject;
    }

    public void setComposedSchemas(CodegenComposedSchemas composedSchemas) {
        this.composedSchemas = composedSchemas;
    }

    public CodegenComposedSchemas getComposedSchemas() {
        return composedSchemas;
    }

    public boolean getHasMultipleTypes() {
        return hasMultipleTypes;
    }

    public void setHasMultipleTypes(boolean hasMultipleTypes) {
        this.hasMultipleTypes = hasMultipleTypes;
    }

    public boolean getIsFloat() {
        return isFloat;
    }

    public void setIsFloat(boolean isFloat) {
        this.isFloat = isFloat;
    }

    public boolean getIsDouble() {
        return isDouble;
    }

    public void setIsDouble(boolean isDouble) {
        this.isDouble = isDouble;
    }

    public boolean getIsInteger() {
        return isInteger;
    }

    public void setIsInteger(boolean isInteger) {
        this.isInteger = isInteger;
    }

    public boolean getIsLong() {
        return isLong;
    }

    public void setIsLong(boolean isLong) {
        this.isLong = isLong;
    }

    public boolean getIsBinary() {
        return false;
    }

    public void setIsBinary(boolean isBinary) {}

    public boolean getIsByteArray() {
        return false;
    }

    public void setIsByteArray(boolean isByteArray) {}

    public boolean getIsDecimal() {
        return isDecimal;
    }

    public void setIsDecimal(boolean isDecimal) {
        this.isDecimal = isDecimal;
    }

    public boolean getIsEnum() {
        return isEnum;
    }

    public void setIsEnum(boolean isEnum) {
        this.isEnum = isEnum;
    }

    public boolean getHasEnums() {
        return hasEnums;
    }

    public void setHasEnums(boolean hasEnums) {
        this.hasEnums = hasEnums;
    }


    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodegenEnum)) return false;
        CodegenEnum that = (CodegenEnum) o;
        return
                isString == that.isString &&
                isInteger == that.isInteger &&
                isShort == that.isShort &&
                isLong == that.isLong &&
                isUnboundedInteger == that.isUnboundedInteger &&
                isBoolean == that.isBoolean &&
                isNumber == that.isNumber &&
                isNumeric == that.isNumeric &&
                isFloat == that.isFloat &&
                isDouble == that.isDouble &&
                isDate == that.isDate &&
                isDateTime == that.isDateTime &&
                hasVars == that.hasVars &&
                emptyVars == that.emptyVars &&
                hasMoreModels == that.hasMoreModels &&
                hasEnums == that.hasEnums &&
                isEnum == that.isEnum &&
                isNullable == that.isNullable &&
                hasRequired == that.hasRequired &&
                isDeprecated == that.isDeprecated &&
                hasReadOnly == that.hasReadOnly &&
                hasOnlyReadOnly == that.hasOnlyReadOnly &&
                hasValidation == that.hasValidation &&
                isDecimal == that.isDecimal &&
                        isUri == that.isUri &&
                hasMultipleTypes == that.getHasMultipleTypes() &&
                        Objects.equals(datatypeWithEnum, that.datatypeWithEnum) &&
                Objects.equals(format, that.getFormat()) &&
                Objects.equals(filePackage, that.filePackage) &&
                Objects.equals(composedSchemas, that.composedSchemas) &&
                Objects.equals(name, that.name) &&
                Objects.equals(classname, that.classname) &&
                Objects.equals(description, that.description) &&
                Objects.equals(dataType, that.dataType) &&
                Objects.equals(classFilename, that.classFilename) &&
                Objects.equals(externalDocumentation, that.externalDocumentation) &&
                Objects.equals(vendorExtensions, that.vendorExtensions);
    }

    public int hashCode() {
        return Objects.hash(
                getName(), getClassname(),
                getDescription(), getDataType(), getClassFilename(), getFilePackage(), isString, isInteger, isLong, isNumber, isNumeric,
                isFloat, isDouble,
                isDate, isDateTime, hasValidation, isShort, isUnboundedInteger, isBoolean, isUri,
                getEnumVars(), hasVars, getDatatypeWithEnum(),
                isEmptyVars(), hasMoreModels, hasEnums, isEnum, isNullable, hasRequired,
                isDeprecated, hasReadOnly, hasOnlyReadOnly, getExternalDocumentation(), getVendorExtensions(),
                getAdditionalPropertiesIsAnyType(), getComposedSchemas(), hasMultipleTypes, isDecimal,
                format);
    }

    public String toString() {
        String sb = "CodegenEnum{" + "name='" + name + '\'' +
                ", classname='" + classname + '\'' +
                ", description='" + description + '\'' +
                ", dataType='" + dataType + '\'' +
                ", datatypeWithEnum='" + datatypeWithEnum + '\'' +
                ", classFilename='" + classFilename + '\'' +
                ", filePackage='" + filePackage + '\'' +
                ", isString=" + isString +
                ", isInteger=" + isInteger +
                ", isShort=" + isShort +
                ", isLong=" + isLong +
                ", isUnboundedInteger=" + isUnboundedInteger +
                ", isBoolean=" + isBoolean +
                ", isNumber=" + isNumber +
                ", isNumeric=" + isNumeric +
                ", isFloat=" + isFloat +
                ", isDouble=" + isDouble +
                ", isDate=" + isDate +
                ", isDateTime=" + isDateTime +
                ", enumVars=" + enumVars +
                ", hasVars=" + hasVars +
                ", emptyVars=" + emptyVars +
                ", hasMoreModels=" + hasMoreModels +
                ", hasEnums=" + hasEnums +
                ", isEnum=" + isEnum +
                ", isNullable=" + isNullable +
                ", hasRequired=" + hasRequired +
                ", isDeprecated=" + isDeprecated +
                ", hasReadOnly=" + hasReadOnly +
                ", hasOnlyReadOnly=" + hasOnlyReadOnly +
                ", externalDocumentation=" + externalDocumentation +
                ", vendorExtensions=" + vendorExtensions +
                ", hasValidation='" + hasValidation +
                ", getAdditionalPropertiesIsAnyType=" + getAdditionalPropertiesIsAnyType() +
                ", composedSchemas=" + composedSchemas +
                ", hasMultipleTypes=" + hasMultipleTypes +
                ", hasMultipleTypes=" + hasMultipleTypes +
                ", isDecimal=" + isDecimal +
                ", isUri=" + isUri +
                ", format=" + format +
                '}';
        return sb;
    }

    public boolean isEmptyVars() {
        return emptyVars;
    }

    public void setEmptyVars(boolean emptyVars) {
        this.emptyVars = emptyVars;
    }

}
