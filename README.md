# th2-replay-script-generator-core (0.0.3)

Core library for replay script generators

## Message transformation

Message transformation can be configured by adding a list transformation commands mapped to a message name and its protocol to `transform` section of the main configuration.

### Transformation command template

```
(set|remove|add|put): json-path to a node (or nodes) to execute operation on
[field]: field-name # for put operation
[value]: value-of-any-type # for set|add|put operations (excludes value-from)
[value-from]: json-path # for set|add|put operations (excludes value), can be relative
[only-if]: # operation condition
  [value-of]: json-path # can be relative i.e. start with '@'
  is-equal-to: value-of-any-type
```

Fields wrapped in square brackets are optional

### Transformation configuration example:

For example, transformation configuration for `fix` protocol `NewOrderSingle` message which conditionally replaces `Account` field, would look like this:

```yaml
transform:
  fix:
    NewOrderSingle:
      - set: $.Account
        value: NewAccount
        only-if:
          is-equal-to: OldAccount
```

## Changes

### 1.0.0

#### Changed:

+ Complete refactoring of message transformation mechanism. Now it uses `JSONPath`, allows multiple transformations for a single path and using value from another field in conditions or as a value for `set`/`put`/`add` operations.

### 0.0.3

#### Changed:

+ Correct serialization to JSON (do not print default values)
