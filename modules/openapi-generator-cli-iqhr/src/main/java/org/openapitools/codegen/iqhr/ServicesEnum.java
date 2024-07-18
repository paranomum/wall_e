package org.openapitools.codegen.iqhr;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum ServicesEnum {
    AUTH("identity", "http://iqhr-identity-service-default-iqhr.apps.okd.stage.digital.rt.ru/auth/v3/api-docs"),
    RECRUITING("recruiting", "http://iqhr-recruiting-service.apps.okd.stage.digital.rt.ru/recruiting/v3/api-docs/recruiting"),
    CANDIDATE_STORAGE_OUT("candidate-storage", "http://iqhr-candidatestorageout-service-iqhr.apps.okd.stage.digital.rt.ru/candidatestorage/v3/api-docs"),
    CANDIDATE_STORAGE_PRIVATE("candidate-storage-private", "http://iqhr-candidatestorage-service-default-iqhr.apps.okd.stage.digital.rt.ru/candidatestorage/v3/api-docs"),
    QUESTIONARY("questionary", "http://iqhr-questionnaire-service-iqhr.apps.okd.stage.digital.rt.ru/api/questionary/v3/api-docs"),
    VACANCY("vacancy", "http://iqhr-vacancy-portal-default-iqhr.apps.okd.stage.digital.rt.ru/vacancyportal/v3/api-docs"),
    DICTIONARY("dictionary", "http://iqhr-dictionary-service-default-iqhr.apps.okd.stage.digital.rt.ru/dictionary/v3/api-docs"),
    AGREEMENT("agreement", "http://iqhr-agreement-service.apps.okd.stage.digital.rt.ru/api/agreement/v3/api-docs"),
    ESIA("esia", "http://iqhr-esia-service-iqhr.apps.okd.stage.digital.rt.ru/api/esia/v3/api-docs"),
    WORKPLACE("workplace", "http://iqhr-workplace-service.apps.okd.stage.digital.rt.ru/workplace/v3/api-docs/workplace"),
    VATS("vats", "http://iqhr-vats-service-rtk-dev.apps.okd.stage.digital.rt.ru/vats/v3/api-docs");

    private static Map<String, String> SERVICE_TO_URL = new HashMap<>();

    static {
        for(ServicesEnum e : values()) {
            SERVICE_TO_URL.put(e.command, e.url);
        }
    }

    public final String command;
    public final String url;

    private ServicesEnum(String command, String url) {
        this.command = command;
        this.url = url;
    }

    public static String getServiceUrl(String service) {
        return SERVICE_TO_URL.get(service);
    }


}
