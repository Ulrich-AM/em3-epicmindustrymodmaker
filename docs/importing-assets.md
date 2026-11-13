# Importing Salvo sprites

EM3 does not include Mindustry's artwork. Until the PNGs are linked, the scene shows a missing-assets message and the parts have no pixels.

The files for Mindustry **v160.7** are:

- `core/assets-raw/sprites/blocks/turrets/salvo/salvo-preview.png`
- `core/assets-raw/sprites/blocks/turrets/salvo/salvo-barrel.png`
- `core/assets-raw/sprites/blocks/turrets/salvo/salvo-barrel-heat.png`
- `core/assets-raw/sprites/blocks/turrets/salvo/salvo-side-l.png`
- `core/assets-raw/sprites/blocks/turrets/salvo/salvo-side-r.png`
- `core/assets-raw/sprites/blocks/turrets/bases/block-2.png`

Left and right side images are different files. EM3 does not mirror one image to invent the other.

## Download inside EM3

Tools → **Download official Salvo sprites**.

EM3 copies those six files from the `v160.7` tag into `~/.em3/cache/mindustry-v160.7/` and links them to the open project. Save the project to copy them into that project's `assets/` folder.

## Import a checkout or install

Tools → **Import sprites from folder...** and choose either:

- a clone of `https://github.com/Anuken/Mindustry` checked out at tag `v160.7`, or
- any directory that contains those PNG file names.

The scan prefers paths under `turrets/salvo/` and `turrets/bases/`.

## After saving

`project.json` stores paths such as `assets/salvo-barrel.png`. Reopening the project uses those relative paths. A missing file stays in the map, is listed in Assets and the console, and is skipped in the scene.
