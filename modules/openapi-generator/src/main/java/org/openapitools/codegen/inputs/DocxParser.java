package org.openapitools.codegen.inputs;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.poi.xwpf.usermodel.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

			Map<String, Schema> schemas = convertJsonToOpenApiComponents(responseExampleJson);
			if (openAPI.getComponents() == null) {
				openAPI.setComponents(new Components());
			}
			schemas.forEach((componentName, schema) -> {
				openAPI.getComponents().addSchemas(componentName, schema);
			});

			Schema resultSchema = generateResultSchema(responseExampleJson);

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
