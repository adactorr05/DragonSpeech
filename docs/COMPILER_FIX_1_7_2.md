# Dragon Speech 1.7.2 compiler fix

Fixed the v1.7.1 compile failure in `WordOfWordsService.buildContext()`.

The trap-context loop creates a `SigilManager.SigilView view` and a `JsonObject v`. The remove-cost calculation accidentally called `v.id()`, but `JsonObject` has no `id()` method. It now correctly calls `view.id()`.

Changed:

```java
// broken 1.7.1
Long.toString(v.id())

// fixed 1.7.2
Long.toString(view.id())
```

No gameplay behavior was intentionally changed in this hotfix.
