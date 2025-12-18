package org.openapitools.codegen.inputs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.*;
import org.apache.poi.xwpf.usermodel.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Конвертирует Word документ в OpenAPI 3.0 спецификацию
 */
public class DocxParser {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final Logger LOGGER = LoggerFactory.getLogger(DocxParser.class);
	/**
	 * Парсит Word документ и возвращает OpenAPI спецификацию
	 */
	public OpenAPI parseDocxToOpenAPI(String docxPath) throws Exception {
		OpenAPI openAPI = new OpenAPI();

		// Инициализируем базовую информацию
		openAPI.setInfo(new Info()
				.title("Generated API")
				.version("1.0.0"));

		openAPI.setServers(List.of(
				new Server().url("https://api.example.com")
		));

		Paths paths = new Paths();
		openAPI.setPaths(paths);

		try (FileInputStream fis = new FileInputStream(docxPath);
			 XWPFDocument document = new XWPFDocument(fis)) {

			// Извлекаем информацию из документа
			String title = extractTitle(document);
			String endpoint = extractEndpoint(document);
			String method = extractMethod(endpoint);
			String path = extractPath(endpoint);
			List<RequestParameter> requestParams = extractRequestParameters(document);
			List<SchemaNode> schemaNodes = convert(document, "TemplateDto");

			String responseExampleJson = extractResponse(document);
			List<ErrorCase> errorCases = extractErrorCases(document);
			LOGGER.info("{\ntitle - {},\nendpoint - {}\nmethod - {}\npath - {}\n,requestParams - {}\nresponseExample - {}\n}"
			,title,endpoint,  method, path, requestParams, responseExampleJson);
			// Обновляем информацию
			openAPI.getInfo().setTitle(title.isEmpty() ? "Generated API" : title);

			// Создаем PathItem
			PathItem pathItem = new PathItem();
			Operation operation = new Operation();
			operation.setDescription(title);
			operation.setOperationId(generateOperationId(method, path));

			// Добавляем параметры
			List<Parameter> parameters = new ArrayList<>();
			for (RequestParameter param : requestParams) {
				if (path.contains("{" + param.getName() + "}")) {
					parameters.add(createPathParameter(param));
				} else {
					parameters.add(createQueryParameter(param));
				}
			}
			operation.setParameters(parameters);

			Components components = new Components();

			// 3. Добавляем все схемы в components.schemas
			for (SchemaNode node : schemaNodes) {
				Schema<?> schema = node.getSchema();
				String name = schema.getTitle(); // или другое правило нейминга
				components.addSchemas(name, schema);
			}

			openAPI.setComponents(components);

//			Schema resultSchema = generateResultSchema(responseExampleJson);
			Schema resultSchema = getMainSchema(schemaNodes);

			// Добавляем response
			ApiResponses responses = createResponses(resultSchema, errorCases);
			operation.setResponses(responses);

			// Добавляем метод к пути
			switch (method.toUpperCase(Locale.ROOT)) {
				case "GET":
					pathItem.setGet(operation);
					break;
				case "POST":
					pathItem.setPost(operation);
					break;
				case "PUT":
					pathItem.setPut(operation);
					break;
				case "DELETE":
					pathItem.setDelete(operation);
					break;
				case "PATCH":
					pathItem.setPatch(operation);
					break;
			}

			paths.addPathItem(path, pathItem);
		}

		return openAPI;
	}

	/**
	 * Извлекает заголовок из документа
	 */
	private String extractTitle(XWPFDocument document) {
		for (XWPFParagraph paragraph : document.getParagraphs()) {
			String text = paragraph.getText().trim();
			if (!text.isEmpty() && !text.startsWith("#") &&
					!text.startsWith("IQHR") && !text.startsWith("Exported")) {
				return text;
			}
		}
		return "Получение шаблона анкеты кандидата по ID";
	}

	/**
	 * Извлекает полный endpoint в формате "GET .../candidate-form-templates/{id}"
	 */
	private String extractEndpoint(XWPFDocument document) {
		String fullText = extractFullText(document);

		// Поиск паттерна "GET .../path"
		Pattern pattern = Pattern.compile("(GET|POST|PUT|DELETE|PATCH)\\s+(\\.\\.\\.)?(/[\\w\\-/{}/]+)");
		Matcher matcher = pattern.matcher(fullText);

		if (matcher.find()) {
			String method = matcher.group(1);
			String dots = matcher.group(2) != null ? matcher.group(2) : "";
			String path = matcher.group(3);
			return method + " " + dots + path;
		}

		return "GET .../candidate-form-templates/{id}";
	}

	/**
	 * Извлекает HTTP метод из endpoint
	 * Пример: "GET .../candidate-form-templates/{id}" -> "GET"
	 */
	private String extractMethod(String endpoint) {
		if (endpoint == null || endpoint.isEmpty()) {
			return "GET";
		}

		Pattern pattern = Pattern.compile("^(GET|POST|PUT|DELETE|PATCH)");
		Matcher matcher = pattern.matcher(endpoint.trim());

		if (matcher.find()) {
			return matcher.group(1);
		}

		return "GET";
	}

	/**
	 * Извлекает путь из endpoint
	 * Пример: "GET .../candidate-form-templates/{id}" -> "/candidate-form-templates/{id}"
	 */
	private String extractPath(String endpoint) {
		if (endpoint == null || endpoint.isEmpty()) {
			return "/candidate-form-templates/{id}";
		}

		Pattern pattern = Pattern.compile("(/[\\w\\-/{}/]+)");
		Matcher matcher = pattern.matcher(endpoint);

		if (matcher.find()) {
			return matcher.group(1);
		}

		return "/candidate-form-templates/{id}";
	}

	/**
	 * Извлекает параметры запроса из таблицы
	 */
	private List<RequestParameter> extractRequestParameters(XWPFDocument document) {
		List<RequestParameter> params = new ArrayList<>();

		for (XWPFTable table : document.getTables()) {
			List<XWPFTableRow> rows = table.getRows();

			if (rows.size() > 1 && isParametersTable(rows.get(0))) {
				for (int i = 1; i < rows.size(); i++) {
					XWPFTableRow row = rows.get(i);
					if (row.getTableCells().size() >= 4) {
						RequestParameter param = new RequestParameter();

						List<XWPFTableCell> cells = row.getTableCells();
						param.setName(cells.get(0).getText().trim());
						param.setType(cells.get(1).getText().trim());

						// Пропускаем колонку "Наименование"
						String required = cells.get(3).getText().trim();
						param.setRequired("Да".equalsIgnoreCase(required));

						if (cells.size() > 4) {
							param.setDefaultValue(cells.get(4).getText().trim());
						}

						if (cells.size() > 5) {
							param.setDescription(cells.get(5).getText().trim());
						}

						params.add(param);
					}
				}
				break;
			}
		}

		return params;
	}

	/**
	 * Проверяет, является ли строка таблицы заголовком параметров
	 */
	private boolean isParametersTable(XWPFTableRow headerRow) {
		List<XWPFTableCell> cells = headerRow.getTableCells();
		if (cells.size() < 3) {
			return false;
		}

		String firstCell = cells.get(0).getText().toLowerCase(Locale.ROOT);
		String secondCell = cells.get(1).getText().toLowerCase(Locale.ROOT);

		return (firstCell.contains("параметр") || firstCell.contains("name")) &&
				(secondCell.contains("тип") || secondCell.contains("type"));
	}

	/**
	 * Универсальное извлечение JSON-примера ответа из секции
	 * "Пример ответа сервиса", без привязки к названию корневого поля.
	 */
	private String extractResponse(XWPFDocument document) {
		List<IBodyElement> bodyElements = document.getBodyElements();

		boolean inResponseSection = false;

		int j = 0;
		for (int i = 0; i < bodyElements.size(); i++) {
			IBodyElement element = bodyElements.get(i);

			// 1. Находим заголовок "Пример ответа сервиса"
			if (!inResponseSection && element.getElementType() == BodyElementType.PARAGRAPH) {
				XWPFParagraph p = (XWPFParagraph) element;
				String text = p.getText();
				if (text != null && text.contains("Пример ответа сервиса")) {
					j++;
					if (j == 2)
						inResponseSection = true;
				}
				continue;
			}

			if (!inResponseSection) {
				continue;
			}

			// 2. После заголовка ищем первый "кодовый" блок

			// 2.1. Таблица – типичный контейнер для примера
			if (element.getElementType() == BodyElementType.TABLE) {
				String json = collectJsonFromTable((XWPFTable) element);
				if (!json.isEmpty()) {
					return json;
				}
				// если из таблицы не получилось – продолжаем дальше
			}

			// 2.2. Параграфы подряд с фигурными скобками
			if (element.getElementType() == BodyElementType.PARAGRAPH) {
				XWPFParagraph p = (XWPFParagraph) element;
				String text = p.getText();
				if (text != null && text.contains("{")) {
					// считаем, что это начало JSON, собираем дальше
					return collectJsonFromParagraphs(bodyElements, i);
				}
			}

			// Если встретили другой крупный заголовок – выходим, JSON не нашли
			if (element.getElementType() == BodyElementType.PARAGRAPH) {
				XWPFParagraph p = (XWPFParagraph) element;
				String text = p.getText();
				if (text != null && (text.startsWith("# ") || text.startsWith("## "))) {
					break;
				}
			}
		}

		return "";
	}

	/**
	 * Собирает JSON из последовательности параграфов, начиная с индекса startIndex.
	 */
	private String collectJsonFromParagraphs(List<IBodyElement> bodyElements, int startIndex) {
		StringBuilder json = new StringBuilder();
		int braceCount = 0;
		boolean started = false;

		for (int i = startIndex; i < bodyElements.size(); i++) {
			IBodyElement element = bodyElements.get(i);
			if (element.getElementType() != BodyElementType.PARAGRAPH) {
				break;
			}

			XWPFParagraph p = (XWPFParagraph) element;
			String text = p.getText();
			if (text == null || text.isEmpty()) {
				continue;
			}

			// пока не начали – ищем первую "{"
			if (!started && !text.contains("{")) {
				continue;
			}

			json.append(text).append("\n");

			for (char c : text.toCharArray()) {
				if (c == '{') {
					braceCount++;
					started = true;
				} else if (c == '}') {
					braceCount--;
				}
			}

			if (started && braceCount == 0) {
				break;
			}
		}

		return json.toString().trim();
	}

	/**
	 * Собирает JSON из таблицы – каждую ячейку считаем строкой текста.
	 */
	private String collectJsonFromTable(XWPFTable table) {
		StringBuilder json = new StringBuilder();
		int braceCount = 0;
		boolean started = false;

		for (XWPFTableRow row : table.getRows()) {
			for (XWPFTableCell cell : row.getTableCells()) {
				String text = cell.getText();
				if (text == null || text.isEmpty()) {
					continue;
				}

				if (!started && !text.contains("{")) {
					continue;
				}

				json.append(text).append("\n");

				for (char c : text.toCharArray()) {
					if (c == '{') {
						braceCount++;
						started = true;
					} else if (c == '}') {
						braceCount--;
					}
				}

				if (started && braceCount == 0) {
					return json.toString().trim();
				}
			}
		}

		return json.toString().trim();
	}

	/**
	 * Ищет начало следующего заголовка после "Пример ответа сервиса"
	 * Примитивно: ищем фразы "Мапинг", "Алгоритм работы", "Параметры запроса" и т.п.
	 */
	private int indexOfNextHeader(String text) {
		int min = -1;

		String[] markers = new String[] {
				"Мапинг",
				"Алгоритм работы",
				"Параметры запроса",
				"Общее описание",
				"# ",
				"## "
		};

		for (String marker : markers) {
			int idx = text.indexOf(marker);
			if (idx != -1 && idx > 0) {
				if (min == -1 || idx < min) {
					min = idx;
				}
			}
		}

		return min;
	}



	/**
	 * Вспомогательный метод для извлечения всего текста из документа
	 */
	private String extractFullText(XWPFDocument document) {
		StringBuilder fullText = new StringBuilder();

		for (XWPFParagraph paragraph : document.getParagraphs()) {
			fullText.append(paragraph.getText()).append("\n");
		}

		for (XWPFTable table : document.getTables()) {
			for (XWPFTableRow row : table.getRows()) {
				for (XWPFTableCell cell : row.getTableCells()) {
					fullText.append(cell.getText()).append(" ");
				}
				fullText.append("\n");
			}
		}

		return fullText.toString();
	}

	private List<ErrorCase> extractErrorCases(XWPFDocument document) {
		List<ErrorCase> errors = new ArrayList<>();

		for (XWPFParagraph para : document.getParagraphs()) {
			String text = para.getText().trim();

			Pattern pattern = Pattern.compile("(\\d{3})\\s+([A-Za-z\\s]+)");
			Matcher matcher = pattern.matcher(text);

			if (matcher.find()) {
				errors.add(new ErrorCase(
						Integer.parseInt(matcher.group(1)),
						matcher.group(2),
						text
				));
			}
		}

		return errors;
	}

	private Parameter createPathParameter(RequestParameter param) {
		PathParameter parameter = new PathParameter();
		parameter.setName(param.getName());
		parameter.setRequired(param.isRequired());
		parameter.setDescription(param.getDescription());

		//edit mapType
		Schema<?> schema = new Schema<>();
		schema.setType(mapType(param.getType()));
		String mapFormat = mapFormat(param.getType());
		if (mapFormat != null)
			schema.setFormat(mapFormat);
		parameter.setSchema(schema);

		return parameter;
	}

	private Parameter createQueryParameter(RequestParameter param) {
		QueryParameter parameter = new QueryParameter();
		parameter.setName(param.getName());
		parameter.setRequired(param.isRequired());
		parameter.setDescription(param.getDescription());

		Schema<?> schema = new Schema<>();
		schema.setType(mapType(param.getType()));
		parameter.setSchema(schema);

		return parameter;
	}

	private ApiResponses createResponses(Schema resultSchema, List<ErrorCase> errorCases) {
		ApiResponses responses = new ApiResponses();

		// 200 успешный ответ
		ApiResponse successResponse = new ApiResponse()
				.description("Successful response");

		if (resultSchema != null) {
			try {
				LOGGER.debug("Создана главная Schema: ResultDto");
				MediaType mediaType = new MediaType()
						.schema(resultSchema);

				Content content = new Content()
						.addMediaType("application/json", mediaType);

				successResponse.setContent(content);

			} catch (Exception e) {
				LOGGER.error("✗ Ошибка при преобразовании примера ответа: {}",
						e.getMessage(), e);
				// При ошибке возвращаем базовый ответ без схемы
			}
		}

		responses.addApiResponse("200", successResponse);

		// Добавляем ошибки
		for (ErrorCase error : errorCases) {
			responses.addApiResponse(
					String.valueOf(error.getCode()),
					new ApiResponse().description(error.getDescription())
			);
		}

		return responses;
	}

	private Schema<?> generateSchemaFromJson(String json) throws Exception {
		Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
		return mapJsonToSchema(parsed);
	}

	private Schema<?> mapJsonToSchema(Object obj) {
		Schema<?> schema = new Schema<>();

		if (obj instanceof Map) {
			schema.setType("object");
			Map<String, Object> map = (Map<String, Object>) obj;
			map.forEach((key, value) -> {
				schema.addProperties(key, mapJsonToSchema(value));
			});
		} else if (obj instanceof List) {
			schema.setType("array");
			List<?> list = (List<?>) obj;
			if (!list.isEmpty()) {
				schema.setItems(mapJsonToSchema(list.get(0)));
			}
		} else if (obj instanceof String) {
			schema.setType("string");
		} else if (obj instanceof Integer) {
			schema.setType("integer");
		} else if (obj instanceof Double) {
			schema.setType("number");
		} else if (obj instanceof Boolean) {
			schema.setType("boolean");
		} else {
			schema.setType("object");
		}

		return schema;
	}

	private String mapType(String docxType) {
		switch (docxType.toLowerCase(Locale.ROOT)) {
			case "long":
			case "integer":
			case "int":
				return "integer";
			case "string":
			case "varchar":
				return "string";
			case "boolean":
				return "boolean";
			case "double":
			case "float":
				return "number";
			case "date":
				return "string";
			default:
				return "string";
		}
	}

	private String mapFormat(String docxType) {
		switch (docxType.toLowerCase(Locale.ROOT)) {
			case "long":
				return "int64";
			default:
				return null;
		}
	}


	private String generateOperationId(String method, String path) {
		return method.toLowerCase(Locale.ROOT) + path
				.replaceAll("[{}/-]", "_")
				.replaceAll("_+", "_");
	}

	private String cleanJsonString(String jsonString) {
		if (jsonString == null || jsonString.isEmpty()) {
			return jsonString;
		}

		// Удаляем NBSP (неразрывный пробел, char 160, \u00A0)
		String cleaned = jsonString.replace('\u00A0', ' ');

		// Удаляем другие невидимые управляющие символы
		cleaned = cleaned.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");

		// Удаляем переводы строк и табуляции (но сохраняем пробелы в строках)
		cleaned = cleaned.replaceAll("\\u2028|\\u2029", "\n");

		// Удаляем NBSP символы полностью
		cleaned = cleaned.replaceAll("\u00A0", "");

		// Нормализуем множественные пробелы на один
		cleaned = cleaned.replaceAll(" +", " ");

		cleaned = cleaned.trim();

		return cleaned;
	}

	/**
	 * Пытается "залечить" неполный JSON, добавляя недостающие закрывающие скобки.
	 * Это нужно для JSON, который был обрезан при вытаскивании из документа.
	 */
	private static String fixIncompleteJson(String json) {
		if (json == null || json.isEmpty()) {
			return json;
		}

		int openBraces = 0;
		int openBrackets = 0;
		int openParens = 0;
		boolean inString = false;
		boolean escaped = false;

		// Считаем открытые и закрытые скобки
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);

			if (escaped) {
				escaped = false;
				continue;
			}

			if (c == '\\') {
				escaped = true;
				continue;
			}

			if (c == '"' && !escaped) {
				inString = !inString;
				continue;
			}

			if (inString) {
				continue;
			}

			if (c == '{') openBraces++;
			else if (c == '}') openBraces--;
			else if (c == '[') openBrackets++;
			else if (c == ']') openBrackets--;
			else if (c == '(') openParens++;
			else if (c == ')') openParens--;
		}

		// Добавляем недостающие закрывающие скобки
		StringBuilder fixed = new StringBuilder(json);

		for (int i = 0; i < openBraces; i++) {
			fixed.append("}");
		}
		for (int i = 0; i < openBrackets; i++) {
			fixed.append("]");
		}
		for (int i = 0; i < openParens; i++) {
			fixed.append(")");
		}

		return fixed.toString();
	}

	/**
	 * Преобразует JSON строку (результат extractResponse) в Map компонентов OpenAPI Schema.
	 *
	 * @param jsonResponse JSON строка из документа
	 * @return Map<String, Schema> где ключ - имя компонента, значение - Schema
	 */
	public Map<String, Schema> convertJsonToOpenApiComponents(String jsonResponse) {
		Map<String, Schema> components = new LinkedHashMap<>();

		try {
			// Очищаем JSON от скрытых символов и неправильного форматирования
			String cleanedJson = fixIncompleteJson(cleanJsonString(jsonResponse));

			JsonNode rootNode = objectMapper.readTree(cleanedJson);
			JsonNode resultNode = rootNode.get("result");

			if (resultNode == null) {
				return components;
			}

			JsonNode templateNode = resultNode.get("template");

			if (templateNode == null) {
				return components;
			}

			// Создаём главную Schema для template
			Schema templateSchema = createSchemaFromNode(templateNode, "TemplateDto");
			components.put("TemplateDto", templateSchema);

			// Обрабатываем properties верхнего уровня (секции)
			JsonNode propertiesNode = templateNode.get("properties");
			if (propertiesNode != null && propertiesNode.isObject()) {
				Iterator<String> fieldNames = propertiesNode.fieldNames();
				while (fieldNames.hasNext()) {
					String sectionName = fieldNames.next();
					JsonNode sectionNode = propertiesNode.get(sectionName);

					// Создаём Schema для каждой секции
					String sectionDtoName = toPascalCase(sectionName) + "Dto";
					Schema sectionSchema = createSchemaFromNode(sectionNode, sectionDtoName);
					components.put(sectionDtoName, sectionSchema);

					// Обрабатываем вложенные свойства секции (поля)
					JsonNode sectionPropertiesNode = sectionNode.get("properties");
					if (sectionPropertiesNode != null && sectionPropertiesNode.isObject()) {
						Iterator<String> fieldNamesInSection = sectionPropertiesNode.fieldNames();
						while (fieldNamesInSection.hasNext()) {
							String fieldName = fieldNamesInSection.next();
							JsonNode fieldNode = sectionPropertiesNode.get(fieldName);

							// Обрабатываем вложенные структуры в полях
							extractNestedStructures(fieldNode, components, sectionDtoName);
						}
					}
				}
			}

		} catch (Exception e) {
			throw new RuntimeException("Ошибка при парсинге JSON: " + e.getMessage(), e);
		}

		return components;
	}

	/**
	 * Рекурсивно извлекает вложенные структуры (errorMessage, items, prefilledValue и т.д.)
	 */
	private static void extractNestedStructures(JsonNode fieldNode, Map<String, Schema> components,
												String parentDtoName) {
		if (fieldNode == null || !fieldNode.isObject()) {
			return;
		}

		// Проверяем errorMessage
		JsonNode errorMessageNode = fieldNode.get("errorMessage");
		if (errorMessageNode != null && errorMessageNode.isObject()) {
			String errorMessageDtoName = "ErrorMessageDto";
			if (!components.containsKey(errorMessageDtoName)) {
				Schema errorMessageSchema = createSchemaFromNode(errorMessageNode, errorMessageDtoName);
				components.put(errorMessageDtoName, errorMessageSchema);
			}
		}

		// Проверяем items (для array типов)
		JsonNode itemsNode = fieldNode.get("items");
		if (itemsNode != null && itemsNode.isObject()) {
			String itemsDtoName = "ItemsDto";
			if (!components.containsKey(itemsDtoName)) {
				Schema itemsSchema = createSchemaFromNode(itemsNode, itemsDtoName);
				components.put(itemsDtoName, itemsSchema);

				// Проверяем errorMessage внутри items
				JsonNode itemsErrorMessageNode = itemsNode.get("errorMessage");
				if (itemsErrorMessageNode != null && itemsErrorMessageNode.isObject()) {
					String itemsErrorMessageDtoName = "ItemsErrorMessageDto";
					if (!components.containsKey(itemsErrorMessageDtoName)) {
						Schema itemsErrorMessageSchema = createSchemaFromNode(itemsErrorMessageNode,
								itemsErrorMessageDtoName);
						components.put(itemsErrorMessageDtoName, itemsErrorMessageSchema);
					}
				}
			}
		}

		// Проверяем prefilledValue (если это object)
		JsonNode prefilledValueNode = fieldNode.get("prefilledValue");
		if (prefilledValueNode != null && prefilledValueNode.isObject()) {
			String prefilledValueDtoName = "PrefilledValueDto";
			if (!components.containsKey(prefilledValueDtoName)) {
				Schema prefilledValueSchema = createSchemaFromNode(prefilledValueNode, prefilledValueDtoName);
				components.put(prefilledValueDtoName, prefilledValueSchema);
			}
		}
	}

	/**
	 * Создаёт Schema из JsonNode.
	 */
	private static Schema createSchemaFromNode(JsonNode node, String dtoName) {
		Schema schema = new Schema<>();

		// Устанавливаем title (имя DTO)
		schema.setTitle(dtoName);

		// Обрабатываем основные свойства
		if (node.has("type")) {
			schema.setType(node.get("type").asText());
		}

		if (node.has("title")) {
			String title = node.get("title").asText();
			// Если title не совпадает с dtoName, устанавливаем его как description
			if (!title.equals(dtoName)) {
				schema.setDescription(title);
			}
		}

		if (node.has("description")) {
			schema.setDescription(node.get("description").asText());
		}

		// Обрабатываем required
		if (node.has("required") && node.get("required").isArray()) {
			List<String> required = new ArrayList<>();
			node.get("required").forEach(item -> required.add(item.asText()));
			schema.setRequired(required);
		}

		// Обрабатываем properties
		if (node.has("properties") && node.get("properties").isObject()) {
			Map<String, Schema> properties = new LinkedHashMap<>();
			JsonNode propertiesNode = node.get("properties");

			Iterator<String> fieldNames = propertiesNode.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				JsonNode fieldNode = propertiesNode.get(fieldName);

				Schema propertySchema = new Schema<>();
				buildPropertySchema(fieldNode, propertySchema);
				properties.put(fieldName, propertySchema);
			}

			schema.setProperties(properties);
		}

		// Обрабатываем items (для array типов)
		if (node.has("items") && node.get("items").isObject()) {
			Schema itemsSchema = new Schema<>();
			JsonNode itemsNode = node.get("items");
			buildPropertySchema(itemsNode, itemsSchema);
			schema.setItems(itemsSchema);
		}

		// Обрабатываем числовые ограничения
		if (node.has("minLength")) {
			schema.setMinLength(node.get("minLength").asInt());
		}
		if (node.has("maxLength")) {
			schema.setMaxLength(node.get("maxLength").asInt());
		}
		if (node.has("minItems")) {
			schema.setMinItems(node.get("minItems").asInt());
		}
		if (node.has("maxItems")) {
			schema.setMaxItems(node.get("maxItems").asInt());
		}

		// Обрабатываем паттерны и форматы
		if (node.has("pattern")) {
			schema.setPattern(node.get("pattern").asText());
		}
		if (node.has("format")) {
			schema.setFormat(node.get("format").asText());
		}

		// Обрабатываем пользовательские свойства
		addCustomProperties(node, schema);

		return schema;
	}

	/**
	 * Строит Schema для свойства, обрабатывая все стандартные JSON Schema поля.
	 */
	private static void buildPropertySchema(JsonNode fieldNode, Schema schema) {
		if (fieldNode == null || !fieldNode.isObject()) {
			return;
		}

		// Базовые типы
		if (fieldNode.has("type")) {
			schema.setType(fieldNode.get("type").asText());
		}

		// Метаданные
		if (fieldNode.has("title")) {
			schema.setTitle(fieldNode.get("title").asText());
		}
		if (fieldNode.has("description")) {
			schema.setDescription(fieldNode.get("description").asText());
		}

		// Ограничения строк
		if (fieldNode.has("minLength")) {
			schema.setMinLength(fieldNode.get("minLength").asInt());
		}
		if (fieldNode.has("maxLength")) {
			schema.setMaxLength(fieldNode.get("maxLength").asInt());
		}
		if (fieldNode.has("pattern")) {
			schema.setPattern(fieldNode.get("pattern").asText());
		}

		// Ограничения массивов
		if (fieldNode.has("minItems")) {
			schema.setMinItems(fieldNode.get("minItems").asInt());
		}
		if (fieldNode.has("maxItems")) {
			schema.setMaxItems(fieldNode.get("maxItems").asInt());
		}

		// Форматы
		if (fieldNode.has("format")) {
			schema.setFormat(fieldNode.get("format").asText());
		}

		// Вложенные items для array типов
		if (fieldNode.has("items") && fieldNode.get("items").isObject()) {
			Schema itemsSchema = new Schema<>();
			buildPropertySchema(fieldNode.get("items"), itemsSchema);
			schema.setItems(itemsSchema);
		}

		// Вложенные properties для object типов
		if (fieldNode.has("properties") && fieldNode.get("properties").isObject()) {
			Map<String, Schema> properties = new LinkedHashMap<>();
			JsonNode propertiesNode = fieldNode.get("properties");

			Iterator<String> fieldNames = propertiesNode.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				JsonNode propNode = propertiesNode.get(fieldName);

				Schema propSchema = new Schema<>();
				buildPropertySchema(propNode, propSchema);
				properties.put(fieldName, propSchema);
			}

			schema.setProperties(properties);
		}

		// Обработка required для objects
		if (fieldNode.has("required") && fieldNode.get("required").isArray()) {
			List<String> required = new ArrayList<>();
			fieldNode.get("required").forEach(item -> required.add(item.asText()));
			schema.setRequired(required);
		}

		// Пользовательские свойства (uiType, dictionaryType и т.д.)
		addCustomProperties(fieldNode, schema);
	}

	/**
	 * Добавляет пользовательские свойства в Schema (расширения для UI).
	 */
	private static void addCustomProperties(JsonNode node, Schema schema) {
		Map<String, Object> extensions = new HashMap<>();

		// uiType - тип UI элемента
		if (node.has("uiType")) {
			extensions.put("x-ui-type", node.get("uiType").asText());
		}

		// Свойства Dictionary
		if (node.has("dictionaryType")) {
			extensions.put("x-dictionary-type", node.get("dictionaryType").asText());
		}
		if (node.has("dictionaryAlias")) {
			extensions.put("x-dictionary-alias", node.get("dictionaryAlias").asText());
		}

		// Файловые свойства
		if (node.has("multi")) {
			extensions.put("x-multi", node.get("multi").asBoolean());
		}
		if (node.has("maxFileSize")) {
			extensions.put("x-max-file-size", node.get("maxFileSize").asLong());
		}
		if (node.has("contentMediaType")) {
			extensions.put("x-content-media-type", node.get("contentMediaType").asText());
		}

		// UI форматы
		if (node.has("uiFormat")) {
			extensions.put("x-ui-format", node.get("uiFormat").asText());
		}

		// Предзаполненные значения
		if (node.has("prefilledValue")) {
			JsonNode prefilledNode = node.get("prefilledValue");
			if (prefilledNode.isTextual()) {
				extensions.put("x-prefilled-value", prefilledNode.asText());
			} else if (prefilledNode.isObject()) {
				extensions.put("x-prefilled-value", prefilledNode.toString());
			}
		}

		// Сообщения об ошибках
		if (node.has("errorMessage") && node.get("errorMessage").isObject()) {
			extensions.put("x-error-message", node.get("errorMessage").toString());
		}

		// Добавляем расширения в Schema
		if (!extensions.isEmpty()) {
			extensions.forEach((key, value) -> schema.addExtension(key, value));
		}
	}

	private Schema generateResultSchema(String jsonResponse) {
		Schema resultSchema = new Schema<>();

		try {
			String cleanedJson = fixIncompleteJson(cleanJsonString(jsonResponse));

			JsonNode rootNode = objectMapper.readTree(cleanedJson);
			JsonNode resultNode = rootNode.get("result");

			if (resultNode == null) {
				return resultSchema;
			}

			resultSchema.setType("object");
			resultSchema.setTitle("ResultDto");
			resultSchema.setDescription("Результат запроса шаблона анкеты кандидата");

			Map<String, Schema> resultProperties = new LinkedHashMap<>();

			// Свойство id
			if (resultNode.has("id")) {
				Schema idSchema = new Schema<>();
				idSchema.setType("string");
				idSchema.setTitle("id");
				idSchema.setDescription("Уникальный идентификатор результата");
				resultProperties.put("id", idSchema);
			}

			// Свойство template - это ссылка на компонент TemplateDto
			if (resultNode.has("template")) {
				Schema templateRefSchema = new Schema<>();
				templateRefSchema.set$ref("#/components/schemas/TemplateDto");
				resultProperties.put("template", templateRefSchema);
			}

			resultSchema.setProperties(resultProperties);
			resultSchema.setRequired(Arrays.asList("id", "template"));

		} catch (Exception e) {
			throw new RuntimeException("Ошибка при генерации Result Schema: " + e.getMessage(), e);
		}

		return resultSchema;
	}

	/**
	 * Конвертирует camelCase или snake_case в PascalCase.
	 * Пример: "candidate_basics" -> "CandidateBasics", "candidateBasics" -> "CandidateBasics"
	 */
	private static String toPascalCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		String[] words;

		// Если есть подчеркивания - разбиваем по ним
		if (input.contains("_")) {
			words = input.split("_");
		} else {
			// Иначе разбиваем по заглавным буквам в camelCase
			words = input.split("(?=[A-Z])");
		}

		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) continue;
			result.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
					.append(word.substring(1).toLowerCase(Locale.ROOT));
		}

		return result.toString();
	}

	/**
	 * Парсит DOCX файл и извлекает все таблицы маппинга
	 */
	public List<MappingEntry> parseDocument(String filePath) throws IOException {
		try (FileInputStream fis = new FileInputStream(filePath);
			 XWPFDocument document = new XWPFDocument(fis)) {

			return extractMappingRules(document);
		}
	}

	/**
	 * Извлекает правила маппинга из таблицы DOCX.
	 * Ищет таблицу, где первый заголовок похож на "Поле".
	 * Поддерживает вложенные поля (с отступом в первом столбце).
	 *
	 * @param document DOCX документ
	 * @return список MappingEntry с иерархией
	 */
	public List<MappingEntry> extractMappingRules(XWPFDocument document) {
		List<MappingEntry> mappings = new ArrayList<>();

		for (XWPFTable table : document.getTables()) {
			List<XWPFTableRow> rows = table.getRows();

			// Проверка, что таблица не пустая и это нужная таблица (по заголовку)
			if (rows.size() > 1 && isMappingTable(rows.get(0))) {

				// Проходим со 2-й строки (index 1), пропуская шапку
				for (int i = 1; i < rows.size(); i++) {
					XWPFTableRow row = rows.get(i);
					List<XWPFTableCell> cells = row.getTableCells();

					// Ожидаем минимум 5 колонок: Поле, Тип, Наименование, Обязательность, Маппинг
					if (cells.size() >= 5) {
						MappingEntry entry = parseTableRow(cells, mappings);
						if (entry != null) {
							mappings.add(entry);
						}
					}
				}

				// Если нашли нужную таблицу и распарсили, выходим из цикла по таблицам
				break;
			}
		}

		return mappings;
	}

	/**
	 * Парсит одну строку таблицы и создает MappingEntry.
	 * Обрабатывает вложенные поля по количеству ведущих точек/пробелов.
	 */
	private MappingEntry parseTableRow(List<XWPFTableCell> cells, List<MappingEntry> previousEntries) {
		try {
			String rawField = cells.get(0).getText();

			// Проверяем, является ли строка пустой (пропускаем такие строки)
			if (isRowEmpty(cells)) {
				return null;
			}

			// Определяем уровень вложенности по количеству ведущих точек/пробелов
			int nestLevel = calculateNestLevel(rawField);
			String cleanFieldName = extractCleanFieldName(rawField);

			// Пропускаем строки с пустым названием поля
			if (cleanFieldName.isEmpty()) {
				return null;
			}

			MappingEntry entry = new MappingEntry();
			entry.setField(cleanFieldName);
			entry.setType(cleanText(cells.get(1).getText()));
			entry.setDescription(cleanText(cells.get(2).getText()));

			// Обработка обязательности (поддержка различных форматов)
			String reqText = cleanText(cells.get(3).getText());
			entry.setRequired(isRequired(reqText));

			entry.setMappingRule(cleanText(cells.get(4).getText()));

			// Определяем родителя на основе уровня вложенности
			MappingEntry parent = determineParent(nestLevel, previousEntries);
			entry.setParent(parent);

			return entry;

		} catch (Exception e) {
			System.err.println("Ошибка при парсинге строки таблицы: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Проверяет, является ли строка полностью пустой
	 */
	private boolean isRowEmpty(List<XWPFTableCell> cells) {
		for (XWPFTableCell cell : cells) {
			String text = cleanText(cell.getText());
			if (!text.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Вычисляет уровень вложенности поля по количеству ведущих символов
	 * (точки, пробелы, табуляция)
	 */
	private int calculateNestLevel(String fieldText) {
		String[] nestedCalc = fieldText.split("\\.");
		return nestedCalc.length - 1;
	}

	/**
	 * Извлекает чистое имя поля, удаляя все ведущие служебные символы
	 */
	private String extractCleanFieldName(String fieldText) {
		// Удаляем ведущие точки, пробелы, табуляции
		String cleaned = fieldText.replaceAll("^[\\s\\t]+", "").trim();

		// Удаляем специальные символы, оставляя только буквы, цифры и подчеркивание
		String cleanedSecond = cleaned.replaceAll("[^a-zA-Z0-9_.]", "");

		String[] names = cleanedSecond.split("\\.");

		return names[names.length - 1];
	}

	/**
	 * Проверяет, является ли поле обязательным
	 * Поддерживает: "Да", "Yes", "true", "1"
	 */
	private boolean isRequired(String text) {
		return "да".equalsIgnoreCase(text) ||
				"yes".equalsIgnoreCase(text) ||
				"true".equalsIgnoreCase(text) ||
				"1".equals(text) ||
				"v".equalsIgnoreCase(text) ||
				"x".equalsIgnoreCase(text);
	}

	/**
	 * Определяет родительское поле на основе уровня вложенности.
	 * Для вложенных полей (уровень > 0) ищет ближайшее предыдущее поле на уровень выше.
	 */
	private MappingEntry determineParent(int nestLevel, List<MappingEntry> previousEntries) {
		if (nestLevel == 0 || previousEntries.isEmpty()) {
			return null;
		}

		// Для каждого уровня вложенности идем в обратном порядке
		// Ищем ближайшее поле на нужном уровне выше
		int targetLevel = nestLevel - 1;

		for (int i = previousEntries.size() - 1; i >= 0; i--) {
			MappingEntry candidate = previousEntries.get(i);

			// Вычисляем уровень вложенности кандидата
			int candidateLevel = calculateCandidateLevel(candidate);

			if (candidateLevel == targetLevel) {
				return candidate;
			}
		}

		// Если не найдено точного совпадения, возвращаем последнее поле на уровень выше
		if (!previousEntries.isEmpty()) {
			MappingEntry last = previousEntries.get(previousEntries.size() - 1);
			if (calculateCandidateLevel(last) == targetLevel) {
				return last;
			}
		}

		return null;
	}

	/**
	 * Вычисляет глубину вложенности существующей MappingEntry
	 */
	private int calculateCandidateLevel(MappingEntry entry) {
		int level = 0;
		MappingEntry current = entry;
		while (current.getParent() != null) {
			level++;
			current = current.getParent();
		}
		return level;
	}

	/**
	 * Очищает текст из ячейки таблицы:
	 * - удаляет лишние пробелы
	 * - удаляет невидимые символы (zero-width, BOM и т.д.)
	 * - нормализует перевод строк
	 */
	private String cleanText(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}

		// Удаляем BOM и другие невидимые символы Unicode
		text = text.replaceAll("\\u200B|\\u200C|\\u200D|\\uFEFF", "");

		// Нормализуем перевод строк и табуляцию
		text = text.replaceAll("[\\r\\n\\t]+", " ");

		// Удаляем множественные пробелы
		text = text.replaceAll(" +", " ");

		// Удаляем пробелы в начале и конце
		return text.trim();
	}

	/**
	 * Проверяет заголовки таблицы, чтобы убедиться, что это таблица маппинга.
	 */
	private boolean isMappingTable(XWPFTableRow headerRow) {
		List<XWPFTableCell> cells = headerRow.getTableCells();
		if (cells.isEmpty()) return false;

		// Проверяем первую ячейку, она должна содержать "Поле"
		String firstHeader = cells.get(0).getText().trim();
		return firstHeader.equalsIgnoreCase("Поле") || firstHeader.contains("Поле");
	}

	/**
	 * Сериализация в красивый JSON строку
	 */
	private String serializeToJson(Object object) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			// Включаем pretty-print (отступы)
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			return mapper.writeValueAsString(object);
		} catch (Exception e) {
			throw new RuntimeException("Ошибка при создании JSON", e);
		}
	}

	public List<MappingEntry> parseTable(String filePath, int tableIndex) throws IOException {
		List<MappingEntry> entries = new ArrayList<>();

		try (FileInputStream fis = new FileInputStream(filePath);
			 XWPFDocument document = new XWPFDocument(fis)) {

			if (document.getTables().size() <= tableIndex) {
				throw new IllegalArgumentException("Таблица с индексом " + tableIndex + " не найдена");
			}

			XWPFTable table = document.getTables().get(tableIndex);

			// Пропускаем заголовок (первая строка)
			for (int i = 1; i < table.getRows().size(); i++) {
				XWPFTableRow row = table.getRows().get(i);

				if (row.getTableCells().size() < 5) {
					continue;
				}

				MappingEntry entry = parseRow(row, entries);
				if (entry != null) {
					entries.add(entry);
				}
			}
		}

		return entries;
	}

	private MappingEntry parseRow(XWPFTableRow row, List<MappingEntry> previousEntries) {
		try {
			String field = getCellText(row, 0).trim();
			String type = getCellText(row, 1).trim();
			String description = getCellText(row, 2).trim();
			String obligatory = getCellText(row, 3).trim();
			String mappingRule = getCellText(row, 4).trim();

			if (field.isEmpty()) {
				return null;
			}

			boolean required = "да".equalsIgnoreCase(obligatory) || "true".equalsIgnoreCase(obligatory);

			// Определяем родителя для вложенных полей
			MappingEntry parent = detectParent(field, previousEntries);

			// Очищаем название поля (удаляем точки если это вложенное поле)
			String cleanField = extractFieldName(field);

			return new MappingEntry(
					cleanField,
					type,
					description,
					required,
					mappingRule,
					parent
			);
		} catch (Exception e) {
			System.err.println("Ошибка при парсинге строки: " + e.getMessage());
			return null;
		}
	}

	private String getCellText(XWPFTableRow row, int cellIndex) {
		if (row.getTableCells().size() <= cellIndex) {
			return "";
		}
		return row.getTableCells().get(cellIndex).getText();
	}

	/**
	 * Определяет родительский элемент по отступу или иерархии
	 */
	private MappingEntry detectParent(String field, List<MappingEntry> previousEntries) {
		// Если поле начинается с точки или имеет отступ - это вложенное поле
		if (field.startsWith(".")) {
			if (!previousEntries.isEmpty()) {
				return previousEntries.get(previousEntries.size() - 1);
			}
		}
		return null;
	}

	/**
	 * Извлекает имя поля, удаляя служебные символы
	 */
	private String extractFieldName(String field) {
		return field.replaceAll("^\\s*\\.+\\s*", "").trim();
	}

	/**
	 * Строит список SchemaNode (bottom-up).
	 * main = true ТОЛЬКО для root элементов (parent == null).
	 * Вложенные Object/Map остаются ИНЛАЙН в root schema.
	 */
	public List<SchemaNode> buildSchemas(List<MappingEntry> entries, String mainSchemaName) {
		// 1. Сортируем записи по глубине (от самых глубоких к корневым)
		List<MappingEntry> sortedByDepth = entries.stream()
				.sorted((a, b) -> Integer.compare(b.getDepthLevel(), a.getDepthLevel()))
				.collect(Collectors.toList());

		// 2. Группируем по уровню вложенности
		Map<Integer, List<MappingEntry>> entriesByLevel = new LinkedHashMap<>();
		for (MappingEntry entry : sortedByDepth) {
			int level = entry.getDepthLevel();
			entriesByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(entry);
		}

		System.out.println("\n=== Построение SchemaNodes (bottom-up) ===");
		System.out.println("Всего entries: " + entries.size());
		System.out.println("Всего уровней: " + entriesByLevel.size());

		for (Map.Entry<Integer, List<MappingEntry>> levelEntry : entriesByLevel.entrySet()) {
			System.out.println("  Уровень " + levelEntry.getKey() + ": " + levelEntry.getValue().size() + " записей");
		}

		// 3. Кэш для уже построенных Schema
		Map<MappingEntry, Schema<?>> schemaCache = new LinkedHashMap<>();

		// 4. Проходим по уровням от самых глубоких к корневым
		List<Integer> levelsSorted = sortedByLevel(entriesByLevel.keySet());

		for (int level : levelsSorted) {
			List<MappingEntry> levelEntries = entriesByLevel.get(level);

			System.out.println("\n--- Обработка уровня вложенности: " + level + " ---");

			for (MappingEntry entry : levelEntries) {
				Schema<?> schema = buildEntrySchema(entry, entries, schemaCache);
				schemaCache.put(entry, schema);

				String parentInfo = entry.getParent() != null ?
						" → parent: " + entry.getParent().getField() : "";
				String requiredMark = entry.isRequired() ? " *" : "";

				System.out.println("  ✓ " + entry.getField() +
						" [" + entry.getType() + "]" + requiredMark + parentInfo);
			}
		}

		// 5. Собираем root schema из root элементов (ТОЛЬКО они становятся SchemaNode)
		System.out.println("\n--- Построение root schema '" + mainSchemaName + "' ---");

		Map<String, Schema> rootProperties = new LinkedHashMap<>();
		List<String> rootRequired = new ArrayList<>();

		List<MappingEntry> rootEntries = entries.stream()
				.filter(e -> e.getParent() == null)
				.sorted(Comparator.comparing(MappingEntry::getField))
				.collect(Collectors.toList());

		for (MappingEntry rootEntry : rootEntries) {
			Schema<?> schema = schemaCache.getOrDefault(rootEntry,
					buildEntrySchema(rootEntry, entries, schemaCache));

			// Для root элементов просто добавляем их schema инлайн
			rootProperties.put(rootEntry.getField(), schema);

			System.out.println("  ✓ " + rootEntry.getField() +
					(rootEntry.isRequired() ? " *" : ""));

			if (rootEntry.isRequired()) {
				rootRequired.add(rootEntry.getField());
			}
		}

		ObjectSchema rootSchema = new ObjectSchema();
		rootSchema.setTitle(mainSchemaName);
		rootSchema.setProperties(rootProperties);
		if (!rootRequired.isEmpty()) {
			rootSchema.setRequired(rootRequired);
		}

		// 6. Результат: только ONE root SchemaNode с main=true
		List<SchemaNode> result = new ArrayList<>();
		SchemaNode mainNode = new SchemaNode(true, rootSchema);
		result.add(mainNode);

		System.out.println("\n✅ Результат: " + result.size() + " SchemaNode(s)");
		System.out.println("  [MAIN] " + mainSchemaName);

		return result;
	}

	/** Старый метод buildSchema переименовываем во внутренний */
	private Schema<?> buildRootSchema(List<MappingEntry> entries, String schemaName) {
		ObjectSchema rootSchema = new ObjectSchema();
		rootSchema.setTitle(schemaName);

		// ===== ниженаписанный код – это твой текущий bottom-up buildSchema
		// просто перенесён внутрь buildRootSchema БЕЗ изменений =====

		// Шаг 1: Сортируем записи по глубине (от самых глубоких к корневым)
		List<MappingEntry> sortedByDepth = entries.stream()
				.sorted((a, b) -> Integer.compare(b.getDepthLevel(), a.getDepthLevel()))
				.collect(Collectors.toList());

		// Шаг 2: Группируем по уровню вложенности
		Map<Integer, List<MappingEntry>> entriesByLevel = new LinkedHashMap<>();
		for (MappingEntry entry : sortedByDepth) {
			int level = entry.getDepthLevel();
			entriesByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(entry);
		}

		System.out.println("\n=== Построение Schema (bottom-up) ===");
		System.out.println("Всего entries: " + entries.size());
		System.out.println("Всего уровней: " + entriesByLevel.size());

		for (Map.Entry<Integer, List<MappingEntry>> levelEntry : entriesByLevel.entrySet()) {
			System.out.println("  Уровень " + levelEntry.getKey() + ": " + levelEntry.getValue().size() + " записей");
		}

		// Шаг 3: Кэш для уже построенных Schema
		Map<MappingEntry, Schema<?>> schemaCache = new LinkedHashMap<>();

		// Шаг 4: Проходим по уровням от самых глубоких к корневым
		List<Integer> levelsSorted = sortedByLevel(entriesByLevel.keySet());

		for (int level : levelsSorted) {
			List<MappingEntry> levelEntries = entriesByLevel.get(level);

			System.out.println("\n--- Обработка уровня вложенности: " + level + " ---");

			for (MappingEntry entry : levelEntries) {
				Schema<?> schema = buildEntrySchema(entry, entries, schemaCache);
				schemaCache.put(entry, schema);

				String parentInfo = entry.getParent() != null ?
						" → parent: " + entry.getParent().getField() : "";
				String requiredMark = entry.isRequired() ? " *" : "";

				System.out.println("  ✓ " + entry.getField() +
						" [" + entry.getType() + "]" + requiredMark + parentInfo);
			}
		}

		// Шаг 5: Собираем корневую схему из root элементов
		Map<String, Schema> rootProperties = new LinkedHashMap<>();
		List<String> rootRequired = new ArrayList<>();

		List<MappingEntry> rootEntries = entries.stream()
				.filter(e -> e.getParent() == null)
				.sorted(Comparator.comparing(MappingEntry::getField))
				.collect(Collectors.toList());

		System.out.println("\n--- Сборка root schema (" + rootEntries.size() + " полей) ---");

		for (MappingEntry rootEntry : rootEntries) {
			Schema<?> schema = schemaCache.getOrDefault(rootEntry,
					buildEntrySchema(rootEntry, entries, schemaCache));
			rootProperties.put(rootEntry.getField(), schema);

			System.out.println("  ✓ " + rootEntry.getField() +
					(rootEntry.isRequired() ? " *" : ""));

			if (rootEntry.isRequired()) {
				rootRequired.add(rootEntry.getField());
			}
		}

		rootSchema.setProperties(rootProperties);
		if (!rootRequired.isEmpty()) {
			rootSchema.setRequired(rootRequired);
		}

		return rootSchema;
	}

	/**
	 * Сортирует ключи по убыванию для обхода от самых глубоких
	 */
	private List<Integer> sortedByLevel(Set<Integer> levels) {
		return levels.stream()
				.sorted(Collections.reverseOrder())
				.collect(Collectors.toList());
	}

	/**
	 * Строит Schema для одного entry (bottom-up подход)
	 */
	private Schema<?> buildEntrySchema(MappingEntry entry, List<MappingEntry> allEntries,
									   Map<MappingEntry, Schema<?>> schemaCache) {

		List<MappingEntry> children = findChildren(entry, allEntries);

		if (children.isEmpty()) {
			return buildSimpleFieldSchema(entry);
		}

		boolean isMapKey = entry.getParent() != null &&
				isMapType(entry.getParent().getType());

		if (isMapKey) {
			return buildMapValueObjectSchema(entry, children, allEntries, schemaCache);
		}

		if (isMapType(entry.getType())) {
			return buildMapSchemaBottomUp(entry, children, allEntries, schemaCache);
		}

		return buildObjectSchemaBottomUp(entry, children, allEntries, schemaCache);
	}

	/**
	 * Строит Schema для value объекта Map (когда entry это ключ Map)
	 */
	private Schema<?> buildMapValueObjectSchema(MappingEntry mapKey, List<MappingEntry> valueFields,
												List<MappingEntry> allEntries,
												Map<MappingEntry, Schema<?>> schemaCache) {

		System.out.println("      [MapKey→Value] " + mapKey.getField() +
				" (type: " + mapKey.getType() + ")");

		Map<String, Schema> valueProperties = new LinkedHashMap<>();
		List<String> valueRequired = new ArrayList<>();

		for (MappingEntry valueField : valueFields) {
			Schema<?> fieldSchema = schemaCache.getOrDefault(valueField,
					buildEntrySchema(valueField, allEntries, schemaCache));
			valueProperties.put(valueField.getField(), fieldSchema);

			if (valueField.isRequired()) {
				valueRequired.add(valueField.getField());
			}
		}

		ObjectSchema valueObject = new ObjectSchema();
		valueObject.setDescription("Value объект для Map ключа: " + mapKey.getField());
		valueObject.setProperties(valueProperties);
		if (!valueRequired.isEmpty()) {
			valueObject.setRequired(valueRequired);
		}

		return valueObject;
	}

	/**
	 * Строит Schema для Map (bottom-up)
	 */
	private Schema<?> buildMapSchemaBottomUp(MappingEntry mapEntry, List<MappingEntry> mapKeys,
											 List<MappingEntry> allEntries,
											 Map<MappingEntry, Schema<?>> schemaCache) {

		System.out.println("    [Map] " + mapEntry.getField() +
				" (ключей: " + mapKeys.size() + ", type: " + mapEntry.getType() + ")");

		ObjectSchema mapSchema = new ObjectSchema();
		mapSchema.setDescription(mapEntry.getDescription());
		mapSchema.setTitle(mapEntry.getField());

		if (mapKeys.isEmpty()) {
			mapSchema.setAdditionalProperties(new StringSchema());
			return mapSchema;
		}

		// Проверка 1: Все ли keys имеют одинаковый тип?
		String expectedKeyType = mapKeys.get(0).getType();
		boolean allKeyTypesMatch = mapKeys.stream()
				.allMatch(key -> key.getType().equalsIgnoreCase(expectedKeyType));

		if (!allKeyTypesMatch) {
			System.err.println("  ⚠️  WARNING: Map '" + mapEntry.getField() +
					"' имеет ключи разных типов!");
			System.err.println("     Ожидается: " + expectedKeyType +
					", но найдены: " + mapKeys.stream()
					.map(MappingEntry::getType)
					.distinct()
					.collect(Collectors.joining(", ")));
			System.err.println("     Формируем как Map<String, Object>");
			mapSchema.setAdditionalProperties(new ObjectSchema());
			return mapSchema;
		}

		// Проверка 2: Все ли keys имеют детей?
		boolean allKeysHaveChildren = mapKeys.stream()
				.allMatch(key -> !findChildren(key, allEntries).isEmpty());

		// Проверка 3: Все ли keys имеют одинаковую структуру детей?
		boolean allKeyChildrenMatch = true;
		if (allKeysHaveChildren && mapKeys.size() > 1) {
			List<MappingEntry> firstKeyChildren = findChildren(mapKeys.get(0), allEntries);
			Set<String> firstKeyFieldNames = firstKeyChildren.stream()
					.map(MappingEntry::getField)
					.collect(Collectors.toSet());

			for (int i = 1; i < mapKeys.size(); i++) {
				Set<String> keyFieldNames = findChildren(mapKeys.get(i), allEntries).stream()
						.map(MappingEntry::getField)
						.collect(Collectors.toSet());

				if (!keyFieldNames.equals(firstKeyFieldNames)) {
					allKeyChildrenMatch = false;
					System.err.println("  ⚠️  WARNING: Map '" + mapEntry.getField() +
							"' ключи имеют разные структуры!");
					System.err.println("     Ключ '" + mapKeys.get(0).getField() +
							"' имеет поля: " + firstKeyFieldNames);
					System.err.println("     Ключ '" + mapKeys.get(i).getField() +
							"' имеет поля: " + keyFieldNames);
					break;
				}
			}
		}

		if (allKeysHaveChildren && allKeyChildrenMatch) {
			// ✅ Формируем Map<KeyType, ValueObject>
			Map<String, Schema> mergedProperties = new LinkedHashMap<>();
			Set<String> mergedRequired = new LinkedHashSet<>();

			List<MappingEntry> referenceKeyChildren = findChildren(mapKeys.get(0), allEntries);

			for (MappingEntry childField : referenceKeyChildren) {
				Schema<?> childSchema = schemaCache.getOrDefault(childField,
						buildEntrySchema(childField, allEntries, schemaCache));

				mergedProperties.put(childField.getField(), childSchema);
				if (childField.isRequired()) {
					mergedRequired.add(childField.getField());
				}
			}

			ObjectSchema valueObject = new ObjectSchema();
			valueObject.setProperties(mergedProperties);
			if (!mergedRequired.isEmpty()) {
				valueObject.setRequired(new ArrayList<>(mergedRequired));
			}

			mapSchema.setAdditionalProperties(valueObject);

		} else if (allKeysHaveChildren) {
			System.err.println("  ⚠️  Формируем как Map<String, Object> из-за разных структур");
			mapSchema.setAdditionalProperties(new ObjectSchema());

		} else {
			Schema<?> valueSchema = mapTypeToSchema(expectedKeyType);
			mapSchema.setAdditionalProperties(valueSchema);
		}

		return mapSchema;
	}

	/**
	 * Строит Schema для Object (bottom-up)
	 */
	private Schema<?> buildObjectSchemaBottomUp(MappingEntry entry, List<MappingEntry> children,
												List<MappingEntry> allEntries,
												Map<MappingEntry, Schema<?>> schemaCache) {

		if (entry.getParent() != null && isMapType(entry.getParent().getType())) {
			System.err.println("  ⚠️  WARNING: Object '" + entry.getField() +
					"' это ключ Map, но обрабатывается как Object!");
		}

		System.out.println("    [Object] " + entry.getField() +
				" (свойств: " + children.size() + ")");

		ObjectSchema objectSchema = new ObjectSchema();
		objectSchema.setDescription(entry.getDescription());

		Map<String, Schema> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (MappingEntry child : children) {
			Schema<?> childSchema = schemaCache.getOrDefault(child,
					buildEntrySchema(child, allEntries, schemaCache));
			properties.put(child.getField(), childSchema);

			if (child.isRequired()) {
				required.add(child.getField());
			}
		}

		objectSchema.setProperties(properties);
		if (!required.isEmpty()) {
			objectSchema.setRequired(required);
		}

		return objectSchema;
	}

	/**
	 * Строит Schema для простого поля (без детей)
	 */
	private Schema<?> buildSimpleFieldSchema(MappingEntry entry) {
		Schema<?> schema = mapTypeToSchema(entry.getType());

		if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
			schema.setDescription(entry.getDescription());
		}

		return schema;
	}

	/**
	 * Проверяет, является ли тип Map
	 */
	private boolean isMapType(String typeString) {
		if (typeString == null || typeString.isEmpty()) {
			return false;
		}
		String type = typeString.toLowerCase().trim();
		return type.contains("map");
	}

	/**
	 * Находит всех children для entry
	 */
	private List<MappingEntry> findChildren(MappingEntry parent, List<MappingEntry> allEntries) {
		return allEntries.stream()
				.filter(e -> parent.equals(e.getParent()))
				.collect(Collectors.toList());
	}

	/**
	 * Маппит строковый тип из таблицы на Swagger Schema тип
	 */
	private Schema<?> mapTypeToSchema(String typeString) {
		if (typeString == null || typeString.isEmpty()) {
			return new StringSchema();
		}

		String type = typeString.toLowerCase().trim();

		if (type.contains("long")) {
			io.swagger.v3.oas.models.media.IntegerSchema intSchema =
					new io.swagger.v3.oas.models.media.IntegerSchema();
			intSchema.setFormat("int64");
			return intSchema;
		} else if (type.contains("integer") || type.contains("int")) {
			return new io.swagger.v3.oas.models.media.IntegerSchema();
		} else if (type.contains("boolean")) {
			return new io.swagger.v3.oas.models.media.BooleanSchema();
		} else if (type.contains("double") || type.contains("float")) {
			return new io.swagger.v3.oas.models.media.NumberSchema();
		} else if (type.contains("datetime")) {
			return new io.swagger.v3.oas.models.media.DateTimeSchema();
		} else if (type.contains("date")) {
			return new io.swagger.v3.oas.models.media.DateSchema();
		} else if (type.contains("list") || type.contains("array")) {
			ArraySchema arraySchema = new ArraySchema();
			arraySchema.setItems(new StringSchema());
			return arraySchema;
		} else if (type.contains("map")) {
			return new ObjectSchema();
		} else if (type.contains("object")) {
			return new ObjectSchema();
		} else {
			return new StringSchema();
		}
	}


	public List<SchemaNode> convert(XWPFDocument docxFilePath, String schemaName) throws IOException {
		System.out.println("=== Шаг 1: Парсирование DOCX файла ===");
		List<MappingEntry> entries = extractMappingRules(docxFilePath);

		if (entries.isEmpty()) {
			System.err.println("❌ Ошибка: Не найдено записей в таблице!");
			return null;
		}

		System.out.println("\n=== Шаг 2: Распарсено " + entries.size() + " записей ===");
		printEntriesTree(entries);

		System.out.println("\n=== Шаг 3: Построение OpenAPI Schema ===");
		List<SchemaNode> schema = buildSchemas(entries, schemaName);

		return schema;
	}

	private String schemaToCleanJson(Schema<?> schema) {
		try {
			return objectMapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString(schema);
		} catch (Exception e) {
			System.err.println("❌ Ошибка сериализации: " + e.getMessage());
			e.printStackTrace();
			return schema.toString();
		}
	}

	private void printEntriesTree(List<MappingEntry> entries) {
		for (MappingEntry entry : entries) {
			String indent = "  ".repeat(entry.getDepthLevel());
			String marker = entry.getParent() != null ? "├─ " : "• ";
			String required = entry.isRequired() ? " *" : "";
			String parent = entry.getParent() != null ? " (parent: " + entry.getParent().getField() + ")" : "";

			System.out.println(String.format(
					"%s%s%-25s [%-15s]%s%s",
					indent,
					marker,
					entry.getField(),
					entry.getType(),
					required,
					parent
			));
		}
	}

	private Schema getMainSchema(List<SchemaNode> schemas) {
		return schemas.stream().filter(e -> e.main).collect(Collectors.toList()).get(0).getSchema();
	}
}

// Вспомогательные классы
@lombok.Data
@NoArgsConstructor
@Getter
@Setter
@lombok.AllArgsConstructor
class RequestParameter {
	private String name;
	private String type;
	private String description;
	private boolean required;
	private String defaultValue;
}

@lombok.Data
@lombok.AllArgsConstructor
class ErrorCase {
	private int code;
	private String description;
	private String details;
}

@Data
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
class MappingEntry {
	private String field;
	private String type;
	private String description;
	private boolean required;
	private String mappingRule;
	private MappingEntry parent;

	public boolean isNested() {
		return parent != null;
	}

	public String getFullPath() {
		if (parent == null) {
			return field;
		}
		return parent.getFullPath() + "." + field;
	}

	public int getDepthLevel() {
		int level = 0;
		MappingEntry current = this;
		while (current.getParent() != null) {
			level++;
			current = current.getParent();
		}
		return level;
	}
}

@Data
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor// Не включать null поля в JSON
class SchemaNode {
	// Метаданные из таблицы
	public boolean main;
	public Schema schema;
}
