package org.openapitools.codegen.iqhr.cmd;

import com.google.common.base.Objects;
import io.airlift.airline.Command;
import io.airlift.airline.Option;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConfigLoader;
import org.openapitools.codegen.iqhr.ServicesEnum;
import org.openapitools.codegen.meta.GeneratorMetadata;
import org.openapitools.codegen.meta.Stability;

import java.util.*;
import java.util.stream.Collectors;

// NOTE: List can later have subcommands such as list languages, list types, list frameworks, etc.
@SuppressWarnings({"java:S106"})
@Command(name = "list", description = "Lists the available services")
public class ListGenerators extends OpenApiGeneratorCommand {

    @Override
    public void execute() {

        StringBuilder sb = new StringBuilder();

        ServicesEnum[] types = ServicesEnum.values();

        sb.append(System.lineSeparator());
        sb.append("The following services are available:");
        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());

        for (ServicesEnum type : types) {
            sb.append("    - ").append(type.name().toLowerCase(Locale.ROOT)).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        System.out.printf(Locale.ROOT, "%s%n", sb.toString());
    }
}
