# Third-party sprites

The six Salvo images used by the preset come from Mindustry v160.7:

- `salvo-preview.png`
- `salvo-barrel.png`
- `salvo-barrel-heat.png`
- `salvo-side-l.png`
- `salvo-side-r.png`
- `block-2.png`

Source: https://github.com/Anuken/Mindustry/tree/v160.7

Mindustry is licensed under the GNU General Public License version 3. Those PNG files keep that license. They are not MIT. EM3's own code remains under the MIT license in `LICENSE`.

The images are not stored in the EM3 git repository. A Windows release downloads the pinned v160.7 files into `sprites/salvo` inside the application folder and includes this notice. A source checkout downloads them on first use into `~/.em3/cache/mindustry-v160.7`, or you can import a local Mindustry tree. EM3 does not replace sprite paths that a saved project already has.
