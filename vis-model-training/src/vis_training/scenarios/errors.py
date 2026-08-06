"""Stable TRAIN-04 error types and CLI exit codes."""


class ScenarioError(Exception):
    exit_code = 5


class ScenarioConfigurationError(ScenarioError):
    exit_code = 2


class ScenarioValidationError(ScenarioError):
    exit_code = 3

    def __init__(self, failures):
        self.failures = list(failures)
        super().__init__(f"Scenario validation failed with {len(self.failures)} error(s)")


class ScenarioContaminationError(ScenarioError):
    exit_code = 4

    def __init__(self, failures):
        self.failures = list(failures)
        super().__init__(f"Scenario contamination check failed with {len(self.failures)} error(s)")
