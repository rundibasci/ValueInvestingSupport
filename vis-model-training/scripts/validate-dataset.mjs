import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const inputSchema = JSON.parse(await readFile(join(root, "schemas/thesis-input.schema.json"), "utf8"));
const outputSchema = JSON.parse(await readFile(join(root, "schemas/thesis-output.schema.json"), "utf8"));
const expectedScenarios = [
  ["example-001.json", "VIS-TRAIN-0001", "VIS1", "UNDERVALUED_STRONG_BUSINESS", "POTENTIALLY_UNDERVALUED", false],
  ["example-002.json", "VIS-TRAIN-0002", "VIS2", "VALUE_TRAP", "UNDER_REVIEW", true],
  ["example-003.json", "VIS-TRAIN-0003", "VIS3", "INSUFFICIENT_DATA", "INSUFFICIENT_DATA", true],
  ["example-004.json", "VIS-TRAIN-0004", "VIS4", "OVERVALUED_STRONG_BUSINESS", "POTENTIALLY_OVERVALUED", false],
  ["example-005.json", "VIS-TRAIN-0005", "VIS5", "FAIR_VALUE", "FAIRLY_VALUED", false],
  ["example-006.json", "VIS-TRAIN-0006", "VIS6", "DIVIDEND_RISK", "UNDER_REVIEW", true],
  ["example-007.json", "VIS-TRAIN-0007", "VIS7", "HIGH_LEVERAGE", "UNDER_REVIEW", true],
  ["example-008.json", "VIS-TRAIN-0008", "VIS8", "FCF_DETERIORATION", "UNDER_REVIEW", true],
  ["example-009.json", "VIS-TRAIN-0009", "VIS9", "CONTRADICTORY_SIGNALS", "UNDER_REVIEW", true],
  ["example-010.json", "VIS-TRAIN-0010", "VIS10", "STALE_DATA", "UNDER_REVIEW", true]
];

const isObject = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const typeMatches = (value, expected) => {
  if (expected === "null") return value === null;
  if (expected === "object") return isObject(value);
  if (expected === "array") return Array.isArray(value);
  if (expected === "number") return typeof value === "number" && Number.isFinite(value);
  return typeof value === expected;
};

function validateSchema(value, schema, path = "$") {
  if (schema.$ref) {
    assert(schema.$ref.startsWith("#/$defs/"), `${path}: unsupported ref ${schema.$ref}`);
    const definition = schema.$ref.slice("#/$defs/".length);
    const document = schema === inputSchema || inputSchema.$defs?.[definition] ? inputSchema : outputSchema;
    return validateSchema(value, document.$defs[definition], path);
  }

  if (schema.type) {
    const types = Array.isArray(schema.type) ? schema.type : [schema.type];
    assert(types.some((type) => typeMatches(value, type)), `${path}: expected ${types.join("|")}`);
  }
  if (schema.enum) assert(schema.enum.includes(value), `${path}: value is not in enum`);
  if (value === null) return;

  if (typeof value === "number") {
    if (schema.minimum !== undefined) assert(value >= schema.minimum, `${path}: below minimum`);
    if (schema.maximum !== undefined) assert(value <= schema.maximum, `${path}: above maximum`);
    if (schema.exclusiveMinimum !== undefined) assert(value > schema.exclusiveMinimum, `${path}: below exclusive minimum`);
  }
  if (typeof value === "string") {
    if (schema.minLength !== undefined) assert(value.length >= schema.minLength, `${path}: too short`);
    if (schema.maxLength !== undefined) assert(value.length <= schema.maxLength, `${path}: too long`);
    if (schema.format === "date") {
      assert(/^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)), `${path}: invalid date`);
    }
  }
  if (Array.isArray(value)) {
    if (schema.minItems !== undefined) assert(value.length >= schema.minItems, `${path}: too few items`);
    if (schema.uniqueItems) assert(new Set(value.map(JSON.stringify)).size === value.length, `${path}: duplicate items`);
    if (schema.items) value.forEach((item, index) => validateSchema(item, schema.items, `${path}[${index}]`));
  }
  if (isObject(value)) {
    for (const required of schema.required ?? []) {
      assert(Object.hasOwn(value, required), `${path}: missing required property ${required}`);
    }
    if (schema.additionalProperties === false) {
      for (const key of Object.keys(value)) {
        assert(Object.hasOwn(schema.properties ?? {}, key), `${path}: unexpected property ${key}`);
      }
    }
    for (const [key, child] of Object.entries(value)) {
      if (schema.properties?.[key]) validateSchema(child, schema.properties[key], `${path}.${key}`);
    }
  }
}

function parseConversation(document, source) {
  assert(Array.isArray(document.messages) && document.messages.length === 3, `${source}: expected three messages`);
  assert.deepEqual(document.messages.map(({ role }) => role), ["system", "user", "assistant"], `${source}: invalid roles`);
  assert(isObject(document.metadata), `${source}: metadata missing`);
  assert.equal(typeof document.metadata.exampleId, "string", `${source}: exampleId missing`);
  assert.equal(typeof document.metadata.scenarioType, "string", `${source}: scenarioType missing`);
  assert.equal(document.metadata.source, "MANUAL", `${source}: source must be MANUAL`);
  assert.equal(document.metadata.datasetVersion, "1.0", `${source}: unexpected dataset version`);

  const assistantContent = document.messages[2].content.trim();
  assert(assistantContent.startsWith("{") && assistantContent.endsWith("}"), `${source}: assistant content must contain only one JSON object`);

  const input = JSON.parse(document.messages[1].content);
  const output = JSON.parse(assistantContent);
  validateSchema(input, inputSchema);
  validateSchema(output, outputSchema);

  for (const section of ["bullCase", "bearCase"]) {
    for (const evidence of output[section]) {
      for (const field of evidence.evidenceFields) {
        assert(Object.hasOwn(input, field), `${source}: evidence field ${field} is absent from input`);
        assert.notEqual(input[field], null, `${source}: evidence field ${field} is null`);
      }
    }
  }
  const outputText = Object.values(output)
    .flatMap(function flatten(value) {
      if (typeof value === "string") return [value];
      if (Array.isArray(value)) return value.flatMap(flatten);
      if (isObject(value)) return Object.values(value).flatMap(flatten);
      return [];
    })
    .join("\n");
  assert(!/\b(buy|sell|hold)\b/i.test(outputText), `${source}: prohibited investment instruction`);
  assert(!/```|(^|\n)\s{0,3}(#{1,6}|[-*])\s/m.test(outputText), `${source}: markdown is prohibited`);

  const inputNumbers = new Set(Object.values(input).filter((value) => typeof value === "number"));
  for (const match of outputText.matchAll(/(?<![A-Za-z])[-+]?\d+(?:\.\d+)?/g)) {
    const number = Number(match[0]);
    assert(inputNumbers.has(number), `${source}: unsupported numeric claim ${match[0]}`);
  }
  return { input, output };
}

const exampleNames = (await readdir(join(root, "examples")))
  .filter((name) => /^example-\d{3}\.json$/.test(name))
  .sort();
assert.deepEqual(exampleNames, expectedScenarios.map(([name]) => name));

const examples = [];
const exampleIds = new Set();
const symbols = new Set();
const scenarioTypes = new Set();
const classifications = new Set();
for (const [index, name] of exampleNames.entries()) {
  const document = JSON.parse(await readFile(join(root, "examples", name), "utf8"));
  const { input, output } = parseConversation(document, name);
  const [, expectedId, expectedSymbol, expectedScenario, expectedClassification, expectedReview] = expectedScenarios[index];
  assert.equal(document.metadata.exampleId, expectedId, `${name}: unexpected exampleId`);
  assert.equal(input.symbol, expectedSymbol, `${name}: unexpected symbol`);
  assert.equal(document.metadata.scenarioType, expectedScenario, `${name}: unexpected scenarioType`);
  assert.equal(output.classification, expectedClassification, `${name}: unexpected classification`);
  assert.equal(output.humanReviewRequired, expectedReview, `${name}: unexpected humanReviewRequired`);
  assert(!exampleIds.has(expectedId), `${name}: duplicate exampleId`);
  assert(!symbols.has(expectedSymbol), `${name}: duplicate symbol`);
  assert(!scenarioTypes.has(expectedScenario), `${name}: duplicate scenarioType`);
  exampleIds.add(expectedId);
  symbols.add(expectedSymbol);
  scenarioTypes.add(expectedScenario);
  classifications.add(expectedClassification);
  examples.push(document);
}
assert.deepEqual(
  classifications,
  new Set(["POTENTIALLY_UNDERVALUED", "FAIRLY_VALUED", "POTENTIALLY_OVERVALUED", "UNDER_REVIEW", "INSUFFICIENT_DATA"]),
  "examples must cover all allowed classifications"
);

const lines = (await readFile(join(root, "datasets/seed-dataset-v1.jsonl"), "utf8"))
  .split(/\r?\n/)
  .filter((line) => line.trim().length > 0);
assert.equal(lines.length, 10, "seed dataset must contain exactly ten non-empty lines");
const dataset = lines.map((line, index) => {
  const document = JSON.parse(line);
  parseConversation(document, `seed-dataset-v1.jsonl:${index + 1}`);
  return document;
});
assert.deepEqual(dataset, examples, "JSONL records must exactly match the source examples");

console.log("TRAIN-01 validation passed: 2 schemas, 10 examples, 10 matching JSONL records, 5 classifications.");
