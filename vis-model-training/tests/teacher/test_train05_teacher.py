import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from vis_training.teacher.config import readiness
from vis_training.teacher.calibration_gate import evaluate_calibration_gate
from vis_training.teacher.cli import main as teacher_cli_main
from vis_training.teacher.critic import CriticRunner
from vis_training.teacher.errors import TeacherDataError, TeacherManifestMismatch
from vis_training.teacher.fake_backend import FakeCriticBackend, FakeTeacherBackend, expected_thesis
from vis_training.teacher.huggingface_backend import HuggingFaceBackend
from vis_training.teacher.io import append_jsonl, iter_jsonl
from vis_training.teacher.pipeline import CandidateRunner
from vis_training.teacher.report import build_report
from vis_training.teacher.recovery import recover_wrapped_critic
from vis_training.teacher.review import check_review, prepare_review, summarize_review
from vis_training.teacher.smoke import select_calibration, select_smoke, write_calibration_plan
from vis_training.teacher.validation import financial_safety_errors

ROOT = Path(__file__).parents[2]
CONFIG = ROOT / "config/teacher-v2.json"
SCENARIOS = ROOT / "datasets/candidates/scenarios-v1.jsonl"
CALIBRATION_REPORT = ROOT / "reports/teacher/train-05-calibration-summary-v1.json"
HUMAN_SUMMARY = ROOT / "reports/teacher/train-05-calibration-human-review-summary-v1.json"
CALIBRATION_GATE = ROOT / "config/calibration-v2-gate.json"


def records(path): return list(iter_jsonl(path))


def test_config_and_pinned_revisions_are_smoke_ready():
    result = readiness(ROOT, CONFIG)
    assert result["localToolingReady"] is True and result["smokeReady"] is True
    assert result["smokeBlockers"] == []
    assert len(result["artifactSha256"]) == 5
    config = json.loads(CONFIG.read_text())
    assert config["teacherPromptVersion"] == "teacher-prompt-v2"
    assert config["criticPromptVersion"] == "critic-prompt-v3"


def test_calibration_prompts_encode_human_review_regressions():
    teacher = (ROOT / "prompts/teacher-prompt-v2.txt").read_text()
    critic = (ROOT / "prompts/critic-prompt-v3.txt").read_text()
    for required in ("FAIR_VALUE", "LEVERAGE_REQUIRES_CONTEXT", "value-trap", "Stable does not mean growing", "AVOID"):
        assert required.lower() in (teacher + critic).lower()
    assert "Do not use REVIEW as the default" in critic
    assert "An ACCEPT with any score below 2" in critic


def test_huggingface_backend_manifest_is_pinned():
    revision = "005ad3404e59d6023443cb575daa05336842228a"
    manifest = HuggingFaceBackend("google/gemma-3-27b-it", revision, revision).manifest()
    assert manifest["revision"] == revision and manifest["tokenizerRevision"] == revision
    converted = HuggingFaceBackend._processor_messages([{"role": "user", "content": "hello"}])
    assert converted == [{"role": "user", "content": [{"type": "text", "text": "hello"}]}]


def test_candidate_pipeline_two_slots_resume_and_manifest_guard(tmp_path):
    output, manifest = tmp_path / "candidates.jsonl", tmp_path / "manifest.json"
    backend = FakeTeacherBackend()
    runner = CandidateRunner(ROOT, CONFIG, backend, clock=lambda: "2026-08-06T00:00:00Z")
    assert runner.run(SCENARIOS, output, manifest, limit=2)["processed"] == 4
    original = output.read_bytes()
    assert runner.run(SCENARIOS, output, manifest, limit=2)["skipped"] == 4
    assert output.read_bytes() == original and len(backend.calls) == 4
    assert {x["candidateIndex"] for x in records(output)} == {1, 2}
    with pytest.raises(TeacherManifestMismatch):
        runner.run(SCENARIOS, output, manifest, run_id="changed", limit=2)


def test_failures_are_accounted_and_duplicate_state_is_rejected(tmp_path):
    output, manifest = tmp_path / "candidates.jsonl", tmp_path / "manifest.json"
    ids = ["TC-SCN-000001-01", "TC-SCN-000001-02"]
    backend = FakeTeacherBackend(failure_ids=[ids[0]], invalid_json_ids=[ids[1]])
    CandidateRunner(ROOT, CONFIG, backend).run(SCENARIOS, output, manifest, limit=1)
    assert [x["status"] for x in records(output)] == ["GENERATION_FAILED", "PARSE_REJECTED"]
    assert "secret payload" not in output.read_text()
    append_jsonl(output, records(output)[0])
    with pytest.raises(TeacherDataError): CandidateRunner(ROOT, CONFIG, backend).run(SCENARIOS, output, manifest, limit=1)


def test_structural_semantic_and_financial_gates(tmp_path):
    scenario = records(SCENARIOS)[0]
    invalid = expected_thesis(scenario); invalid.pop("summary")
    wrong = expected_thesis(scenario); wrong["summary"] = "This is a good value score."
    backend = FakeTeacherBackend(overrides={"TC-SCN-000001-01": invalid, "TC-SCN-000001-02": wrong})
    output = tmp_path / "c.jsonl"
    CandidateRunner(ROOT, CONFIG, backend).run(SCENARIOS, output, tmp_path / "m.json", limit=1)
    assert [x["status"] for x in records(output)] == ["STRUCTURAL_REJECTED", "SEMANTIC_REJECTED"]
    scenarios = {x["scenarioType"]: x for x in records(SCENARIOS)}
    over = expected_thesis(scenarios["OVERVALUED_STRONG"]); over["classification"] = "POTENTIALLY_UNDERVALUED"
    assert "OVERVALUATION_DIRECTION_INCORRECT" in financial_safety_errors(scenarios["OVERVALUED_STRONG"], over)
    dividend = expected_thesis(scenarios["DIVIDEND_RISK"]); dividend["bearCase"] = []
    assert "DIVIDEND_RISK_EVIDENCE_OMITTED" in financial_safety_errors(scenarios["DIVIDEND_RISK"], dividend)
    fair = expected_thesis(scenarios["FAIR_VALUE"]); fair["classification"] = "POTENTIALLY_OVERVALUED"
    assert "FAIR_VALUE_CLASSIFICATION_REQUIRED" in financial_safety_errors(scenarios["FAIR_VALUE"], fair)


@pytest.mark.parametrize(
    "scenario_type",
    [
        "ADVERSARIAL_INPUT",
        "CONTRADICTORY_SIGNALS",
        "DIVIDEND_RISK",
        "HIGH_LEVERAGE",
        "INCONSISTENT_DATA",
        "STALE_DATA",
        "VALUE_TRAP",
    ],
)
def test_human_review_failures_require_under_review(scenario_type):
    scenario = next(item for item in records(SCENARIOS) if item["scenarioType"] == scenario_type)
    output = expected_thesis(scenario)
    output["classification"] = "POTENTIALLY_UNDERVALUED"
    output["humanReviewRequired"] = False
    assert "REVIEW_CLASSIFICATION_REQUIRED" in financial_safety_errors(scenario, output)


def test_critic_reviews_only_parseable_and_never_mutates_candidate(tmp_path):
    candidates, manifest, critics = tmp_path / "c.jsonl", tmp_path / "m.json", tmp_path / "r.jsonl"
    CandidateRunner(ROOT, CONFIG, FakeTeacherBackend(invalid_json_ids=["TC-SCN-000001-02"])).run(SCENARIOS, candidates, manifest, limit=1)
    before = candidates.read_bytes()
    backend = FakeCriticBackend()
    assert CriticRunner(ROOT, CONFIG, backend).run(SCENARIOS, candidates, critics)["processed"] == 1
    assert candidates.read_bytes() == before and len(backend.calls) == 1
    assert CriticRunner(ROOT, CONFIG, backend).run(SCENARIOS, candidates, critics)["skipped"] == 1


def test_critic_rejects_replacement_shape(tmp_path):
    schema = json.loads((ROOT / "schemas/critic-review.schema.json").read_text())
    review = {"verdict": "ACCEPT", "scores": {"grounding": 2, "classification": 2, "riskCoverage": 2, "decisionSupportSafety": 2}, "errorCodes": [], "rationale": "ok", "evidenceFields": [], "replacementOutput": {}}
    assert list(Draft202012Validator(schema).iter_errors(review))


def test_report_smoke_and_review_artifacts(tmp_path):
    candidates, manifest, critics = tmp_path / "c.jsonl", tmp_path / "m.json", tmp_path / "r.jsonl"
    CandidateRunner(ROOT, CONFIG, FakeTeacherBackend()).run(SCENARIOS, candidates, manifest, limit=20)
    CriticRunner(ROOT, CONFIG, FakeCriticBackend()).run(SCENARIOS, candidates, critics)
    report = build_report(candidates, critics, hourly_rate=0.4)
    assert report["denominators"]["candidateSlots"] == 40
    assert report["denominators"]["usableCriticReviews"] == 40
    assert report["usage"]["estimatedCostUsd"] is not None and report["automaticTrainingPromotion"] is False
    smoke = select_smoke(SCENARIOS)
    assert len(smoke) == 20 and len({x["scenarioType"] for x in smoke}) == 14
    smoke_scenarios = tmp_path / "smoke.jsonl"
    for scenario in smoke: append_jsonl(smoke_scenarios, scenario)
    full_candidates = tmp_path / "full.jsonl"
    CandidateRunner(ROOT, CONFIG, FakeTeacherBackend()).run(smoke_scenarios, full_candidates, tmp_path / "full-manifest.json")
    form_path = tmp_path / "review.json"
    form = prepare_review(full_candidates, form_path, 30)
    assert len(form["reviews"]) == 30 and len({x["scenarioType"] for x in form["reviews"]}) == 14
    assert check_review(form_path)["complete"] is False


def test_calibration_selection_is_deterministic_and_balanced():
    first = select_calibration(SCENARIOS, 50)
    second = select_calibration(SCENARIOS, 50)
    assert [x["scenarioId"] for x in first] == [x["scenarioId"] for x in second]
    assert len(first) == 50 and len({x["scenarioId"] for x in first}) == 50
    counts = {}
    for scenario in first:
        counts[scenario["scenarioType"]] = counts.get(scenario["scenarioType"], 0) + 1
    assert len(counts) == 14
    assert max(counts.values()) - min(counts.values()) <= 1


def test_calibration_plan_records_budget_and_stop_gate(tmp_path):
    plan = write_calibration_plan(SCENARIOS, tmp_path / "plan.json", dataset_output=tmp_path / "scenarios.jsonl")
    assert plan["candidateSlotCount"] == 100
    assert plan["programBudgetCapUsd"] == 50.0
    assert plan["calibrationBudgetCapUsd"] == 10.0
    assert plan["requiresStopBeforeBulk"] is True
    with pytest.raises(TeacherDataError):
        write_calibration_plan(SCENARIOS, tmp_path / "invalid.json", program_budget_cap_usd=5, calibration_budget_cap_usd=10)


def test_calibration_v1_is_blocked_by_versioned_quality_gate():
    result = evaluate_calibration_gate(
        json.loads(CALIBRATION_REPORT.read_text()),
        json.loads(HUMAN_SUMMARY.read_text()),
        json.loads(CALIBRATION_GATE.read_text()),
    )
    assert result["decision"] == "NO_GO"
    assert result["bulkAuthorizedByGate"] is False
    assert {
        "canonical_critic_rate",
        "decisive_critic_rate",
        "human_accept_rate",
        "validator_false_positive_rate",
        "average_grounding",
        "zero_score_rate_classification",
    } <= set(result["failedCriteria"])


def test_human_review_summary_is_reproducible_and_sanitized(tmp_path):
    scenario_types = sorted({item["scenarioType"] for item in records(SCENARIOS)})
    reviews = []
    for index in range(30):
        reviews.append({
            "candidateId": f"TC-TEST-{index:03d}",
            "scenarioType": scenario_types[index % 14],
            "candidateStatus": "SEMANTIC_REJECTED" if index == 0 else "CRITIC_PENDING",
            "reviewerAlias": "reviewer",
            "reviewedAt": "2026-08-24T00:00:00Z",
            "accepted": index < 24,
            "scores": {"grounding": 2, "classification": 0 if index < 3 else 2, "riskCoverage": 2, "decisionSupportSafety": 2},
            "notes": "Synthetic gate fixture.",
        })
    review = tmp_path / "review.json"
    review.write_text(json.dumps({"formatVersion": "1.0", "minimumReviews": 30, "automaticTrainingPromotion": False, "reviews": reviews}))
    summary = summarize_review(review, tmp_path / "summary.json")
    assert summary["reviewedCandidateCount"] == 30
    assert summary["categoryCount"] == 14
    assert summary["decisions"] == {"ACCEPT": 24, "REJECT": 6}
    assert summary["candidateStatusByDecision"]["SEMANTIC_REJECTED"]["ACCEPT"] == 1
    assert summary["scores"]["classification"]["distribution"]["0"] == 3
    assert summary["sanitization"]["containsRawModelOutput"] is False


def test_calibration_gate_accepts_a_report_meeting_every_threshold():
    report = json.loads(CALIBRATION_REPORT.read_text())
    human = json.loads(HUMAN_SUMMARY.read_text())
    thresholds = json.loads(CALIBRATION_GATE.read_text())
    report["critic"].update(canonicalReviews=98, wrappedJsonRecovered=2, usableReviews=100,
                             verdicts={"ACCEPT": 80, "REJECT": 18, "REVIEW": 2})
    human["decisions"] = {"ACCEPT": 27, "REJECT": 3}
    human["candidateStatusByDecision"]["SEMANTIC_REJECTED"]["ACCEPT"] = 1
    for name, average in {"grounding": 1.9, "classification": 1.9, "riskCoverage": 1.7, "decisionSupportSafety": 1.9}.items():
        human["scores"][name] = {"average": average, "distribution": {"0": 1, "1": 1, "2": 28}}
    result = evaluate_calibration_gate(report, human, thresholds)
    assert result["decision"] == "GO"
    assert result["failedCriteria"] == []
    assert all(item["passed"] for item in result["criteria"])


def test_calibration_gate_cli_returns_four_and_can_write_report(tmp_path, capsys):
    output = tmp_path / "gate.json"
    exit_code = teacher_cli_main([
        "--root", str(ROOT), "calibration-gate",
        "--report", str(CALIBRATION_REPORT),
        "--human-summary", str(HUMAN_SUMMARY),
        "--thresholds", str(CALIBRATION_GATE),
        "--output", str(output),
    ])
    assert exit_code == 4
    assert json.loads(output.read_text())["decision"] == "NO_GO"
    assert json.loads(capsys.readouterr().out)["bulkAuthorizedByGate"] is False


def test_recovery_accepts_only_single_schema_valid_json_fence(tmp_path):
    schema = ROOT / "schemas/critic-review.schema.json"
    good = {"verdict": "REVIEW", "scores": {"grounding": 1, "classification": 1, "riskCoverage": 1, "decisionSupportSafety": 1}, "errorCodes": [], "rationale": "Review required.", "evidenceFields": []}
    source = tmp_path / "source.jsonl"
    base = {"criticId": "CR-TC-SCN-000001-01", "candidateId": "TC-SCN-000001-01", "status": "CRITIC_FAILED", "rawReview": "```json\n" + json.dumps(good) + "\n```", "parsedReview": None, "criticError": "INVALID_JSON", "inputTokens": 1, "outputTokens": 1, "latencyMs": 1, "provenance": {}}
    append_jsonl(source, base)
    result = recover_wrapped_critic(source, tmp_path / "recovered.jsonl", schema)
    assert result == {"total": 1, "canonical": 0, "recovered": 1, "unrecoverable": 0, "usable": 1}
    recovered = records(tmp_path / "recovered.jsonl")[0]
    assert recovered["status"] == "RECOVERED_REVIEW" and recovered["rawReview"] == base["rawReview"]
