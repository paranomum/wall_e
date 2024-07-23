package org.openapitools.codegen.iqhr.cmd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.airline.Command;
import org.openapitools.codegen.iqhr.model.ServicesConfig;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

// NOTE: List can later have subcommands such as list languages, list types, list frameworks, etc.
@SuppressWarnings({"java:S106"})
@Command(name = "list", description = "Lists the available services")
public class ListGenerators extends OpenApiGeneratorCommand {

    private String configPath;

    @Override
    public void execute() {

        StringBuilder sb = new StringBuilder();

        Map<String, String> types = new HashMap<>();
        try {
            types = new ObjectMapper().readValue(new File(replaceJarFile()), ServicesConfig.class).getServices();
        }
        catch (IOException e) {
            System.err.println("[error] Can't read config file");
            return;
        }

        sb.append(System.lineSeparator());
        sb.append("The following services are available:");
        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());

        for (String type : types.keySet()) {
            sb.append("    - ").append(type.toLowerCase(Locale.ROOT)).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        System.out.printf(Locale.ROOT, "%s%n", sb.toString());
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
