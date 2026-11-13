# Known limitations — v0.2.0

- The viewport does not boot Arc or call `DrawTurret.draw`. It evaluates the v160.7 formulas in EM3's own renderer. Sprite corners use `Math.cos` / `Math.sin` instead of Arc's degree lookup table. `PartProgress` sine uses `Math.sin`. Nearest filtering skips the linear-filter bleed pass.
- Test mode and the shot playback curve are not `TurretBuild.update`. Reload still uses DrawTurret's convention: 1 means just fired. Freeze pauses that preview. It does not stop the game, because no game is running.
- The ground shadow uses the preview sprite, black at alpha 0.22, shifted by `-elevation` on world x and world y, then rotated by aim minus 90 degrees. That matches `Drawf.shadow`'s call from `DrawTurret` for Salvo, whose block recoil is 0. In v0.1.3 the shadow was hidden together with the base, and on the dark viewport a 0.22 black sprite is easy to miss beside the radius-4 outline. Those are now separate. If a screenshot shows a different defect than this, it was not reproduced here.
- Step interpolation and a lone constant key are not written as Mindustry moves. The json panel says so. There is no scale gizmo. Heat does not move parts.
- Easing beyond linear and step is not offered. Mindustry `compress` is linear, and unsupported curves are not approximated on export.
- Only the Salvo turret is preset. Other content types have folders reserved in the project and no editors yet.
- Whole-turret `salvo-outline` is not drawn. Mindustry generates that from an atlas region named `salvo`, and the raw tree has no `salvo.png`. Part outlines are generated from the region canvases.
- Full 64×64 canvases are drawn. v160.7 `pack.json` strips whitespace toward the center but does not crop X/Y, and `SpriteBatch.draw` does not apply atlas offsets.
- imgui-java 1.86.12 publishes natives for Linux, Windows, and macOS x64. It does not publish a macOS arm64 binary. On Apple Silicon, run an x64 JDK.
- Workspace layout is Dear ImGui's ini file in `~/.em3/workspace.ini`. Panels dock inside the primary window. Extra OS windows for multi-monitor docking are not enabled. v0.2.0 uses a new dock id, so an older layout is not reused.
- The Windows package is produced by GitHub Actions. Launching `EM3.exe` was not done from the Linux environment used to write this version.
- Downloading sprites needs network access to `raw.githubusercontent.com`. The git repository does not contain the GPL artwork or a copy of Arial.
