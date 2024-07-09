package org.openapitools.codegen.languages;

import org.openapitools.codegen.*;

import java.io.File;
import java.util.*;

import org.openapitools.codegen.model.ModelsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaConfigCodegen extends DefaultCodegen implements CodegenConfig {
    public static final String PROJECT_NAME = "projectName";

    private final Logger LOGGER = LoggerFactory.getLogger(JavaConfigCodegen.class);

    public CodegenType getTag() {
        return CodegenType.CONFIG;
    }

    public String getName() {
        return "java";
    }

    public String getHelp() {
        return "Generates a java config.";
    }

    public JavaConfigCodegen() {
        super();

        outputFolder = "generated-code" + File.separator + "java";
        modelTemplateFiles.put("model.mustache", ".zz");
        apiTemplateFiles.put("api.mustache", ".zz");
        embeddedTemplateDir = templateDir = "java";
        apiPackage = "Apis";
        modelPackage = "Models";
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        // TODO: Fill this out.
    }

    @Override
    public Map<String, CodegenEnum> combineEnums(Map<String, ModelsMap> objs) {
        return null;
    }
}
