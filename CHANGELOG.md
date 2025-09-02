# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
