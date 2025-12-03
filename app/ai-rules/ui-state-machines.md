---
description: Overview and index of UI State Machine implementation guidelines
globs: ["**/*StateMachine*.kt", "**/*UiStateMachine*.kt"]
alwaysApply: true
---

# UI State Machine Implementation

## Summary

UI State Machines manage reactive UI logic through declarative composition using Jetpack Compose Runtime, focusing solely on presentation and navigation logic while delegating business logic to domain Services. This documentation is split into focused sections for optimal AI assistant performance and developer navigation.

## Documentation Structure

This rule is split into three focused documents:

### Core Concepts and Fundamentals
**@ai-rules/ui-state-machines-basics.md** - Essential patterns and concepts
- Core interface and Props design
- Type 1 vs Type 2 State Machine patterns
- Basic state management and service integration
- Dependency injection and navigation principles
- When to use State Machines vs simple models

### Models and Presentation
**@ai-rules/ui-state-machines-models.md** - Model design and UI presentation
- Model hierarchy and common model types
- FormBodyModel patterns and custom implementations
- Immutable collections and data composition rules
- Presentation styles and overlay management
- Model naming and reusability guidelines

### Advanced Patterns and Techniques
**@ai-rules/ui-state-machines-patterns.md** - Complex scenarios and optimization
- Error handling and retry patterns
- Performance optimization and remember key management
- Analytics integration and screen tracking
- Advanced side effects and complex flow management
- Testing considerations and debugging techniques

## Quick Reference

For immediate implementation guidance, choose the appropriate documentation:

- **Getting Started**: Use @ai-rules/ui-state-machines-basics.md for core concepts and fundamental patterns
- **Building Models**: Use @ai-rules/ui-state-machines-models.md for model design and presentation logic  
- **Advanced Features**: Use @ai-rules/ui-state-machines-patterns.md for error handling and complex scenarios

## Related Rules

- @ai-rules/domain-service-pattern.md (for business logic delegation)
- @ai-rules/strong-typing.md (for Props and Model type definitions)
- @ai-rules/module-structure.md (for State Machine placement in UI modules)