# Animation editor and test mode

The animation panel edits per-part tracks. Progress is normalized from 0 to 1. Each part has its own tracks. Warmup, recoil, charge, and reload are separate channels. Heat stays a visual overlay.

## Tracks

Supported properties:

- position x
- position y
- rotation
- scale x
- scale y

Position and rotation are offsets added to the part's base transform. Scale is an offset added to the part scale and exports as `gx` or `gy`.

Salvo's existing warmup and recoil moves are loaded as linear keys from 0 to 1. The original `moveX`, `moveY`, `moveRot`, and `PartMove` values stay in the project. While a track exists for a property, the preview uses the track so the motion is not applied twice.

## Timeline

Select a part, then a channel. The rows show keyframe diamonds and a playhead at that channel's current value.

- `add key` inserts keys for x, y, and rotation at the playhead
- click a diamond to select it
- drag a diamond to change its time
- `delete key` removes the selection
- `copy` and `paste` copy the selected key, or the whole property if none is selected
- the time and value fields edit the selected key numerically
- `play`, `pause`, `loop`, `reverse`, and `speed` drive the shot preview
- the channel slider scrubs that channel and pauses the shot curve

Interpolation is linear. `step` holds the previous value until the next key. Step cannot be represented by Mindustry's `compress` operation, so the json output reports an error and leaves that segment out.

## Auto-key

`auto-key` is shown next to the playback controls.

- on: changing x, y, or rotation in the inspector or with a gizmo writes a key on the active channel at the playhead
- off: those edits change the part's base transform

## Gizmos

`g` selects move. `r` selects rotate. The handles sit on the selected part.

- dragging an axis moves that part in its local x or y
- dragging the ring changes rotation
- turret aim and mirrored sides are included in the conversion
- a drag does not pan the view
- escape cancels the drag
- one drag is one undo step
- `snap` uses 1 world unit and 15 degrees

`ctrl+z` undoes. `ctrl+y` and `ctrl+shift+z` redo.

## Test mode

`test` or `f5` aims the turret at the cursor while the scene is hovered. The turret stays at the center of the world. Left click fires. Holding the button repeats at the fire rate, from 0.5 to 15 shots per second. Escape leaves test mode and keeps animation edits.

The test panel is editor-only. It does not change the exported reload, firing pattern, or rotation speed. Freeze keeps the current rotation, warmup, recoil, reload, heat, charge, and part transforms so they can be inspected. This driver is not `TurretBuild.update`.

## Export

A straight 0-to-1 linear track on the part's own progress channel is written back as `moveX`, `moveY`, or `moveRot`. The same shape on another channel becomes one `PartMove`. Several keys become additive `PartMove` entries whose progress is `compress` from the previous key time to the next. A single constant key is kept in the EM3 project and reported in the output panel, because it is not a `PartMove`.

Editor shadow controls, test settings, and undo history are not exported.
