# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [8.0.0] - 2026-08-16

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 38.6.0 → 39.0.1 (major)

## [7.4.0] - 2026-08-13

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 38.3.0 → 38.6.0 (minor)

## [7.3.0] - 2026-08-11

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 38.2.0 → 38.3.0 (minor)

## [7.2.0] - 2026-08-10

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 38.1.0 → 38.2.0 (minor)

## [7.1.0] - 2026-08-05

### Dependencies
- Updated dependencies

## [7.0.1] - 2026-08-02

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 38.0.0 → 38.0.1 (patch)

## [7.0.0] - 2026-07-29

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 37.6.0 → 38.0.0 (major)

## [6.3.0] - 2026-07-26

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 37.4.0 → 37.6.0 (minor)

## [6.2.0] - 2026-07-23

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 37.2.0 → 37.4.0 (minor)

## [6.1.0] - 2026-07-22

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 37.0.0 → 37.2.0 (minor)

## [6.0.0] - 2026-07-21

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 36.10.0 → 37.0.0 (major)

## [5.5.0] - 2026-07-18

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 36.9.0 → 36.10.0 (minor)

## [5.4.0] - 2026-07-15

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 36.7.0 → 36.9.0 (minor)

## [5.3.0] - 2026-07-13

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 36.3.1 → 36.7.0 (minor)

## [5.2.0] - 2026-07-08

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 36.2.0 → 36.3.1 (minor)

## [5.1.0] - 2026-07-06

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 36.1.0 → 36.2.0 (minor)

## [5.0.0] - 2026-06-30

### Dependencies
- **ch.admin.bit.jeap:jeap-spring-boot-parent**: 35.11.0 → 36.1.0 (major)

## [4.4.0] - 2026-06-23

### Changed

- Update parent from 35.6.0 to 35.11.0

## [4.3.0] - 2026-06-12

### Changed

- Update parent from 35.3.0 to 35.6.0

## [4.2.0] - 2026-06-08

### Changed

- The reaction graph now only presents reactions that have been observed within the statistics period.
  Reactions that have not been observed recently are no longer shown in the graph, while reactions themselves are
  never deleted and reappear in the graph as soon as they are observed again.

## [4.1.0] - 2026-06-04

### Changed

- Update parent from 35.0.0 to 35.3.0

## [4.0.0] - 2026-05-28

### Changed

- Official release with spring boot 4

## [2.27.0] - 2026-04-16

### Changed

- Update parent from 33.2.0 to 33.3.0

## [2.26.0] - 2026-04-13

### Changed

- Update parent from 33.1.1 to 33.2.0

## [2.25.0] - 2026-04-09

### Changed

- Update parent from 33.1.0 to 33.1.1

## [2.24.0] - 2026-04-09

### Changed

- Update parent from 33.0.0 to 33.1.0

## [2.23.0] - 2026-04-02

### Changed

- Update parent from 32.0.0 to 33.0.0

## [2.22.1] - 2026-04-02

### Changed

- Update postgres images to version 17.

## [2.22.0] - 2026-03-31

### Changed

- Update parent from 31.5.0 to 32.0.0

## [2.21.0] - 2026-03-26

### Changed

- Update parent from 31.4.0 to 31.5.0

## [2.20.0] - 2026-03-23

### Changed

- Update parent from 31.0.0 to 31.4.0

## [2.19.1] - 2026-03-16

### Changed

- Inserts a new interface row using the next sequence value an do  nothing on conflict to avoid potential issues with concurrent inserts of reactions with multiple actions.

## [2.19.0] - 2026-03-12

### Changed

- Update parent from 30.20.0 to 31.0.0

## [2.18.0] - 2026-03-09

### Changed

- Add new StatisticsController to retrieve the last observation date
- Update parent from 30.19.0 to 30.20.0

## [2.17.0] - 2026-03-05

### Changed

- Update parent from 30.18.0 to 30.19.0

## [2.16.0] - 2026-03-02

### Changed

- Update parent from 30.15.0 to 30.18.0

## [2.15.0] - 2026-01-28

### Changed

- Update parent from 30.14.0 to 30.15.0

## [2.14.0] - 2026-01-26

### Changed

- Update parent from 30.8.0 to 30.14.0

## [2.13.0] - 2026-01-14

### Changed

- Update parent from 30.7.0 to 30.8.0

## [2.12.0] - 2026-01-07

### Changed

- Update parent from 30.6.0 to 30.7.0

## [2.11.0] - 2025-12-23

### Changed

- Add endpoint to list known component names

## [2.10.0] - 2025-12-23

### Changed

- Update parent from 30.5.0 to 30.6.0

## [2.9.0] - 2025-12-19

### Changed

- Update parent from 30.4.0 to 30.5.0

## [2.8.0] - 2025-12-17

### Changed

- Update parent from 30.3.0 to 30.4.0

## [2.7.0] - 2025-12-16

### Changed

- Update parent from 30.2.0 to 30.3.0

## [2.6.0] - 2025-12-08

### Changed

- Update parent from 30.1.0 to 30.2.0

## [2.5.0] - 2025-12-05

### Changed

- Update parent from 30.0.0 to 30.1.0

## [2.4.0] - 2025-12-03

### Changed

- Update parent from 29.4.0 to 30.0.0

## [2.3.0] - 2025-12-02

### Changed

- Update parent from 29.2.0 to 29.4.0

## [2.2.0] - 2025-11-28

### Changed

- Update parent from 28.3.0 to 29.2.0

## [2.1.0] - 2025-11-14

### Changed

- Update parent from 27.4.0 to 28.3.0

## [2.0.0] - 2025-11-05

### Removed

- Removed observed reactions statistics api.

## [1.27.2] - 2025-10-17

### Changed

- System names are now saved in lower case for reactions.

## [1.27.1] - 2025-10-16

### Changed

- System name now case insensitiv when searching for system related reaction graph.


## [1.27.0] - 2025-10-08

### Changed

- The message graph shows also messages before and after the reactions related to the given message type and variant.

## [1.26.1] - 2025-10-03

### Changed

- SystemController does not return a null value in the systems list anymore.

## [1.26.0] - 2025-10-03

### Changed

- Update parent from 27.3.0 to 27.4.0

## [1.25.1] - 2025-10-01

### Fixed

- Fixed null system reaction graph handling.

## [1.25.0] - 2025-09-29

### Changed

- Update parent from 27.2.0 to 27.3.0

## [1.24.0] - 2025-09-19

### Changed

- Update parent from 27.1.1 to 27.2.0

## [1.23.0] - 2025-09-18

### Added

- Added new endpoint /api/graphs/components/{component-name} to retrieve component related reaction graph
- Added new endpoint /api/graphs/messages/{message-type} to retrieve message related reaction graph

## [1.22.0] - 2025-09-17

### Added

- Added new endpoint /api/graphs/systems/{system-name} to retrieve system related reaction graph

## [1.21.0] - 2025-09-16

### Added

- Added new endpoint /api/graphs to retrieve all reactions graph

## [1.20.0] - 2025-09-16

### Added

- Added new endpoint /api/systems/names to retrieve all system names

## [1.19.0] - 2025-09-16

### Added

- Implemented ReactionGraphBuilderService to generate and hold full, in memory domain graph.

## [1.18.0] - 2025-09-12

### Changed

- Adapt database schema to introduce interface table for reactions and actions.

## [1.17.0] - 2025-09-11

### Changed

- Update parent from 27.1.0 to 27.1.1

## [1.16.0] - 2025-09-10

### Changed

- Update parent from 26.76.0 to 27.1.0

## [1.15.0] - 2025-09-02

### Added

- Implemented graph-related classes for the graph rest api

### Changed

- Refactor domain structure by moving classes to 'models' package.

## [1.14.0] - 2025-09-02

### Changed

- Update parent from 26.75.0 to 26.76.0

## [1.13.0] - 2025-09-02

### Changed

- Cleanup unused columns, indices and endpoints after removing compatibility for old ReactionIdentifiedEvents

## [1.12.0] - 2025-09-01

### Added

- Removed backwards compatibility for old ReactionIdentifiedEvents

## [1.11.0] - 2025-08-27

### Added

- Save system name to reactions. 

## [1.10.0] - 2025-08-26

### Changed

- Update parent from 26.72.0 to 26.74.0

## [1.9.3] - 2025-08-26

### Changed

- Using native query instead of a JPA one to avoid out of memory errors when deleting observed reactions as part of the housekeeping process

## [1.9.2] - 2025-08-26

### Changed

- Fix missing acknowledgement of reaction observed events

## [1.9.1] - 2025-08-26

### Changed

- Added a new index for column idempotence_id in observed_reaction table

## [1.9.0] - 2025-08-05

### Changed

- Update parent from 26.71.0 to 26.72.0

## [1.8.0] - 2025-07-25

### Changed

- Update parent from 26.68.0 to 26.71.0

## [1.7.0] - 2025-07-08

### Changed

- Update parent from 26.67.0 to 26.68.0

## [1.6.0] - 2025-07-04

### Changed

- Update parent from 26.63.1 to 26.67.0

## [1.5.0] - 2025-06-20

### Changed

- Added support for reactions with multiple actions
- Update parent from 26.63.0 to 26.63.1
 
## [1.4.0] - 2025-06-19

### Changed

- Update parent from 26.61.0 to 26.63.0

## [1.3.0] - 2025-06-17

### Changed

- Update parent from 26.56.0 to 26.61.0

## [1.2.0] - 2025-06-12

### Changed

- Update parent from 26.55.0 to 26.56.0

## [1.1.0] - 2025-06-06

### Changed

- Update parent from 26.48.0 to 26.55.0

## [1.0.0] - 28.05.2025

### Changed

- Initial version
