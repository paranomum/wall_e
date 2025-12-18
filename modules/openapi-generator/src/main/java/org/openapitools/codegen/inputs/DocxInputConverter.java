package org.openapitools.codegen.inputs;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Конвертирует Word документ в OpenAPI спецификацию
 * Используется OpenAPI Generator для чтения .docx вместо JSON/YAML
 */
@Slf4j
public class DocxInputConverter {

	private final Logger LOGGER = LoggerFactory.getLogger(DocxInputConverter.class);

	private final DocxParser parser;

	public DocxInputConverter() {
		this.parser = new DocxParser();
	}

	/**
	 * Конвертирует .docx файл в OpenAPI JSON
	 */
	@SneakyThrows
	public OpenAPI convertDocxToOpenApiJson(String docxFilePath) {
		log.info("Converting DOCX to OpenAPI: {}", docxFilePath);

		File docxFile = new File(docxFilePath);
		if (!docxFile.exists()) {
			throw new IllegalArgumentException("File not found: " + docxFilePath);
		}

		if (!docxFilePath.endsWith(".docx")) {
			throw new IllegalArgumentException("File must be .docx format");
		}

		// Парсим документ
		OpenAPI openAPI = parser.parseDocxToOpenAPI(docxFilePath);

		// Конвертируем в JSON
		String json = Json.pretty(openAPI);

		LOGGER.info(json);

		return  openAPI;
	}
}
