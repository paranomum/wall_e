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

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.features.BeanValidationFeatures;
import org.openapitools.codegen.languages.features.GzipFeatures;
import org.openapitools.codegen.languages.features.PerformBeanValidationFeatures;
import org.openapitools.codegen.meta.features.DocumentationFeature;
import org.openapitools.codegen.meta.features.GlobalFeature;
import org.openapitools.codegen.meta.features.SecurityFeature;
import org.openapitools.codegen.model.*;
import org.openapitools.codegen.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static java.util.Collections.sort;
import static org.openapitools.codegen.utils.StringUtils.camelize;

public class JavaClientCodegen extends AbstractJavaCodegen
        implements BeanValidationFeatures, PerformBeanValidationFeatures, GzipFeatures {

    static final String MEDIA_TYPE = "mediaType";

    private final Logger LOGGER = LoggerFactory.getLogger(JavaClientCodegen.class);

    public static final String USE_RX_JAVA2 = "useRxJava2";
    public static final String USE_RX_JAVA3 = "useRxJava3";
    public static final String DO_NOT_USE_RX = "doNotUseRx";
    public static final String USE_PLAY_WS = "usePlayWS";
    public static final String ASYNC_NATIVE = "asyncNative";
    public static final String CONFIG_KEY = "configKey";
    public static final String CONFIG_KEY_FROM_CLASS_NAME = "configKeyFromClassName";
    public static final String PARCELABLE_MODEL = "parcelableModel";
    public static final String USE_RUNTIME_EXCEPTION = "useRuntimeException";
    public static final String USE_REFLECTION_EQUALS_HASHCODE = "useReflectionEqualsHashCode";
    public static final String CASE_INSENSITIVE_RESPONSE_HEADERS = "caseInsensitiveResponseHeaders";
    public static final String MICROPROFILE_FRAMEWORK = "microprofileFramework";
    public static final String MICROPROFILE_MUTINY = "microprofileMutiny";
    public static final String USE_ABSTRACTION_FOR_FILES = "useAbstractionForFiles";
    public static final String DYNAMIC_OPERATIONS = "dynamicOperations";
    public static final String SUPPORT_STREAMING = "supportStreaming";
    public static final String SUPPORT_URL_QUERY = "supportUrlQuery";
    public static final String ERROR_OBJECT_TYPE = "errorObjectType";

    public static final String WEBCLIENT = "webclient";
    public static final String RESTCLIENT = "restclient";
    public static final String REST_ASSURED = "rest-assured";
    public static final String RETROFIT_2 = "retrofit2";
    public static final String VERTX = "vertx";
    public static final String MICROPROFILE = "microprofile";
    public static final String APACHE = "apache-httpclient";
    public static final String MICROPROFILE_REST_CLIENT_VERSION = "microprofileRestClientVersion";
    public static final String MICROPROFILE_REST_CLIENT_DEFAULT_VERSION = "2.0";
    public static final String MICROPROFILE_REST_CLIENT_DEFAULT_ROOT_PACKAGE = "javax";
    public static final String MICROPROFILE_DEFAULT = "default";
    public static final String MICROPROFILE_KUMULUZEE = "kumuluzee";
    public static final String WEBCLIENT_BLOCKING_OPERATIONS = "webclientBlockingOperations";
    public static final String USE_ENUM_CASE_INSENSITIVE = "useEnumCaseInsensitive";

    public static final String SERIALIZATION_LIBRARY_GSON = "gson";
    public static final String SERIALIZATION_LIBRARY_JACKSON = "jackson";
    public static final String SERIALIZATION_LIBRARY_JSONB = "jsonb";

    public static final String GENERATE_CLIENT_AS_BEAN = "generateClientAsBean";

//    protected String gradleWrapperPackage = "gradle.wrapper";
    protected boolean useRxJava = false;
    protected boolean useRxJava2 = false;
    protected boolean useRxJava3 = false;
    // backwards compatibility for openapi configs that specify neither rx1 nor rx2
    // (mustache does not allow for boolean operators so we need this extra field)
    protected boolean doNotUseRx = true;
    protected boolean usePlayWS = false;
    protected String microprofileFramework = MICROPROFILE_DEFAULT;
    protected boolean microprofileMutiny = false;
    protected String configKey = null;
    protected boolean configKeyFromClassName = false;

    protected boolean asyncNative = false;
    protected boolean parcelableModel = false;
    protected boolean useBeanValidation = false;
    protected boolean performBeanValidation = false;
    protected boolean useGzipFeature = false;
    protected boolean useRuntimeException = false;
    protected boolean useReflectionEqualsHashCode = false;
    protected boolean caseInsensitiveResponseHeaders = false;
    protected boolean useAbstractionForFiles = false;
    protected boolean dynamicOperations = false;
    protected boolean supportStreaming = false;
    protected boolean withAWSV4Signature = false;
    protected String errorObjectType;
    protected String authFolder;
    protected String serializationLibrary = null;
    protected boolean useOneOfDiscriminatorLookup = false; // use oneOf discriminator's mapping for model lookup
    protected String rootJavaEEPackage;
    protected Map<String, MpRestClientVersion> mpRestClientVersions = new LinkedHashMap<>();
    protected boolean useSingleRequestParameter = false;
    protected boolean webclientBlockingOperations = false;
    protected boolean generateClientAsBean = false;
    protected boolean useEnumCaseInsensitive = false;

    protected int maxAttemptsForRetry = 1;
    protected long waitTimeMillis = 10L;

    private static class MpRestClientVersion {
        public final String rootPackage;
        public final String pomTemplate;

        public MpRestClientVersion(String rootPackage, String pomTemplate) {
            this.rootPackage = rootPackage;
            this.pomTemplate = pomTemplate;
        }
    }

    @Override
    public DocumentationProvider defaultDocumentationProvider() {
        return DocumentationProvider.SOURCE;
    }

    @Override
    public List<DocumentationProvider> supportedDocumentationProvider() {
        List<DocumentationProvider> documentationProviders = new ArrayList<>();
        documentationProviders.add(DocumentationProvider.NONE);
        documentationProviders.add(DocumentationProvider.SOURCE);
        return documentationProviders;
    }

    @Override
    public List<AnnotationLibrary> supportedAnnotationLibraries() {
        List<AnnotationLibrary> annotationLibraries = new ArrayList<>();
        annotationLibraries.add(AnnotationLibrary.NONE);
        annotationLibraries.add(AnnotationLibrary.SWAGGER1);
        annotationLibraries.add(AnnotationLibrary.SWAGGER2);
        return annotationLibraries;
    }

    public JavaClientCodegen() {
        super();

        // TODO: Move GlobalFeature.ParameterizedServer to library: jersey after moving featureSet to generatorMetadata
        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .includeGlobalFeatures(GlobalFeature.ParameterizedServer)
                .includeSecurityFeatures(SecurityFeature.OAuth2_AuthorizationCode,
                        SecurityFeature.OAuth2_ClientCredentials,
                        SecurityFeature.OAuth2_Password,
                        SecurityFeature.SignatureAuth,//jersey only
                        SecurityFeature.AWSV4Signature)//okhttp-gson only
        );

        outputFolder = "generated-code" + File.separator + "java";
        embeddedTemplateDir = templateDir = "Java";
        invokerPackage = "org.openapitools.client";
        artifactId = "openapi-java-client";
        apiPackage = "org.openapitools.client.api";
        modelPackage = "org.openapitools.client.dto";
        enumPackage = "org.openapitools.client.model";
        rootJavaEEPackage = MICROPROFILE_REST_CLIENT_DEFAULT_ROOT_PACKAGE;

        // cliOptions default redefinition need to be updated
        updateOption(CodegenConstants.INVOKER_PACKAGE, this.getInvokerPackage());
        updateOption(CodegenConstants.ARTIFACT_ID, this.getArtifactId());
        updateOption(CodegenConstants.API_PACKAGE, apiPackage);
        updateOption(CodegenConstants.MODEL_PACKAGE, modelPackage);
        updateOption(CodegenConstants.ENUM_PACKAGE, enumPackage);

        modelTestTemplateFiles.put("model_test.mustache", ".java");

        cliOptions.add(CliOption.newBoolean(USE_RX_JAVA2, "Whether to use the RxJava2 adapter with the retrofit2 library. IMPORTANT: This option has been deprecated."));
        cliOptions.add(CliOption.newBoolean(USE_RX_JAVA3, "Whether to use the RxJava3 adapter with the retrofit2 library. IMPORTANT: This option has been deprecated."));
        cliOptions.add(CliOption.newBoolean(PARCELABLE_MODEL, "Whether to generate models for Android that implement Parcelable with the okhttp-gson library."));
        cliOptions.add(CliOption.newBoolean(USE_PLAY_WS, "Use Play! Async HTTP client (Play WS API)"));
        cliOptions.add(CliOption.newBoolean(USE_BEANVALIDATION, "Use BeanValidation API annotations"));
        cliOptions.add(CliOption.newBoolean(PERFORM_BEANVALIDATION, "Perform BeanValidation"));
        cliOptions.add(CliOption.newBoolean(USE_GZIP_FEATURE, "Send gzip-encoded requests"));
        cliOptions.add(CliOption.newBoolean(USE_RUNTIME_EXCEPTION, "Use RuntimeException instead of Exception. Only jersey2, jersey3, okhttp-gson, vertx, microprofile support this option."));
        cliOptions.add(CliOption.newBoolean(ASYNC_NATIVE, "If true, async handlers will be used, instead of the sync version"));
        cliOptions.add(CliOption.newBoolean(USE_REFLECTION_EQUALS_HASHCODE, "Use org.apache.commons.lang3.builder for equals and hashCode in the models. WARNING: This will fail under a security manager, unless the appropriate permissions are set up correctly and also there's potential performance impact."));
        cliOptions.add(CliOption.newString(MICROPROFILE_FRAMEWORK, "Framework for microprofile. Possible values \"kumuluzee\""));
        cliOptions.add(CliOption.newString(MICROPROFILE_MUTINY, "Whether to use async types for microprofile (currently only Smallrye Mutiny is supported)."));
        cliOptions.add(CliOption.newBoolean(USE_ABSTRACTION_FOR_FILES, "Use alternative types instead of java.io.File to allow passing bytes without a file on disk. Available on resttemplate, webclient, restclient, libraries"));
        cliOptions.add(CliOption.newBoolean(DYNAMIC_OPERATIONS, "Generate operations dynamically at runtime from an OAS", this.dynamicOperations));
        cliOptions.add(CliOption.newBoolean(SUPPORT_STREAMING, "Support streaming endpoint (beta)", this.supportStreaming));
        cliOptions.add(CliOption.newBoolean(CodegenConstants.WITH_AWSV4_SIGNATURE_COMMENT, CodegenConstants.WITH_AWSV4_SIGNATURE_COMMENT_DESC + " (only available for okhttp-gson library)", this.withAWSV4Signature));
//        cliOptions.add(CliOption.newString(GRADLE_PROPERTIES, "Append additional Gradle properties to the gradle.properties file"));
        cliOptions.add(CliOption.newString(ERROR_OBJECT_TYPE, "Error Object type. (This option is for okhttp-gson only)"));
        cliOptions.add(CliOption.newString(CONFIG_KEY, "Config key in @RegisterRestClient. Default to none. Only `microprofile` supports this option."));
        cliOptions.add(CliOption.newString(CONFIG_KEY_FROM_CLASS_NAME, "If true, set tag as key in @RegisterRestClient. Default to false. Only `microprofile` supports this option."));
        cliOptions.add(CliOption.newBoolean(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP, CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP_DESC + " Only jersey2, jersey3, native, okhttp-gson support this option."));
        cliOptions.add(CliOption.newString(MICROPROFILE_REST_CLIENT_VERSION, "Version of MicroProfile Rest Client API."));
        cliOptions.add(CliOption.newBoolean(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER, "Setting this property to true will generate functions with a single argument containing all API endpoint parameters instead of one argument per parameter. ONLY jersey2, jersey3, okhttp-gson, microprofile support this option."));
        cliOptions.add(CliOption.newBoolean(WEBCLIENT_BLOCKING_OPERATIONS, "Making all WebClient operations blocking(sync). Note that if on operation 'x-webclient-blocking: false' then such operation won't be sync", this.webclientBlockingOperations));
        cliOptions.add(CliOption.newBoolean(GENERATE_CLIENT_AS_BEAN, "For resttemplate, configure whether to create `ApiClient.java` and Apis clients as bean (with `@Component` annotation).", this.generateClientAsBean));
        cliOptions.add(CliOption.newBoolean(SUPPORT_URL_QUERY, "Generate toUrlQueryString in POJO (default to true). Available on `native`, `apache-httpclient` libraries."));
        cliOptions.add(CliOption.newBoolean(USE_ENUM_CASE_INSENSITIVE, "Use `equalsIgnoreCase` when String for enum comparison", useEnumCaseInsensitive));

        supportedLibraries.put(RETROFIT_2, "HTTP client: OkHttp 3.x. JSON processing: Gson 2.x (Retrofit 2.3.0). Enable the RxJava adapter using '-DuseRxJava[2/3]=true'. (RxJava 1.x or 2.x or 3.x)");
        supportedLibraries.put(WEBCLIENT, "HTTP client: Spring WebClient 5.x. JSON processing: Jackson 2.9.x");
        supportedLibraries.put(RESTCLIENT, "HTTP client: Spring RestClient 6.1. JSON processing: Jackson 2.9.x");
        supportedLibraries.put(VERTX, "HTTP client: VertX client 3.x. JSON processing: Jackson 2.9.x");
        supportedLibraries.put(REST_ASSURED, "HTTP client: rest-assured : 4.x. JSON processing: Gson 2.x or Jackson 2.10.x. Only for Java 8");
        supportedLibraries.put(MICROPROFILE, "HTTP client: Microprofile client 1.x. JSON processing: JSON-B or Jackson 2.9.x");
        supportedLibraries.put(APACHE, "HTTP client: Apache httpclient 5.x");

        CliOption libraryOption = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        libraryOption.setEnum(supportedLibraries);
        // set okhttp-gson as the default
        cliOptions.add(libraryOption);

        CliOption serializationLibrary = new CliOption(CodegenConstants.SERIALIZATION_LIBRARY, "Serialization library, default depends on value of the option library");
        Map<String, String> serializationOptions = new HashMap<>();
        serializationOptions.put(SERIALIZATION_LIBRARY_GSON, "Use Gson as serialization library");
        serializationOptions.put(SERIALIZATION_LIBRARY_JACKSON, "Use Jackson as serialization library");
        serializationOptions.put(SERIALIZATION_LIBRARY_JSONB, "Use JSON-B as serialization library");
        serializationLibrary.setEnum(serializationOptions);
        cliOptions.add(serializationLibrary);

        // Ensure the OAS 3.x discriminator mappings include any descendent schemas that allOf
        // inherit from self, any oneOf schemas, any anyOf schemas, any x-discriminator-values,
        // and the discriminator mapping schemas in the OAS document.
        this.setLegacyDiscriminatorBehavior(false);

        initMpRestClientVersionToRootPackage();
    }

    private void initMpRestClientVersionToRootPackage() {
        mpRestClientVersions.put("1.4.1", new MpRestClientVersion("javax", "pom.mustache"));
        mpRestClientVersions.put("2.0", new MpRestClientVersion("javax", "pom.mustache"));
        mpRestClientVersions.put("3.0", new MpRestClientVersion("jakarta", "pom_3.0.mustache"));
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "java";
    }

    @Override
    public String getHelp() {
        return "Generates a Java client library (HTTP lib: Jersey (1.x, 2.x), Retrofit (2.x), OpenFeign (10.x) and more.";
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        super.addOperationToGroup(tag, resourcePath, operation, co, operations);
        if (MICROPROFILE.equals(getLibrary())) {
            co.subresourceOperation = !co.path.isEmpty();
        }
    }

    @Override
    public void processOpts() {
        dateLibrary = "java8";
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP)) {
            setUseOneOfDiscriminatorLookup(convertPropertyToBooleanAndWriteBack(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP));
        } else {
            additionalProperties.put(CodegenConstants.USE_ONEOF_DISCRIMINATOR_LOOKUP, useOneOfDiscriminatorLookup);
        }

        if (additionalProperties.containsKey(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER)) {
            this.setUseSingleRequestParameter(convertPropertyToBoolean(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER));
        }
        writePropertyBack(CodegenConstants.USE_SINGLE_REQUEST_PARAMETER, getUseSingleRequestParameter());

        if (!useRxJava && !useRxJava2 && !useRxJava3) {
            additionalProperties.put(DO_NOT_USE_RX, true);
        }

        // Java Play
        if (additionalProperties.containsKey(USE_PLAY_WS)) {
            this.setUsePlayWS(Boolean.parseBoolean(additionalProperties.get(USE_PLAY_WS).toString()));
        }
        additionalProperties.put(USE_PLAY_WS, usePlayWS);

        // Microprofile framework
        if (additionalProperties.containsKey(MICROPROFILE_FRAMEWORK)) {
            if (!MICROPROFILE_KUMULUZEE.equals(microprofileFramework)) {
                throw new RuntimeException("Invalid microprofileFramework '" + microprofileFramework + "'. Must be 'kumuluzee' or none.");
            }
            this.setMicroprofileFramework(additionalProperties.get(MICROPROFILE_FRAMEWORK).toString());
        }
        additionalProperties.put(MICROPROFILE_FRAMEWORK, microprofileFramework);

        if (additionalProperties.containsKey(MICROPROFILE_MUTINY)) {
            this.setMicroprofileMutiny(convertPropertyToBooleanAndWriteBack(MICROPROFILE_MUTINY));
        }

        if (!additionalProperties.containsKey(MICROPROFILE_REST_CLIENT_VERSION)) {
            additionalProperties.put(MICROPROFILE_REST_CLIENT_VERSION, MICROPROFILE_REST_CLIENT_DEFAULT_VERSION);
        } else {
            String mpRestClientVersion = (String) additionalProperties.get(MICROPROFILE_REST_CLIENT_VERSION);
            if (!mpRestClientVersions.containsKey(mpRestClientVersion)) {
                throw new IllegalArgumentException(
                        String.format(Locale.ROOT,
                                "Version %s of MicroProfile Rest Client is not supported or incorrect. Supported versions are %s",
                                mpRestClientVersion,
                                String.join(", ", mpRestClientVersions.keySet())
                        )
                );
            }
        }
        if (!additionalProperties.containsKey("rootJavaEEPackage")) {
            String mpRestClientVersion = (String) additionalProperties.get(MICROPROFILE_REST_CLIENT_VERSION);
            if (mpRestClientVersions.containsKey(mpRestClientVersion)) {
                rootJavaEEPackage = mpRestClientVersions.get(mpRestClientVersion).rootPackage;
            }
            additionalProperties.put("rootJavaEEPackage", rootJavaEEPackage);
        }

        if (additionalProperties.containsKey(CONFIG_KEY)) {
            this.setConfigKey(additionalProperties.get(CONFIG_KEY).toString());
        } else if (additionalProperties.containsKey(CONFIG_KEY_FROM_CLASS_NAME)) {
            this.setConfigKeyFromClassName(Boolean.parseBoolean(additionalProperties.get(CONFIG_KEY_FROM_CLASS_NAME).toString()));
        }

        if (additionalProperties.containsKey(ASYNC_NATIVE)) {
            this.setAsyncNative(convertPropertyToBooleanAndWriteBack(ASYNC_NATIVE));
        }

        if (additionalProperties.containsKey(PARCELABLE_MODEL)) {
            this.setParcelableModel(Boolean.parseBoolean(additionalProperties.get(PARCELABLE_MODEL).toString()));
        }
        // put the boolean value back to PARCELABLE_MODEL in additionalProperties
        additionalProperties.put(PARCELABLE_MODEL, parcelableModel);

        if (additionalProperties.containsKey(USE_BEANVALIDATION)) {
            this.setUseBeanValidation(convertPropertyToBooleanAndWriteBack(USE_BEANVALIDATION));
        }

        if (additionalProperties.containsKey(PERFORM_BEANVALIDATION)) {
            this.setPerformBeanValidation(convertPropertyToBooleanAndWriteBack(PERFORM_BEANVALIDATION));
        }

        if (additionalProperties.containsKey(USE_GZIP_FEATURE)) {
            this.setUseGzipFeature(convertPropertyToBooleanAndWriteBack(USE_GZIP_FEATURE));
        }

        if (additionalProperties.containsKey(USE_RUNTIME_EXCEPTION)) {
            this.setUseRuntimeException(convertPropertyToBooleanAndWriteBack(USE_RUNTIME_EXCEPTION));
        }

        if (additionalProperties.containsKey(USE_REFLECTION_EQUALS_HASHCODE)) {
            this.setUseReflectionEqualsHashCode(convertPropertyToBooleanAndWriteBack(USE_REFLECTION_EQUALS_HASHCODE));
        }

        if (additionalProperties.containsKey(CASE_INSENSITIVE_RESPONSE_HEADERS)) {
            this.setUseReflectionEqualsHashCode(convertPropertyToBooleanAndWriteBack(CASE_INSENSITIVE_RESPONSE_HEADERS));
        }

        if (additionalProperties.containsKey(USE_ABSTRACTION_FOR_FILES)) {
            this.setUseAbstractionForFiles(convertPropertyToBooleanAndWriteBack(USE_ABSTRACTION_FOR_FILES));
        }

        if (additionalProperties.containsKey(DYNAMIC_OPERATIONS)) {
            this.setDynamicOperations(Boolean.parseBoolean(additionalProperties.get(DYNAMIC_OPERATIONS).toString()));
        }
        additionalProperties.put(DYNAMIC_OPERATIONS, dynamicOperations);

        if (additionalProperties.containsKey(SUPPORT_STREAMING)) {
            this.setSupportStreaming(Boolean.parseBoolean(additionalProperties.get(SUPPORT_STREAMING).toString()));
        }
        additionalProperties.put(SUPPORT_STREAMING, supportStreaming);

        if (additionalProperties.containsKey(CodegenConstants.WITH_AWSV4_SIGNATURE_COMMENT)) {
            this.setWithAWSV4Signature(Boolean.parseBoolean(additionalProperties.get(CodegenConstants.WITH_AWSV4_SIGNATURE_COMMENT).toString()));
        }
        additionalProperties.put(CodegenConstants.WITH_AWSV4_SIGNATURE_COMMENT, withAWSV4Signature);

//        if (additionalProperties.containsKey(GRADLE_PROPERTIES)) {
//            this.setGradleProperties(additionalProperties.get(GRADLE_PROPERTIES).toString());
//        }
//        additionalProperties.put(GRADLE_PROPERTIES, gradleProperties);

        if (additionalProperties.containsKey(ERROR_OBJECT_TYPE)) {
            this.setErrorObjectType(additionalProperties.get(ERROR_OBJECT_TYPE).toString());
        }
        additionalProperties.put(ERROR_OBJECT_TYPE, errorObjectType);
        if (additionalProperties.containsKey(WEBCLIENT_BLOCKING_OPERATIONS)) {
            this.webclientBlockingOperations = Boolean.parseBoolean(additionalProperties.get(WEBCLIENT_BLOCKING_OPERATIONS).toString());
        }

        // add URL query deepObject support to native, apache-httpclient by default
        if (additionalProperties.containsKey(SUPPORT_URL_QUERY)) {
            additionalProperties.put(SUPPORT_URL_QUERY, Boolean.parseBoolean(additionalProperties.get(SUPPORT_URL_QUERY).toString()));
        }

        if (additionalProperties.containsKey(GENERATE_CLIENT_AS_BEAN)) {
            this.setGenerateClientAsBean(convertPropertyToBooleanAndWriteBack(GENERATE_CLIENT_AS_BEAN));
        }

        if (additionalProperties.containsKey(USE_ENUM_CASE_INSENSITIVE)) {
            this.setUseEnumCaseInsensitive(Boolean.parseBoolean(additionalProperties.get(USE_ENUM_CASE_INSENSITIVE).toString()));
        }

        if (additionalProperties.containsKey(CodegenConstants.MAX_ATTEMPTS_FOR_RETRY)) {
            this.setMaxAttemptsForRetry(Integer.parseInt(additionalProperties.get(CodegenConstants.MAX_ATTEMPTS_FOR_RETRY).toString()));
        } else {
            additionalProperties.put(CodegenConstants.MAX_ATTEMPTS_FOR_RETRY, maxAttemptsForRetry);
        }

        if (additionalProperties.containsKey(CodegenConstants.WAIT_TIME_OF_THREAD)) {
            this.setWaitTimeMillis(Long.parseLong((additionalProperties.get(CodegenConstants.WAIT_TIME_OF_THREAD).toString())));
        } else {
            additionalProperties.put(CodegenConstants.WAIT_TIME_OF_THREAD, waitTimeMillis);
        }
        writePropertyBack(USE_ENUM_CASE_INSENSITIVE, useEnumCaseInsensitive);

        final String invokerFolder = (sourceFolder + '/' + invokerPackage).replace(".", "/");
        final String apiFolder = (sourceFolder + '/' + apiPackage).replace(".", "/");
        final String modelsFolder = (sourceFolder + File.separator + modelPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
        final String enumsFolder = (sourceFolder + File.separator + enumPackage().replace('.', File.separatorChar)).replace('/', File.separatorChar);
        authFolder = (sourceFolder + '/' + invokerPackage + ".auth").replace(".", "/");

        //Common files
        supportingFiles.add(new SupportingFile("pom.mustache", "", "pom.xml").doNotOverwrite());
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md").doNotOverwrite());
        supportingFiles.add(new SupportingFile("ApiClient.mustache", invokerFolder, "ApiClient.java"));
        supportingFiles.add(new SupportingFile("ServerConfiguration.mustache", invokerFolder, "ServerConfiguration.java"));
        supportingFiles.add(new SupportingFile("ServerVariable.mustache", invokerFolder, "ServerVariable.java"));
//        supportingFiles.add(new SupportingFile("maven.yml.mustache", ".github/workflows", "maven.yml"));
        if (dynamicOperations) {
            supportingFiles.add(new SupportingFile("openapi.mustache", projectFolder + "/resources/openapi", "openapi.yaml"));
            supportingFiles.add(new SupportingFile("apiOperation.mustache", invokerFolder, "ApiOperation.java"));
        } else {
            supportingFiles.add(new SupportingFile("openapi.mustache", "api", "openapi.yaml"));
        }

        // helper for client library that allow to parse/format java.time.OffsetDateTime or org.threeten.bp.OffsetDateTime
        if (additionalProperties.containsKey("jsr310") && (isLibrary(WEBCLIENT) || isLibrary(VERTX) || isLibrary(MICROPROFILE) || isLibrary(APACHE) || isLibrary(RESTCLIENT))) {
            supportingFiles.add(new SupportingFile("JavaTimeFormatter.mustache", invokerFolder, "JavaTimeFormatter.java"));
        }

        supportingFiles.add(new SupportingFile("StringUtil.mustache", invokerFolder, "StringUtil.java"));

        // google-api-client doesn't use the OpenAPI auth, because it uses Google Credential directly (HttpRequestInitializer)
        supportingFiles.add(new SupportingFile("auth/HttpBasicAuth.mustache", authFolder, "HttpBasicAuth.java"));
        supportingFiles.add(new SupportingFile("auth/HttpBearerAuth.mustache", authFolder, "HttpBearerAuth.java"));
        supportingFiles.add(new SupportingFile("auth/ApiKeyAuth.mustache", authFolder, "ApiKeyAuth.java"));

        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

        if (performBeanValidation) {
            supportingFiles.add(new SupportingFile("BeanValidationException.mustache", invokerFolder,
                    "BeanValidationException.java"));
        }

        if (additionalProperties.containsKey(CodegenConstants.SERIALIZATION_LIBRARY)) {
            setSerializationLibrary(additionalProperties.get(CodegenConstants.SERIALIZATION_LIBRARY).toString());
        }

        //TODO: add auto-generated doc to feign

        supportingFiles.add(new SupportingFile("apiException.mustache", invokerFolder, "ApiException.java"));
        supportingFiles.add(new SupportingFile("Configuration.mustache", invokerFolder, "Configuration.java"));
        supportingFiles.add(new SupportingFile("Pair.mustache", invokerFolder, "Pair.java"));

        supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));

        if (WEBCLIENT.equals(getLibrary())) {
            forceSerializationLibrary(SERIALIZATION_LIBRARY_JACKSON);
        }
        else {
            LOGGER.error("Unknown library option (-l/--library): {}", getLibrary());
        }

        if (getSerializationLibrary() == null) {
            LOGGER.info("No serializationLibrary configured, using '{}' as fallback", SERIALIZATION_LIBRARY_GSON);
            setSerializationLibrary(SERIALIZATION_LIBRARY_GSON);
        }
        switch (getSerializationLibrary()) {
            case SERIALIZATION_LIBRARY_JACKSON:
                additionalProperties.put(SERIALIZATION_LIBRARY_JACKSON, "true");
                additionalProperties.remove(SERIALIZATION_LIBRARY_GSON);
                additionalProperties.remove(SERIALIZATION_LIBRARY_JSONB);
                supportingFiles.add(new SupportingFile("RFC3339DateFormat.mustache", invokerFolder, "RFC3339DateFormat.java"));
                break;
            case SERIALIZATION_LIBRARY_GSON:
                additionalProperties.put(SERIALIZATION_LIBRARY_GSON, "true");
                additionalProperties.remove(SERIALIZATION_LIBRARY_JACKSON);
                additionalProperties.remove(SERIALIZATION_LIBRARY_JSONB);
                break;
            case SERIALIZATION_LIBRARY_JSONB:
                additionalProperties.put(SERIALIZATION_LIBRARY_JSONB, "true");
                additionalProperties.remove(SERIALIZATION_LIBRARY_JACKSON);
                additionalProperties.remove(SERIALIZATION_LIBRARY_GSON);
                break;
            default:
                additionalProperties.remove(SERIALIZATION_LIBRARY_JACKSON);
                additionalProperties.remove(SERIALIZATION_LIBRARY_GSON);
                additionalProperties.remove(SERIALIZATION_LIBRARY_JSONB);
                break;
        }

        // authentication related files
        // has OAuth defined
        if (ProcessUtils.hasOAuthMethods(openAPI)) {
            // for okhttp-gson (default), check to see if OAuth is defined and included OAuth-related files accordingly
            if (StringUtils.isEmpty(getLibrary())) {
                supportingFiles.add(new SupportingFile("auth/OAuthOkHttpClient.mustache", authFolder, "OAuthOkHttpClient.java"));
                supportingFiles.add(new SupportingFile("auth/RetryingOAuth.mustache", authFolder, "RetryingOAuth.java"));
            }

            // google-api-client doesn't use the OpenAPI auth, because it uses Google Credential directly (HttpRequestInitializer)
                supportingFiles.add(new SupportingFile("auth/OAuth.mustache", authFolder, "OAuth.java"));
                supportingFiles.add(new SupportingFile("auth/OAuthFlow.mustache", authFolder, "OAuthFlow.java"));
        }
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        super.postProcessOperationsWithModels(objs, allModels);

        if (WEBCLIENT.equals(getLibrary())) {
            OperationMap operations = objs.getOperations();
            if (operations != null) {
                List<CodegenOperation> ops = operations.getOperation();
                for (CodegenOperation operation : ops) {
                    if (!operation.vendorExtensions.containsKey(VendorExtension.X_WEBCLIENT_BLOCKING.getName()) && webclientBlockingOperations) {
                        operation.vendorExtensions.put(VendorExtension.X_WEBCLIENT_BLOCKING.getName(), true);
                    }

                    if (operation.isArray && !"string".equalsIgnoreCase(operation.returnBaseType)) {
                        operation.vendorExtensions.put(VendorExtension.X_WEBCLIENT_RETURN_EXCEPT_LIST_OF_STRING.getName(), true);
                    }
                }
            }
        }

        return objs;
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        return super.apiFilename(templateName, tag);
    }

    /**
     * Prioritizes consumes mime-type list by moving json-vendor and json mime-types up front, but
     * otherwise preserves original consumes definition order.
     * [application/vnd...+json,... application/json, ..as is..]
     *
     * @param consumes consumes mime-type list
     * @return
     */
    static List<Map<String, String>> prioritizeContentTypes(List<Map<String, String>> consumes) {
        if (consumes.size() <= 1)
            return consumes;

        List<Map<String, String>> prioritizedContentTypes = new ArrayList<>(consumes.size());

        List<Map<String, String>> jsonVendorMimeTypes = new ArrayList<>(consumes.size());
        List<Map<String, String>> jsonMimeTypes = new ArrayList<>(consumes.size());

        for (Map<String, String> consume : consumes) {
            if (isJsonVendorMimeType(consume.get(MEDIA_TYPE))) {
                jsonVendorMimeTypes.add(consume);
            } else if (isJsonMimeType(consume.get(MEDIA_TYPE))) {
                jsonMimeTypes.add(consume);
            } else
                prioritizedContentTypes.add(consume);
        }

        prioritizedContentTypes.addAll(0, jsonMimeTypes);
        prioritizedContentTypes.addAll(0, jsonVendorMimeTypes);
        return prioritizedContentTypes;
    }

    private static boolean isMultipartType(List<Map<String, String>> consumes) {
        Map<String, String> firstType = consumes.get(0);
        if (firstType != null) {
            return "multipart/form-data".equals(firstType.get(MEDIA_TYPE));
        }
        return false;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!BooleanUtils.toBoolean(model.isEnum)) {
            //final String lib = getLibrary();
            //Needed imports for Jackson based libraries
            if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_JACKSON)) {
                model.imports.add("JsonProperty");
                model.imports.add("JsonValue");
                model.imports.add("JsonInclude");
                model.imports.add("JsonTypeName");
            }
            if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_GSON)) {
                model.imports.add("SerializedName");
                model.imports.add("TypeAdapter");
                model.imports.add("JsonAdapter");
                model.imports.add("JsonReader");
                model.imports.add("JsonWriter");
                model.imports.add("IOException");
            }
        } else { // enum class
            //Needed imports for Jackson's JsonCreator
            if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_JACKSON)) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
        }
        if (MICROPROFILE.equals(getLibrary())) {
            model.imports.remove("ApiModelProperty");
            model.imports.remove("ApiModel");
            model.imports.remove("JsonSerialize");
            model.imports.remove("ToStringSerializer");
        }

        if (!BooleanUtils.toBoolean(model.isEnum)) {
            // needed by all pojos, but not enums
            if (AnnotationLibrary.SWAGGER2.equals(getAnnotationLibrary())) {
                model.imports.add("Schema");
            }
        }

        if ("set".equals(property.containerType) && !JACKSON.equals(serializationLibrary)) {
            // clean-up
            model.imports.remove("JsonDeserialize");
            property.vendorExtensions.remove("x-setter-extra-annotation");
        }
    }

    @Override
    public CodegenModel fromModel(String name, Schema model) {
        CodegenModel codegenModel = super.fromModel(name, model);
        if (MICROPROFILE.equals(getLibrary())) {
            // Remove io.swagger.annotations.ApiModel import
            codegenModel.imports.remove("ApiModel");
        }

        // TODO: inverse logic. Do not add the imports unconditionally in the first place.
        if (!AnnotationLibrary.SWAGGER1.equals(getAnnotationLibrary())) {
            // Remove io.swagger.annotations.* imports
            codegenModel.imports.remove("ApiModel");
            codegenModel.imports.remove("ApiModelProperty");
        }

        if (codegenModel.description != null) {
            if (AnnotationLibrary.SWAGGER2.equals(getAnnotationLibrary())) {
                codegenModel.imports.add("Schema");
            }
        }

        return codegenModel;
    }

    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        objs = super.postProcessModelsEnum(objs);

        //Needed import for Gson based libraries
        //ADD IMPORTS HERE
        List<Map<String, String>> imports = objs.getImports();

        if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_GSON)) {
            for (ModelMap mo : objs.getModels()) {
                CodegenModel cm = mo.getModel();
                // for enum model
                if (Boolean.TRUE.equals(cm.isEnum) && cm.allowableValues != null) {
                    cm.imports.add(importMapping.get("SerializedName"));
                    Map<String, String> item = new HashMap<String, String>();
                    item.put("import", importMapping.get("SerializedName"));
                    imports.add(item);
                }
            }
        }
        return objs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        objs = super.postProcessModels(objs);

        List<ModelMap> models = objs.getModels();

        if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_JACKSON)) {
            List<Map<String, String>> imports = objs.getImports();
            for (ModelMap mo : models) {
                CodegenModel cm = mo.getModel();
                boolean addNullableImports = false;

                for (CodegenProperty var : cm.vars) {
                    addNullableImports = isAddNullableImports(cm, addNullableImports, var);
                    if (Boolean.TRUE.equals(var.getVendorExtensions().get("x-enum-as-string"))) {
                        // treat enum string as just string
                        var.datatypeWithEnum = var.dataType;

                        if (StringUtils.isNotEmpty(var.defaultValue)) { // has default value
                            String defaultValue = var.defaultValue.substring(var.defaultValue.lastIndexOf('.') + 1);
                            for (Map<String, Object> enumVars : (List<Map<String, Object>>) var.getAllowableValues().get("enumVars")) {
                                if (defaultValue.equals(enumVars.get("name"))) {
                                    // update default to use the string directly instead of enum string
                                    var.defaultValue = (String) enumVars.get("value");
                                }
                            }
                        }

                        // add import for Set, HashSet
                        cm.imports.add("Set");
                        Map<String, String> importsSet = new HashMap<>();
                        importsSet.put("import", "java.util.Set");
                        imports.add(importsSet);
                        Map<String, String> importsHashSet = new HashMap<>();
                        importsHashSet.put("import", "java.util.HashSet");
                        imports.add(importsHashSet);
                    }

                }

                if (addNullableImports) {
                    Map<String, String> imports2Classnames = new HashMap<>();
                    imports2Classnames.put("JsonNullable", "org.openapitools.jackson.nullable.JsonNullable");
                    imports2Classnames.put("NoSuchElementException", "java.util.NoSuchElementException");
                    imports2Classnames.put("JsonIgnore", "com.fasterxml.jackson.annotation.JsonIgnore");
                    addImports(imports, cm, imports2Classnames);
                }
            }
        }

        for (ModelMap mo : models) {
            CodegenModel cm = mo.getModel();

            cm.getVendorExtensions().putIfAbsent("x-implements", new ArrayList<String>());
            if (this.parcelableModel) {
                ((ArrayList<String>) cm.getVendorExtensions().get("x-implements")).add("Parcelable");
            }
        }

        return objs;
    }

    @Override
    public Map<String, CodegenEnum> combineEnums(Map<String, ModelsMap> objs) {
        Map<String, CodegenEnum> enums = new HashMap<>();

        for (String key : objs.keySet()) {
            for (ModelMap modelMap : objs.get(key).getModels()) {
                CodegenModel m = modelMap.getModel();
                if (m.hasEnums) {
                    for (CodegenProperty var : m.getVars()) {
                        if (var.isEnum) {
                            CodegenEnum ce = new CodegenEnum();
                            ce.classname = var.enumName;
                            ce.name = var.name;
                            ce.filePackage = enumPackage;
                            ce.hasEnums = true;
                            ce.enumVars = parseAllowableValues(var.allowableValues.get("enumVars"));
                            ce.description = var.description;
                            ce.dataType = var.dataType;
                            ce.additionalEnumTypeAnnotations = (List<String>) modelMap.get(ADDITIONAL_ENUM_TYPE_ANNOTATIONS);
                            ce.useEnumCaseInsensitive = false;
                            ce.isNullable = var.isNullable;
                            ce.enumUnknownDefaultCase = parseEnumValues(var.allowableValues);
                            if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_JACKSON)) {
                                ce.jackson = true;
                            }
                            if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_GSON)) {
                                ce.gson = true;
                            }
                            if (additionalProperties.containsKey(SERIALIZATION_LIBRARY_JSONB)) {
                                ce.jsonb = true;
                            }
                            ce.isUri = false;
                            if (enums.containsKey(var.name)){
                                enums.replace(var.name, combineToEnum(enums.get(var.name), ce));
                            }
                            enums.putIfAbsent(var.name, ce);
                        }
                    }
                }
            }
        }

        return enums;
    }

    private CodegenEnum combineToEnum(CodegenEnum old, CodegenEnum last) {
        old.enumVars.addAll(last.enumVars);
        if (last.enumUnknownDefaultCase) {
            old.enumUnknownDefaultCase = true;
        }
        return old;
    }

    private boolean parseEnumValues(Map<String, Object> allowableValues) {
        return false;
    }

    private Set<EnumProperty> parseAllowableValues(Object objs) {
        List<Map<String, Object>> allowableValues = (List<Map<String, Object>>) objs;
        Set<EnumProperty> enumProperties = new HashSet<>();

        for(Map<String, Object> enumToValue : allowableValues) {
            EnumProperty enumProperty = new EnumProperty();
            enumProperty.name = enumToValue.get("name").toString();
            enumProperty.value = enumToValue.get("value").toString();
            enumProperty.enumUnknownDefaultCase = false;
            enumProperty.isString = true;
            enumProperty.withXml = false;
            enumProperty.isNullable = false;
            if (enumProperty.name.equals("unknown_default_open_api"))
                enumProperty.enumUnknownDefaultCase = true;
            enumProperties.add(enumProperty);
        }

        return enumProperties;
    }
    
    @Override
    protected boolean isConstructorWithAllArgsAllowed(CodegenModel codegenModel) {
        // implementation detail: allVars is not reliable if openapiNormalizer.REFACTOR_ALLOF_WITH_PROPERTIES_ONLY is disabled
        if (codegenModel.readOnlyVars.size() != codegenModel.vars.size() + codegenModel.parentVars.size()) {
            return super.isConstructorWithAllArgsAllowed(codegenModel);
        }
        return false;
    }

    public void setUseOneOfDiscriminatorLookup(boolean useOneOfDiscriminatorLookup) {
        this.useOneOfDiscriminatorLookup = useOneOfDiscriminatorLookup;
    }

    private boolean getUseSingleRequestParameter() {
        return useSingleRequestParameter;
    }

    private void setUseSingleRequestParameter(boolean useSingleRequestParameter) {
        this.useSingleRequestParameter = useSingleRequestParameter;
    }

    public void setDoNotUseRx(boolean doNotUseRx) {
        this.doNotUseRx = doNotUseRx;
    }

    public void setUsePlayWS(boolean usePlayWS) {
        this.usePlayWS = usePlayWS;
    }

    public void setAsyncNative(boolean asyncNative) {
        this.asyncNative = asyncNative;
    }

    public void setMicroprofileFramework(String microprofileFramework) {
        this.microprofileFramework = microprofileFramework;
    }

    public void setMicroprofileMutiny(boolean microprofileMutiny) {
        this.microprofileMutiny = microprofileMutiny;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public void setParcelableModel(boolean parcelableModel) {
        this.parcelableModel = parcelableModel;
    }

    public void setUseBeanValidation(boolean useBeanValidation) {
        this.useBeanValidation = useBeanValidation;
    }

    public void setPerformBeanValidation(boolean performBeanValidation) {
        this.performBeanValidation = performBeanValidation;
    }

    public void setUseGzipFeature(boolean useGzipFeature) {
        this.useGzipFeature = useGzipFeature;
    }

    public void setUseRuntimeException(boolean useRuntimeException) {
        this.useRuntimeException = useRuntimeException;
    }

    public void setUseReflectionEqualsHashCode(boolean useReflectionEqualsHashCode) {
        this.useReflectionEqualsHashCode = useReflectionEqualsHashCode;
    }

    public void setUseAbstractionForFiles(boolean useAbstractionForFiles) {
        this.useAbstractionForFiles = useAbstractionForFiles;
    }

    public void setDynamicOperations(final boolean dynamicOperations) {
        this.dynamicOperations = dynamicOperations;
    }

    public void setSupportStreaming(final boolean supportStreaming) {
        this.supportStreaming = supportStreaming;
    }

    public void setWithAWSV4Signature(boolean withAWSV4Signature) {
        this.withAWSV4Signature = withAWSV4Signature;
    }

    public void setErrorObjectType(final String errorObjectType) {
        this.errorObjectType = errorObjectType;
    }

    public void setGenerateClientAsBean(boolean generateClientAsBean) {
        this.generateClientAsBean = generateClientAsBean;
    }

    public void setUseEnumCaseInsensitive(boolean useEnumCaseInsensitive) {
        this.useEnumCaseInsensitive = useEnumCaseInsensitive;
    }

    public void setMaxAttemptsForRetry(int maxAttemptsForRetry) {
        this.maxAttemptsForRetry = maxAttemptsForRetry;
    }

    public void setWaitTimeMillis(long waitTimeMillis) {
        this.waitTimeMillis = waitTimeMillis;
    }

    /**
     * Serialization library.
     *
     * @return 'gson' or 'jackson'
     */
    public String getSerializationLibrary() {
        return serializationLibrary;
    }

    public void setSerializationLibrary(String serializationLibrary) {
        if (SERIALIZATION_LIBRARY_JACKSON.equalsIgnoreCase(serializationLibrary)) {
            this.serializationLibrary = SERIALIZATION_LIBRARY_JACKSON;
        } else if (SERIALIZATION_LIBRARY_GSON.equalsIgnoreCase(serializationLibrary)) {
            this.serializationLibrary = SERIALIZATION_LIBRARY_GSON;
        } else if (SERIALIZATION_LIBRARY_JSONB.equalsIgnoreCase(serializationLibrary)) {
            this.serializationLibrary = SERIALIZATION_LIBRARY_JSONB;
        } else {
            throw new IllegalArgumentException("Unexpected serializationLibrary value: " + serializationLibrary);
        }
    }

    public void forceSerializationLibrary(String serializationLibrary) {
        if ((this.serializationLibrary != null) && !this.serializationLibrary.equalsIgnoreCase(serializationLibrary)) {
            LOGGER.warn(
                    "The configured serializationLibrary '{}', is not supported by the library: '{}', switching back to: {}",
                    this.serializationLibrary, getLibrary(), serializationLibrary);
        }
        setSerializationLibrary(serializationLibrary);
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        generateYAMLSpecFile(objs);
        return super.postProcessSupportingFileData(objs);
    }

    private void setConfigKeyFromClassName(boolean configKeyFromClassName) {
        this.configKeyFromClassName = configKeyFromClassName;
    }

    @Override
    public String toApiVarName(String name) {
        String apiVarName = super.toApiVarName(name);
        if (reservedWords.contains(apiVarName)) {
            apiVarName = escapeReservedWord(apiVarName);
        }
        return apiVarName;
    }

    @Override
    public void addImportsToOneOfInterface(List<Map<String, String>> imports) {
        for (String i : Arrays.asList("JsonSubTypes", "JsonTypeInfo", "JsonIgnoreProperties")) {
            Map<String, String> oneImport = new HashMap<>();
            oneImport.put("import", importMapping.get(i));
            if (!imports.contains(oneImport)) {
                imports.add(oneImport);
            }
        }
    }

    @Override
    public List<VendorExtension> getSupportedVendorExtensions() {
        List<VendorExtension> extensions = super.getSupportedVendorExtensions();
        extensions.add(VendorExtension.X_WEBCLIENT_BLOCKING);
        return extensions;
    }
}
