# epic mindustry mod maker (em3)

EM3 v0.2.0 is a desktop editor for building and previewing a Mindustry turret, starting with Salvo from Mindustry **v160.7**.

The window title is `epic mindustry mod maker (em3)`. Panel names and buttons are lowercase. The font is Arial when the system has it. Technical names, sprite filenames, and project JSON stay as written.

The window is a dark docking workspace: hierarchy, scene, inspector, animation, JSON output, console, and assets. Panels can be dragged, split, and saved with Dear ImGui's layout file. Docking stays inside the primary window. imgui-java's extra platform windows are not enabled, because they submitted no geometry to the main viewport in the Linux smoke test.

## Architecture

EM3 is one Gradle project. Packages split the work:

| Package | Role |
| --- | --- |
| `em3.app` | Startup, session, overlays, shadow debug, undo |
| `em3.ui` | Docking workspace, timeline, gizmos, test mode |
| `em3.viewport` | OpenGL scene framebuffer |
| `em3.mindustry` | Pinned v160.7 constants and official asset URLs |
| `em3.animation` | Channels, keyframes, progress, test preview, sprite placement |
| `em3.project` | EM3 project files, separate from generated content JSON |
| `em3.assets` | PNG loading, outline generation, Salvo sprite lookup |
| `em3.export` | Mindustry content JSON |
| `em3.presets` | Salvo |

### Rendering and Arc

Mindustry draws through Arc. Arc's `Lwjgl3Application` creates the GLFW window, the OpenGL context, and the frame loop. `Draw` and `SpriteBatch` also expect Arc's atlas, shaders, and `Core.batch`. imgui-java can attach to an existing GLFW window, but it cannot safely share that lifecycle with an Arc application. Arc is not published as a stable Maven artifact, and loading Mindustry plus a second LWJGL would put two native copies on the path.

EM3 therefore owns one GLFW 3.2 core context. Dear ImGui docks in that loop. The scene is rendered into a texture with nearest-neighbor filtering. Placement follows the v160.7 `RegionPart.draw` and `DrawTurret.draw` math that was read from source. **The viewport does not call `DrawTurret.draw`.**

Documented differences:

- Sprite corners use `Math.cos` / `Math.sin`. Arc's `Mathf.cosDeg` is a lookup table, so a corner can sit a fraction of a pixel away.
- `PartProgress` `sin` and `absin` use `Math.sin`. Arc uses the same kind of lookup table.
- Outlines use Arc's radius test (`rx*rx + ry*ry <= radius*radius`, default radius 4, color `404049`). Mindustry's bleed pass runs only when linear filtering is enabled. EM3 uses nearest filtering, so bleed is not applied.
- The shot curve and test mode are editor previews. Neither is `TurretBuild.update`.

Salvo has no raw `salvo.png`. The in-world colored pieces are `block-2`, `salvo-barrel`, the mirrored `salvo-side-r` / `salvo-side-l` plates, and `salvo-barrel-heat`. `salvo-preview` is the icon and the ground shadow (`Pal.shadow`, alpha 0.22), offset by elevation on world x and world y. Outlines are a separate radius-4 pass. The shadow is not drawn again in color on top of the parts.

## Animation and test mode

See [docs/animation-and-test.md](docs/animation-and-test.md).

The animation panel has one set of tracks per part and per channel (warmup, recoil, charge, reload). `g` moves the selection and `r` rotates it. `test` or `f5` aims the turret with the mouse and fires it at a preview rate. `ctrl+z` undoes an edit. Supported keys export as Mindustry `PartMove` values. Step keys are preview-only and produce an export error.

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

See [docs/importing-assets.md](docs/importing-assets.md) and [THIRD_PARTY_SPRITES.md](THIRD_PARTY_SPRITES.md).

A new project with an empty sprite map links the six official files from `sprites/salvo` beside the application, or from `~/.em3/cache/mindustry-v160.7`. A saved project keeps the paths it already has. The Windows zip downloads the pinned v160.7 PNGs into the app folder. They are GPL-3.0 and are not committed.

## Project files

Saving creates a folder with `project.json`, `assets/`, and directories for later content (`turrets/`, `sprites/`, `effects/`, `units/`, `factories/`, `export/`). Sprite paths inside `project.json` are relative to that folder. Unknown JSON fields on the turret and on parts are kept and written back out.

The JSON Output panel is a separate Mindustry content document. Editor-only values (`editorVisible`, hierarchy ids, overlay toggles) are not exported.

## Windows package

GitHub Actions builds an `app-image` with `jpackage` on `windows-latest` and attaches it to a release when a `v*` tag is pushed. This Linux workspace does not run that Windows package.

## License

EM3's own code is MIT. Mindustry and Arc are GPL-3.0. Their source is referenced, not copied into this tree.
