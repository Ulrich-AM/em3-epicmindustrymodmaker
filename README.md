# EM3 — Epic Mindustry Mod Maker

EM3 v0.1.0 is a desktop editor for inspecting and editing a Mindustry turret, starting with Salvo from Mindustry **v160.7**.

The window is a dark docking workspace: hierarchy, scene, inspector, animation, JSON output, console, and assets. Panels can be dragged, split, and saved with Dear ImGui's layout file. Docking stays inside the primary window. imgui-java's extra platform windows are not enabled, because they submitted no geometry to the main viewport in the Linux smoke test.

## Architecture

EM3 is one Gradle project. Packages split the work:

| Package | Role |
| --- | --- |
| `em3.app` | Startup, session, overlays, log |
| `em3.ui` | Docking workspace and panels |
| `em3.viewport` | OpenGL scene framebuffer |
| `em3.mindustry` | Pinned v160.7 constants and official asset URLs |
| `em3.animation` | `PartParams`, progress evaluation, sprite placement |
| `em3.project` | EM3 project files, separate from generated content JSON |
| `em3.assets` | PNG loading and outline generation |
| `em3.export` | Mindustry content JSON |
| `em3.presets` | Salvo |

### Rendering and Arc

Mindustry draws through Arc. Arc's `Lwjgl3Application` creates the GLFW window, the OpenGL context, and the frame loop. `Draw` and `SpriteBatch` also expect Arc's atlas, shaders, and `Core.batch`. imgui-java can attach to an existing GLFW window, but it cannot safely share that lifecycle with an Arc application. Arc is not published as a stable Maven artifact, and loading Mindustry plus a second LWJGL would put two native copies on the path.

EM3 therefore owns one GLFW 3.2 core context. Dear ImGui docks in that loop. The scene is rendered into a texture with nearest-neighbor filtering. Placement follows the v160.7 `RegionPart.draw` and `DrawTurret.draw` math that was read from source. **The viewport does not call `DrawTurret.draw`.**

Documented differences:

- Sprite corners use `Math.cos` / `Math.sin`. Arc's `Mathf.cosDeg` is a lookup table, so a corner can sit a fraction of a pixel away.
- `PartProgress` `sin` and `absin` use `Math.sin`. Arc uses the same kind of lookup table.
- Outlines use Arc's radius test (`rx*rx + ry*ry <= radius*radius`, default radius 4, color `404049`). Mindustry's bleed pass runs only when linear filtering is enabled. EM3 uses nearest filtering, so bleed is not applied.
- The animation curve is an editor preview. It is not `TurretBuild.update`.

Salvo has no raw `salvo.png`. The in-world colored pieces are `block-2`, `salvo-barrel`, the mirrored `salvo-side-r` / `salvo-side-l` plates, and `salvo-barrel-heat`. `salvo-preview` is the icon and the shadow (`Pal.shadow`, alpha 0.22), offset by elevation. It is not drawn again in color on top of the parts.

## Requirements

- JDK 17 or newer to build
- Windows, Linux, or macOS for the editor
- A Mindustry v160.7 source checkout or the in-app download, for the sprites

Artwork and fonts from Mindustry are not stored in this repository.

## Build

```bash
./gradlew test
./gradlew run
```

Pinned versions: Gradle 8.10.2, LWJGL 3.3.3, imgui-java 1.86.12 (docking branch of Dear ImGui 1.86), Jackson 2.18.2, JUnit 5.11.3.

## Sprites

See [docs/importing-assets.md](docs/importing-assets.md).

## Project files

Saving creates a folder with `project.json`, `assets/`, and directories for later content (`turrets/`, `sprites/`, `effects/`, `units/`, `factories/`, `export/`). Sprite paths inside `project.json` are relative to that folder. Unknown JSON fields on the turret and on parts are kept and written back out.

The JSON Output panel is a separate Mindustry content document. Editor-only values (`editorVisible`, hierarchy ids, overlay toggles) are not exported.

## Windows package

GitHub Actions builds an `app-image` with `jpackage` on `windows-latest` and attaches it to a release when a `v*` tag is pushed. This Linux workspace does not run that Windows package.

## License

EM3's own code is MIT. Mindustry and Arc are GPL-3.0. Their source is referenced, not copied into this tree.
