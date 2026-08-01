import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const inputSchema = JSON.parse(await readFile(join(root, "schemas/thesis-input.schema.json"), "utf8"));
const outputSchema = JSON.parse(await readFile(join(root, "schemas/thesis-output.schema.json"), "utf8"));

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

  const input = JSON.parse(document.messages[1].content);
  const output = JSON.parse(document.messages[2].content);
  validateSchema(input, inputSchema);
  validateSchema(output, outputSchema);

  for (const section of ["bullCase", "bearCase"]) {
    for (const evidence of output[section]) {
      for (const field of evidence.evidenceFields) {
        assert(Object.hasOwn(input, field), `${source}: evidence field ${field} is absent from input`);
      }
    }
  }
  assert(!/\b(buy|sell|hold)\b/i.test(document.messages[2].content), `${source}: prohibited investment instruction`);
  return { input, output };
}

const exampleNames = (await readdir(join(root, "examples")))
  .filter((name) => /^example-\d{3}\.json$/.test(name))
  .sort();
assert.deepEqual(exampleNames, ["example-001.json", "example-002.json", "example-003.json"]);

const examples = [];
for (const name of exampleNames) {
  const document = JSON.parse(await readFile(join(root, "examples", name), "utf8"));
  parseConversation(document, name);
  examples.push(document);
}

const lines = (await readFile(join(root, "datasets/seed-dataset-v1.jsonl"), "utf8"))
  .split(/\r?\n/)
  .filter((line) => line.trim().length > 0);
assert.equal(lines.length, 3, "seed dataset must contain exactly three non-empty lines");
const dataset = lines.map((line, index) => {
  const document = JSON.parse(line);
  parseConversation(document, `seed-dataset-v1.jsonl:${index + 1}`);
  return document;
});
assert.deepEqual(dataset, examples, "JSONL records must exactly match the source examples");

assert.equal(JSON.parse(examples[0].messages[2].content).classification, "POTENTIALLY_UNDERVALUED");
assert.equal(JSON.parse(examples[0].messages[2].content).humanReviewRequired, false);
assert.equal(JSON.parse(examples[1].messages[2].content).classification, "UNDER_REVIEW");
assert.equal(JSON.parse(examples[1].messages[2].content).humanReviewRequired, true);
assert.equal(JSON.parse(examples[2].messages[2].content).classification, "INSUFFICIENT_DATA");
assert.equal(JSON.parse(examples[2].messages[2].content).humanReviewRequired, true);

console.log("TRAIN-01 validation passed: 2 schemas, 3 examples, 3 matching JSONL records.");
