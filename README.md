# PLC4X NiFi Fix Repository

**⚠️ Temporary Fork Notice**: This repository is a temporary fork created to address a specific issue with PLC4X and Apache NiFi. It will be deprecated or deleted once Apache PLC4X officially releases version 0.13. Use this fork only if you require an immediate fix before the official release.

---

## Overview

This repository provides a fix for a compatibility issue between Apache PLC4X and Apache NiFi 2.x versions. The solution for NiFi has already been built as a .nar file containing the PLC4X fix. You can find the .nar file in the `/nifi_builded_nar` folder.

If you'd like to build it yourself, you can run docker-compose up, and the generated outputs will be placed in the `./out/.repository` folder. You can also build it using the mvnw tool, but I recommend using Docker.

**Key Clarifications**:
- **Full PLC4X Build**: This repository builds the **entire PLC4X project** (not just the NiFi components) to ensure compatibility and avoid dependency mismatches.
- **Temporary Use**: This fork is intended as a stopgap solution until PLC4X v0.13 is officially released.

---

## Prerequisites

- Docker
- Docker Compose
