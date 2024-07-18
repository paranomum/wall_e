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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.spi.FilterAttachable;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Option;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.MergedSpecBuilder;
import org.openapitools.codegen.iqhr.ServicesEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.openapitools.codegen.config.CodegenConfiguratorUtils.*;

@SuppressWarnings({"java:S106"})
@Command(name = "generate", description = "Generate code with the specified generator.")
public class Generate extends OpenApiGeneratorCommand {

    public CodegenConfigurator configurator;
    public Generator generator;

    @Option(name = {"-s", "--service"}, title = "service to generate",
            description = "services of project fro generation")
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

        String serviceProcessed = service.toLowerCase(Locale.ROOT);
        String url = ServicesEnum.getServiceUrl(serviceProcessed);
        if (url == null) {
            System.err.println("[error] There is no such service");
            System.exit(1);
        }

        String[] outDir = null;
        if (isNotEmpty(output)) {
            outDir = new String[]{"-o", output};
        }

        String modelPackage = "ru.rt.iqhr." + serviceProcessed + ".dto";
        String apiPackage = "ru.rt.iqhr." + serviceProcessed + ".api";
        String enumPackage = "ru.rt.iqhr." + serviceProcessed + ".model";
        String invokerPackage = "ru.rt.iqhr.invoker";

        String[] commonArgs =
                {"generate",
                        "-g", "java",
                        "--library", "webclient",
                        "-i", url,
                        "--api-package", apiPackage,
                        "--model-package", modelPackage,
                        "--invoker-package", invokerPackage,
                        "--enum-package", enumPackage};

        String[] allArgs = ArrayUtils.addAll(commonArgs, outDir);

        Cli.CliBuilder<Runnable> builder =
                Cli.<Runnable>builder("openapi-generator-cli")
                        .withCommands(org.openapitools.codegen.cmd.Generate.class);

        org.openapitools.codegen.cmd.Generate generate = (org.openapitools.codegen.cmd.Generate) builder.build().parse(allArgs);
        generate.run();
    }
}
