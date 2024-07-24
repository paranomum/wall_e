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

package org.openapitools.codegen.iqhr.cmd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Option;
import org.apache.commons.lang3.ArrayUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.iqhr.model.ServicesConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@SuppressWarnings({"java:S106"})
@Command(name = "generate", description = "Generate code with the specified generator.")
public class Generate extends OpenApiGeneratorCommand {

    public CodegenConfigurator configurator;
    public Generator generator;

    @Option(name = {"-c", "--config"}, title = "config with all services",
            description = "config with all data for generation, must be absolute path")
    private String configPath;

    @Option(name = {"-s", "--service"}, title = "service to generate",
            description = "services of project for generation", required = true)
    private String service;

    @Option(name = {"-o", "--output"}, title = "output directory",
            description = "where to write the generated files (current dir by default)")
    private String output = "";

    @Override
    public void execute() {
        if (service == null) {
            System.err.println("[error] Required option '-s' is missing");
            System.exit(1);
        }

        if(configPath == null) {
            configPath = replaceJarFile();
        }

        File configFile = new File(configPath);

        if(!configFile.canRead()) {
            System.err.println("[error] Can't read file. Check permissions, if the path is absolute and existence \n" +
                    "Config file path now is - " + configPath);
            System.exit(1);
        }

        ServicesConfig servicesConfig = null;

        try {
            servicesConfig = new ObjectMapper().readValue(configFile, ServicesConfig.class);
        }
        catch (IOException e) {
            System.err.println("[error] Can't readFile(). Check correction of file\n" +
                    "file path - " + configFile.getPath() + "\n" +
                    "error - " + e);
            System.exit(1);
        }

        String serviceProcessed = service.toLowerCase(Locale.ROOT);
        String url =  servicesConfig.getServices().get(serviceProcessed);
        if (url == null) {
            System.err.println("[error] There is no such service");
            System.exit(1);
        }

        String[] outDir = null;
        if (isNotEmpty(output)) {
            outDir = new String[]{"-o", output};
        }

        String modelPackage = servicesConfig.getBasePath() + '.' + serviceProcessed + ".dto";
        String apiPackage = servicesConfig.getBasePath() + '.' + serviceProcessed + ".api";
        String enumPackage = servicesConfig.getBasePath() + '.' + serviceProcessed + ".model";
        String invokerPackage = servicesConfig.getBasePath() + '.' +  ".invoker";

        String[] commonArgs =
                {"generate",
                        "-g", "java",
                        "--library", "webclient",
                        "-i", url,
                        "--api-package", apiPackage.replace('-', '_'),
                        "--model-package", modelPackage.replace('-', '_'),
                        "--invoker-package", invokerPackage.replace('-', '_'),
                        "--enum-package", enumPackage.replace('-', '_'), "--skip-validate-spec"};

        String[] allArgs = ArrayUtils.addAll(commonArgs, outDir);

        Cli.CliBuilder<Runnable> builder =
                Cli.<Runnable>builder("openapi-generator-cli")
                        .withCommands(org.openapitools.codegen.cmd.Generate.class);

        org.openapitools.codegen.cmd.Generate generate = (org.openapitools.codegen.cmd.Generate) builder.build().parse(allArgs);
        generate.run();
    }

    private String replaceJarFile() {
        try {
            File jarFile = new File(Generate.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI().getPath());
            configPath = jarFile.getParentFile().getPath();
        }
        catch (URISyntaxException ignored) {}
        if (System.getProperty("os.name").contains("Windows"))
            return configPath + "\\config-services.json";
        else
            return configPath + "/config-services.json";
    }
}
