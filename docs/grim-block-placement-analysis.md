---
name: grim-block-placement-analysis
description: Analyze Grim-compatible Minecraft block placement and block-interaction checks, including rotation placement, air/liquid placement, position placement, fabricated hit results, cursor bounds, interaction validity, and hit-point adjustments for replaceable blocks such as slabs. Use when implementing or reviewing placement raycasts, ItemPlacementContext.canReplaceExisting(), BlockHitResult fabrication, InteractUtils.canInteractAndPlace or isInteractAcceptable style validation, or anti-cheat-compatible block interaction modules.
---

# Grim Block Placement Analysis

Use this skill when a placement helper must distinguish the geometric checks used by Grim instead of treating every valid `BlockHitResult` as equivalent. Read the local merged Minecraft source skill as well when the implementation depends on vanilla `canReplace` or `getPlacementState`.

The interaction-module validation guidance is included in the `Interaction Module Validation` section below.

## Placement Checks

Keep these checks separate:

- `rotationPlace`: the view ray only needs to intersect the interaction block's bounding box. The fabricated hit point can be aimed at; the player's body does not need to be on a particular side.
- `airLiquidPlace`: the interaction position must be supported by a block that is neither air nor liquid. Check the block at `interactPos` before accepting the placement.
- `positionPlace`: the player must be on the opposite side of the interaction face from the target placement point. For a target point `hitPos`, interaction direction `direction`, and eye position `eyePos`, use:

  ```java
  Vec3d view = eyePos.subtract(hitPos);
  Vec3d normal = Vec3d.of(direction.getVector());
  boolean valid = enablePositionPlace || view.dotProduct(normal) < 0.0D;
  ```

- `fabricatedHitResult`: after selecting the target face and hit point, preserve the target block position, face, hit coordinates, and `insideBlock` flag consistently. A fabricated result still has to satisfy vanilla placement context rules and Grim cursor bounds.

Do not use the air/liquid check as a substitute for the position check. A rotation placement can be geometrically valid while a position placement is invalid, and a position-valid placement can still fail because the supporting block is air or fluid.

## Grim Fabricated Cursor

The supplied Grim `onBlockPlace` check validates local cursor coordinates in the placed-against block's coordinate system:

- `cursor == null`: return without checking.
- For ordinary shapes, use `maxBound = 1.0` and `minBound = 0.0`.
- For shapes extending beyond a cube, and lecterns, use `maxBound = 1.5` and `minBound = -0.5`.
- Reject any coordinate below `minBound - MAX_DOUBLE_ERROR`.
- Reject any coordinate above `maxBound + FLOAT_STEP_AT_ONE`.
- The lower-bound tolerance accounts for double arithmetic noise near zero.
- The upper-bound tolerance is one float step near `1.0`; it is larger than the double arithmetic error in Minecraft coordinates.

This is a cursor-local-coordinate check, not a line-of-sight check and not a replacement check. A cursor passing these bounds does not imply that `ItemPlacementContext.canReplaceExisting()` is true.

## Vanilla Replacement Context

For direct replacement candidates:

1. Build a `BlockHitResult` for the target block and candidate face.
2. Build an `ItemPlacementContext` with the actual block item stack.
3. Check `context.canReplaceExisting()` before view, support, or predicted-state checks.
4. Use the same hit point when predicting `getPlacementState`; changing it after context construction can invalidate the earlier replacement decision.

`ItemPlacementContext` computes `canReplaceExisting` by calling the hit block state's `canReplace`. If false, `context.getBlockPos()` becomes the offset position; if true, it remains the hit block position.

## Slab Hit-Point Rule

In Yarn 1.21.11 `SlabBlock.canReplace`:

- A bottom slab accepts same-item horizontal replacement only when `hitY > 0.5`.
- A top slab accepts same-item horizontal replacement only when `hitY <= 0.5`.
- A bottom slab also accepts `Direction.UP`.
- A top slab also accepts `Direction.DOWN`.
- A double slab cannot be replaced by another slab.

Therefore, for a horizontal fabricated hit against a same-block slab:

- bottom slab: use a local hit `Y` around `0.75`;
- top slab: use a local hit `Y` around `0.25`;
- preserve the candidate face and target block position.

The adjustment is valid because it changes only the cursor/hit point within the target block. It does not bypass vanilla placement logic; `ItemPlacementContext.canReplaceExisting()` remains authoritative. Keep the point inside Grim's ordinary `[0, 1]` cursor bounds unless the actual target shape requires an extended bound.

## Workflow

1. Read `ItemPlacementContext`, `BlockItem`, and the target block's `canReplace` plus `getPlacementState`.
2. Enumerate candidate faces in preferred order, then all six directions.
3. Construct the candidate hit result and context before view/support checks.
4. Reject candidates failing `canReplaceExisting`.
5. Apply rotation, air/liquid, and position checks independently.
6. Predict the placed state using the same hit result and retain only the requested target state.
7. Preserve `insideBlock` consistently when constructing the final fabricated result.
8. When using hit-point nudges, document which vanilla predicate depends on that coordinate and keep the nudge within legitimate cursor bounds.

## Local Source

Use the local merged Yarn source tree recorded by `minecraft-merged-source-locator`, especially:

- `net/minecraft/item/ItemPlacementContext.java`
- `net/minecraft/item/BlockItem.java`
- `net/minecraft/block/AbstractBlock.java`
- `net/minecraft/block/SlabBlock.java`
- `net/minecraft/block/SnowBlock.java`

## Interaction Module Validation

Use this reference when writing or reviewing a block interaction or placement module that fabricates hit results, predicts supporting faces, or has to remain compatible with Grim-style checks.

## Validation Order

Keep these checks separate and run them in order:

1. Confirm the target action is meaningful.
   For placement, prove the target block is replaceable or that the intended state is reachable through a legal placement context.
   For direct block interaction, prove the target block can actually consume the current item or empty-hand interaction.
2. Build the exact `BlockHitResult`.
   Preserve block position, side, local cursor point, and `insideBlock`.
   Do not change the hit point after later checks have started to depend on it.
3. Validate support geometry.
   For support-based placement, use `InteractionTasks.getPlaceSupportingResult(...)` or equivalent logic to prove the supporting block is non-air, non-liquid unless air place is intentional, and position checks still pass.
4. Validate sneak-state compatibility.
   Treat `FlagEntry.flag()` as "this action requires cancelling the normal block interaction".
   Only accept the result when `InteractUtils.canInteractAndPlace(player, result)` is true.
5. Validate item or block semantics.
   Use `InteractUtils.isInteractAcceptable(...)` for direct block interaction.
   Use `ItemPlacementContext.canReplaceExisting()` and then predicted `getPlacementState(...)` for placement.
6. Execute with the same validated result.
   Call `InteractionTasks.handlePlaceMode(...)` or the direct interact helper using the exact validated hit result.

Do not collapse all of these into a single boolean named "can interact". The failure reason matters because sneak, replaceability, support, and semantics fail for different reasons.

## Placement Module Pattern

For a correct placement module, keep the responsibilities split like this:

1. Candidate search:
   Generate candidate `BlockPos` values in gameplay order, usually nearest or highest priority first.
2. Suitability filter:
   Check world-state constraints such as replaceable state, collision, predicted target state, and any module-specific scoring like explosion damage.
3. Supporting-hit construction:
   Call `InteractionTasks.getPlaceSupportingResult(...)` or a stricter helper to build the final support-side `BlockHitResult`.
4. Interaction validation:
   Reject null results and reject any result failing `InteractUtils.canInteractAndPlace(...)`.
5. Execution:
   Swap inventory only after a candidate has passed validation.
   Place using `InteractionTasks.handlePlaceMode(...)`.
6. State update:
   Update local caches only after the place call has been issued.

This separation keeps search logic reusable and prevents modules from mutating inventory or local state before proving that the interaction is geometrically and semantically valid.

## Direct Interaction Module Pattern

For non-placement modules, validate in this order:

1. Identify the block or entity the module wants to use.
2. Prove the current stack or empty hand can produce a meaningful interaction.
   For blocks, use `InteractUtils.isInteractAcceptable(world, player, pos, state, stack)`.
   For entities, use the corresponding entity interaction helpers.
3. Build a hit result that matches the intended face and cursor.
4. If the interaction shares the same support/sneak constraints as placement, keep using `FlagEntry` and `canInteractAndPlace`.
5. Execute using the project interaction wrapper instead of bypassing it.

## Common Failure Modes

- Recomputing a different `BlockHitResult` after validation, which invalidates earlier `canReplaceExisting` or cursor assumptions.
- Using `canInteractAndPlace` as if it proved replaceability. It only proves sneak-state compatibility for the already chosen result.
- Using `getPlaceSupportingResult` as if it proved the target position is placeable. It only proves a support-side interaction candidate exists.
- Swapping inventory before the module has found a validated candidate.
- Updating local block caches before the interaction packet/path has actually been issued.
