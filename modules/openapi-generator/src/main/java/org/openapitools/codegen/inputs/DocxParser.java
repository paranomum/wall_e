package org.openapitools.codegen.inputs;

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
				Schema<?> refSchema = new Schema<>().$ref("#/components/schemas/" + resultSchema.getTitle());
				MediaType mediaType = new MediaType().schema(refSchema);
				Content content = new Content().addMediaType("application/json", mediaType);
				successResponse.setContent(content);
			} catch (Exception e) {
				LOGGER.error("✗ Ошибка при установке схемы ответа: {}", e.getMessage(), e);
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
			entry.setMapKey(false);

			if (entry.getParent() != null) {
				if (entry.getParent().getType().contains("Map")) {
					String getKey = entry.getParent().getType().split(",")[0];
					getKey = getKey.substring(4);
					System.out.println(getKey);
					if (getKey.toLowerCase(Locale.ROOT).trim().equals(entry.getType().toLowerCase(Locale.ROOT).trim()))
						entry.setMapKey(true);
					else {
						LOGGER.warn("MAP DETECTED BUT WITH AN ERROR");
					}
				}
			}

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
	 * Строит список SchemaNode (bottom-up).
	 * main = true ТОЛЬКО для root элементов (parent == null).
	 * Вложенные Object/Map остаются ИНЛАЙН в root schema.
	 */
	public List<SchemaNode> buildSchemas(List<MappingEntry> entries, String mainSchemaName) {
		List<MappingEntry> sortedByDepth = entries.stream()
				.sorted((a, b) -> Integer.compare(b.getDepthLevel(), a.getDepthLevel()))
				.collect(Collectors.toList());

		Map<Integer, List<MappingEntry>> entriesByLevel = new LinkedHashMap<>();
		for (MappingEntry entry : sortedByDepth) {
			int level = entry.getDepthLevel();
			entriesByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(entry);
		}

		System.out.println("\n=== Построение SchemaNodes (с отдельными schemas) ===");
		System.out.println("Всего entries: " + entries.size());
		System.out.println("Всего уровней: " + entriesByLevel.size());

		for (Map.Entry<Integer, List<MappingEntry>> levelEntry : entriesByLevel.entrySet()) {
			System.out.println("  Уровень " + levelEntry.getKey() + ": " + levelEntry.getValue().size() + " записей");
		}

		Map<MappingEntry, Schema<?>> schemaCache = new LinkedHashMap<>();
		List<SchemaNode> allSchemaNodes = new ArrayList<>();

		List<Integer> levelsSorted = sortedByLevel(entriesByLevel.keySet());

		for (int level : levelsSorted) {
			List<MappingEntry> levelEntries = entriesByLevel.get(level);
			System.out.println("\n--- Обработка уровня: " + level + " ---");


			for (MappingEntry entry : levelEntries) {
				Schema<?> schema;

				if ("Object".equalsIgnoreCase(entry.getType()) || isMapType(entry.getType())) {
					schema = buildObjectOrMapSchema(entry, entries, schemaCache, entriesByLevel);
					String marker = isMapType(entry.getType()) ? "[Map]" : "[Object]";
					System.out.println("  ✓ " + marker + " " + entry.getField() +
							(entry.getParent() != null ? " → parent: " + entry.getParent().getField() : " (ROOT)"));

					if (level > 0 && entry.getParent() != null) {
						allSchemaNodes.add(new SchemaNode(false, schema));
						System.out.println("    → Добавлена как intermediate SchemaNode");
					}
				} else {
					schema = buildSimpleFieldSchema(entry);
					System.out.println("  ✓ [" + entry.getType() + "] " + entry.getField() +
							(entry.getParent() != null ? " → parent: " + entry.getParent().getField() : " (ROOT)"));
				}

				schemaCache.put(entry, schema);
			}
		}

		System.out.println("\n--- Сборка главной схемы (root элементы с $ref) ---");

		Map<String, Schema> rootProperties = new LinkedHashMap<>();
		List<String> rootRequired = new ArrayList<>();

		List<MappingEntry> rootEntries = entries.stream()
				.filter(e -> e.getParent() == null && e.getDepthLevel() == 0)
				.sorted(Comparator.comparing(MappingEntry::getField))
				.collect(Collectors.toList());

		System.out.println("Найдено root элементов: " + rootEntries.size());

		for (MappingEntry rootEntry : rootEntries) {
			Schema<?> schema = schemaCache.get(rootEntry);
			if (schema == null) {
				schema = buildSimpleFieldSchema(rootEntry);
			} else if ("Object".equalsIgnoreCase(rootEntry.getType()) || isMapType(rootEntry.getType())) {
				String refName = rootEntry.getField();
				schema = new Schema<>().$ref("#/components/schemas/" + refName);
				System.out.println("    → Используется $ref на: " + refName);
			}

			rootProperties.put(rootEntry.getField(), schema);
			System.out.println("  ✓ " + rootEntry.getField() + " [" + rootEntry.getType() + "]" +
					(rootEntry.isRequired() ? " *" : ""));

			if (rootEntry.isRequired()) {
				rootRequired.add(rootEntry.getField());
			}
		}

		ObjectSchema mainSchema = new ObjectSchema();
		mainSchema.setTitle(mainSchemaName);
		mainSchema.setProperties(rootProperties);
		if (!rootRequired.isEmpty()) {
			mainSchema.setRequired(rootRequired);
		}

		List<SchemaNode> result = new ArrayList<>();
		result.add(new SchemaNode(true, mainSchema));

		for (Map.Entry<MappingEntry, Schema<?>> cacheEntry : schemaCache.entrySet()) {
			Schema<?> schema = cacheEntry.getValue();
			// Если это Object или Map (не simple field)
			if ("Object".equalsIgnoreCase(cacheEntry.getKey().getType())
					|| isMapType(cacheEntry.getKey().getType())) {
				result.add(new SchemaNode(false, schema));
				System.out.println("  [Intermediate] " + schema.getTitle());
			}
		}

		System.out.println("\n✅ Результат: " + result.size() + " SchemaNode(s)");
		System.out.println("  [MAIN] " + mainSchemaName);

		return result;
	}

	/**
	 * Строит Schema для Object или Map, группируя его children.
	 */
	private Schema<?> buildObjectOrMapSchema(MappingEntry entry,
											 List<MappingEntry> allEntries,
											 Map<MappingEntry, Schema<?>> schemaCache,
											 Map<Integer, List<MappingEntry>> entriesByLevel) {

		List<MappingEntry> children = findChildren(entry, allEntries);

		if (isMapType(entry.getType())) {
			return buildMapSchema(entry, children, allEntries, schemaCache);
		}

		ObjectSchema objectSchema = new ObjectSchema();
		objectSchema.setTitle(entry.getField());
		objectSchema.setDescription(entry.getDescription());

		Map<String, Schema> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (MappingEntry child : children) {
			Schema<?> childSchema = schemaCache.get(child);

			if (childSchema == null) {
				if (("Object".equalsIgnoreCase(child.getType()) || isMapType(child.getType()))
						&& !findChildren(child, allEntries).isEmpty()) {
					childSchema = new Schema<>().$ref("#/components/schemas/" + child.getField());
				} else {
					childSchema = buildSimpleFieldSchema(child);
				}
			}

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


	private Schema<?> buildMapSchema(MappingEntry mapEntry,
									 List<MappingEntry> mapKeys,
									 List<MappingEntry> allEntries,
									 Map<MappingEntry, Schema<?>> schemaCache) {

		ObjectSchema mapSchema = new ObjectSchema();
		mapSchema.setTitle(mapEntry.getField());
		mapSchema.setDescription(mapEntry.getDescription());

		if (mapKeys.isEmpty()) {
			mapSchema.setAdditionalProperties(new StringSchema());
			return mapSchema;
		}

		// Проверка 1: Все ли keys имеют одинаковый тип?
		String expectedKeyType = mapKeys.get(0).getType();
		boolean allKeyTypesMatch = mapKeys.stream()
				.allMatch(key -> key.getType().equalsIgnoreCase(expectedKeyType));

		if (!allKeyTypesMatch) {
			LOGGER.warn("⚠️  Map '{}' имеет ключи разных типов! Ожидается: {}, но найдены: {}",
					mapEntry.getField(),
					expectedKeyType,
					mapKeys.stream()
							.map(MappingEntry::getType)
							.distinct()
							.collect(Collectors.joining(", ")));

			mapSchema.setAdditionalProperties(new ObjectSchema());
			return mapSchema;
		}

		// Проверка 2: Все ли keys имеют детей и одинаковую структуру?
		boolean allKeysHaveChildren = mapKeys.stream()
				.allMatch(key -> !findChildren(key, allEntries).isEmpty());

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
					LOGGER.warn("⚠️  Map '{}' ключи имеют разные структуры! " +
									"Ключ '{}' имеет поля: {}, Ключ '{}' имеет поля: {}",
							mapEntry.getField(),
							mapKeys.get(0).getField(), firstKeyFieldNames,
							mapKeys.get(i).getField(), keyFieldNames);
					break;
				}
			}
		}

		// Формируем Value Schema
		if (allKeysHaveChildren && allKeyChildrenMatch) {
			// ⭐ КЛЮЧЕВОЕ: если Map имеет именованные дети с общей структурой,
			// возвращаем $ref на первого ребенка (например, "properties")
			// Это создает компонент в components.schemas с этим именем
			Schema<?> valueRefSchema = new Schema<>();
			valueRefSchema.$ref("#/components/schemas/" + mapKeys.get(0).getField());
			mapSchema.setAdditionalProperties(valueRefSchema);

			LOGGER.debug("✓ Map '{}' → additionalProperties: $ref to '{}'",
					mapEntry.getField(), mapKeys.get(0).getField());
		} else if (allKeysHaveChildren) {
			LOGGER.warn("⚠️  Map '{}' ключи имеют разные структуры - формируем как Map<String, Object>",
					mapEntry.getField());
			mapSchema.setAdditionalProperties(new ObjectSchema());
		} else {
			Schema<?> valueSchema = mapTypeToSchema(expectedKeyType);
			mapSchema.setAdditionalProperties(valueSchema);
		}

		return mapSchema;
	}


	/**
	 * Строит Schema для простого поля (String, Long, Integer и т.д.).
	 */
	private Schema<?> buildSimpleFieldSchema(MappingEntry entry) {
		Schema<?> schema = mapTypeToSchema(entry.getType());

		if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
			schema.setDescription(entry.getDescription());
		}

		return schema;
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
	private boolean isMapKey;

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
