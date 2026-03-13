# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java Maven project for algorithm problem solving and coding practice. It uses Java 21 and follows standard Maven directory structure.

## Build Commands

```bash
# Compile the project
cd claude-code-demo && mvn compile

# Package into JAR
cd claude-code-demo && mvn package

# Clean build artifacts
cd claude-code-demo && mvn clean

# Run the main class
cd claude-code-demo && mvn exec:java -Dexec.mainClass="com.feng.claudecode.Main"

# Or run directly with Java (after compiling)
cd claude-code-demo && java -cp target/classes com.feng.claudecode.Main
```

## Project Structure

```
claude-code-demo/
├── pom.xml                           # Maven configuration, Java 21
└── src/main/java/com/feng/claudecode/
    ├── Main.java                     # Entry point
    └── Solution.java                 # Algorithm solutions
```

## Key Files

- **Solution.java**: Contains algorithm implementations for competitive programming problems:
  - `maxProfit()`: Stock trading with cooldown period (DP)
  - `find_lucky_person()`: Circular linked list simulation game
  - `maximumResources()`: Monotonic stack optimization problem

- **Main.java**: Entry point for testing and debugging

## Development Notes

- Java version: 21 (configured in `pom.xml`)
- Package: `com.feng.claudecode`
- No external dependencies in pom.xml (standard library only)
- The `.gitignore` references a `front/` directory for frontend work, but it doesn't currently exist

## IDE Configuration

IntelliJ IDEA configuration is in `.idea/`. Do not commit IDE-specific files (already in `.gitignore`).
