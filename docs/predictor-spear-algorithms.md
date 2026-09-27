---
name: predictor-spear-algorithms
description: Evaluate and design Minecraft Elytra spear position predictors using delayed server observations, fixed-pitch new4 diagonal ascent trajectories, the 0.6x1.8x0.6 player box, and ray-through-box attack success. Use when comparing predictor methods or history lengths for spear targeting, choosing predictorPos aim heights, or tuning PositionPredict and ElytraBot spear behavior.
---

# Predictor Spear Algorithms

Use the spear attack objective as the primary metric: a prediction is successful when a ray from our eye position toward `predictorPos + aimY` intersects the target's real future player box. Do not rank methods only by Euclidean position error.

## Geometry Contract

Model a normal player as:

- width `0.6`, height `1.8`, depth `0.6`;
- predicted and actual positions are the bottom-center position;
- actual box is `[x-0.3, x+0.3] x [y, y+1.8] x [z-0.3, z+0.3]`.

At each client prediction update:

1. Predict the future bottom-center position.
2. Build `aim = predictedPosition + (0, aimY, 0)`.
3. Build the forward spear ray from our eye position through `aim`.
4. Test ray-AABB intersection against the target's real box at the attack horizon.
5. Count the sample as a spear success if the forward ray intersects the box.

Generic geometry checks may evaluate `aimY=0.9` and `aimY=1.62`. The corrected
client/server experiment requested here uses `aimY=0.6`, exactly one third of
the 1.8-block player height.

This benchmark tests geometric target acquisition only. The default ray range is deliberately large so it measures direction rather than reach. Unless explicitly added to the simulator, do not claim that it models block occlusion, spear reach, attack cooldown, client rotation interpolation, or packet loss.

## Server and Client Observation Model

Keep server and client clocks separate. The server ground truth is `P[serverTick]`
and advances every server tick. The server publishes only:

```text
P[0], P[2], P[4], ...
```

A packet sent at `serverTick` arrives at the first integer client update at or
after `serverTick + latencyTicks`. The client evaluates on every client tick
`N`, using only packets whose receive tick is at most `N`. The actual position
for that evaluation is `P[N]`, not `P[serverTick + ticksLater]`.

Match `PredictorImpl`'s step semantics:

```text
futureSteps = clientTick - latestReceiveTick + ticksLater
```

Here `ticksLater=1` compensates the one-tick phase in the supplied example:
server `0 ->` client `1`, server `2 ->` client `3`. It is not an additional
future attack lead. `ticksLater>=2` predicts ahead of the current real position.

For a phase-aligned two-tick packet schedule and integer client updates,
latency `0.5` and `1.0` tick can map to the same receive ticks. Report them
separately, but do not claim that this discrete model distinguishes sub-tick
arrival times.

History lengths `1..20` are evaluated as requested. `history=1` can become a
duplicated latest packet under the Java-compatible window fill, producing a
zero-motion baseline. Keep it in complete tables, but use `history>=2` when
selecting a real velocity predictor.

## Trajectory Contract

Correct the meaning of "accelerating upward": it is not a vertical-only trajectory. Use a fixed negative Minecraft pitch so the player moves diagonally forward and upward.

The repository's new4 model is:

- keep pitch and yaw fixed for the entire trajectory;
- calculate Elytra gliding velocity from the previous velocity;
- construct the axis-limit bounds from the current and previous look;
- follow the horizontal axis bounds toward the look direction;
- take the positive Y boundary for upward motion;
- advance the ground truth every tick;
- publish only every `syncInterval` ticks, normally `2`.

Use a fixed pitch per benchmark run, not a changing pitch curve. The default spear benchmark is:

- `pitch=-45`, `yaw=45`;
- initial total speeds `3`, `4`, and `5 b/t`;
- `syncInterval=2`;
- prediction horizon `2` ticks;
- history lengths `1..20`.

The initial speed only seeds the first new4 recurrence. It does not mean that the target remains at 3-5 b/t for the whole 120-tick simulation; report the trajectory speed range separately when changing the simulator.

## Evaluation Workflow

Use `scripts/run_predictor_spear_client_server_experiment.py` for the corrected
dual-clock benchmark. The older `run_predictor_spear_experiment.py` is a
same-clock historical comparison and must not be used for final conclusions.

Example:

```powershell
python scripts/run_predictor_spear_client_server_experiment.py
```

Before changing the evaluator or predictor selection policy, review this document's Geometry Contract, Trajectory Contract, and Evaluation Workflow.

Compare, in this order:

- spear box hit rate;
- worst-case and 95th-percentile miss behavior;
- mean vertical error;
- mean Euclidean error.

For spear targeting, a lower Euclidean error is not sufficient if the prediction ray still misses the box.

## Historical Same-Clock Results

The following results are retained for comparison only. They used a server
observation as if it were immediately visible to the client, so they are not
valid for the requested server/client model.

The evaluator was run with three initial speeds, 120 ticks, two-tick synchronization, two-tick prediction horizon, and histories `1..10`.

At the representative `pitch=-45`, `yaw=45` trajectory:

- With the forward-ray definition, `QUADRATIC` was usable from history `3`; for `aimY=0.9` it peaked at `17.2%` at history `5`, while histories `3-10` stayed around `15-17%`.
- `NV` was unstable at histories `2-3` and then plateaued around `15%` for `aimY=0.9`; with `aimY=1.62`, it plateaued around `16.7%`.
- `LINEAR` degraded as the history grew: for `aimY=0.9` it fell from `17.2%` at history `1-2` to `1.1%` at history `10`.
- `ROTATIONAL` had zero hits for `aimY=0.9` and only `8-10.6%` for `aimY=1.62`; it should not be selected for this fixed-direction diagonal ascent.
- `aimY=0.9` and `aimY=1.62` change hit rates near the box boundary, but neither changes the basic predictor ranking.

The absolute rates are low because this benchmark uses a target distance of `24` blocks and a two-tick future ray direction; they are useful for comparing methods under the same geometry, not as a direct in-game hit probability. Across fixed-pitch runs `-30`, `-45`, and `-60`, no single method/history won every geometry. Use short-window `QUADRATIC` as the acceleration candidate, `NV` as the longer-window fallback, and calibrate target distance and ray range before making gameplay claims.

## Selection Guidance

Start with this corrected spear-oriented policy:

1. For current-position compensation in this model, use `LINEAR/history=2`.
2. For one extra tick of lead, use `NV/history=3`.
3. Reject any candidate when its vertical acceleration or one-step residual is implausible.
4. Treat longer-horizon `ROTATIONAL` results as geometry-specific fallbacks, not as a general acceleration predictor.
5. Keep history=1 only as an explicit zero-motion fallback; do not use its raw hit-rate win as evidence of motion prediction.
6. When multiple aim heights are evaluated, prefer the one with the larger box-intersection margin, not a hard-coded height.


## Corrected Client-Server Results

The corrected experiment is stored under
`scripts/predictor_spear_client_server_experiment/`. It uses 80 server ticks,
`yaw=5*N`, per-yaw new4 optimal pitch, sync interval 2, latency `0.5` and
`1.0` tick, history `1..20`, ticksLater `1..8`, initial speeds `3/4/5 b/t`,
the `0.6 x 1.8 x 0.6` box, and aim offset `0.6`.

The two latency values have identical integer receive schedules in this
phase-aligned benchmark. Across both latency values:

- Raw complete-table winner: `NV/history=1/ticksLater=1`, `28.80%` hit rate.
- Recommended non-degenerate current-position predictor:
  `LINEAR/history=2/ticksLater=1`, `27.89%` hit rate.
- Recommended non-degenerate one-extra-tick predictor:
  `NV/history=3/ticksLater=2`, `24.61%` hit rate.
- For ticksLater `3..6`, `ROTATIONAL` gives the highest remaining box-hit
  rate in this fixed-pitch scan, but its position error is already large and it
  is not a general acceleration model.

Axis analysis is stored in
`scripts/predictor_spear_client_server_experiment/axis_error_analysis_report.md`.
The dominant error is Y: for `NV/history=3/ticksLater=2`, mean absolute Y
error is about `2.46` blocks versus `1.00` block horizontal error; for
`ROTATIONAL/history=20/ticksLater=3`, it is about `2.57` versus `0.99`.
However, replacing Y with raw recent acceleration makes results worse:
`NV_ACCEL_Y` falls to `21.83%` from `24.61%`, and direct rotational
acceleration falls to about `0.42%` at ticksLater 2. The viable direction is
base-specific Y bias correction or a bounded NV/ROTATIONAL Y blend, not an
unbounded acceleration term.

The raw history-1 result is a zero-motion baseline and should not be used as
the live spear predictor without a guard. Use
`best_by_latency_horizon_practical.csv` for the history>=2 selection table.

## Historical Full Yaw/Parameter Scan Result

The older complete same-clock experiment is stored under
`scripts/predictor_spear_experiment/`. Its numeric conclusions are historical
only; use the corrected directory above for implementation decisions.

- The per-yaw new4 pitch is symmetric every 90 degrees. It is approximately `-60°` on axis directions, `-45°` at diagonal directions such as `45°`, and interpolates between them according to the yaw-dependent horizontal axis constraint.
- If all horizons are treated equally, the highest hit rate is about `19.20%` at `ticksLater=1`. The best tie-break result is `ROTATIONAL`, history `20`, but this is not a sufficient reason to use rotational prediction for multi-tick spear attacks.
- For the operational two-tick horizon, `LINEAR`, history `2`, is best: hit rate about `17.99%`, mean vertical error `0.2064 b`.
- For `ticksLater=3..8`, `QUADRATIC`, history `4`, is the best hit-rate combination in this fixed-pitch new4 scan. Hit rate falls from about `16.13%` at horizon 3 to `9.16%` at horizon 8.
- The corrected one-tick result is the current-position compensation case.
  For a live spear mode, start with `LINEAR/history=2` and use `NV/history=3`
  for a one-extra-tick lead. Treat longer-horizon results as geometry-specific
  fallback data and add residual/acceleration guards before using them.

The experiment report and complete tables are in `scripts/predictor_spear_experiment/experiment_report.md`, `predictor_results_all.csv`, `predictor_results_aggregate.csv`, `best_by_horizon.csv`, and `best_by_yaw.csv`.
