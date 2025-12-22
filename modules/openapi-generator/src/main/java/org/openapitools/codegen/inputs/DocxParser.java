package org.openapitools.codegen.inputs;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
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

import static org.openapitools.codegen.utils.StringUtils.camelize;

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
			List<RequestParameter> requestPathParams = extractRequestPathParameters(document);
			List<SchemaNode> requestBodySchemas = convert(document, "RequestDto", "Формат тела запроса");
			Schema requestBody = getMainSchema(requestBodySchemas);
			List<SchemaNode> schemaNodes = convert(document, "ResponseDto", "Мапинг");

			List<ErrorCase> errorCases = extractErrorCases(document);

			// Обновляем информацию
			openAPI.getInfo().setTitle(title.isEmpty() ? "Generated API" : title);

			// Создаем PathItem
			PathItem pathItem = new PathItem();
			Operation operation = new Operation();
			operation.setDescription(title);
			operation.setOperationId(generateOperationId(method, path));

			// Добавляем параметры
			List<Parameter> parameters = new ArrayList<>();
			for (RequestParameter param : requestPathParams) {
				if (path.contains("{" + param.getName() + "}")) {
					parameters.add(createPathParameter(param));
				} else {
					parameters.add(createQueryParameter(param));
				}
			}
			if (requestBody != null) {
				RequestBody requestBodyParam = new RequestBody()
						.description("Описание тела запроса")
						.required(true)
						.content(new Content()
								.addMediaType("application/json", new MediaType()
										.schema(new Schema<>()
												.$ref("#/components/schemas/" + camelize(requestBody.getTitle()) + "Dto")
										)
								)
						);
				operation.setRequestBody(requestBodyParam);
			}
			operation.setParameters(parameters);

			Components components = new Components();

			// 3. Добавляем все схемы в components.schemas
			for (SchemaNode node : schemaNodes) {
				Schema<?> schema = node.getSchema();
				String name = schema.getTitle();
				components.addSchemas(name, schema);
			}

			for (SchemaNode node : requestBodySchemas) {
				Schema<?> schema = node.getSchema();
				String name = schema.getTitle();
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

		// Поиск паттерна "HTTP_METHOD .../path"
		Pattern pattern = Pattern.compile("(GET|POST|PUT|DELETE|PATCH)\\s+(\\.\\.\\.)?(/[\\w\\-/{}/]+)");
		Matcher matcher = pattern.matcher(fullText);

		if (matcher.find()) {
			String method = matcher.group(1);
			String dots = matcher.group(2) != null ? matcher.group(2) : "";
			String path = matcher.group(3);
			return method + " " + dots + path;
		}

		return "GET .../NOTHING";
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

		return "/NOTHING";
	}

	/**
	 * Извлекает параметры запроса из таблицы
	 */
	private List<RequestParameter> extractRequestPathParameters(XWPFDocument document) {
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
				Schema<?> refSchema = new Schema<>().$ref("#/components/schemas/" + camelize(resultSchema.getTitle()) + "Dto");
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
			case "bool":
				return "boolean";
			case "double":
			case "float":
				return "number";
			case "date":
			case "uuid":
			case "date-time":
			case "byte":
			case "binary":
				return "string";
			default:
				return "string";
		}
	}

	private String mapFormat(String docxType) {
		switch (docxType.toLowerCase(Locale.ROOT)) {
			case "long":
				return "int64";
			case "int":
			case "integer":
				return "int32";
			case "float":
				return "float";
			case "double":
				return "double";
			case "date":
				return "date";
			case "datetime":
			case "timestamp":
				return "date-time";
			case "byte":
				return "byte";
			case "binary":
				return "binary";
			case "uuid":
				return "uuid";
			case "email":
				return "email";
			case "url":
			case "uri":
				return "uri";
			case "decimal":
			case "bigdecimal":
				return "decimal";
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
	 * @param table DOCX документ
	 * @return список MappingEntry с иерархией
	 */
	public List<MappingEntry> extractMappingRules(XWPFTable table) {
		List<MappingEntry> mappings = new ArrayList<>();

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
						if (entry.isMapKey()) {
							String rawField = cells.get(0).getText();
							int nestLevel = calculateNestLevel(rawField);
							int indexToChange = mappings.indexOf(determineParent(nestLevel, mappings));
							MappingEntry mapToChange = mappings.get(indexToChange);
							mapToChange.setMapKeyAlias(entry.getName());
							mappings.remove(indexToChange);
							mappings.add(indexToChange, mapToChange);
						}
						if (entry != null) {
							mappings.add(entry);
						}
					}
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
			entry.setName(cleanFieldName);
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
			entry.setMap(false);
			entry.setMapKeyAlias(null);

			if (entry.getType().contains("Map")){
				entry.setMap(true);
			}
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
		String cleanedSecond = cleaned.replaceAll("[^a-zA-Z0-9_.]", "").replaceAll("\\[N]", "");

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
	 * Строит схему Object.
	 * Встраивает свойства inline (без создания отдельных компонентов для вложенных Object и Map).
	 * КЛЮЧЕВОЕ: Map-поля встраиваются inline, а не создают $ref
	 */
	private Schema<?> buildObjectSchema(MappingEntry entry,
										List<MappingEntry> allEntries,
										Map<MappingEntry, Schema<?>> schemaCache,
										Map<Integer, List<MappingEntry>> entriesByLevel) {

		ObjectSchema objectSchema = new ObjectSchema();
		objectSchema.setTitle(camelize(entry.getName()) + "Dto");
		objectSchema.setDescription(entry.getDescription());

		List<MappingEntry> children = findChildren(entry, allEntries);
		Map<String, Schema> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (MappingEntry child : children) {
			Schema<?> childSchema = null;

			if (isMapType(child.getType())) {
				// Это Map-поле: строим его INLINE (встраиваем сразу, не делаем отдельный компонент)
				List<MappingEntry> mapKeys = findChildren(child, allEntries);
				childSchema = buildMapSchema(child, mapKeys, allEntries);
				// ✅ ВСТРАИВАЕМ INLINE, а не делаем $ref

			} else if ("Object".equalsIgnoreCase(child.getType())) {
				// Это Object-поле внутри Object: встраиваем inline
				childSchema = buildObjectSchema(child, allEntries, schemaCache, entriesByLevel);

			} else {
				// Простой тип
				childSchema = schemaCache.get(child);
				if (childSchema == null) {
					childSchema = buildSimpleFieldSchema(child);
				}
			}

			if (childSchema != null) {
				properties.put(child.getName(), childSchema);
				if (child.isRequired()) {
					required.add(child.getName());
				}
			}
		}

		objectSchema.setProperties(properties);
		if (!required.isEmpty()) {
			objectSchema.setRequired(required);
		}

		return objectSchema;
	}

	/**
	 * Исправленный метод построения схемы для Map.
	 * Использует $ref на схему значения, которая определена в components.
	 */
	private Schema<?> buildMapSchema(MappingEntry mapEntry,
									 List<MappingEntry> mapKeys,
									 List<MappingEntry> allEntries) {

		MapSchema mapSchema = new MapSchema();
		mapSchema.setTitle(camelize(mapEntry.getName()) + "Dto");
		mapSchema.setDescription(mapEntry.getDescription());

		if (mapKeys.isEmpty()) {
			mapSchema.setAdditionalProperties(new ObjectSchema());
			return mapSchema;
		}

		// Берем первый ключ как образец структуры значения
		MappingEntry keyEntry = mapKeys.get(0);
		List<MappingEntry> valueFields = findChildren(keyEntry, allEntries);

		if (valueFields.isEmpty()) {
			// Если структура значения простая (нет вложенных полей)
			mapSchema.setAdditionalProperties(mapTypeToSchema(keyEntry.getType()));
		} else {
			// Если структура сложная, мы ожидаем, что для keyEntry уже создана SchemaNode
			// и она будет добавлена в components.
			// Поэтому здесь мы создаем $ref на неё.

			String refName = keyEntry.getName(); // Имя компонента = имя поля ключа (например, "alias")

			// Создаем схему-ссылку
			Schema<?> refSchema = new Schema<>().$ref("#/components/schemas/" + camelize(refName) + "Dto");

			mapSchema.setAdditionalProperties(refSchema);
			LOGGER.debug("✓ Map '{}' → additionalProperties: $ref to '{}'", mapEntry.getName(), refName);
		}

		return mapSchema;
	}

	/**
	 * Исправленный метод buildSchemas.
	 * Ключевое отличие: правильная логика создания компонентов:
	 * - MapKey (alias) → ВСЕГДА компонент
	 * - Map (если root или parent MapKey) → компонент
	 * - Map (если child Object) → встраивается inline в Object
	 * - Object (root) → компонент
	 * - Object (внутри Object) → встраивается inline
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

		Map<MappingEntry, Schema<?>> schemaCache = new LinkedHashMap<>();
		List<SchemaNode> allSchemaNodes = new ArrayList<>();

		// Сортировка уровней от глубокого к поверхностному
		List<Integer> levelsSorted = new ArrayList<>(entriesByLevel.keySet());
		levelsSorted.sort(Collections.reverseOrder());

		List<Map<MappingEntry, Schema<?>>> components = new ArrayList<>();

		for (int level : levelsSorted) {
			List<MappingEntry> levelEntries = entriesByLevel.get(level);
			Map<MappingEntry, Schema<?>> listSchemas = new HashMap<>();
			for (MappingEntry entry : levelEntries) {
				Schema<?> schema;

				if (isCollection(entry.getType())) {
					// Map обрабатывается отдельно
					List<MappingEntry> keys = findChildren(entry, entries);
					schema = buildMapSchema(entry, keys, entries);

					// Map становится компонентом ТОЛЬКО если:
					// 1. Это root level (parent == null) - НЕ добавляем здесь
					// 2. Его parent является MapKey (например, "alias")
					// НО НЕ добавляем в компоненты, если это child обычного Object

					MappingEntry parent = entry.getParent();
					boolean parentIsMapKey = parent != null && parent.isMapKey();

					if(entry.isMap()) {
						if (entry.getType().contains("Object")) {
							schema = buildMapStringObject(entry);
						}
					}
					else if(entry.getType().toLowerCase(Locale.ROOT).trim().contains("list")) {
						if (entry.getType().contains("Object")) {
							schema = buildListObject(entry);
						}
					}
					if (parentIsMapKey && level > 0 && !entry.isMapKey()) {
						allSchemaNodes.add(new SchemaNode(false, schema));
					}
				} else if ("Object".equalsIgnoreCase(entry.getType())) {
					if (level == levelsSorted.get(0)) {
						schema = buildSimpleFieldSchema(entry);
					} else {
						// Object: может встраиваться inline или быть компонентом
						schema = buildObjectSchema(entry, entries, schemaCache, entriesByLevel);

						// Object становится компонентом если:
						// 1. Это MapKey (alias) - ВСЕГДА
						// 2. Это Root element (parent == null) - добавляем после
						// 3. Его parent является MapKey

						if (entry.isMapKey()) {
							{
								// MapKey ВСЕГДА компонент
//								allSchemaNodes.add(new SchemaNode(false, schema));
								entry.setType("#/components/schemas/" + camelize(schema.getTitle()) + "Dto");
							}
						} else if (entry.getParent() != null && entry.getParent().isMapKey()) {
							// Child MapKey - добавляем как компонент
//							allSchemaNodes.add(new SchemaNode(false, schema));
							entry.setType("#/components/schemas/" + camelize(schema.getTitle()) + "Dto");
						}
					}
				} else {
					// Простой тип
					schema = buildSimpleFieldSchema(entry);
				}
				listSchemas.put(entry, schema);

				schemaCache.put(entry, schema);
			}
			if (levelEntries.get(0).getParent() != null && !levelEntries.get(0).isMapKey())
				components.add(listSchemas);

		}

		List<SchemaNode> result = new ArrayList<>();

		for (Map<MappingEntry, Schema<?>> schemas : components) {
			ObjectSchema mainSchema = new ObjectSchema();
			Map<String, Schema> rootProperties = new LinkedHashMap<>();
			List<String> rootRequired = new ArrayList<>();
			for (MappingEntry map : schemas.keySet()) {
				if (mainSchema.getTitle() == null) {
					mainSchema.setTitle(camelize(map.getParent().getName()) + "Dto");
				}
				Schema<?> schema = schemas.get(map);
				if (schema == null)
					schema = buildSimpleFieldSchema(map);

				// Для корневых элементов используем $ref если это сложные типы
				if (map.getType().contains("#/components/schemas/")) {
					schema.$ref("#/components/schemas/" + camelize(map.getName()) + "Dto");
				} else if (map.getType().toLowerCase(Locale.ROOT).contains("object")) {
					if (map.getType().toLowerCase(Locale.ROOT).trim().contains("list")) {
						Schema items = new Schema<>().$ref("#/components/schemas/" + camelize(map.getName()) + "Dto");
						schema.setItems(items);
					} else if (map.getType().toLowerCase(Locale.ROOT).trim().contains("map")) {
						schema = buildMapStringObject(map);
					} else {
						schema = new Schema<>().$ref("#/components/schemas/" + camelize(map.getName()) + "Dto");
						Schema<?> rootSchema = schemaCache.get(map);
						if (rootSchema != null) {
							allSchemaNodes.add(new SchemaNode(false, rootSchema));
						}
					}
				}

				rootProperties.put(map.getName(), schema);
				if (map.isRequired()) rootRequired.add(map.getName());
			}

			mainSchema.setProperties(rootProperties);
			if (!rootRequired.isEmpty()) mainSchema.setRequired(rootRequired);
			result.add(new SchemaNode(false, mainSchema));
		}


		// Сборка главной схемы (Root)
		ObjectSchema mainSchema = new ObjectSchema();
		mainSchema.setTitle(mainSchemaName);
		Map<String, Schema> rootProperties = new LinkedHashMap<>();
		List<String> rootRequired = new ArrayList<>();

		List<MappingEntry> rootEntries = entries.stream()
				.filter(e -> e.getParent() == null && e.getDepthLevel() == 0)
				.sorted(Comparator.comparing(MappingEntry::getName))
				.collect(Collectors.toList());

		for (MappingEntry rootEntry : rootEntries) {
			Schema<?> schema = schemaCache.get(rootEntry);
			if (schema == null) schema = buildSimpleFieldSchema(rootEntry);
			if (rootEntry.getType().toLowerCase(Locale.ROOT).contains("object")) {
				if (rootEntry.getType().toLowerCase(Locale.ROOT).trim().contains("list")) {
					Schema items = new Schema<>().$ref("#/components/schemas/" + camelize(rootEntry.getName()) + "Dto");
					schema.setItems(items);
				} else if (rootEntry.getType().toLowerCase(Locale.ROOT).trim().contains("map")) {
					schema = buildMapStringObject(rootEntry);
				} else {
					schema = new Schema<>().$ref("#/components/schemas/" + camelize(rootEntry.getName()) + "Dto");
					Schema<?> rootSchema = schemaCache.get(rootEntry);
					if (rootSchema != null) {
						allSchemaNodes.add(new SchemaNode(false, rootSchema));
					}
				}
			}

			rootProperties.put(rootEntry.getName(), schema);
			if (rootEntry.isRequired()) rootRequired.add(rootEntry.getName());
		}

		if (mainSchemaName.toLowerCase(Locale.ROOT).contains("response")) {
			rootProperties.put("errorCode", buildSimpleFieldSchema("integer")); //integer
			rootProperties.put("errorMsg", buildSimpleFieldSchema("string")); //string
			rootProperties.put("msg", buildSimpleFieldSchema("string")); //sting
		}

		mainSchema.setProperties(rootProperties);
		if (!rootRequired.isEmpty()) mainSchema.setRequired(rootRequired);

		result.add(new SchemaNode(true, mainSchema));

		return result;
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

	private Schema<?> buildSimpleFieldSchema(String type) {
		Schema<?> schema = mapTypeToSchema(type);
		return schema;
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
	private Schema<?> buildMapValueObjectSchema(MappingEntry map, List<MappingEntry> valueFields,
												List<MappingEntry> allEntries,
												Map<MappingEntry, Schema<?>> schemaCache) {

		Schema schema = new Schema();
		Map<String, Schema> valueProperties = new LinkedHashMap<>();
		List<String> valueRequired = new ArrayList<>();

		if (isMapType(map.getType())) {
			schema.type("object");
			schema.additionalProperties(new Schema().$ref("#/components/schemas/" + camelize(map.getMapKeyAlias()) + "Dto"));
		}

		System.out.println("      [MapKey→Value] " + map.getName() +
				" (type: " + map.getType() + ")");


		for (MappingEntry valueField : valueFields) {
			Schema<?> fieldSchema = schemaCache.getOrDefault(valueField,
					buildEntrySchema(valueField, allEntries, schemaCache));
			valueProperties.put(valueField.getName(), fieldSchema);

			if (valueField.isRequired()) {
				valueRequired.add(valueField.getName());
			}
		}

		ObjectSchema valueObject = new ObjectSchema();
		valueObject.setDescription("Value объект для Map ключа: " + map.getName());
		valueObject.setProperties(valueProperties);
		if (!valueRequired.isEmpty()) {
			valueObject.setRequired(valueRequired);
		}

		return valueObject;
	}

	private Schema buildMapStringObject(MappingEntry map) {
		Schema schema = new Schema();

		if (isMapType(map.getType())) {
			schema.type("object");
			schema.additionalProperties(new Schema().$ref("#/components/schemas/" + camelize(map.getMapKeyAlias()) + "Dto"));
		}

		return schema;
	}

	private Schema buildListObject(MappingEntry map) {
		Schema schema = new Schema();

		if (isMapType(map.getType())) {
			schema.type("array");
			schema.items(new Schema().$ref("#/components/schemas/" + camelize(map.getName()) + "Dto"));
		}

		return schema;
	}

	/**
	 * Строит Schema для Map (bottom-up)
	 */
	private Schema<?> buildMapSchemaBottomUp(MappingEntry mapEntry, List<MappingEntry> mapKeys,
											 List<MappingEntry> allEntries,
											 Map<MappingEntry, Schema<?>> schemaCache) {

		System.out.println("    [Map] " + mapEntry.getName() +
				" (ключей: " + mapKeys.size() + ", type: " + mapEntry.getType() + ")");

		ObjectSchema mapSchema = new ObjectSchema();
		mapSchema.setDescription(mapEntry.getDescription());
		mapSchema.setTitle(camelize(mapEntry.getName()) + "Dto");

		if (mapKeys.isEmpty()) {
			mapSchema.setAdditionalProperties(new StringSchema());
			return mapSchema;
		}

		// Проверка 1: Все ли keys имеют одинаковый тип?
		String expectedKeyType = mapKeys.get(0).getType();
		boolean allKeyTypesMatch = mapKeys.stream()
				.allMatch(key -> key.getType().equalsIgnoreCase(expectedKeyType));

		if (!allKeyTypesMatch) {
			System.err.println("  ⚠️  WARNING: Map '" + mapEntry.getName() +
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
					.map(MappingEntry::getName)
					.collect(Collectors.toSet());

			for (int i = 1; i < mapKeys.size(); i++) {
				Set<String> keyFieldNames = findChildren(mapKeys.get(i), allEntries).stream()
						.map(MappingEntry::getName)
						.collect(Collectors.toSet());

				if (!keyFieldNames.equals(firstKeyFieldNames)) {
					allKeyChildrenMatch = false;
					System.err.println("  ⚠️  WARNING: Map '" + mapEntry.getName() +
							"' ключи имеют разные структуры!");
					System.err.println("     Ключ '" + mapKeys.get(0).getName() +
							"' имеет поля: " + firstKeyFieldNames);
					System.err.println("     Ключ '" + mapKeys.get(i).getName() +
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

				mergedProperties.put(childField.getName(), childSchema);
				if (childField.isRequired()) {
					mergedRequired.add(childField.getName());
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
			System.err.println("  ⚠️  WARNING: Object '" + entry.getName() +
					"' это ключ Map, но обрабатывается как Object!");
		}

		System.out.println("    [Object] " + entry.getName() +
				" (свойств: " + children.size() + ")");

		ObjectSchema objectSchema = new ObjectSchema();
		objectSchema.setDescription(entry.getDescription());

		Map<String, Schema> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (MappingEntry child : children) {
			Schema<?> childSchema = schemaCache.getOrDefault(child,
					buildEntrySchema(child, allEntries, schemaCache));
			properties.put(child.getName(), childSchema);

			if (child.isRequired()) {
				required.add(child.getName());
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
		String type = typeString.toLowerCase(Locale.ROOT).trim();
		return type.contains("map");
	}

	private boolean isCollection(String typeString) {
		if (typeString == null || typeString.isEmpty()) {
			return false;
		}
		String type = typeString.toLowerCase(Locale.ROOT).trim();
		return type.contains("map") || type.contains("list") || type.contains("set");
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

		String type = typeString.toLowerCase(Locale.ROOT).trim();

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


	public List<SchemaNode> convert(XWPFDocument docxFilePath, String schemaName, String paragraphName) throws IOException {
		List<IBodyElement> bodyElements = docxFilePath.getBodyElements();
		XWPFTable table = null;
		boolean inRequestSection = false;
		int j = 0;
		for (int i = 0; i < bodyElements.size(); i++) {
			IBodyElement element = bodyElements.get(i);

			if (!inRequestSection && element.getElementType() == BodyElementType.PARAGRAPH) {
				XWPFParagraph p = (XWPFParagraph) element;
				String text = p.getText();
				if (text != null && text.contains(paragraphName)) {
					j++;
					if (j == 2)
						inRequestSection = true;
				}
				continue;
			}

			if (inRequestSection && element.getElementType() == BodyElementType.TABLE) {
				table = (XWPFTable) element;  // ← Просто кастим в XWPFTable
				break;  // ← Выходим, так как нашли нужную таблицу
			}
		}

		if (table != null) {

			List<MappingEntry> entries = extractMappingRules(table);

			if (entries.isEmpty()) {
				System.err.println("❌ Ошибка: Не найдено записей в таблице!");
				return new ArrayList();
			}
			List<SchemaNode> schema = buildSchemas(entries, schemaName);
			return schema;
		}

		return new ArrayList();
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

	private Schema getMainSchema(List<SchemaNode> schemas) {
			SchemaNode schemeNode = schemas.stream().filter(SchemaNode::isMain)
				.findFirst()
				.orElse(null);
			if (schemeNode != null) {
				return schemeNode.getSchema();
			}
			return null;
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
	private String name;
	private String type;
	private String description;
	private boolean required;
	private String mappingRule;
	private MappingEntry parent;
	private boolean isMapKey;
	private boolean isMap;
	private String mapKeyAlias;

	public boolean isNested() {
		return parent != null;
	}

	public String getFullPath() {
		if (parent == null) {
			return name;
		}
		return parent.getFullPath() + "." + name;
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
