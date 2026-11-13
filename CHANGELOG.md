# Changelog

## v0.1.1 — Windows package

The v0.1.0 tag is unchanged. Its Actions runs never started: the workflow title was invalid YAML because of an unquoted colon, so GitHub rejected the file before any job.

- Quote the release title so the workflow loads.
- Build a Windows `jpackage` app-image and fail the job unless `EM3.exe` and `runtime/bin/java.exe` are present.
- Publish that zip on the release for the tag that was pushed.

## v0.1.0 — Foundation and Salvo Preview

- Docking editor with hierarchy, scene, inspector, animation, JSON, console, and assets.
- Salvo preset from Mindustry v160.7 `Blocks.salvo`: mirrored side plates and recoil barrel.
- Scene placement follows `RegionPart` and `DrawTurret` math, with radius-4 outlines.
- Warmup, reload, recoil, charge, and heat preview controls.
- EM3 project save and open, separate from exported content JSON.
- Official sprite download and folder import. Game artwork is not committed.
- Headless tests for transforms, mirroring, progress, outlines, export, and project round-trip.
- Windows `jpackage` app-image workflow.
