# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org)

## [Unreleased]

### Added
- Project constitution: mission statement, tech-stack decisions, and phased roadmap (`specs/`)
- Phase Z1 feature specification: requirements, implementation plan, and validation criteria
- Spring Boot 3.4.0 backend scaffold with `demo` profile (no database or Redis required)
- Maven 3.9.16 wrapper (`mvnw`) and base package `it.mazzoni.vis`
- Caffeine in-memory cache wired via Spring Cache for the demo profile
- `/actuator/health` endpoint (only endpoint exposed in demo profile)
- `docker-compose.yml` with PostgreSQL 16 and Redis 7 for future phases
- `.env.example` documenting all future environment variables
- Global `/changelog` Claude Code skill for generating Keep-a-Changelog entries
