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

package org.openapitools.codegen.java;

import org.junit.jupiter.api.Assertions;
import static org.openapitools.codegen.TestUtils.assertFileContains;
import static org.openapitools.codegen.TestUtils.assertFileNotContains;
import static org.openapitools.codegen.TestUtils.validateJavaSourceFiles;
import static org.openapitools.codegen.languages.JavaClientCodegen.USE_ENUM_CASE_INSENSITIVE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableMap;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.util.SchemaTypeUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.TestUtils;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.java.assertions.JavaFileAssert;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.openapitools.codegen.languages.features.BeanValidationFeatures;
import org.openapitools.codegen.languages.features.CXFServerFeatures;
import org.openapitools.codegen.meta.features.SecurityFeature;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

public class JavaClientCodegenTest {

    @Test
    public void arraysInRequestBody() {
        OpenAPI openAPI = TestUtils.createOpenAPI();
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOpenAPI(openAPI);

        RequestBody body1 = new RequestBody();
        body1.setDescription("A list of ids");
        body1.setContent(
                new Content()
                        .addMediaType(
                                "application/json",
                                new MediaType().schema(new ArraySchema().items(new StringSchema()))));
        CodegenParameter codegenParameter1 = codegen.fromRequestBody(body1, new HashSet<String>(), null);
        Assertions.assertEquals(codegenParameter1.description, "A list of ids");
        Assertions.assertEquals(codegenParameter1.dataType, "List<String>");
        Assertions.assertEquals(codegenParameter1.baseType, "String");

        RequestBody body2 = new RequestBody();
        body2.setDescription("A list of list of values");
        body2.setContent(
                new Content()
                        .addMediaType(
                                "application/json",
                                new MediaType()
                                        .schema(
                                                new ArraySchema().items(new ArraySchema().items(new IntegerSchema())))));
        CodegenParameter codegenParameter2 = codegen.fromRequestBody(body2, new HashSet<String>(), null);
        Assertions.assertEquals(codegenParameter2.description, "A list of list of values");
        Assertions.assertEquals(codegenParameter2.dataType, "List<List<Integer>>");
        Assertions.assertEquals(codegenParameter2.baseType, "List");

        RequestBody body3 = new RequestBody();
        body3.setDescription("A list of points");
        body3.setContent(
                new Content()
                        .addMediaType(
                                "application/json",
                                new MediaType()
                                        .schema(
                                                new ArraySchema()
                                                        .items(new ObjectSchema().$ref("#/components/schemas/Point")))));
        ObjectSchema point = new ObjectSchema();
        point.addProperty("message", new StringSchema());
        point.addProperty("x", new IntegerSchema().format(SchemaTypeUtil.INTEGER32_FORMAT));
        point.addProperty("y", new IntegerSchema().format(SchemaTypeUtil.INTEGER32_FORMAT));
        CodegenParameter codegenParameter3 = codegen.fromRequestBody(body3, new HashSet<String>(), null);
        Assertions.assertEquals(codegenParameter3.description, "A list of points");
        Assertions.assertEquals(codegenParameter3.dataType, "List<Point>");
        Assertions.assertEquals(codegenParameter3.baseType, "Point");
    }

    @Test
    public void nullValuesInComposedSchema() {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        ComposedSchema schema = new ComposedSchema();
        CodegenModel result = codegen.fromModel("CompSche",
                schema);
        Assertions.assertEquals(result.name, "CompSche");
    }

    @Test
    public void testParametersAreCorrectlyOrderedWhenUsingRetrofit() {
        JavaClientCodegen javaClientCodegen = new JavaClientCodegen();
        javaClientCodegen.setLibrary(JavaClientCodegen.RETROFIT_2);

        CodegenOperation codegenOperation = new CodegenOperation();
        CodegenParameter queryParamRequired = createQueryParam("queryParam1", true);
        CodegenParameter queryParamOptional = createQueryParam("queryParam2", false);
        CodegenParameter pathParam1 = createPathParam("pathParam1");
        CodegenParameter pathParam2 = createPathParam("pathParam2");

        codegenOperation.allParams.addAll(Arrays.asList(queryParamRequired, pathParam1, pathParam2, queryParamOptional));
        OperationMap operations = new OperationMap();
        operations.setOperation(codegenOperation);

        OperationsMap objs = new OperationsMap();
        objs.setOperation(operations);
        objs.setImports(new ArrayList<>());

        javaClientCodegen.postProcessOperationsWithModels(objs, Collections.emptyList());

        Assertions.assertEquals(Arrays.asList(pathParam1, pathParam2, queryParamRequired, queryParamOptional), codegenOperation.allParams);
    }

    @Test
    public void testInitialConfigValues() throws Exception {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.processOpts();

        Assertions.assertEquals(codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP), Boolean.FALSE);
        Assertions.assertFalse(codegen.isHideGenerationTimestamp());

        Assertions.assertEquals(codegen.modelPackage(), "org.openapitools.client.model");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE),
                "org.openapitools.client.model");
        Assertions.assertEquals(codegen.apiPackage(), "org.openapitools.client.api");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.API_PACKAGE),
                "org.openapitools.client.api");
        Assertions.assertEquals(codegen.getInvokerPackage(), "org.openapitools.client");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE),
                "org.openapitools.client");
        Assertions.assertEquals(codegen.getSerializationLibrary(), JavaClientCodegen.SERIALIZATION_LIBRARY_GSON);
    }

    @Test
    public void testSettersForConfigValues() throws Exception {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setHideGenerationTimestamp(true);
        codegen.setModelPackage("xyz.yyyyy.zzzzzzz.model");
        codegen.setApiPackage("xyz.yyyyy.zzzzzzz.api");
        codegen.setInvokerPackage("xyz.yyyyy.zzzzzzz.invoker");
        codegen.setSerializationLibrary("JACKSON");
        codegen.processOpts();

        Assertions.assertEquals(codegen.additionalProperties().get(CodegenConstants.HIDE_GENERATION_TIMESTAMP), Boolean.TRUE);
        Assertions.assertTrue(codegen.isHideGenerationTimestamp());
        Assertions.assertEquals(codegen.modelPackage(), "xyz.yyyyy.zzzzzzz.model");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE),
                "xyz.yyyyy.zzzzzzz.model");
        Assertions.assertEquals(codegen.apiPackage(), "xyz.yyyyy.zzzzzzz.api");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.API_PACKAGE), "xyz.yyyyy.zzzzzzz.api");
        Assertions.assertEquals(codegen.getInvokerPackage(), "xyz.yyyyy.zzzzzzz.invoker");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE),
                "xyz.yyyyy.zzzzzzz.invoker");
        Assertions.assertEquals(codegen.getSerializationLibrary(), JavaClientCodegen.SERIALIZATION_LIBRARY_GSON); // the library JavaClientCodegen.OKHTTP_GSON only supports GSON
    }

    @Test
    public void testImportMappingResult() throws IOException {
        File output = Files.createTempDirectory("test").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .addTypeMapping("OffsetDateTime", "Instant")
                .addImportMapping("OffsetDateTime", "java.time.Instant")
                .setGeneratorName("java")
                .setInputSpec("src/test/resources/3_0/echo_api.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();

        DefaultGenerator generator = new DefaultGenerator();

        List<File> files = generator.opts(clientOptInput).generate();

        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/org/openapitools/client/api/QueryApi.java"),
                "import java.time.Instant;");
    }

    @Test
    public void testPackageNamesSetInvokerDerivedFromApi() {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen
                .additionalProperties()
                .put(CodegenConstants.MODEL_PACKAGE, "xyz.yyyyy.zzzzzzz.mmmmm.model");
        codegen.additionalProperties().put(CodegenConstants.API_PACKAGE, "xyz.yyyyy.zzzzzzz.aaaaa.api");
        codegen.processOpts();

        Assertions.assertEquals(codegen.modelPackage(), "xyz.yyyyy.zzzzzzz.mmmmm.model");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE),
                "xyz.yyyyy.zzzzzzz.mmmmm.model");
        Assertions.assertEquals(codegen.apiPackage(), "xyz.yyyyy.zzzzzzz.aaaaa.api");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.API_PACKAGE),
                "xyz.yyyyy.zzzzzzz.aaaaa.api");
        Assertions.assertEquals(codegen.getInvokerPackage(), "xyz.yyyyy.zzzzzzz.aaaaa");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE),
                "xyz.yyyyy.zzzzzzz.aaaaa");
    }

    @Test
    public void testPackageNamesSetInvokerDerivedFromModel() {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen
                .additionalProperties()
                .put(CodegenConstants.MODEL_PACKAGE, "xyz.yyyyy.zzzzzzz.mmmmm.model");
        codegen.processOpts();

        Assertions.assertEquals(codegen.modelPackage(), "xyz.yyyyy.zzzzzzz.mmmmm.model");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.MODEL_PACKAGE),
                "xyz.yyyyy.zzzzzzz.mmmmm.model");
        Assertions.assertEquals(codegen.apiPackage(), "org.openapitools.client.api");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.API_PACKAGE),
                "org.openapitools.client.api");
        Assertions.assertEquals(codegen.getInvokerPackage(), "xyz.yyyyy.zzzzzzz.mmmmm");
        Assertions.assertEquals(
                codegen.additionalProperties().get(CodegenConstants.INVOKER_PACKAGE),
                "xyz.yyyyy.zzzzzzz.mmmmm");
    }

    @Test
    public void testGetSchemaTypeWithComposedSchemaWithAllOf() {
        final OpenAPI openAPI =
                TestUtils.parseFlattenSpec("src/test/resources/2_0/composed-allof.yaml");
        final JavaClientCodegen codegen = new JavaClientCodegen();

        Operation operation = openAPI.getPaths().get("/ping").getPost();
        CodegenOperation co = codegen.fromOperation("/ping", "POST", operation, null);
        Assertions.assertEquals(co.allParams.size(), 1);
        Assertions.assertEquals(co.allParams.get(0).baseType, "MessageEventCoreWithTimeListEntries");
    }

    @Test
    public void updateCodegenPropertyEnum() {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        CodegenProperty array = codegenPropertyWithArrayOfIntegerValues();

        codegen.updateCodegenPropertyEnum(array);

        List<Map<String, String>> enumVars =
                (List<Map<String, String>>) array.getItems().getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Map<String, String> testedEnumVar = enumVars.get(0);
        Assertions.assertNotNull(testedEnumVar);
        Assertions.assertEquals(testedEnumVar.getOrDefault("name", ""), "NUMBER_1");
        Assertions.assertEquals(testedEnumVar.getOrDefault("value", ""), "1");
    }

    @Test
    public void updateCodegenPropertyEnumWithCustomNames() {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        CodegenProperty array = codegenPropertyWithArrayOfIntegerValues();
        array
                .getItems()
                .setVendorExtensions(
                        Collections.singletonMap("x-enum-varnames", Collections.singletonList("ONE")));

        codegen.updateCodegenPropertyEnum(array);

        List<Map<String, String>> enumVars =
                (List<Map<String, String>>) array.getItems().getAllowableValues().get("enumVars");
        Assertions.assertNotNull(enumVars);
        Map<String, String> testedEnumVar = enumVars.get(0);
        Assertions.assertNotNull(testedEnumVar);
        Assertions.assertEquals(testedEnumVar.getOrDefault("name", ""), "ONE");
        Assertions.assertEquals(testedEnumVar.getOrDefault("value", ""), "1");
    }

    @Test
    public void testReferencedHeader() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue855.yaml");
        JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOpenAPI(openAPI);

        ApiResponse ok_200 = openAPI.getComponents().getResponses().get("OK_200");
        CodegenResponse response = codegen.fromResponse("200", ok_200);

        Assertions.assertEquals(response.headers.size(), 1);
        CodegenProperty header = response.headers.get(0);
        Assertions.assertEquals(header.dataType, "UUID");
        Assertions.assertEquals(header.baseName, "Request");
    }

    @Test
    public void testAuthorizationScopeValues_Issue392() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue392.yaml");

        final DefaultGenerator defaultGenerator = new DefaultGenerator();

        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.openAPI(openAPI);
        clientOptInput.config(new JavaClientCodegen());

        defaultGenerator.opts(clientOptInput);
        final List<CodegenOperation> codegenOperations =
                defaultGenerator.processPaths(openAPI.getPaths()).get("Pet");

        // Verify GET only has 'read' scope
        final CodegenOperation getCodegenOperation =
                codegenOperations.stream()
                        .filter(it -> it.httpMethod.equals("GET"))
                        .collect(Collectors.toList())
                        .get(0);
        assertTrue(getCodegenOperation.hasAuthMethods);
        assertEquals(getCodegenOperation.authMethods.size(), 1);
        final List<Map<String, Object>> getScopes = getCodegenOperation.authMethods.get(0).scopes;
        assertEquals(getScopes.size(), 1, "GET scopes don't match. actual::" + getScopes);

        // POST operation should have both 'read' and 'write' scope on it
        final CodegenOperation postCodegenOperation =
                codegenOperations.stream()
                        .filter(it -> it.httpMethod.equals("POST"))
                        .collect(Collectors.toList())
                        .get(0);
        assertTrue(postCodegenOperation.hasAuthMethods);
        assertEquals(postCodegenOperation.authMethods.size(), 1);
        final List<Map<String, Object>> postScopes = postCodegenOperation.authMethods.get(0).scopes;
        assertEquals(postScopes.size(), 2, "POST scopes don't match. actual:" + postScopes);
    }

    @Test
    public void testAuthorizationsMethodsSizeWhenFiltered() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue4584.yaml");

        final DefaultGenerator defaultGenerator = new DefaultGenerator();

        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.openAPI(openAPI);
        clientOptInput.config(new JavaClientCodegen());

        defaultGenerator.opts(clientOptInput);
        final List<CodegenOperation> codegenOperations =
                defaultGenerator.processPaths(openAPI.getPaths()).get("Pet");

        final CodegenOperation getCodegenOperation =
                codegenOperations.stream()
                        .filter(it -> it.httpMethod.equals("GET"))
                        .collect(Collectors.toList())
                        .get(0);
        assertTrue(getCodegenOperation.hasAuthMethods);
        assertEquals(getCodegenOperation.authMethods.size(), 2);
    }

    @Test
    public void testFreeFormObjects() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue796.yaml");
        JavaClientCodegen codegen = new JavaClientCodegen();

        Schema test1 = openAPI.getComponents().getSchemas().get("MapTest1");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm1 = codegen.fromModel("MapTest1", test1);
        Assertions.assertEquals(cm1.getDataType(), "Map");
        Assertions.assertEquals(cm1.getParent(), "HashMap<String, Object>");
        Assertions.assertEquals(cm1.getClassname(), "MapTest1");

        Schema test2 = openAPI.getComponents().getSchemas().get("MapTest2");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm2 = codegen.fromModel("MapTest2", test2);
        Assertions.assertEquals(cm2.getDataType(), "Map");
        Assertions.assertEquals(cm2.getParent(), "HashMap<String, Object>");
        Assertions.assertEquals(cm2.getClassname(), "MapTest2");

        Schema test3 = openAPI.getComponents().getSchemas().get("MapTest3");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm3 = codegen.fromModel("MapTest3", test3);
        Assertions.assertEquals(cm3.getDataType(), "Map");
        Assertions.assertEquals(cm3.getParent(), "HashMap<String, Object>");
        Assertions.assertEquals(cm3.getClassname(), "MapTest3");

        Schema other = openAPI.getComponents().getSchemas().get("OtherObj");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm = codegen.fromModel("OtherObj", other);
        Assertions.assertEquals(cm.getDataType(), "Object");
        Assertions.assertEquals(cm.getClassname(), "OtherObj");
    }

    @Test
    public void testBearerAuth() {
        final OpenAPI openAPI =
                TestUtils.parseFlattenSpec("src/test/resources/3_0/pingBearerAuth.yaml");
        JavaClientCodegen codegen = new JavaClientCodegen();

        List<CodegenSecurity> security = codegen.fromSecurity(openAPI.getComponents().getSecuritySchemes());
        Assertions.assertEquals(security.size(), 1);
        Assertions.assertEquals(security.get(0).isBasic, Boolean.TRUE);
        Assertions.assertEquals(security.get(0).isBasicBasic, Boolean.FALSE);
        Assertions.assertEquals(security.get(0).isBasicBearer, Boolean.TRUE);
    }

    private CodegenProperty codegenPropertyWithArrayOfIntegerValues() {
        CodegenProperty array = new CodegenProperty();
        final CodegenProperty items = new CodegenProperty();
        final HashMap<String, Object> allowableValues = new HashMap<>();
        allowableValues.put("values", Collections.singletonList(1));
        items.setAllowableValues(allowableValues);
        items.dataType = "Integer";
        array.setItems(items);
        array.dataType = "Array";
        array.mostInnerItems = items;
        return array;
    }

    private CodegenParameter createPathParam(String name) {
        CodegenParameter codegenParameter = createStringParam(name);
        codegenParameter.isPathParam = true;
        return codegenParameter;
    }

    private CodegenParameter createQueryParam(String name, boolean required) {
        CodegenParameter codegenParameter = createStringParam(name);
        codegenParameter.isQueryParam = true;
        codegenParameter.required = required;
        return codegenParameter;
    }

    private CodegenParameter createStringParam(String name) {
        CodegenParameter codegenParameter = new CodegenParameter();
        codegenParameter.paramName = name;
        codegenParameter.baseName = name;
        codegenParameter.dataType = "String";
        return codegenParameter;
    }

    @Test
    public void escapeName() {
        final JavaClientCodegen codegen = new JavaClientCodegen();
        assertEquals(codegen.toApiVarName("Default"), "_default");
        assertEquals(codegen.toApiVarName("int"), "_int");
        assertEquals(codegen.toApiVarName("pony"), "pony");
    }

    @Test
    public void testAnyType() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/any_type.yaml");
        JavaClientCodegen codegen = new JavaClientCodegen();

        Schema test1 = openAPI.getComponents().getSchemas().get("AnyValueModel");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm1 = codegen.fromModel("AnyValueModel", test1);
        Assertions.assertEquals(cm1.getClassname(), "AnyValueModel");

        final CodegenProperty property1 = cm1.allVars.get(0);
        Assertions.assertEquals(property1.baseName, "any_value");
        Assertions.assertEquals(property1.dataType, "Object");
        Assertions.assertTrue(property1.isPrimitiveType);
        Assertions.assertFalse(property1.isContainer);
        Assertions.assertFalse(property1.isFreeFormObject);
        Assertions.assertTrue(property1.isAnyType);

        final CodegenProperty property2 = cm1.allVars.get(1);
        Assertions.assertEquals(property2.baseName, "any_value_with_desc");
        Assertions.assertEquals(property2.dataType, "Object");
        Assertions.assertFalse(property2.required);
        Assertions.assertTrue(property2.isPrimitiveType);
        Assertions.assertFalse(property2.isContainer);
        Assertions.assertFalse(property2.isFreeFormObject);
        Assertions.assertTrue(property2.isAnyType);

        final CodegenProperty property3 = cm1.allVars.get(2);
        Assertions.assertEquals(property3.baseName, "any_value_nullable");
        Assertions.assertEquals(property3.dataType, "Object");
        Assertions.assertFalse(property3.required);
        Assertions.assertTrue(property3.isPrimitiveType);
        Assertions.assertFalse(property3.isContainer);
        Assertions.assertFalse(property3.isFreeFormObject);
        Assertions.assertTrue(property3.isAnyType);

        Schema test2 = openAPI.getComponents().getSchemas().get("AnyValueModelInline");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm2 = codegen.fromModel("AnyValueModelInline", test2);
        Assertions.assertEquals(cm2.getClassname(), "AnyValueModelInline");

        final CodegenProperty cp1 = cm2.vars.get(0);
        Assertions.assertEquals(cp1.baseName, "any_value");
        Assertions.assertEquals(cp1.dataType, "Object");
        Assertions.assertFalse(cp1.required);
        Assertions.assertTrue(cp1.isPrimitiveType);
        Assertions.assertFalse(cp1.isContainer);
        Assertions.assertFalse(cp1.isFreeFormObject);
        Assertions.assertTrue(cp1.isAnyType);

        final CodegenProperty cp2 = cm2.vars.get(1);
        Assertions.assertEquals(cp2.baseName, "any_value_with_desc");
        Assertions.assertEquals(cp2.dataType, "Object");
        Assertions.assertFalse(cp2.required);
        Assertions.assertTrue(cp2.isPrimitiveType);
        Assertions.assertFalse(cp2.isContainer);
        Assertions.assertFalse(cp2.isFreeFormObject);
        Assertions.assertTrue(cp2.isAnyType);

        final CodegenProperty cp3 = cm2.vars.get(2);
        Assertions.assertEquals(cp3.baseName, "any_value_nullable");
        Assertions.assertEquals(cp3.dataType, "Object");
        Assertions.assertFalse(cp3.required);
        Assertions.assertTrue(cp3.isPrimitiveType);
        Assertions.assertFalse(cp3.isContainer);
        Assertions.assertFalse(cp3.isFreeFormObject);
        Assertions.assertTrue(cp3.isAnyType);

        // map
        // Should allow in any type including map, https://github.com/swagger-api/swagger-parser/issues/1603
        final CodegenProperty cp4 = cm2.vars.get(3);
        Assertions.assertEquals(cp4.baseName, "map_free_form_object");
        Assertions.assertEquals(cp4.dataType, "Map<String, Object>");
        Assertions.assertFalse(cp4.required);
        Assertions.assertTrue(cp4.isPrimitiveType);
        Assertions.assertTrue(cp4.isContainer);
        Assertions.assertTrue(cp4.isMap);
        Assertions.assertTrue(cp4.isFreeFormObject);
        Assertions.assertFalse(cp4.isAnyType);
        Assertions.assertFalse(cp4.isModel);

        // Should allow in any type including map, https://github.com/swagger-api/swagger-parser/issues/1603
        final CodegenProperty cp5 = cm2.vars.get(4);
        Assertions.assertEquals(cp5.baseName, "map_any_value_with_desc");
        Assertions.assertEquals(cp5.dataType, "Map<String, Object>");
        Assertions.assertFalse(cp5.required);
        Assertions.assertTrue(cp5.isPrimitiveType);
        Assertions.assertTrue(cp5.isContainer);
        Assertions.assertTrue(cp5.isMap);
        Assertions.assertTrue(cp5.isFreeFormObject);
        Assertions.assertFalse(cp5.isAnyType);
        Assertions.assertFalse(cp5.isModel);

        // Should allow in any type including map, https://github.com/swagger-api/swagger-parser/issues/1603
        final CodegenProperty cp6 = cm2.vars.get(5);
        Assertions.assertEquals(cp6.baseName, "map_any_value_nullable");
        Assertions.assertEquals(cp6.dataType, "Map<String, Object>");
        Assertions.assertFalse(cp6.required);
        Assertions.assertTrue(cp6.isPrimitiveType);
        Assertions.assertTrue(cp6.isContainer);
        Assertions.assertTrue(cp6.isMap);
        Assertions.assertTrue(cp6.isFreeFormObject);
        Assertions.assertFalse(cp6.isAnyType);

        // array
        // Should allow in any type including array, https://github.com/swagger-api/swagger-parser/issues/1603
        final CodegenProperty cp7 = cm2.vars.get(6);
        Assertions.assertEquals(cp7.baseName, "array_any_value");
        Assertions.assertEquals(cp7.dataType, "List<Object>");
        Assertions.assertFalse(cp7.required);
        Assertions.assertTrue(cp7.isPrimitiveType);
        Assertions.assertTrue(cp7.isContainer);
        Assertions.assertTrue(cp7.isArray);
        Assertions.assertFalse(cp7.isFreeFormObject);
        Assertions.assertFalse(cp7.isAnyType);

        // Should allow in any type including array, https://github.com/swagger-api/swagger-parser/issues/1603
        final CodegenProperty cp8 = cm2.vars.get(7);
        Assertions.assertEquals(cp8.baseName, "array_any_value_with_desc");
        Assertions.assertEquals(cp8.dataType, "List<Object>");
        Assertions.assertFalse(cp8.required);
        Assertions.assertTrue(cp8.isPrimitiveType);
        Assertions.assertTrue(cp8.isContainer);
        Assertions.assertTrue(cp8.isArray);
        Assertions.assertFalse(cp8.isFreeFormObject);
        Assertions.assertFalse(cp8.isAnyType);

        // Should allow in any type including array, https://github.com/swagger-api/swagger-parser/issues/1603
        final CodegenProperty cp9 = cm2.vars.get(8);
        Assertions.assertEquals(cp9.baseName, "array_any_value_nullable");
        Assertions.assertEquals(cp9.dataType, "List<Object>");
        Assertions.assertFalse(cp9.required);
        Assertions.assertTrue(cp9.isPrimitiveType);
        Assertions.assertTrue(cp9.isContainer);
        Assertions.assertTrue(cp9.isArray);
        Assertions.assertFalse(cp9.isFreeFormObject);
        Assertions.assertFalse(cp9.isAnyType);
    }

    /**
     * See https://github.com/OpenAPITools/openapi-generator/issues/4803
     * <p>
     * UPDATE: the following test has been ignored due to https://github.com/OpenAPITools/openapi-generator/pull/11081/
     * We will contact the contributor of the following test to see if the fix will break their use cases and
     * how we can fix it accordingly.
     */
    @Test
    @Ignore
    public void testWebClientFormMultipart() throws IOException {

        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");


        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.WEBCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/form-multipart-binary-array.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));


        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/MultipartApi.java");
        TestUtils.assertFileContains(
                defaultApi,
                // multiple files
                "multipartArray(List<File> files)",
                "formParams.addAll(\"files\","
                        + " files.stream().map(FileSystemResource::new).collect(Collectors.toList()));",

                // mixed
                "multipartMixed(File file, MultipartMixedMarker marker)",
                "formParams.add(\"file\", new FileSystemResource(file));",

                // single file
                "multipartSingle(File file)",
                "formParams.add(\"file\", new FileSystemResource(file));");
    }

    @Test
    public void shouldGenerateBlockingAndNoBlockingOperationsForWebClient() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.WEBCLIENT_BLOCKING_OPERATIONS, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator =
                new CodegenConfigurator()
                        .setGeneratorName("java")
                        .setLibrary(JavaClientCodegen.WEBCLIENT)
                        .setAdditionalProperties(properties)
                        .setInputSpec(
                                "src/test/resources/3_0/petstore-with-fake-endpoints-models-for-testing.yaml")
                        .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        DefaultGenerator generator = new DefaultGenerator();
        Map<String, File> files = generator.opts(configurator.toClientOptInput()).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("StoreApi.java"))
                .assertMethod("getInventory")
                .hasReturnType(
                        "Mono<Map<String, Integer>>") // explicit 'x-webclient-blocking: false' which overrides
                // global config
                .toFileAssert()
                .assertMethod("placeOrder")
                .hasReturnType("Order"); // use global config

        JavaFileAssert.assertThat(files.get("PetApi.java"))
                .assertMethod("findPetsByStatus")
                .hasReturnType(
                        "List<Pet>"); // explicit 'x-webclient-blocking: true' which overrides global config
    }

    @Test
    void testNotDuplicateOauth2FlowsScopes() {
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/issue_7614.yaml");

        final ClientOptInput clientOptInput = new ClientOptInput()
                .openAPI(openAPI)
                .config(new JavaClientCodegen());

        final DefaultGenerator defaultGenerator = new DefaultGenerator();
        defaultGenerator.opts(clientOptInput);

        final Map<String, List<CodegenOperation>> paths = defaultGenerator.processPaths(openAPI.getPaths());
        final List<CodegenOperation> codegenOperations = paths.values().stream().flatMap(Collection::stream).collect(Collectors.toList());

        final CodegenOperation getWithBasicAuthAndOauth =
                getByOperationId(codegenOperations, "getWithBasicAuthAndOauth");
        assertEquals(getWithBasicAuthAndOauth.authMethods.size(), 3);
        assertEquals(getWithBasicAuthAndOauth.authMethods.get(0).name, "basic_auth");
        final Map<String, Object> passwordFlowScope = getWithBasicAuthAndOauth.authMethods.get(1).scopes.get(0);
        assertEquals(passwordFlowScope.get("scope"), "something:create");
        assertEquals(passwordFlowScope.get("description"), "create from password flow");
        final Map<String, Object> clientCredentialsFlow = getWithBasicAuthAndOauth.authMethods.get(2).scopes.get(0);
        assertEquals(clientCredentialsFlow.get("scope"), "something:create");
        assertEquals(clientCredentialsFlow.get("description"), "create from client credentials flow");

        final CodegenOperation getWithOauthAuth =
                getByOperationId(codegenOperations, "getWithOauthAuth");
        assertEquals(getWithOauthAuth.authMethods.size(), 2);
        final Map<String, Object> passwordFlow = getWithOauthAuth.authMethods.get(0).scopes.get(0);
        assertEquals(passwordFlow.get("scope"), "something:create");
        assertEquals(passwordFlow.get("description"), "create from password flow");

        final Map<String, Object> clientCredentialsCreateFlow = getWithOauthAuth.authMethods.get(1).scopes.get(0);
        assertEquals(clientCredentialsCreateFlow.get("scope"), "something:create");
        assertEquals(
                clientCredentialsCreateFlow.get("description"), "create from client credentials flow");

        final Map<String, Object> clientCredentialsProcessFlow = getWithOauthAuth.authMethods.get(1).scopes.get(1);
        assertEquals(clientCredentialsProcessFlow.get("scope"), "something:process");
        assertEquals(
                clientCredentialsProcessFlow.get("description"), "process from client credentials flow");
    }

    private CodegenOperation getByOperationId(List<CodegenOperation> codegenOperations, String operationId) {
        return getByCriteria(codegenOperations, (co) -> co.operationId.equals(operationId))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        String.format(
                                                Locale.ROOT, "Operation with id [%s] does not exist", operationId)));
    }

    private Optional<CodegenOperation> getByCriteria(List<CodegenOperation> codegenOperations, Predicate<CodegenOperation> filter) {
        return codegenOperations.stream()
                .filter(filter)
                .findFirst();
    }

    /**
     * See https://github.com/OpenAPITools/openapi-generator/issues/6715
     * <p>
     * UPDATE: the following test has been ignored due to https://github.com/OpenAPITools/openapi-generator/pull/11081/
     * We will contact the contributor of the following test to see if the fix will break their use cases and
     * how we can fix it accordingly.
     */
    @Test
    @Ignore
    public void testWebClientWithUseAbstractionForFiles() throws IOException {

        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.USE_ABSTRACTION_FOR_FILES, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.WEBCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/form-multipart-binary-array.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));


        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/MultipartApi.java");
        TestUtils.assertFileContains(
                defaultApi,
                // multiple files
                "multipartArray(java.util.Collection<org.springframework.core.io.AbstractResource> files)",
                "formParams.addAll(\"files\", files.stream().collect(Collectors.toList()));",

                // mixed
                "multipartMixed(org.springframework.core.io.AbstractResource file, MultipartMixedMarker"
                        + " marker)",
                "formParams.add(\"file\", file);",

                // single file
                "multipartSingle(org.springframework.core.io.AbstractResource file)",
                "formParams.add(\"file\", file);");
    }

    /**
     * See https://github.com/OpenAPITools/openapi-generator/issues/8352
     */
    @Test
    public void testWebClientWithFreeFormInQueryParameters() throws IOException {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");

        final File output = Files.createTempDirectory("test")
                .toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator().setGeneratorName("java")
                .setLibrary(JavaClientCodegen.WEBCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/issue8352.yaml")
                .setOutputDir(output.getAbsolutePath()
                        .replace("\\", "/"));

        final DefaultGenerator generator = new DefaultGenerator();
        final List<File> files = generator.opts(configurator.toClientOptInput())
                .generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        final Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/ApiClient.java");
        TestUtils.assertFileContains(defaultApi, "value instanceof Map");
    }

    @Test
    public void testExtraAnnotationsMicroprofile() throws IOException {
        testExtraAnnotations(JavaClientCodegen.MICROPROFILE);
    }

    @Test
    public void testExtraAnnotationsVertx() throws IOException {
        testExtraAnnotations(JavaClientCodegen.VERTX);
    }

    @Test
    public void testExtraAnnotationsRetrofit2() throws IOException {
        testExtraAnnotations(JavaClientCodegen.RETROFIT_2);
    }

    @Test
    public void testExtraAnnotationsWebClient() throws IOException {
        testExtraAnnotations(JavaClientCodegen.WEBCLIENT);
    }

    @Test
    public void testExtraAnnotationsRestAssured() throws IOException {
        testExtraAnnotations(JavaClientCodegen.REST_ASSURED);
    }

    @Test
    public void testExtraAnnotationsApache() throws IOException {
        testExtraAnnotations(JavaClientCodegen.APACHE);
    }

    @Test
    public void testDefaultMicroprofileRestClientVersion() throws Exception {
        File output = Files.createTempDirectory("test").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.MICROPROFILE)
                .setInputSpec("src/test/resources/3_0/petstore.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(clientOptInput).generate();

        TestUtils.ensureContainsFile(files, output, "pom.xml");

        validateJavaSourceFiles(files);

        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<microprofile.rest.client.api.version>2.0</microprofile.rest.client.api.version>");
        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<smallrye.rest.client.version>1.2.1</smallrye.rest.client.version>");
        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<java.version>1.8</java.version>");
        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/org/openapitools/client/api/PetApi.java"),
                "import javax.");

        output.deleteOnExit();
    }

    @Test
    public void testMicroprofileRestClientVersion_1_4_1() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(JavaClientCodegen.MICROPROFILE_REST_CLIENT_VERSION, "1.4.1");

        File output = Files.createTempDirectory("test").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setAdditionalProperties(properties)
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.MICROPROFILE)
                .setInputSpec("src/test/resources/3_0/petstore.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(clientOptInput).generate();

        TestUtils.ensureContainsFile(files, output, "pom.xml");

        validateJavaSourceFiles(files);

        TestUtils.assertFileContains(
                Paths.get(output + "/pom.xml"),
                "<microprofile.rest.client.api.version>1.4.1</microprofile.rest.client.api.version>");
        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<smallrye.rest.client.version>1.2.1</smallrye.rest.client.version>");
        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<java.version>1.8</java.version>");
        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/org/openapitools/client/api/PetApi.java"),
                "import javax.");

        output.deleteOnExit();
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp =
                    "Version incorrectVersion of MicroProfile Rest Client is not supported or incorrect."
                            + " Supported versions are 1.4.1, 2.0, 3.0")
    public void testMicroprofileRestClientIncorrectVersion() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(JavaClientCodegen.MICROPROFILE_REST_CLIENT_VERSION, "incorrectVersion");

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setAdditionalProperties(properties)
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.MICROPROFILE)
                .setInputSpec("src/test/resources/3_0/petstore.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(clientOptInput).generate();
        fail("Expected an exception that did not occur");
    }

    @Test
    public void testMicroprofileRestClientVersion_3_0() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(JavaClientCodegen.MICROPROFILE_REST_CLIENT_VERSION, "3.0");

        File output = Files.createTempDirectory("test").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setAdditionalProperties(properties)
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.MICROPROFILE)
                .setInputSpec("src/test/resources/3_0/petstore.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(clientOptInput).generate();

        TestUtils.ensureContainsFile(files, output, "pom.xml");

        validateJavaSourceFiles(files);

        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<microprofile.rest.client.api.version>3.0</microprofile.rest.client.api.version>");
        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<jersey.mp.rest.client.version>3.0.4</jersey.mp.rest.client.version>");
        TestUtils.assertFileContains(Paths.get(output + "/pom.xml"),
                "<java.version>11</java.version>");
        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/org/openapitools/client/api/PetApi.java"),
                "import jakarta.");

        output.deleteOnExit();
    }

    @Test
    public void testMicroprofileGenerateCorrectJsonbCreator_issue12622() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(JavaClientCodegen.MICROPROFILE_REST_CLIENT_VERSION, "3.0");

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setAdditionalProperties(properties)
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.MICROPROFILE)
                .setInputSpec("src/test/resources/bugs/issue_12622.json")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        Map<String, File> files = generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("Foo.java"))
                .assertConstructor("String", "Integer")
                .hasParameter("b")
                .assertParameterAnnotations()
                .containsWithNameAndAttributes(
                        "JsonbProperty", ImmutableMap.of("value", "\"b\"", "nillable", "true"))
                .toParameter()
                .toConstructor()
                .hasParameter("c")
                .assertParameterAnnotations()
                .containsWithNameAndAttributes("JsonbProperty", ImmutableMap.of("value", "\"c\""));
    }

    @Test
    public void testJavaClientDefaultValues_issueNoNumber() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(JavaClientCodegen.MICROPROFILE_REST_CLIENT_VERSION, "3.0");

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator =
                new CodegenConfigurator()
                        .setAdditionalProperties(properties)
                        .setGeneratorName("java")
                        .setLibrary(JavaClientCodegen.WEBCLIENT)
                        .setInputSpec(
                                "src/test/resources/bugs/java-codegen-empty-array-as-default-value/issue_wrong-default.yaml")
                        .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        Map<String, File> files = generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("DefaultValuesType.java"))
                .hasProperty("stringDefault")
                .asString().endsWith("= new ArrayList<>();");
        JavaFileAssert.assertThat(files.get("DefaultValuesType.java"))
                .hasProperty("stringDefault2")
                .asString().endsWith("= new ArrayList<>(Arrays.asList(\"Hallo\", \"Huhu\"));");
        JavaFileAssert.assertThat(files.get("DefaultValuesType.java"))
                .hasProperty("objectDefault")
                .asString().endsWith("= new ArrayList<>();");
    }

    @Test
    public void testWebClientJsonCreatorWithNullable_issue12790() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AbstractJavaCodegen.OPENAPI_NULLABLE, "true");

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setAdditionalProperties(properties)
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.WEBCLIENT)
                .setInputSpec("src/test/resources/bugs/issue_12790.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        Map<String, File> files = generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("TestObject.java"))
                .printFileContent()
                .assertConstructor("String", "String")
                .bodyContainsLines(
                        "this.nullableProperty = nullableProperty == null ? JsonNullable.<String>undefined() :"
                                + " JsonNullable.of(nullableProperty);",
                        "this.notNullableProperty = notNullableProperty;");
    }

    public void testExtraAnnotations(String library) throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();
        String outputPath = output.getAbsolutePath().replace('\\', '/');

        Map<String, Object> properties = new HashMap<>();
        properties.put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(library)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/issue_11772.yml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();

        generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
        generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
        generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
        generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "false");
        generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
        generator.opts(clientOptInput).generate();

        TestUtils.assertExtraAnnotationFiles(
                outputPath + "/src/main/java/org/openapitools/client/model");
    }

    /**
     * See https://github.com/OpenAPITools/openapi-generator/issues/11340
     */
    @Test
    public void testReferencedHeader2() throws Exception {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put(BeanValidationFeatures.USE_BEANVALIDATION, "true");
        final CodegenConfigurator configurator = new CodegenConfigurator().setGeneratorName("java")
                .setAdditionalProperties(additionalProperties)
                .setInputSpec("src/test/resources/3_0/issue-11340.yaml")
                .setOutputDir(output.getAbsolutePath()
                        .replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();

        Map<String, File> files = generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("DefaultApi.java"))
                .assertMethod("operationWithHttpInfo")
                .hasParameter("requestBody")
                .assertParameterAnnotations()
                .containsWithName("NotNull")
                .toParameter().toMethod()
                .hasParameter("xNonNullHeaderParameter")
                .assertParameterAnnotations()
                .containsWithName("NotNull");
    }

    @Test
    public void testReturnTypeMapping() throws IOException {
        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setInputSpec("src/test/resources/3_0/issue14525.yaml")
                .addTypeMapping("array", "Stack")
                .addImportMapping("Stack", "java.util.Stack")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(clientOptInput).generate();

        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/org/openapitools/client/api/DefaultApi.java"),
                "import java.util.Stack;");
    }

    @Test
    public void testJdkHttpClientWithAndWithoutParentExtension() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.model");
        properties.put(CodegenConstants.INVOKER_PACKAGE, "xyz.abcdef.invoker");

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                // use default `okhttp-gson`
                //.setLibrary(JavaClientCodegen.NATIVE)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/allOf_extension_parent.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
        generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
        List<File> files = generator.opts(clientOptInput).generate();

        Assertions.assertEquals(files.size(), 27);
        validateJavaSourceFiles(files);

        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/xyz/abcdef/model/Child.java"),
                "public class Child extends Person {");
        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/xyz/abcdef/model/Adult.java"),
                "public class Adult extends Person {");
        TestUtils.assertFileContains(
                Paths.get(output + "/src/main/java/xyz/abcdef/model/AnotherChild.java"),
                "public class AnotherChild {");
    }

    @Test
    public void testDiscriminatorWithMappingIssue14731() throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();
        String outputPath = output.getAbsolutePath().replace('\\', '/');
        OpenAPI openAPI =
                new OpenAPIParser()
                        .readLocation("src/test/resources/bugs/issue_14731.yaml", null, new ParseOptions())
                        .getOpenAPI();

        JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOutputDir(output.getAbsolutePath());
        codegen.additionalProperties().put(CXFServerFeatures.LOAD_TEST_DATA_FROM_FILE, "true");
        codegen.setUseOneOfInterfaces(true);

        ClientOptInput input = new ClientOptInput();
        input.openAPI(openAPI);
        input.config(codegen);

        DefaultGenerator generator = new DefaultGenerator();
        generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
        generator.setGeneratorPropertyDefault(CodegenConstants.LEGACY_DISCRIMINATOR_BEHAVIOR, "false");

        codegen.setUseOneOfInterfaces(true);
        codegen.setLegacyDiscriminatorBehavior(false);
        codegen.setUseJakartaEe(true);
        codegen.setModelNameSuffix("DTO");

        generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
        generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
        generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
        generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");

        generator.opts(input).generate();

        assertFileNotContains(
                Paths.get(
                        outputPath + "/src/main/java/org/openapitools/client/model/ChildWithMappingADTO.java"),
                "@JsonTypeName");
        assertFileNotContains(
                Paths.get(
                        outputPath + "/src/main/java/org/openapitools/client/model/ChildWithMappingBDTO.java"),
                "@JsonTypeName");
    }

    @Test
    public void shouldProperlyExplodeWebClientQueryParameters() {
        final Map<String, File> files = generateFromContract(
                "src/test/resources/3_0/java/explode-query-parameter.yaml",
                JavaClientCodegen.WEBCLIENT
        );

        JavaFileAssert.assertThat(files.get("DefaultApi.java"))
                .printFileContent()
                .assertMethod("searchRequestCreation")
                .bodyContainsLines(
                        "queryParams.putAll(apiClient.parameterToMultiValueMap(null, \"regular-param\","
                                + " regularParam));")
                .bodyContainsLines(
                        "queryParams.putAll(apiClient.parameterToMultiValueMap(null, \"someString\","
                                + " objectParam.getSomeString()));")
                .bodyContainsLines(
                        "queryParams.putAll(apiClient.parameterToMultiValueMap(null, \"someBoolean\","
                                + " objectParam.getSomeBoolean()));")
                .bodyContainsLines(
                        "queryParams.putAll(apiClient.parameterToMultiValueMap(null, \"someInteger\","
                                + " objectParam.getSomeInteger()));");
    }

    private static Map<String, File> generateFromContract(final String pathToSpecification, final String library) {
        return generateFromContract(pathToSpecification, library, new HashMap<>());
    }

    @SneakyThrows
    private static Map<String, File> generateFromContract(
            final String pathToSpecification,
            final String library,
            final Map<String, Object> properties
    ) {
        return generateFromContract(pathToSpecification, library, properties, configurator -> {});
    }

    @SneakyThrows
    private static Map<String, File> generateFromContract(
            final String pathToSpecification,
            final String library,
            final Map<String, Object> properties,
            final Consumer<CodegenConfigurator> consumer
    ) {
        final File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(library)
                .setAdditionalProperties(properties)
                .setInputSpec(pathToSpecification)
                .setOutputDir(output.getAbsolutePath());
        consumer.accept(configurator);
        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        final DefaultGenerator generator = new DefaultGenerator();
        return generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));
    }

    @Test
    public void testForJavaApacheHttpClientJsonSubtype() throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();
        String outputPath = output.getAbsolutePath().replace('\\', '/');
        OpenAPI openAPI =
                new OpenAPIParser()
                        .readLocation("src/test/resources/bugs/issue_14917.yaml", null, new ParseOptions())
                        .getOpenAPI();

        JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOutputDir(output.getAbsolutePath());

        ClientOptInput input = new ClientOptInput();
        input.openAPI(openAPI);
        input.config(codegen);

        DefaultGenerator generator = new DefaultGenerator();
        codegen.setLibrary(JavaClientCodegen.APACHE);

        generator.opts(input).generate();

        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Cat.java"),
                "@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property ="
                        + " \"petType\", visible = true)");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Cat.java"),
                "mappings.put");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Cat.java"),
                "@JsonSubTypes.Type(value = Cat.class, name = \"cat\")");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Cat.java"),
                "@JsonSubTypes.Type(value = Dog.class, name = \"dog\")");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Cat.java"),
                "@JsonSubTypes.Type(value = Lizard.class, name = \"lizard\")");

        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property ="
                        + " \"petType\", visible = true)");
        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonSubTypes.Type(value = Cat.class, name = \"cat\")");
        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonSubTypes.Type(value = Dog.class, name = \"dog\")");
        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonSubTypes.Type(value = Lizard.class, name = \"lizard\")");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonSubTypes.Type(value = Cat.class, name = \"Cat\")");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonSubTypes.Type(value = Dog.class, name = \"Dog\")");
        assertFileNotContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "@JsonSubTypes.Type(value = Lizard.class, name = \"Lizard\")");
    }

    @Test
    public void testIsOverriddenProperty() {
        final OpenAPI openAPI =
                TestUtils.parseFlattenSpec("src/test/resources/3_0/allOf_composition_discriminator.yaml");
        JavaClientCodegen codegen = new JavaClientCodegen();

        Schema test1 = openAPI.getComponents().getSchemas().get("Cat");
        codegen.setOpenAPI(openAPI);
        CodegenModel cm1 = codegen.fromModel("Cat", test1);

        CodegenProperty cp0 = cm1.getAllVars().get(0);
        Assertions.assertEquals(cp0.getName(), "petType");
        Assertions.assertEquals(cp0.isOverridden, true);

        CodegenProperty cp1 = cm1.getAllVars().get(1);
        Assertions.assertEquals(cp1.getName(), "name");
        Assertions.assertEquals(cp1.isOverridden, false);
    }

    @Test
    public void testForJavaApacheHttpClientOverrideSetter() throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();
        String outputPath = output.getAbsolutePath().replace('\\', '/');
        OpenAPI openAPI =
                new OpenAPIParser()
                        .readLocation(
                                "src/test/resources/3_0/allOf_composition_discriminator.yaml",
                                null,
                                new ParseOptions())
                        .getOpenAPI();

        JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOutputDir(output.getAbsolutePath());

        ClientOptInput input = new ClientOptInput();
        input.openAPI(openAPI);
        input.config(codegen);

        DefaultGenerator generator = new DefaultGenerator();
        codegen.setLibrary(JavaClientCodegen.APACHE);

        generator.opts(input).generate();

        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Cat.java"),
                "  @Override\n" + "  public Cat petType(String petType) {");
        assertFileContains(
                Paths.get(outputPath + "/src/main/java/org/openapitools/client/model/Pet.java"),
                "  }\n" + "\n" + "  public Pet petType(String petType) {\n");
    }

    @DataProvider(name = "shouldNotAddAdditionalModelAnnotationsToAbstractOpenApiSchema_issue15684")
    public static Object[][] shouldNotAddAdditionalModelAnnotationsToAbstractOpenApiSchema_issue15684_dataProvider() {
        return new Object[][]{{"okhttp-gson"}, {"jersey2"}, {"jersey3"}, {"native"}};
    }

    @Test(dataProvider = "shouldNotAddAdditionalModelAnnotationsToAbstractOpenApiSchema_issue15684")
    public void shouldNotAddAdditionalModelAnnotationsToAbstractOpenApiSchema_issue15684(String library) throws Exception {
        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator =
                new CodegenConfigurator()
                        .setGeneratorName("java")
                        .setLibrary(library)
                        .addAdditionalProperty(
                                AbstractJavaCodegen.ADDITIONAL_MODEL_TYPE_ANNOTATIONS, "@annotation1;@annotation2")
                        .setInputSpec("src/test/resources/3_0/deprecated-properties.yaml")
                        .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        Map<String, File> files = generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("AbstractOpenApiSchema.java"))
                .assertTypeAnnotations()
                .doesNotContainsWithName("annotation1")
                .doesNotContainsWithName("annotation2");
        JavaFileAssert.assertThat(files.get("Animal.java"))
                .assertTypeAnnotations()
                .containsWithName("annotation1")
                .containsWithName("annotation2");
    }

    @Test
    public void testWebClientSupportListOfStringReturnType_issue7118() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.USE_ABSTRACTION_FOR_FILES, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.WEBCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/bugs/issue_7118.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path userApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/UsersApi.java");
        TestUtils.assertFileContains(
                userApi,
                // set of string
                "ParameterizedTypeReference<Set<String>> localVarReturnType = new"
                        + " ParameterizedTypeReference<Set<String>>() {};",
                "getUserIdSetRequestCreation().toEntity(localVarReturnType)",
                // list of string
                "ParameterizedTypeReference<List<String>> localVarReturnType = new"
                        + " ParameterizedTypeReference<List<String>>() {};",
                "getUserIdListRequestCreation().toEntity(localVarReturnType)");
    }

    @Test
    public void testEnumCaseInsensitive_issue8084() throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/issue8084.yaml");
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setOutputDir(output.getAbsolutePath());
        codegen.additionalProperties().put(USE_ENUM_CASE_INSENSITIVE, "true");

        ClientOptInput input = new ClientOptInput();
        input.openAPI(openAPI);
        input.config(codegen);

        DefaultGenerator generator = new DefaultGenerator();

        Map<String, File> files = generator.opts(input).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("EnumTest.java"));
        javaFileAssert
                .assertMethod("fromValue")
                .bodyContainsLines("if (b.value.equalsIgnoreCase(value)) {");
    }

    @Test
    public void testEnumCaseSensitive_issue8084() throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();

        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/2_0/issue8084.yaml");
        final JavaClientCodegen codegen = new JavaClientCodegen();
        codegen.setOpenAPI(openAPI);
        codegen.setOutputDir(output.getAbsolutePath());
        codegen.additionalProperties().put(USE_ENUM_CASE_INSENSITIVE, "false");

        ClientOptInput input = new ClientOptInput();
        input.openAPI(openAPI);
        input.config(codegen);

        DefaultGenerator generator = new DefaultGenerator();

        Map<String, File> files = generator.opts(input).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert javaFileAssert = JavaFileAssert.assertThat(files.get("EnumTest.java"));
        javaFileAssert
                .assertMethod("fromValue")
                .bodyContainsLines("if (b.value.equals(value)) {");
    }

    @Test
    public void testWebClientResponseTypeWithUseAbstractionForFiles_issue16589() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.USE_ABSTRACTION_FOR_FILES, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.WEBCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/issue13146_file_abstraction_response.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));


        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/ResourceApi.java");

        TestUtils.assertFileContains(defaultApi,
                "Mono<org.springframework.core.io.Resource> resourceInResponse()",
                "Mono<ResponseEntity<org.springframework.core.io.Resource>> resourceInResponseWithHttpInfo()",
                "ParameterizedTypeReference<org.springframework.core.io.Resource> localVarReturnType = new ParameterizedTypeReference<org.springframework.core.io.Resource>()"
        );
    }

    @Test
    public void testHandleConstantParams() throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/java/autoset_constant.yaml");
        final DefaultGenerator defaultGenerator = new DefaultGenerator();
        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.openAPI(openAPI);
        JavaClientCodegen javaClientCodegen = new JavaClientCodegen();
        javaClientCodegen.setOutputDir(output.getAbsolutePath());
        javaClientCodegen.additionalProperties().put(CodegenConstants.AUTOSET_CONSTANTS, "true");
        javaClientCodegen.setAutosetConstants(true);
        clientOptInput.config(javaClientCodegen);
        defaultGenerator.opts(clientOptInput);

        Map<String, File> files = defaultGenerator.generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        File apiFile = files.get("HelloExampleApi.java");

        Assertions.assertNotNull(apiFile);
        JavaFileAssert.assertThat(apiFile)
                .assertMethod("helloCall", "String", "ApiCallback")
                .bodyContainsLines(
                        "localVarHeaderParams.put(\"X-CUSTOM_CONSTANT_HEADER\", \"CONSTANT_VALUE\")");
    }

    @Test
    public void testAllOfWithSinglePrimitiveTypeRef() throws IOException {
        File output = Files.createTempDirectory("test_allOf_primitive_type").toFile().getCanonicalFile();
        output.deleteOnExit();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allof_primitive.yaml");
        final DefaultGenerator defaultGenerator = new DefaultGenerator();
        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.openAPI(openAPI);
        JavaClientCodegen javaClientCodegen = new JavaClientCodegen();
        javaClientCodegen.setOutputDir(output.getAbsolutePath());
        javaClientCodegen.setAutosetConstants(true);
        clientOptInput.config(javaClientCodegen);
        defaultGenerator.opts(clientOptInput);

        Map<String, File> files = defaultGenerator.generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        File apiFile = files.get("AllOfDatetime.java");
        assertEquals(apiFile, null);
    }

    @Test
    public void testOpenapiGeneratorIgnoreListOption() throws IOException {
        File output = Files.createTempDirectory("openapi_generator_ignore_list_test_folder").toFile().getCanonicalFile();
        output.deleteOnExit();
        final OpenAPI openAPI = TestUtils.parseFlattenSpec("src/test/resources/3_0/allof_primitive.yaml");
        final DefaultGenerator defaultGenerator = new DefaultGenerator();
        final ClientOptInput clientOptInput = new ClientOptInput();
        clientOptInput.openAPI(openAPI);
        JavaClientCodegen javaClientCodegen = new JavaClientCodegen();
        javaClientCodegen.setOutputDir(output.getAbsolutePath());
        javaClientCodegen.setAutosetConstants(true);
        javaClientCodegen.openapiGeneratorIgnoreList().add("README.md");
        javaClientCodegen.openapiGeneratorIgnoreList().add("pom.xml");
        clientOptInput.config(javaClientCodegen);
        defaultGenerator.opts(clientOptInput);

        Map<String, File> files = defaultGenerator.generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        // make sure README.md and pom.xml are not generated
        assertEquals(files.get("README.md"), null);
        assertEquals(files.get("pom.xml"), null);
    }

    @Test
    public void testMicroprofileHandleURIEnum() throws IOException {
        String[] expectedInnerEnumLines = new String[] {
            "V1_SCHEMA_JSON(URI.create(\"https://example.com/v1/schema.json\"))",
            "V2_SCHEMA_JSON(URI.create(\"https://example.com/v2/schema.json\"))",
            "generator.write(obj.value.toASCIIString())"
        };

        String[] expectedEnumLines = new String[] {
            "V1_METADATA_JSON(URI.create(\"https://example.com/v1/metadata.json\"))",
            "V2_METADATA_JSON(URI.create(\"https://example.com/v2/metadata.json\"))"
        };

        testHandleURIEnum(JavaClientCodegen.MICROPROFILE, expectedInnerEnumLines, expectedEnumLines);
    }

    private void testHandleURIEnum(String library, String[] expectedInnerEnumLines, String[] expectedEnumLines) throws IOException {
        File output = Files.createTempDirectory("test").toFile().getCanonicalFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(library)
                .setInputSpec("src/test/resources/3_0/enum-and-inner-enum-uri.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        final DefaultGenerator defaultGenerator = new DefaultGenerator();

        defaultGenerator.opts(clientOptInput);

        Map<String, File> files = defaultGenerator.generate().stream()
                        .collect(Collectors.toMap(File::getName, Function.identity()));

        // enum
        File modelFile = files.get("Metadata.java");
        Assertions.assertNotNull(modelFile);
        JavaFileAssert.assertThat(modelFile).fileContains(expectedEnumLines);

        // Inner enum
        File apiFile = files.get("V1SchemasGetDefaultResponse.java");
        Assertions.assertNotNull(apiFile);
        JavaFileAssert.assertThat(apiFile).fileContains(expectedInnerEnumLines);
    }

    @Test
    public void testRestClientFormMultipart() throws IOException {

        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");


        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.RESTCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/form-multipart-binary-array.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));


        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/MultipartApi.java");
        TestUtils.assertFileContains(
                defaultApi,
                // multiple files
                "multipartArray(List<File> files)",
                "formParams.addAll(\"files\","
                        + " files.stream().map(FileSystemResource::new).collect(Collectors.toList()));",

                // mixed
                "multipartMixed(MultipartMixedStatus status, File _file, MultipartMixedRequestMarker marker, List<MultipartMixedStatus> statusArray)",
                "formParams.add(\"file\", new FileSystemResource(_file));",

                // single file
                "multipartSingle(File _file)",
                "formParams.add(\"file\", new FileSystemResource(_file));");
    }

    @Test
    public void testRestClientWithUseAbstractionForFiles() throws IOException {

        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.USE_ABSTRACTION_FOR_FILES, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.RESTCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/form-multipart-binary-array.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));


        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/MultipartApi.java");
        TestUtils.assertFileContains(
                defaultApi,
                // multiple files
                "multipartArray(java.util.Collection<org.springframework.core.io.AbstractResource> files)",
                "formParams.addAll(\"files\", files.stream().collect(Collectors.toList()));",

                // mixed
                "multipartMixed(MultipartMixedStatus status, org.springframework.core.io.AbstractResource _file, MultipartMixedRequestMarker marker, List<MultipartMixedStatus> statusArray)",
                "formParams.add(\"file\", _file);",

                // single file
                "multipartSingle(org.springframework.core.io.AbstractResource _file)",
                "formParams.add(\"file\", _file);");
    }

    @Test
    public void testRestClientWithFreeFormInQueryParameters() throws IOException {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");

        final File output = Files.createTempDirectory("test")
                .toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator().setGeneratorName("java")
                .setLibrary(JavaClientCodegen.RESTCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/issue8352.yaml")
                .setOutputDir(output.getAbsolutePath()
                        .replace("\\", "/"));

        final DefaultGenerator generator = new DefaultGenerator();
        final List<File> files = generator.opts(configurator.toClientOptInput())
                .generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        final Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/ApiClient.java");
        TestUtils.assertFileContains(defaultApi, "value instanceof Map");
    }

    @Test
    public void testRestClientJsonCreatorWithNullable_issue12790() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AbstractJavaCodegen.OPENAPI_NULLABLE, "true");

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setAdditionalProperties(properties)
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.RESTCLIENT)
                .setInputSpec("src/test/resources/bugs/issue_12790.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        Map<String, File> files = generator.opts(clientOptInput).generate().stream()
                .collect(Collectors.toMap(File::getName, Function.identity()));

        JavaFileAssert.assertThat(files.get("TestObject.java"))
                .printFileContent()
                .assertConstructor("String", "String")
                .bodyContainsLines(
                        "this.nullableProperty = nullableProperty == null ? JsonNullable.<String>undefined() :"
                                + " JsonNullable.of(nullableProperty);",
                        "this.notNullableProperty = notNullableProperty;");
    }

    @Test
    public void testRestClientSupportListOfStringReturnType_issue7118() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.USE_ABSTRACTION_FOR_FILES, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.RESTCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/bugs/issue_7118.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));

        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path userApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/UsersApi.java");
        TestUtils.assertFileContains(
                userApi,
                // set of string
                "ParameterizedTypeReference<Set<String>> localVarReturnType = new"
                        + " ParameterizedTypeReference<>() {};",
                "getUserIdSetRequestCreation().toEntity(localVarReturnType)",
                // list of string
                "ParameterizedTypeReference<List<String>> localVarReturnType = new"
                        + " ParameterizedTypeReference<>() {};",
                "getUserIdListRequestCreation().toEntity(localVarReturnType)");
    }

    @Test
    public void testRestClientResponseTypeWithUseAbstractionForFiles_issue16589() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CodegenConstants.API_PACKAGE, "xyz.abcdef.api");
        properties.put(JavaClientCodegen.USE_ABSTRACTION_FOR_FILES, true);

        File output = Files.createTempDirectory("test").toFile();
        output.deleteOnExit();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary(JavaClientCodegen.RESTCLIENT)
                .setAdditionalProperties(properties)
                .setInputSpec("src/test/resources/3_0/issue13146_file_abstraction_response.yaml")
                .setOutputDir(output.getAbsolutePath().replace("\\", "/"));


        DefaultGenerator generator = new DefaultGenerator();
        List<File> files = generator.opts(configurator.toClientOptInput()).generate();
        files.forEach(File::deleteOnExit);

        validateJavaSourceFiles(files);

        Path defaultApi = Paths.get(output + "/src/main/java/xyz/abcdef/api/ResourceApi.java");

        TestUtils.assertFileContains(defaultApi,
                "org.springframework.core.io.Resource resourceInResponse()",
                "ResponseEntity<org.springframework.core.io.Resource> resourceInResponseWithHttpInfo()",
                "ParameterizedTypeReference<org.springframework.core.io.Resource> localVarReturnType = new ParameterizedTypeReference<>()"
        );
    }

}
