package org.openapitools.codegen.iqhr.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonPropertyOrder({
        ServicesConfig.JSON_PROPERTY_BASE_PATH,
        ServicesConfig.JSON_PROPERTY_SERVICES
})
public class ServicesConfig {

    public static final String JSON_PROPERTY_BASE_PATH = "basePath";
    private String basePath;

    public static final String JSON_PROPERTY_SERVICES = "services";
    private Map<String, String> services;
}
