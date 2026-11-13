# Known limitations — v0.1.0

- The viewport does not boot Arc or call `DrawTurret.draw`. It evaluates the v160.7 formulas in EM3's own renderer. See the README for the lookup-table and bleed differences.
- The playback curve is not `TurretBuild.update`. Reload uses DrawTurret's convention: 1 means just fired.
- There is no keyframe timeline, curve editor, `ShapePart` or `HaloPart` authoring, ammo editor, combat sandbox, or mod packager.
- Only the Salvo turret is preset. Other content types have folders reserved in the project and no editors yet.
- Whole-turret `salvo-outline` is not drawn. Mindustry generates that from an atlas region named `salvo`, and the raw tree has no `salvo.png`. Part outlines are generated from the region canvases.
- Shadow uses the preview sprite tinted with `Pal.shadow`. It does not call `Drawf.shadow`.
- Full 64×64 canvases are drawn. v160.7 `pack.json` strips whitespace toward the center but does not crop X/Y, and `SpriteBatch.draw` does not apply atlas offsets. A packed atlas from a built game can still differ if a later pack setting changes.
- Heat is drawn when `{name}-heat` is loaded. Salvo's side plates have no heat texture. The barrel does.
- imgui-java 1.86.12 publishes natives for Linux, Windows, and macOS x64. It does not publish a macOS arm64 binary. LWJGL itself includes an Apple Silicon classifier. On Apple Silicon, run an x64 JDK or the editor's ImGui natives will not load.
- Workspace layout is Dear ImGui's ini file in `~/.em3/workspace.ini`. Panels dock and resize inside the primary window. Extra OS windows for multi-monitor docking are not enabled.
- The docking layout was rendered under llvmpipe at 1024×720, 1366×768, and 1920×1080. That is a Linux software GL smoke test, not a physical monitor and not Windows. Long inspectors scroll inside their panels. The default split is about 18% hierarchy, 30% inspector, and 36% bottom tabs.
- The Windows package is produced by GitHub Actions. It was not launched on Windows from the Linux environment used to write this version.
- Downloading sprites needs network access to `raw.githubusercontent.com`.
