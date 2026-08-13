# Runic Tome — Audit Report

**Audit date:** 2026-08-13
**Repository:** `C:\Projects\Runic Tome\runic-tome` (branch `main`, working tree dirty)
**Audited revision:** working tree as of `git status` below, i.e. uncommitted work on top of `103096c`
**Operating mode:** `AUDIT_AND_IMPLEMENT`
**Scope of this document:** everything inspected, everything confirmed, everything eliminated, and
everything that could not be checked in this environment.

> This audit did **not** find every possible defect. It reports what was traced in source, what was
> reproduced with an executable probe, and what remains unverified. Sections marked **BLOCKED** were
> impossible to check because no Minecraft client/server, Patchouli jar, or network access to the
> Forge/Maven repositories was available.

---

## 1. Executive summary

Runic Tome's core safety design is sound. Absorption is gated by a single centralized
`AbsorptionPolicy` that runs before any adapter, the unlock path returns a tri-state
`UnlockResult` and only consumes the physical item once storage is confirmed, extraction is
server-authoritative and idempotent, and capability data tolerates corrupt entries on load. The
existing 30-test suite passes.

The audit found one systemic latent bug and a cluster of consequences flowing from it:

**`ForgeRegistries.ITEMS.getValue(unknownId)` returns `minecraft:air`, not `null`.** This was
proven with an executable probe against the project's own Forge 1.20.1 / 47.3.0 classpath. The
codebase contains **nine `item == null` guards that can never fire**. Every "this item isn't
registered, skip it" path is dead: datapack definitions for missing items register a live adapter
named "Air", `extraBookItemIds` typos are silently accepted instead of logged, orphaned library
entries render as "Air" in the UI, and `ItemBasedAdapter`'s "unknown guide item" message never
appears.

The most serious consequence is **global cross-mod state corruption**: because the resolved item is
`AIR`, `new ItemStack(AIR)` is empty, `ItemStack.copy()` on an empty stack returns the shared
`ItemStack.EMPTY` **singleton**, and `UseSimulator` then calls `getOrCreateTag().putBoolean(...)` on
it. This was reproduced: after one such call, `ItemStack.EMPTY.getTag()` returns
`{"runictome:virtual":1b}` process-wide, for every mod.

Also confirmed: the reflective Patchouli adapter does not override `openServer`, so opening a
Patchouli book from the tome makes the server replay an unrelated item's `use()`; a third-party
adapter that throws inside `identify` propagates out of the item-pickup event instead of failing
closed; five configuration options are snapshotted at setup and silently ignored on reload while
the exclusion lists next to them *are* live; the client library cache is never cleared on logout;
the `purgeExcluded` recovery helper advertised in the changelog is unreachable from any command;
and `SyncBookDefsPacket` sizes a collection from an untrusted VarInt before reading it.

19 findings were implemented in this pass and pinned by 43 new tests (30 → 73, all passing). 6 are
recommendations that change gameplay, UX, or config layout and were deliberately left for a product
decision. Save format, config schema, network protocol (`4`) and public API version (`2`) are
unchanged, so **no migration is required**.

The change set was then re-reviewed adversarially, which caught three defects *introduced by the
fixes* — a duplication vector in a "polite" restore path, namespace-blind adapter removal that could
delete a third-party integration on a config reload, and a precedence flip within a priority tier
after a reload. All three were corrected before this report was finalized; section 6 records what
they were, because the reasoning matters more than the diff.

---

## 2. Verified repository and architecture summary

Facts below were read from the repository, not assumed.

| Property | Value | Source |
|---|---|---|
| Mod id | `runictome` | `gradle.properties`, `RunicTome.MOD_ID` |
| Package | `com.otectus.runictome` | source tree |
| Declared version | `0.5.2` | `gradle.properties:19` |
| Minecraft | `1.20.1`, range `[1.20.1,1.21)` | `gradle.properties:6-7` |
| Forge | `47.3.0`, range `[47,)` | `gradle.properties:8-10` |
| Mappings | Parchment `2023.09.03-1.20.1` | `gradle.properties:11-12` |
| Java toolchain | 17 | `build.gradle:16` |
| Gradle wrapper | 8.1.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Network protocol | `"4"` | `RunicTomeNetwork:13` |
| Public API version | `2` | `RunicTomeAPI.API_VERSION` |
| Persistence | player capability `IRunicTomeData` via `RunicTomeDataProvider` | `capability/` |
| Source sets | `src/main/java` (47 files), `src/test/java` (6 files), `src/main/resources`, `src/generated/resources` (declared, absent) | `build.gradle:57` |
| Mixins | none | no mixin config, no `MixinExtras` dependency |

### Entry points and buses

- **Mod constructor** (`RunicTome`): installs the API delegate, registers `commonSetup`,
  `processImc`, `onConfigLoadOrReload`, capability registration on the **mod bus**; registers items;
  registers `COMMON_SPEC`; registers itself on the **Forge bus**.
- **`FMLCommonSetupEvent`** → `enqueueWork` → `RunicTomeNetwork.register()` then
  `ModIntegrations.setupAll()`.
- **`ModConfigEvent`** (load *and* reload) → `AbsorptionPolicy.rebuildFromConfig()`.
- **`InterModProcessEvent`** → `ImcHandler.processImc`.
- **Static `@Mod.EventBusSubscriber` (Forge bus):** `AbsorptionHandler`, `CapabilityEvents`,
  `FirstJoinHandler`, `ServerTickHandler`, `SoulboundHandler`, `RunicTomeCommand`,
  `DatapackBookManager`, `PatchouliReloadHandler`.
- **Client-only subscribers:** `KeyBindings` (+ nested mod-bus subscriber for key mappings).
- **Reload listener:** `DatapackBookManager` via `AddReloadListenerEvent`; datapack sync via
  `OnDatapackSyncEvent` (also used by `PatchouliReloadHandler` to invalidate the custom-item map).

### Configuration inventory (all in one `COMMON` spec)

| Option | Live on reload? | Notes |
|---|---|---|
| `absorbOnPickup` | yes (read per event) | server-authoritative |
| `absorbOnCraft` | yes (read per event) | server-authoritative |
| `extraBookItemIds` | **no** (fixed in this pass) | snapshotted into adapters at setup |
| `absorbUnknownBooks` | **no** (fixed in this pass) | decides whether the heuristic adapter exists |
| `bookKeywords` | **no** (fixed in this pass) | captured into `HeuristicBookAdapter` |
| `bookBlocklist` | **no** (fixed in this pass) | captured into `HeuristicBookAdapter` |
| `bookBlocklistMods` | **no** (fixed in this pass) | captured into `HeuristicBookAdapter` |
| `absorbExclusionItems` | yes | `AbsorptionPolicy.rebuildFromConfig` |
| `absorbExclusionMods` | yes | `AbsorptionPolicy.rebuildFromConfig` |
| `showUnlockToast` | yes (read per toast) | client-only preference in a COMMON spec — see RT-23 |
| `allowBookExtraction` | yes (read per request) | server-authoritative |
| `sweepIntervalTicks` | yes (read per tick) | range 1..1200 |
| `grantTomeOnFirstJoin` | yes (read per login) | |
| `verboseLogging` | yes | |

### Adapters and precedence

Probed highest-priority-first from an immutable snapshot; stable sort preserves registration order
within a priority tier.

| Adapter | `systemId` | Priority |
|---|---|---|
| `PatchouliGuideAdapter` | `runictome:patchouli` | 100 (default) |
| `ItemBasedAdapter` (Tinkers ×6) | `runictome:tinkers/<path>` | 100 |
| `ItemBasedAdapter` (Better Animals Plus, MCA ×N, Immersive Engineering, Modonomicon ×N) | `runictome:<prefix>/<path>` | 100 |
| `ItemBasedAdapter` (config) | `runictome:config/<ns>/<path>` | 100 |
| `ItemBasedAdapter` (datapack) | `runictome:datapack/<ns>/<path>` | 100 |
| `ImcHandler.StaticAdapter` | `runictome:imc_static` | 100 |
| third-party via API/IMC | caller-defined | caller-defined |
| `TaggedGuideBookAdapter` | `runictome:tagged` | 50 |
| `HeuristicBookAdapter` | `runictome:heuristic` | 0 |

`AbsorptionPolicy.evaluate` runs **before** the adapter loop, so the documented precedence
`global exclusion / never-absorb → explicit integration → positive tag → heuristic` holds.

### Packets

| Packet | Direction | Payload | Authorization |
|---|---|---|---|
| `SyncDataPacket` | S→C | full capability NBT | n/a |
| `UnlockBookPacket` | S→C | `BookKey` + retained `ItemStack` | n/a |
| `SyncBookDefsPacket` | S→C | VarInt count + N × (RL, RL, UTF) | n/a |
| `OpenBookPacket` | C→S | `BookKey` | `isBookUnlocked` |
| `ToggleFavoritePacket` | C→S | `BookKey` | `toggleFavorite` rejects unowned |
| `ExtractBookPacket` | C→S | `BookKey` | config gate + `hasBook` |

All six use `consumerMainThread`. **Confirmed by bytecode disassembly** of
`SimpleChannel$MessageBuilder.lambda$consumerMainThread$2` that this wrapper already performs
`ctx.enqueueWork(...)` and `ctx.setPacketHandled(true)`, so no handler is missing either. (It also
discards the returned `CompletableFuture`, so an exception thrown inside a handler is not surfaced
by the wrapper — relevant to RT-05.)

---

## 3. Baseline commands and results

### Attempted, and why the canonical commands could not run

| Command | Result |
|---|---|
| `.\gradlew.bat compileJava` / `test` / `check` / `build` | **BLOCKED.** Not runnable from this session. The cloud container has JDK 21 + Gradle 8.14.3 but **no network route to `maven.minecraftforge.net`, `repo1.maven.org`, `maven.parchmentmc.org`, or `plugins.gradle.org`** (all `curl` probes returned `000`), so ForgeGradle cannot resolve. The user's device VM (`device_bash`) has **no network at all** and only a **JRE 11** — no `javac`, no `javap`, and the bundled `.tooling/jdk17` is a **Windows** JDK that cannot execute there. |

`clean` was not run. No interactive client or server was launched.

### Pre-existing baseline recovered from the repository

`build/test-results/test/TEST-*.xml`, written on host `PC` at **2026-08-06T15:34**, records
**30 tests, 0 failures, 0 errors, 0 skipped** across all six test classes. Source mtimes
(≈15:33) and `build/libs/runictome-0.5.2.jar` (15:34) are consistent with that run, so the working
tree was green before this audit.

### Substitute headless harness built for this audit

Because Gradle was unreachable, an equivalent harness was constructed from the project's **own**
ForgeGradle dependency cache (`.tooling/gradle-home/caches`), which contains the exact
`forge-1.20.1-47.3.0_mapped_parchment_2023.09.03-1.20.1.jar`, `client-extra.jar`, and all runtime
libraries the Gradle build would use. These were placed on a plain `javac`/`java` classpath and
JUnit 5 was driven through `junit-platform-launcher` directly.

```
== compileJava ==        javac --release 17, 47 sources   → exit 0
== compileTestJava ==    javac --release 17,  6 sources   → exit 0
== test ==               JUnit platform launcher          → 30 found / 30 successful / 0 failed
```

This reproduces the recorded Gradle baseline exactly (30/30). It is **not** a substitute for
`gradlew build`: it does not run `processResources` token expansion, `reobfJar`, jar packaging, or
any ForgeGradle validation. Those remain **unverified**.

### Working-tree state at audit start

```
 M .gitignore                                    M src/.../integration/HeuristicBookAdapter.java
 M CHANGELOG.md                                  M src/.../integration/ItemBasedAdapter.java
 M README.md                                     M src/.../integration/ModTags.java
 M gradle.properties                             M src/.../integration/TaggedGuideBookAdapter.java
 M src/.../RunicTome.java                        M src/.../integration/datapack/BookDef.java
 M src/.../RunicTomeConfig.java                  M src/.../integration/datapack/DatapackBookManager.java
 M src/.../api/GuideSystemAdapter.java           M src/.../integration/patchouli/PatchouliGuideAdapter.java
 M src/.../api/IRunicTomeData.java               M src/.../network/OpenBookPacket.java
 M src/.../api/ImcBook.java                      M src/.../network/RunicTomeNetwork.java
 M src/.../api/NameFormat.java                   M src/.../network/SyncBookDefsPacket.java
 M src/.../api/RunicTomeAPI.java                 M src/.../network/ToggleFavoritePacket.java
 M src/.../api/UnlockResult.java                 M src/.../network/UnlockBookPacket.java
 M src/.../capability/RunicTomeData.java         M src/main/resources/assets/runictome/lang/en_us.json
 M src/.../client/ClientDataCache.java           M src/main/resources/data/.../absorb_blocklist.json
 M src/.../client/screen/RunicTomeScreen.java    M src/main/resources/data/.../guide_books.json
 M src/.../command/RunicTomeCommand.java         M src/test/.../AdapterRegistryTest.java
 M src/.../event/AbsorptionHandler.java          M src/test/.../HeuristicBookAdapterTest.java
 M src/.../event/CapabilityEvents.java           M src/test/.../RunicTomeDataTest.java
 M src/.../event/FirstJoinHandler.java          ?? "Midnight Apocalypse [FORGE] 3.2.zip"
 M src/.../event/ServerTickHandler.java         ?? runictome-0.5.0.jar
 M src/.../impl/AdapterRegistry.java            ?? runictome-0.5.1.jar
 M src/.../impl/UseSimulator.java               ?? src/.../impl/AbsorptionPolicy.java
                                                ?? src/.../impl/BookExtraction.java
                                                ?? src/.../network/ExtractBookPacket.java
                                                ?? src/test/.../AbsorptionPolicyTest.java
                                                ?? src/test/.../BookExtractionTest.java
```

The five untracked Java files are the in-progress 0.5.2 extraction + absorption-policy work
described in `CHANGELOG.md [0.5.2]`; they compile and are covered by the two untracked tests. They
were treated as current implementation, not as leftovers. Nothing was reverted, staged, committed,
relocated, or reformatted. `build/`, `logs/`, `.gradle/`, `.tooling/`, the loose 0.5.x jars and the
607 MB `Midnight Apocalypse [FORGE] 3.2.zip` were left untouched; the archive was opened **read-only**
via Python `zipfile` to read `manifest.json` and the `overrides/config/` filenames only.

---

## 4. Findings

Severity: **P0** item loss/corruption, duplication exploit, security bypass, save loss, widespread
crash. **P1** broken core feature, serious false-positive absorption, desync, dedicated-server
incompatibility, major integration failure. **P2** localized correctness, measurable performance,
confusing UX, insufficient validation, maintainability. **P3** polish/optional.

| ID | Area | Sev | Conf | Finding | User impact | Evidence | Reproduction | Recommended action | Effort | Risk | Verification |
|----|------|-----|------|---------|-------------|----------|--------------|--------------------|--------|------|--------------|
| RT-02 | Adapters / integration / datapack | P1 | CONFIRMED | `ForgeRegistries.ITEMS.getValue(id)` returns `minecraft:air` for unknown ids, never `null`. Nine `item == null` guards are dead: `ModIntegrations.registerTinkersBook/registerSimpleBook/registerConfigBooks`, `DatapackBookManager.applyDefs`, `ItemBasedAdapter.open/openServer/displayIcon`, `HeuristicBookAdapter.open/openServer/displayName/displayIcon`, `TaggedGuideBookAdapter.*`, `BookExtraction.materialize`, `GuideSystemAdapter.openServer` default. | Typos in `extraBookItemIds` and datapack `item` fields are silently accepted instead of logged; bogus adapters named "Air" are registered and appear in `/runictome debug dump`; orphaned entries show "Air" in the library UI; `runictome.unknown_item` never displays. | Executable probe against forge-1.20.1-47.3.0: `getValue(nonexistentmod:nonexistent_book)` → `air`; `containsKey(...)` → `false`. | Add `extraBookItemIds = ["typo:does_not_exist"]`; no warning is logged and an adapter is registered. | Replace every `getValue(...) == null` guard with `ForgeRegistries.ITEMS.containsKey(id)`. | S | Low | Unit test asserting `getValue` returns AIR and that resolution helper rejects it. |
| RT-03 | Use simulation | P1 | CONFIRMED | `UseSimulator.simulate{Client,Server}Use` calls `copy()`, `setCount(1)` and `getOrCreateTag()` on a stack that may be empty. `ItemStack.copy()` on an empty stack returns the shared `ItemStack.EMPTY` **singleton**, so the virtual marker is written into global state. | Process-wide cross-mod corruption: `ItemStack.EMPTY.getTag()` stops returning `null` and returns `{"runictome:virtual":1b}`. Any mod that null-checks `stack.getTag()` without an `isEmpty()` guard changes behavior. | Probe: after one call, `ItemStack.EMPTY.getTag()` = `{"runictome:virtual":1b}`; internal `count` also mutated to 1 (masked by `isEmpty()`'s identity check). | Open a library entry whose retained stack is empty (legacy key-only save) and whose `bookId` resolves to no registered item. | Early-return when the incoming stack is empty; never mutate a stack derived from `ItemStack.EMPTY`. | S | Low | Regression test asserting `ItemStack.EMPTY.getTag()` stays `null` after simulating with an empty stack. |
| RT-04 | Patchouli / opening | P1 | CONFIRMED | `PatchouliGuideAdapter` does not override `openServer`. The inherited default treats `key.bookId()` (a *Patchouli book id*) as an *item registry id* and replays that item's `use()` on the server. | Opening a Patchouli book from the tome runs an unrelated item's server-side `use()` — sounds, statistics, cooldowns, advancement triggers, or a second GUI open. When no item matches, it feeds an empty stack into `UseSimulator` (RT-03). | `PatchouliGuideAdapter` defines `open` only; `GuideSystemAdapter.openServer` default at `api/GuideSystemAdapter.java:47-51`. `botania:lexicon` is both a book id and an item id. | Absorb Botania's Lexica Botania, open it from the tome, observe server-side `use()` on the real lexicon in addition to the client GUI. | Override `openServer` to a no-op; Patchouli's `open` is fully client-side. | S | Low | Not automatable without Patchouli — source-level assertion only. |
| RT-05 | Absorption safety / API | P1 | CONFIRMED | `AdapterRegistry.identify` iterates adapters with no per-adapter exception isolation. A third-party, IMC, or datapack adapter throwing from `identify` propagates into `EntityItemPickupEvent`, the inventory sweep, and the craft/smelt handlers. | A single buggy integration turns every item pickup into a thrown exception — the opposite of failing closed. Forge's `consumerMainThread` wrapper also discards the handler future, so a throw inside a packet handler is silent. | `impl/AdapterRegistry.java:82-86`; bytecode of `lambda$consumerMainThread$2` shows the `CompletableFuture` is `pop`ped. | Register an adapter whose `identify` throws; pick up any item. | Wrap each `identify` call in `try/catch (Throwable)`, log once per adapter, and continue with the next adapter. | S | Low | Unit test: adapter that throws must not prevent a later adapter from matching. |
| RT-06 | Configuration / reload | P1 | CONFIRMED | `extraBookItemIds`, `absorbUnknownBooks`, `bookKeywords`, `bookBlocklist`, `bookBlocklistMods` are read once in `ModIntegrations.setupAll()` and captured into adapter instances. `ModConfigEvent` only rebuilds `AbsorptionPolicy`. | Editing those five options and reloading the config appears to work (the file changes, no error) but has no effect until a full restart — while the two `absorbExclusion*` options sitting next to them *do* apply live. Silently inconsistent. | `ModIntegrations.registerHeuristic/registerConfigBooks`; `RunicTome.onConfigLoadOrReload` only calls `AbsorptionPolicy.rebuildFromConfig()`. | Set `absorbUnknownBooks = false`, `/reload`-equivalent config reload, observe the heuristic still absorbing. | Rebuild the config-derived adapter set on every config load/reload, replacing the previous set wholesale (same mechanism as datapack adapters). | M | Med | Unit test on the registry's config-adapter replacement. |
| RT-07 | Networking | P1 | CONFIRMED | `SyncBookDefsPacket.decode` does `new ArrayList<>(buf.readVarInt())` before reading any element. | A hostile or corrupted server can make the client allocate a ~2 billion-entry array list and OOM before any read fails. | `network/SyncBookDefsPacket.java:30-31`. | Send a `SyncBookDefsPacket` whose leading VarInt is `Integer.MAX_VALUE`. | Validate the count against a sane maximum and against remaining readable bytes; never pre-size from an untrusted value. | S | Low | Unit test decoding a hostile buffer. |
| RT-08 | Client state | P1 | CONFIRMED | `ClientDataCache` holds a static `RunicTomeData` that is never cleared on logout, world change, or disconnect. | After leaving a world the previous library persists in memory; between joining a new world and the login `SyncDataPacket` arriving, the tome UI can display and act on another world's books. | `client/ClientDataCache.java` — only `acceptSync`/`acceptUnlock` mutate it; no `LoggingOut` hook anywhere in `src/main/java`. | Join world A, absorb books, quit to menu, join world B, open the tome before the sync lands. | Clear the cache on `ClientPlayerNetworkEvent.LoggingOut`. | S | Low | Unit test on an explicit `clear()`. |
| RT-09 | Commands / recovery | P1 | CONFIRMED | `AbsorptionPolicy.findExcluded` and `purgeExcluded` have **no callers**. `CHANGELOG [0.5.1]` advertises "Policy maintenance helpers … can find and purge already-unlocked entries that now match a global exclusion". | A player who absorbed a functional book before it was added to the exclusion list has no in-game recovery path; the documented feature is unreachable without save editing. | `grep -rn "purgeExcluded\|findExcluded"` returns only the definitions and a test of `isExcludedId`. | Try to invoke it. There is no command. | Expose it as `/runictome purge` with a dry-run listing subcommand. | S | Low | Unit test on `purgeExcluded` semantics. |
| RT-11 | API surface | P2 | CONFIRMED | `ImcHandler.StaticAdapter.enumerateAll()` returns the live internal `ConcurrentHashMap.keySet()`; `displayIcon(key)` returns the stored `ItemStack` without copying. | An API consumer or the UI can mutate the adapter's internal state; icon NBT can be altered in place. | `impl/ImcHandler.java:120-127`. | Call `enumerateAll().clear()` from any consumer. | Return an immutable copy and a defensive `ItemStack.copy()`. | S | Low | Unit test. |
| RT-12 | Extraction | P2 | CONFIRMED | `BookExtraction.materialize`'s last-resort fallback does `ForgeRegistries.ITEMS.getValue(key.bookId())` — treating a book id as an item id. For `runictome:patchouli` keys `bookId` is a Patchouli book id, not an item. | A legacy key-only Patchouli entry can materialize an unrelated item that merely shares the id, with no warning. Combined with RT-02 it can also hand back an empty stack reported as "no physical item". | `impl/BookExtraction.java:106-107`. | Legacy save with a `runictome:patchouli` entry whose id collides with an item. | Only use the registry fallback when the id is actually registered, and log a warning identifying the key when it is used. | S | Low | Unit test on the fallback path. |
| RT-13 | API surface | P2 | CONFIRMED | `RunicTomeData.getBooks()` / `getFavorites()` return `Collections.unmodifiableCollection` **views** of the live `LinkedHashSet`s. | A consumer iterating `getUnlockedBooks(player)` while the server absorbs a book gets a `ConcurrentModificationException`; the view also silently changes under the caller. | `capability/RunicTomeData.java:57-59, 74-77`. | Iterate `getBooks()` and unlock during iteration. | Return an immutable snapshot (`List.copyOf`). | S | Low | Unit test: mutate during iteration of the returned collection. |
| RT-14 | Policy | P2 | CONFIRMED | `AbsorptionPolicy.findExcluded` matches `key.bookId()` against the *item* exclusion sets only. Correct for item-identity systems (`heuristic`, `tagged`, `config`, `datapack`, `ItemBasedAdapter`), meaningless for `runictome:patchouli`, and it ignores the `#runictome:absorb_blocklist` tag that `evaluate()` honours. | A purge cannot remove an excluded Patchouli entry, and reports "nothing matches" for an item a packmaker hard-blocked by tag. | `impl/AbsorptionPolicy.java:86-92` vs `evaluate` at `:48-64`. | Purge a library containing a Patchouli entry, or one blocked only by tag. | Match the retained source stack through the full `evaluate()` gate as well as the book id, and report *why* each entry matched. Because many entries can share one retained item (every generic Patchouli book retains `patchouli:guide_book`), the purge command must show the list before removing anything. | S | Low | Unit tests on both signals and on the reason reported. |
| RT-15 | Documentation | P2 | CONFIRMED | README inaccuracies: (a) `bookBlocklistMods` example lists `rpglore`, which is not a default in `RunicTomeConfig`; (b) "Automatic absorption — and never destructive" understates that absorbing a stack of N identical books destroys N−1 items and re-absorbing a duplicate destroys it outright; (c) "hopper pushes" is cited as a sweep justification although hoppers cannot insert into a player inventory; (d) extraction exemption is described without stating that it is permanent and irreversible. | Packmakers copy a non-existent default; players can lose duplicate books they expected to keep; the extraction contract is ambiguous. | `README.md:63`, `:18`, `:92`, `:21` vs `RunicTomeConfig.java:118`, `AbsorptionHandler`, `ServerTickHandler.scanContainer`, `AbsorptionPolicy.EXTRACTED_MARKER`. | Read both. | Correct the README; state the duplicate/stack policy and the permanence of the extraction marker explicitly. | S | None | Re-read. |
| RT-16 | Release hygiene | P2 | CONFIRMED | `.gitignore` ignores `build`, `.gradle`, `.tooling`, `logs`, `run*` but **not** loose `*.jar` at the repository root nor `*.zip`. The tree currently holds `runictome-0.5.0.jar`, `runictome-0.5.1.jar` and a 607 MB modpack archive as untracked-but-not-ignored files. | One `git add -A` commits a 607 MB binary and two dev jars. | `.gitignore` (34 lines) vs `git status --short`. | `git add -A --dry-run`. | Ignore root-level `*.jar` and `*.zip`. | S | Low | `git status --short` and `git check-ignore`. |
| RT-17 | Build reproducibility | P2 | CONFIRMED | `build.gradle` writes `Implementation-Timestamp: new Date()` into the jar manifest at configuration time. | Byte-identical sources produce different jars; also makes the `jar` task's inputs non-cacheable in principle. | `build.gradle:114`. | Build twice, diff manifests. | Derive the timestamp from `SOURCE_DATE_EPOCH` when present, or drop the attribute. | S | Low | Not verifiable here (Gradle BLOCKED) — left as a recommendation, not applied. |
| RT-18 | Packaging | P2 | CONFIRMED | The optional Patchouli dependency in `mods.toml` is commented out, so no `ordering="AFTER"` is declared. | `PatchouliGuideAdapter.tryInit()` may run before Patchouli finishes setup; `BookRegistry.books` can be empty at prewarm. Self-heals on the first datapack sync, so it is latent rather than fatal. | `src/main/resources/META-INF/mods.toml:24-29`. | Load order dependent. | Uncomment the optional dependency block. | S | Low | Not verifiable here (needs a Forge launch). |
| RT-19 | Localization | P3 | CONFIRMED | `runictome.locked`, `gui.runictome.open`, `gui.runictome.close` exist in `en_us.json` with no referencing code. | Dead strings mislead translators. | `en_us.json` vs `grep Component.translatable`. | Compare. | Remove or wire up. | S | None | Key-vs-usage diff. |
| RT-21 | Logging | P3 | CONFIRMED | `AbsorptionHandler.logUnrecognizedBookLike` does `size() >= MAX` then `add()` on a concurrent set — a benign race can exceed the cap slightly. | None in practice; the set is still bounded in the same order of magnitude. | `event/AbsorptionHandler.java:87-88`. | Concurrent pickups. | Bound with a counter or accept and document. | S | None | n/a |
| RT-25 | Capabilities | P2 | CONFIRMED | `CapabilityEvents.onAttachCapabilities` calls `event.getObject().getCapability(...)` on the entity **during** the attach event to test for a duplicate. | Relies on the provider map being partially built; fragile against Forge internals and other mods attaching in the same event. | `event/CapabilityEvents.java:26`. | Not reproducible without a Forge launch. | Test `event.getCapabilities().containsKey(RunicTomeDataProvider.IDENTIFIER)` instead. | S | Low | Source-level only. |
| RT-01 | Absorption | P2 | CONFIRMED | Absorption destroys the whole stack: pickup discards the entire `ItemEntity`, the sweep clears the whole slot, craft/smelt sets the whole output count to 0 — while only one copy is retained. `ALREADY_HAD` also consumes. | Absorbing a stack of 8 identical manuals yields exactly one extractable copy; the other 7 are gone permanently. This is consistent with the mod's dedup premise but is nowhere documented, and the README claims absorption is "never destructive". | `AbsorptionHandler.onItemPickup:50`, `handleCreation:74`, `ServerTickHandler.scanContainer:67`; `RunicTomeData.unlockBook` normalizes the retained copy to count 1. | Drop 8 stacked guide books, pick them up, extract — one comes back. | **Not changed.** Behaviour is intentional to the design; documented precisely instead, and pinned by a regression test. Making it configurable is listed as a future enhancement. | — | — | Regression test pinning current semantics. |
| RT-10 | Networking / abuse | P2 | SUSPECTED | `ToggleFavoritePacket` triggers `CapabilityEvents.syncTo`, which re-serializes the **entire** library (including every retained `ItemStack` and its NBT) and sends it. There is no rate limit on any C→S packet. | A client can spam favorite toggles and force repeated full-library serialization + transmission. With a large library this is a meaningful per-client amplification. | `ToggleFavoritePacket:34-37` → `CapabilityEvents.syncTo` → `RunicTomeData.serializeNBT`. | Not measured — no server available. | Add an incremental favorite packet and/or a per-player cooldown. Requires a protocol bump. | M | Med | **Recommendation only** — not implemented. |
| RT-20 | Commands | P2 | CONFIRMED | All `/runictome` subcommands use `getPlayerOrException()`: console cannot run any of them, there is no target-player argument, `list` is unpaginated, and all output is `Component.literal` (unlocalized). | Server operators cannot inspect or repair another player's library, and a large library floods chat. | `command/RunicTomeCommand.java` throughout. | `/runictome list` from console. | Add optional `EntityArgument.player`, paginate, localize. | M | Med | **Recommendation only** — beyond the audit's safety scope. |
| RT-22 | Performance | P3 | SUSPECTED | `ServerTickHandler.onServerTick` sweeps **every** online player on the same tick. | On a large server the sweep cost lands in one tick instead of spreading across the interval. | `event/ServerTickHandler.java:29-32`. | Not measured. | Stagger by `player.getId() % interval`. | S | Low | **Recommendation only** — no measurement available. |
| RT-23 | Configuration | P2 | CONFIRMED | `showUnlockToast` is a purely client-side preference living in the COMMON spec. | On a dedicated server the option in `runictome-common.toml` on the *server* has no effect on clients; each player must edit their own common config. Confusing, and the wrong Forge config type. | `RunicTomeConfig.java:140-141`, consumed only in `ClientDataCache.acceptUnlock`. | Toggle it server-side; nothing changes for players. | Move to a CLIENT spec. | S | Med | **Recommendation only** — moving it silently resets every existing user's setting; needs a deprecation plan. |

### Investigated and eliminated (NOT AN ISSUE)

| Claim checked | Verdict |
|---|---|
| Packet handlers might run off the main thread or forget `setPacketHandled` | **Eliminated.** Bytecode of `SimpleChannel$MessageBuilder.lambda$consumerMainThread$2` shows `enqueueWork(...)` + `setPacketHandled(true)`. `OpenBookPacket`'s extra `enqueueWork` is redundant, not incorrect. |
| Duplicate/replayed `ExtractBookPacket` could duplicate an item | **Eliminated.** Handlers are serialized on the server thread; the second request fails the `hasBook` check. Delivery precedes `lockBook`, and `lockBook` returning `false` is logged rather than silently ignored. |
| Extraction could mutate the retained capability stack | **Eliminated.** `getBookStack` returns a copy; covered by `BookExtractionTest`. |
| An empty `bookKeywords` entry would match every item | **Eliminated.** `HeuristicBookAdapter.classify` and `matchedKeyword` both skip blank keywords. |
| `setDatapackAdapters` could remove config/IMC/built-in adapters | **Eliminated.** Removal is scoped to the `datapack/` systemId path prefix; config adapters use `config/`. |
| A corrupt `BookKey` or `ItemStack` in saved NBT could abort capability loading | **Eliminated.** `BookKey.fromNbt` returns `Optional.empty()` for blank/malformed ids (tested); vanilla `ItemStack.of` catches its own `RuntimeException`s. |
| Favorites could survive a locked book, or point at unowned books | **Eliminated.** `lockBook` removes the favorite; `deserializeNBT` filters favorites through `books::contains`; `toggleFavorite` rejects unowned keys. |
| `keepInventory` could double-grant the tome | **Eliminated.** `LivingDropsEvent` does not fire for players when `keepInventory` is on, so nothing is stashed and nothing is re-granted. |
| `AdapterRegistry`'s snapshot could be read while being mutated | **Eliminated.** Writes are `synchronized` and publish an immutable `List.copyOf` to a `volatile` field. |
| `absorbExclusionItems`/`Mods` might replace rather than union the built-in safety set | **Eliminated.** `AbsorptionPolicy.rebuild` seeds from `DEFAULT_*` then adds config values; covered by `AbsorptionPolicyTest`. |
| Malformed config ids could crash startup | **Eliminated.** `parseItems`/`parseNamespaces` use `ResourceLocation.tryParse`/`tryBuild` and log-and-skip. |
| The tome could be lost on death | **Eliminated.** `SoulboundHandler` stashes the count in the capability; `onPlayerClone` drains it and drops on a full inventory. |

### Blocked

| Area | Why |
|---|---|
| `gradlew compileJava / test / check / build`, `reobfJar`, `processResources` token expansion | No network to Forge/Maven/Gradle plugin portal; device VM has no JDK 17. |
| Any in-game behaviour (absorption, extraction, UI, soulbound, first join, dedicated server) | No Minecraft client or server available in this environment. |
| Patchouli reflection against a real Patchouli jar (method/field presence, `BookRegistry` shape, version range) | Patchouli is not a project dependency and is not present in the caches; the modpack archive lists CurseForge project/file IDs only. |
| Completeness of the Midnight Apocalypse exclusion set | `manifest.json` confirms **261 files** and Forge `47.4.12` for MC `1.20.1`, matching the README's "261 exact manifest files". Mod ids cannot be resolved from project IDs offline. `overrides/config/` filenames confirm that `goetyawaken`, `runicskills`, `legendary_additions`, `alexscaves`, `customnpcs`, `uniqueaccessories`, `celestisynth`, `dacxirons`, `sgjourney`, `jsg`, `goety`, `irons_spellbooks`, `patchouli`, `modonomicon` and `mca` are present; absence of a config file does not prove a mod is absent. |
| Tinkers' Construct, MCA, Immersive Engineering, Modonomicon integration behaviour | Mods unavailable. |
| Performance claims (RT-10, RT-22) | No server to measure. |

---

## 5. Prioritized roadmap

> **Status update — 0.6.0.** Everything in tiers 3 and 4 below, and every enhancement in section 13
> except items 5's follow-ons already covered, was implemented in the follow-up pass that shipped as
> `0.6.0`. See `CHANGELOG.md [0.6.0]`. Two things in this report are now out of date: the network
> protocol moved from `4` to `5` (RT-10 required a new packet id), and `gradlew build` is **no longer
> blocked** — it was run successfully on the development machine, including `processResources` token
> expansion and `reobfJar`, with 123/123 tests passing. The jar was also verified byte-identical
> across a clean rebuild, which settles RT-17. Tier 5 (in-game and integration verification) remains
> outstanding and is still the real risk.

**1 — Fix immediately (done in this pass):** RT-02, RT-03, RT-04, RT-05.
**2 — Implement during this pass (done):** RT-06, RT-07, RT-08, RT-09, RT-11, RT-12, RT-13, RT-14, RT-15, RT-16, RT-18, RT-19, RT-21, RT-25, plus documentation of RT-01.
**3 — Schedule for a later patch:** RT-17 (reproducible manifest timestamp — cannot be verified here), RT-22 (sweep staggering, wants measurement first).
**4 — Requires a product/design decision:** RT-01 (make duplicate/stack absorption configurable), RT-10 (incremental favorite packet; needs a protocol bump), RT-20 (command UX: target player, pagination, localization), RT-23 (move `showUnlockToast` to a CLIENT spec; needs a migration plan).
**5 — Blocked by unavailable integration or runtime:** all in-game verification, Patchouli version-range verification, modpack exclusion-set completeness.
**6 — Rejected / not reproducible:** the eliminated claims in the table above.

---

## 6. Implemented changes, grouped by root cause

### A. The defaulted item registry (RT-02) — and everything downstream of it

`ForgeRegistries.ITEMS` has a default value, so `getValue(unregisteredId)` yields `minecraft:air`.
A new `impl/ItemRefs` resolves through `containsKey` first and is now the single way the mod turns
an id into an `Item` or an `ItemStack`. All nine dead `== null` guards were converted. `ItemRefs`
deliberately still resolves `minecraft:air` when that id is asked for explicitly, because it *is*
registered — the fix must not turn a legitimate air lookup into "unregistered".

Consequences fixed by the same change: `extraBookItemIds` typos and unknown datapack `item` fields
are logged and skipped instead of registering an adapter named "Air"; orphaned library entries fall
back to their book id instead of rendering as "Air"; `runictome.unknown_item` can now actually
appear; and adapters hand `ItemStack.EMPTY` — never a stack of air — to the use simulator, which
rejects it.

### B. Mutating shared global state (RT-03)

`UseSimulator` now refuses an empty stack before it copies anything, because `ItemStack.copy()`
returns the `ItemStack.EMPTY` **singleton** for an empty stack and the next two lines wrote the
virtual marker into it. `isVirtual` also short-circuits on empty stacks.

Restoration remains **unconditional**. An earlier revision of this change tried to be polite —
keeping whatever a foreign `use()` had put in the slot and re-homing the player's original stack
elsewhere. Adversarial review caught that this mints a free item on every open for any book whose
`use()` does `player.setItemInHand(hand, new ItemStack(x))`, which is a common "transform on use"
pattern, and `openServer` runs on every open from the tome UI. Discarding the foreign replacement
keeps the simulation net-zero by construction; the replacement is logged instead. **Never trade a
duplication exploit for politeness.**

### C. Server-side open replaying the wrong item (RT-04)

`PatchouliGuideAdapter` overrides both `openServer` overloads to no-ops. A Patchouli `BookKey`
carries a book id, not an item id; the inherited default resolved it against the item registry and
activated whatever item happened to share the name. The default itself was also hardened to resolve
through `ItemRefs` and bail on an empty result.

### D. Failing open instead of closed (RT-05)

`AdapterRegistry.identify` isolates every adapter call in `try/catch (Throwable)`, logs the first
failure per adapter id, and continues down the priority list. The policy evaluation itself is
wrapped too: if the safety gate cannot be evaluated, the item is **not** claimed.

### E. Config options that silently required a restart (RT-06)

`ModIntegrations.applyConfigAdapters()` builds the `extraBookItemIds` adapters plus the keyword
catch-all and swaps the whole set in through `AdapterRegistry.setConfigAdapters`. It runs from
`setupAll()` and again on every `ModConfigEvent`, guarded by a `ready` flag so it cannot run before
common setup (the item registry may not be populated, and `setupAll` performs the first build).

Two hazards found in review shaped the final form:

- **Ownership is namespace-scoped.** The replacement predicate matches only ids in the `runictome`
  namespace. Third-party and datapack systemIds are unrestricted, so `somemod:config/guides` and
  `somemod:heuristic` are legal ids — a namespace-blind path match would have deleted them on the
  next config reload, permanently, since IMC runs once per launch.
- **Precedence is stable across reloads.** The registry records a first-registration sequence per
  systemId and sorts by `(-priority, sequence)`. Remove-and-reinsert would otherwise move config
  adapters behind everything registered after common setup, so a contested item would start
  identifying as a different `BookKey` after the first config touch and be absorbed again as a
  duplicate entry.

The new map is also built completely before being published, so an adapter whose `systemId()` throws
cannot leave the registry half-mutated.

### F. Untrusted decode sizing (RT-07)

`SyncBookDefsPacket` bounds the definition count (`MAX_DEFS = 4096`) and the optional display name
(`MAX_NAME_LENGTH = 256`), validates before allocating, and pre-sizes to `min(size, 64)` so a
truncated payload fails on read rather than on allocation. The encoder clamps symmetrically and logs
what it dropped rather than truncating silently.

### G. Client state leaking across worlds (RT-08)

`ClientLifecycleHandler` clears `ClientDataCache` on `ClientPlayerNetworkEvent.LoggingOut`.

### H. Unreachable recovery (RT-09, RT-12, RT-14)

`/runictome purge` lists matching entries with the reason each matched; `/runictome purge confirm`
removes exactly the listed set — not a freshly recomputed one — so the operator confirms what they
were shown. Matching uses the book id *and* the retained source stack evaluated through the full
`evaluate()` gate, so the `#runictome:absorb_blocklist` tag counts; `EXTRACTED` and `VIRTUAL` are
explicitly excluded as purge reasons. The dry run caps output at 30 entries and warns that many
entries can share one retained item. `BookExtraction.materialize`'s registry fallback now requires a
genuinely registered item and logs which key triggered it.

### I. Mutable internal state escaping (RT-11, RT-13)

`getBooks()`/`getFavorites()` return `List.copyOf` snapshots; `RunicTomeData.bookCount()` was added
so the tome screen's per-frame header does not allocate one. The IMC static adapter copies icons on
the way in and out and returns a snapshot of its book list. `PatchouliGuideAdapter.displayIcon`
copies the stack Patchouli owns.

### J. Smaller corrections

`CapabilityEvents` checks the attach event's own gathered providers instead of querying the entity
mid-gather (RT-25). The unrecognized-book log cap reserves its slot atomically (RT-21).
`AdapterRegistry.unregisterAdapter` was added so a single adapter can be removed (previously only
wholesale prefix replacement existed). Documentation and packaging: README corrections and the
duplicate/extraction policy statements (RT-15, RT-01), root-level `*.jar`/`*.zip` ignored (RT-16),
optional Patchouli dependency declared with an accurate comment about what `ordering="AFTER"` does
and does not guarantee (RT-18), three dead translation keys removed (RT-19).

---

## 7. Files changed and why

| File | Why |
|---|---|
| `impl/ItemRefs.java` **(new)** | Single safe registry resolver; the fix for RT-02. |
| `impl/UseSimulator.java` | Reject empty stacks before mutating (RT-03); unconditional restore with logging; `isVirtual` empty-safe. |
| `impl/AdapterRegistry.java` | Per-adapter exception isolation (RT-05); namespace-scoped, atomic, order-stable `setConfigAdapters`/`setDatapackAdapters`; `unregisterAdapter`. |
| `impl/AbsorptionPolicy.java` | `findExcluded` returns `ExcludedEntry(key, reason)` matched by book id and by retained stack through `evaluate()`; `purge(data, entries)` added (RT-09/RT-14). |
| `impl/BookExtraction.java` | Registry fallback requires a registered item and logs the key (RT-02/RT-12). |
| `impl/ImcHandler.java` | Defensive copies in/out of the static adapter (RT-11). |
| `api/GuideSystemAdapter.java` | Default `openServer` resolves through `ItemRefs` and bails on empty (RT-02/RT-03). |
| `integration/patchouli/PatchouliGuideAdapter.java` | `openServer` no-ops (RT-04); `displayIcon` copies and logs failures. |
| `integration/ItemBasedAdapter.java`, `HeuristicBookAdapter.java`, `TaggedGuideBookAdapter.java` | Resolve through `ItemRefs`; surface `runictome.unknown_item` instead of silently doing nothing (RT-02). |
| `integration/ModIntegrations.java` | `applyConfigAdapters()` + `isReady()`; blank keywords/namespaces filtered; invalid `bookBlocklist` ids logged (RT-06). |
| `RunicTome.java` | Rebuild config-derived adapters on config load/reload, guarded and exception-safe (RT-06). |
| `network/SyncBookDefsPacket.java` | Bounded count and name; validate before allocating (RT-07). |
| `client/ClientLifecycleHandler.java` **(new)** | Clear the client cache on logout (RT-08). |
| `client/ClientDataCache.java` | `clear()`, `size()`; stop double-wrapping an already-immutable snapshot (RT-08/RT-13). |
| `client/screen/RunicTomeScreen.java` | Use `ClientDataCache.size()` in `render()` instead of copying the library every frame. |
| `capability/RunicTomeData.java` | Immutable snapshots, `clear()`, `bookCount()` (RT-13). |
| `event/CapabilityEvents.java` | Attach check via the event's gathered providers (RT-25). |
| `event/AbsorptionHandler.java` | Atomic reservation for the log cap (RT-21). |
| `command/RunicTomeCommand.java` | `/runictome purge [confirm]` with reasons, bounded preview, and confirm-what-was-shown semantics (RT-09). |
| `resources/assets/runictome/lang/en_us.json` | Removed 3 dead keys; added `runictome.open_no_item` (RT-19). |
| `resources/META-INF/mods.toml` | Optional `patchouli` dependency, `ordering="AFTER"` (RT-18). |
| `.gitignore` | Ignore root-level `*.jar` and `*.zip` (RT-16). |
| `README.md` | Duplicate/stack policy, extraction permanence, config-reload semantics, `purge`, `rpglore` and hopper corrections (RT-15/RT-01). |
| `CHANGELOG.md` | `[Unreleased]` entry. |
| `RUNIC-TOME-AUDIT.md` **(new)** | This report. |

**Not changed on purpose:** `gradle.properties` (`mod_version` left at `0.5.2` — cutting a release
is the maintainer's call; the changelog entry is filed under `[Unreleased]`), `build.gradle`,
`RunicTomeConfig.java`, `RunicTomeNetwork.java` (protocol stays `4`), `RunicTomeAPI.java` (API stays
`2`), and every file the working tree already had uncommitted work in beyond the edits listed above.

---

## 8. Tests added or updated

43 new tests across 6 new classes; no existing test was modified or deleted.

| Class | Covers |
|---|---|
| `ItemRefsTest` (6) | Pins that `getValue` returns AIR for unknown ids (the platform behaviour the dead guards assumed away); `resolve`/`exists`/`stackOf`/`openerFor`; explicit `minecraft:air` still resolves. |
| `UseSimulatorSafetyTest` (5) | Pins that `ItemStack.EMPTY.copy()` is the singleton; empty/air/null rejected; the adapter opener path cannot reach the simulator with an empty stack; `ItemStack.EMPTY.getTag()` stays `null`. Guarded by a `@BeforeEach` that fails loudly if an earlier class polluted the singleton. |
| `SyncBookDefsPacketTest` (7) | Round trip; `Integer.MAX_VALUE`, over-limit and negative counts rejected; truncated payload fails on read; encoder clamping; over-long name truncation. |
| `AdapterRegistryIsolationTest` (8) | A throwing adapter is skipped, not fatal; failing alone yields no match (fail closed); wholesale config replacement; heuristic removal; foreign-namespace adapters survive a reload; precedence stable across a reload; `unregisterAdapter`. Registers and removes every id it touches. |
| `LibraryRecoveryTest` (11) | Purge by book id, by retained stack, by namespace, with reasons; idempotence; confirm-what-was-shown; `EXTRACTED`/`VIRTUAL` are not purge reasons; snapshot collections safe to iterate while mutating and not writable; `clear()`; `materialize` on an unregistered id and with a retained stack. |
| `AbsorptionSemanticsTest` (6) | Pins the deduplicating contract: one retained copy regardless of source count, `ALREADY_HAD`/`ADDED` authorize consumption, `FAILED` never does, first variant wins on re-acquire, extraction exemption survives a copy, virtual stacks are never absorbed. |

The two classes that mutate process-wide static state (`AbsorptionPolicy`'s exclusion sets, the
`AdapterRegistry` singleton) reset in `@BeforeEach` **and** `@AfterEach`, because Gradle runs every
test class in one JVM.

---

## 9. Final verification commands and outcomes

```
$ ./verify.sh
== compileJava ==
compileJava exit=0 (49 sources)
== compileTestJava ==
compileTestJava exit=0 (12 sources)
== test ==
RESULT tests=73 succeeded=73 failed=0 aborted=0 skipped=0
```

Baseline before the changes was 30/30 (matching the Gradle run recorded in `build/test-results`);
after, 73/73. `verify.sh` compiles with `javac --release 17` against the exact ForgeGradle
dependency set from `.tooling/gradle-home` and runs JUnit 5 through the platform launcher.

Two executable probes were used as evidence and are reproduced as assertions in
`ItemRefsTest`/`UseSimulatorSafetyTest`:

```
PROBE getValue(nonexistentmod:nonexistent_book) = air     containsKey = false
PROBE ItemStack.EMPTY.copy() == ItemStack.EMPTY           = true
PROBE after one simulateUse on an empty stack:
      ItemStack.EMPTY.getTag() = {"runictome:virtual":1b}
```

`SimpleChannel$MessageBuilder.lambda$consumerMainThread$2` was disassembled with `javap -c` to
confirm the wrapper performs `enqueueWork(...)` + `setPacketHandled(true)` (and discards the
returned future).

**Still not run:** `gradlew compileJava|test|check|build`, `reobfJar`, `processResources` token
expansion, jar packaging, and any Minecraft runtime. Run `./gradlew build` on the development
machine before releasing.

---

## 10. Save / config / network / API compatibility

| Surface | Status |
|---|---|
| Capability NBT (`books`, per-entry `stack`, `favorites`, `receivedTome`, `stashedTomes`) | **Unchanged.** No new keys, no removed keys, no reinterpretation. Legacy key-only entries still load and are still backfilled on re-acquisition. |
| Config file `runictome-common.toml` | **Unchanged schema.** No option added, removed, renamed or re-defaulted. Existing files load as-is; the built-in safety exclusions are still unioned with whatever the file contains. |
| Datapack format `data/<ns>/runictome/books/*.json` | **Unchanged.** Definitions naming an unregistered item are now *skipped with a warning* instead of registering a dead adapter — a behaviour fix, not a format change. |
| Item tags `#runictome:guide_books`, `#runictome:absorb_blocklist` | **Unchanged.** |
| Network protocol | **Stays `4`.** No packet was added, removed, or had its wire layout changed. `SyncBookDefsPacket` gained bounds checks; a conforming payload encodes and decodes byte-identically. A 4096-definition or 256-character ceiling is new, but no real pack approaches either. |
| Public API version | **Stays `2`.** No signature in `com.otectus.runictome.api` changed. `GuideSystemAdapter.openServer`'s *default body* is more conservative; overriding implementations are unaffected. `unregisterAdapter` was added to `impl.AdapterRegistry`, not to `RunicTomeAPI.Delegate`, so no implementor breaks. |
| Behavioural change visible to integrators | `IRunicTomeData.getBooks()`/`getFavorites()` return immutable snapshots rather than live views. Callers that iterated them keep working; a caller that relied on the view updating live would not. Nothing in this repository did. |

**No migration is required.** A 0.5.2 save, config and datapack load unchanged.

---

## 11. Manual verification checklist

None of the following was performed — no Minecraft runtime was available. Work through it on the
development machine before releasing; every item maps to a change or a finding above.

- [ ] `./gradlew build` succeeds; `runClient` and `runServer` start.
- [ ] Fresh singleplayer world: tome granted on first join; absorb a Patchouli book; open it from the tome — **exactly one** GUI opens and no duplicate sound/stat fires (RT-04).
- [ ] Existing 0.1–0.5 save loads; library intact; a legacy key-only entry is backfilled by re-acquiring the book.
- [ ] Dedicated server + client: login sync, absorb, open, extract, favorite all work; `/reload` re-syncs datapack books.
- [ ] Death with `keepInventory` **off** and **on**: exactly one tome returns, none duplicated.
- [ ] Full inventory on first join, and full inventory during extraction: item drops at the player's feet, library entry removed exactly once.
- [ ] Extract → drop → pick back up: the copy is **not** re-absorbed (marker), before and after a relog.
- [ ] Patchouli absent: no crash, `runictome.patchouli_missing` shown. Patchouli present: both generic `patchouli:guide_book` NBT books and custom-item books (`dont_generate_book`) are recognised.
- [ ] Datapack add / modify / remove + `/reload`: adapters appear and disappear; a definition naming a **non-existent item** logs "is not registered, skipping" and registers nothing (RT-02).
- [ ] **Config reload without restarting:** add and then remove an `extraBookItemIds` entry; flip `absorbUnknownBooks`; edit `bookKeywords`. Each takes effect immediately, and `/runictome debug dump` reflects it (RT-06).
- [ ] With a third-party/IMC adapter installed, touch the config file and confirm via `debug dump` that the foreign adapter is **still registered** and still wins for its item (RT-06 review findings).
- [ ] `/runictome purge` on a library with an entry that is now excluded: the dry run lists it with a reason; `purge confirm` removes only that entry (RT-09).
- [ ] Large library (100+ books): UI scrolls, searches and renders without a frame-time regression; the count header is correct.
- [ ] Quit to menu and join a **different** world: the tome does not briefly show the previous world's books (RT-08).
- [ ] Malformed saved data (hand-edit a `books` entry to garbage): the rest of the library still loads.
- [ ] Repeated/duplicate client packets (spam Extract and Favorite): no duplication, no crash.
- [ ] Functional books from the Midnight Apocalypse exclusion set remain physical and keep working (XP deposit/withdraw on `runicskills:leveling_book` in particular).
- [ ] Open a book whose `use()` replaces the held item; confirm the log warns about the replacement and the player's own stack is intact with **no extra item gained** (RT-03 / review finding).

---

## 12. Remaining risks and blocked integrations

- **No runtime validation whatsoever.** Everything here is source tracing, executable probes against
  the real Forge classpath, and unit tests. Behaviour that only manifests in a live client or
  dedicated server — event ordering with other mods, GUI behaviour, load order, capability timing —
  is unverified.
- **`ModConfigEvent` runs on Forge's config-watcher thread.** `applyConfigAdapters()` therefore
  touches the item registry off the server thread. `AdapterRegistry` mutation is synchronized and
  publishes an immutable snapshot, and the rebuild is build-then-swap, so a reader never sees a torn
  state; but `AbsorptionPolicy.rebuildFromConfig()` and the adapter rebuild are not atomic *with
  respect to each other*, so an absorption in the gap can see new exclusion lists against the old
  heuristic snapshot. The window is microseconds and both states are individually safe. Making the
  whole rebuild a single server-thread task would close it properly.
- **The duplicate/stack absorption policy is now documented, not changed.** Absorbing a stack of N
  identical books still destroys N−1 of them. If that is not the intended contract, it needs a
  config option and a design decision, not a patch.
- **`ToggleFavoritePacket` still re-serializes the whole library per toggle** (RT-10) and there is no
  rate limiting on any client→server packet. Fixing it properly needs an incremental packet and a
  protocol bump.
- **`showUnlockToast` is still in the COMMON spec** (RT-23). Moving it to a CLIENT spec would
  silently reset every existing user's setting.
- **Jar manifests still embed a build timestamp** (RT-17), so builds are not reproducible. Not
  changed because it could not be verified here.
- **Blocked:** Patchouli reflection against a real Patchouli jar (supported version range unverified
  — `mods.toml` now declares `[1.20.1,)`, which is an assertion, not a tested claim); Tinkers, MCA,
  Immersive Engineering and Modonomicon behaviour; completeness of the Midnight Apocalypse exclusion
  set (`manifest.json` confirms 261 files and Forge 47.4.12, but CurseForge project IDs cannot be
  resolved to mod ids offline); all performance claims.
- **Working-tree note:** a temporary directory `.tooling/rt-audit-tmp/` was created on disk to move
  the ForgeGradle dependency jars into the audit environment. `.tooling` is gitignored, so it cannot
  reach a commit, but it holds ~115 MB and can be deleted.

---

## 13. Recommended future enhancements (not corrections)

Clearly separated from the confirmed defects above; none of these is a bug.

1. **Make duplicate absorption configurable** — `absorbWholeStack` (default `true`, current
   behaviour) vs. absorbing one and leaving the rest. Addresses the only remaining item-loss
   surprise (RT-01).
2. **Incremental favorite sync + client→server rate limiting** (RT-10). Needs a protocol bump.
3. **Command UX** (RT-20): optional target-player argument so operators can inspect and repair
   another player's library from console, pagination for `list`, and localized output.
4. **Move `showUnlockToast` to a CLIENT spec** with a deprecation cycle (RT-23).
5. **A dry-run / diagnostic mode for packmakers** — e.g. `/runictome debug scan` reporting every
   registered item the current configuration *would* absorb, so a pack can be audited before players
   lose a functional book rather than after. This is the highest-value safety feature the mod does
   not have.
6. **Stagger the inventory sweep across the interval** by player id (RT-22), after measuring.
7. **Reproducible jar manifests** via `SOURCE_DATE_EPOCH` (RT-17).
8. **CI**: there is no workflow in the repository. A `gradlew build` on push would have caught none
   of these findings, but it would keep the 73-test suite honest.
9. **Consider whether the extraction marker should be clearable** by an operator command, so a book
   extracted by mistake can be re-absorbed (currently permanent by design, now documented as such).
