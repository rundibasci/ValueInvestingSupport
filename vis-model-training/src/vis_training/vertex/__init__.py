"""Vertex AI Gemini request-shape adaptation (TA2).

Adapts the unchanged VIS task-contract schemas (`schemas/thesis-output.schema.json`)
into the OpenAPI 3.0 subset Vertex AI's `responseSchema` accepts, without ever
editing the source schema itself. See `specs/2026-08-27-ta2-vertex-prompt-contract/`.
"""
