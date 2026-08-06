"""Stable errors for local TRAIN-05 tooling."""


class TeacherToolingError(Exception):
    exit_code = 5


class TeacherConfigurationError(TeacherToolingError):
    exit_code = 2


class TeacherDataError(TeacherToolingError):
    exit_code = 3


class TeacherManifestMismatch(TeacherToolingError):
    exit_code = 4
