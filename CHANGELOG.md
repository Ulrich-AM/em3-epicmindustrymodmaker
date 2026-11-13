# Changelog

## v0.2.0 — animation editor and interactive testing

- Window title is `epic mindustry mod maker (em3)`. Interface chrome is lowercase. The UI font is Arial when the operating system provides it, otherwise Liberation Sans, DejaVu Sans, or the Dear ImGui default. No proprietary font file is packaged.
- The docking theme is a warmer charcoal. Docking, resizing, and saved layouts remain.
- Ground shadow is separate from outlines, colored sprites, heat, and editor debug graphics. Show, opacity, world x/y offset, and isolation are editor-only and start from DrawTurret's elevation offset and `Pal.shadow` alpha 0.22. The elevation offset is not rotated with aim.
- Official v160.7 Salvo sprites load from the app folder or the local cache when a project has no sprite map. Existing project paths are left alone. The PNGs are GPL-3.0 and are not committed.
- Per-part keyframe tracks for x, y, rotation, scale x, and scale y on warmup, recoil, charge, and reload. Linear interpolation exports. Step interpolation is preview-only and is reported instead of being approximated.
- Salvo's warmup and recoil moves are also stored as keys. Opening and saving keeps the original `PartMove` objects.
- Move gizmo (`g`) and rotate gizmo (`r`), with optional snapping, escape to cancel, and one undo step per drag.
- Test mode aims and fires the same parts the scene draws. Fire rate, turn speed, warmup, cooldown, repeat, loop, time scale, pause, reset, and freeze are editor-only. The driver is not `TurretBuild.update`.
- Supported keys export as `moveX` / `moveY` / `moveRot` or additive `PartMove` values, including `compress` for segments. Unsupported animation is explained in the json panel.
- Undo and redo cover part transforms, keyframe edits, and gizmo drags.

## v0.1.3 — Windows package

v0.1.2 reached `jpackage` and produced `EM3.exe`. The job then stopped because this image has no `runtime/bin/java.exe`. The packager now treats `runtime/bin/server/jvm.dll` as the bundled runtime when the `java` launcher was not copied into the image.

## v0.1.2 — Windows package

v0.1.0 was not moved. Its workflow file was invalid YAML, so Actions never started a job. v0.1.1 fixed that file and the jobs started. The Windows package step then exited 1 without printing a jpackage error, because Git Bash was launching `jpackage.exe`. This tag runs the packager from PowerShell and checks that `EM3.exe` and `runtime/bin/java.exe` are Windows executables before the zip is uploaded.

## v0.1.1 — Workflow file

- Quote the release title so GitHub accepts the workflow.

## v0.1.0 — Foundation and Salvo Preview

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
