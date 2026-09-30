# Vector3 Feature Overview （AI Translated）

Vector3 is an extension of [Flashback](https://modrinth.com/mod/flashback). It lets you animate shapes, text, media and world clips with keyframes on the replay timeline, and adds a set of camera, post-processing and timeline-editing tools.

> Requires: Minecraft 26.3, Fabric Loader, Fabric API, Flashback, RyansRenderingKit
>
> Compatible with: Sodium, Iris

## Shapes

- **Shape track**: a new timeline track. Keyframes record a shape's position, rotation, scale, color, visibility and more, and values are interpolated between keyframes.
- **Shape types**:
  - Geometry: cuboid, sphere, disc, cylinder, cone.
  - Lines: line segment, polyline, arrow.
  - Game content: block (with configurable block properties), item, entity, OBJ model. An entity's "Entity" source can be a new entity or "Project a world entity", which redraws an entity that already exists in the replay, along with its movement and animation, at the shape's position, rotation and scale.
  - Effects: particle emitter, blast (see below).
  - Other: text, image, video (start time, manual/automatic playback, loop, optional audio).
- **Layers**: every shape (except particle emitters) can be placed on the "World" or "Screen (UI)" layer. Shapes on the screen layer are drawn flat above the world and post-processing effects but below fades. Coordinates are in 1080p pixels with the origin at the screen center. They are not affected by shaders, cannot mount entities, and cannot have a world-layer shape as their parent. When you switch layers, the shape moves to the screen center (or 5 blocks in front of the camera) and its size is converted automatically. The gizmo on the screen layer only offers X/Y movement and rotation around Z, Ctrl snaps to 10 pixels, and clicking prefers UI shapes.
- **Wireframe and appearance**: a wireframe can be overlaid, colored separately, or shown on its own. See-through display and glow outlines are supported.
- **OBJ textures, materials and caching**: OBJ models can be imported from files. The look can be a solid color, a single texture, or the model's own MTL materials (multiple materials, each with its own color, opacity and texture); the color property can still tint the whole model. If the MTL file is missing, it falls back to a solid-color material and the model still loads. Static meshes are cached on the GPU, so complex models don't resubmit every vertex each frame. With a shader pack, the shader pack does the shading (MTL and LabPBR normal and specular maps are supported), and you can also tick "Bypass Shaders".
- **OBJ lighting**: with "Lighting" ticked, each face is shaded according to its orientation (the same way vanilla entities are lit), so the model's volume is visible even without shaders, and lit faces still point up after the model is rotated. With shaders enabled, the shader pack handles lighting.
- **Parent-child hierarchy**: a shape can be attached under another shape and follows its parent's movement, rotation and scale. Changing the parent converts values automatically so the shape stays in the same place in the world. In the Shape Manager, drag a shape onto another shape to parent it, or onto empty space to unparent it; all keyframes are converted using the parent's pose at each keyframe's time.
- **Mounting on entities**: a shape can be mounted on an entity, using the feet, body or eyes as the mount point (same as Track Entity), optionally following the entity's facing; the shape's own transform is an offset from the mount point. You can also use "Model part" to mount on a part of the entity's model (for example a hand or the head), following its animation and pose keyframes; any entity with a model is supported, including the Ender Dragon. When the entity is absent, the shape and its child shapes are hidden. Mounting and a parent shape are mutually exclusive.
- **Text**: supports colors and styles in JSON or `§` format. Custom `.ttf`/`.otf` fonts can be used and are rendered crisply with distance fields; shadow, outline (adjustable width, interpolated by keyframes) and camera-facing modes are available.
- **Text transitions**: between two keyframes, only the part after the first difference is erased and retyped; identical text and formatting are kept. If the target keyframe has "Hold Text" ticked, the text simply switches as a whole. Each text keyframe can set a "Text In Animation" (how the text appears at that keyframe) and a "Text Out Animation" (how it disappears when leaving): typewriter, right to left, center outward, word by word, line by line, scramble decode.
- **Text glow**: text can have true bloom, and the outline can glow on its own; custom fonts and vanilla bitmap fonts are both supported. Intensity, spread and color are adjustable and interpolated by keyframes; the color follows the text by default.
- **File browsing**: path fields for OBJ, textures, images, videos, audio, LUTs, lens dirt and so on all have a "Browse..." button that opens the system file dialog.
- **Deleting cleans up**: when a shape's keyframes are deleted from all tracks, or its track is disabled, the shape is removed from the world.

## Particle Emitter

- **Emission over time**: emits the chosen vanilla particle from the emitter shape, driven by replay time: it emits during playback and export, and not while paused or while scrubbing the timeline. The emitter outline is only shown in the editor; rotate the emitter (R) to adjust its direction.
- **Modular settings** (similar to the Unity particle system):
  - Main module: start lifetime, start speed, start size (all with ranges), start color taken from "Color", use the particle's own motion, local simulation space, max particles.
  - Emission: rate over time, rate over distance, and bursts (count, start tick, interval, cycles).
  - Shape: cone, sphere, hemisphere, box, torus, line segment, with radius, radius thickness, angle, arc and randomized direction.
  - Forces: force, noise; color / size over lifetime; physics: override physics, gravity multiplier, drag, block collision.

## Area Shape (AreaShape)

- **Move a piece of the world**: bakes an area of the world into a single object that can be placed elsewhere and moved, rotated, scaled and faded. The blocks at the original location are hidden.
- **Full contents**: block entities follow along. Entities and particles inside the area can optionally be projected too; they transform with the area and become transparent with it.
- **Translucency**: transparency is written straight into vertex colors, so it works with shaders on. The area mesh is drawn in the vanilla main render pass, so its depth ordering against entities and particles is correct.
- **Live sync**: block changes inside the source area are re-baked automatically, without flashing the hidden blocks.
- **Parent-child hierarchy**: when an area is a child shape, the projection follows the parent's transform; the blue source-area box stays where it is. Area shapes can also go on the Screen (UI) layer, in which case only the baked copy is drawn and the source area stays in the world.

## Blast (BlastShape)

- **Scatter / gather**: like an area shape, it bakes a region of the world, then makes its blocks fly apart, or gather back from a scattered state to their original places. It plays automatically from "start tick + duration", or you can control it manually with a "Progress" keyframe.
- **Trajectory**: straight line, parabola, explosion, or a custom path (path points in local space); the lift height and outward bulge are adjustable.
- **Scatter and timing distribution**: scatter center, scatter radius, squash, seed. The order in which blocks depart can be by collision sweep (a sphere / cube sweep volume moving from start to end), from scatter point to condense point, random, bottom to top, top to bottom, and can be staggered.
- **Changes in flight**: rotation, clump size (chunks progressively break into smaller pieces in flight), opacity, size; clump shape can be block grid, noise perturbation, or Voronoi. Motion, rotation, opacity, size and clumps each have their own interpolation curve, which can be arranged separately with the speed curve editor or follow the motion curve.
- **Editing and rendering**: in Geometry (M) mode, the kinds of points currently in use (source area corners, path, sweep, scatter, origin) are shown as differently colored handles, and hovering shows what each is for. Lighting at the hidden source area is recalculated correctly; large numbers of blocks are rendered quickly with GPU skinning and work with shaders on.

## Entity Pose

- **Entity Pose track**: poses the model parts of an entity, blending smoothly between keyframes part by part. You can pick an entity already loaded in the world, or an entity shape. OBJ shapes can also be picked: each `o` / `g` group in the OBJ is a part, and notations like `g a b c` or `arm/hand` express parent-child relationships, with child parts following their parent. The rotation center defaults to the center of the part's bounding box and can be changed in the OBJ shape's "Part Pivots" property; movement is in OBJ units.
- **Modes**: "Replace animation" fixes the ticked rotations to the given values; "Add to animation" layers rotation and movement on top of the existing animation. Rotation and movement are ticked separately for each part, and parts left unticked keep the original animation or the effect of other tracks; several tracks can act on the same entity, layered in track order.
- **Capture current pose**: writes the entity's current animated pose (all parts) into the keyframe as a starting point for adjustment.
- **Viewport manipulator**: each part has an orange marker at its pivot; right-click to select the part. Once selected, three rotation rings controlling the X/Y/Z angles and three movement arrows appear (R shows only the rings, G shows only the arrows); dragging previews in real time, and holding Ctrl snaps.
- **Scope**: any entity drawn with a model can be posed: mobs, players, the Ender Dragon, and also boats, minecarts, end crystals and so on. Armor, capes, elytra and held items follow the pose.
- **Entity shapes**: choose "Shape: name" in the entity dropdown to pose an entity shape. A shape that uses "Project a world entity" follows the original entity's pose (including pose keyframes on the original entity) while it has no pose keyframes of its own; once you add pose keyframes to the shape, the projection is posed on its own and the original entity is unaffected. Entity shapes on the Screen (UI) layer can only be adjusted in the properties window; the viewport manipulator isn't available.
- **Keyframing per part**: with "Key properties separately" turned on, each part's rotation and movement are independent properties, so you can keyframe only the head's rotation and separately the arm's movement without affecting each other (see below).

## Screen VFX

- **Screen VFX track**: replaces the old fade track; fade tracks in old saves are converted to Screen VFX tracks automatically. One keyframe holds a list of effects: use "Add effect" to add the ones you need, each effect gets a collapsible section and can be moved up, moved down or removed, and the list order is the stacking order (each effect type can appear at most once per keyframe; to have two of the same kind, put them on two tracks); multiple tracks are then stacked in track order. Single-effect keyframes and "Combined" effects in old saves are converted to the matching list automatically. All parameters are interpolated between keyframes and also support per-property keyframing; when two keyframes have different effect lists, the list switches at the midpoint. After the last keyframe the state is held until the track is disabled or muted. When the camera preview window splits off the main view, effects only apply to the preview image.
- **Color grading**: basic adjustments (exposure, temperature, tint, hue shift, saturation, contrast), tone mapping (none / neutral / ACES), channel mixer, Lift / Gamma / Gain color wheels, grading curves (the main curve and the various curves between hue, saturation and luminance), split toning, and shadows / midtones / highlights color wheels.
- **Bloom**: intensity, threshold, soft threshold, radius, brightness clamp, color, anti-flicker, high-quality filtering; the lens dirt texture can be given by resource ID or picked directly from a folder.
- **Depth of field**:
  - Modes: focus plane sharp (near and far both blurred), blur near only, blur far only, distance blur (fog-like).
  - Focus: focus distance and sharp range are in blocks; "Autofocus" focuses on the screen center, and "Focus smoothing" makes the focus point transition smoothly instead of jumping.
  - Blur: "Aperture" determines how fast blur grows away from the focus plane, and "Blur size" determines the maximum blur radius; multi-level resolution sampling keeps large blurs smooth. Foreground blur naturally bleeds over the edges of sharp objects.
  - Bokeh shape: circle, or a triangle through an octagon, with adjustable sample count, ring count and rotation.
  - Lens: chromatic blur (adjustable strength), anamorphic lens, FOV-scaled blur, tilt X / Y (tilt-shift effect).
  - "Show focus plane": while a keyframe is selected, tints the sharp region and draws a line where the focus plane meets the scene; when the camera preview splits off the main view, the focus plane is drawn in the world in front of the shooting camera instead.
- **Vignette**: intensity, inner radius.
- **Gaussian blur**: applies a Gaussian blur to the whole image, with the radius in 1080p pixels scaled with resolution and an adjustable mix; for large radii the image is downscaled before blurring, which stays smooth and fast. "Sharp centre" is optional: it keeps the middle of the screen sharp and blurs progressively toward the edges (edge softness is adjustable).
- **LUT**: built-in neutral, warm, cool and teal-orange presets, or use a 256×16 LUT image by resource ID or picked from a folder; strength is adjustable.
- **Fade**: color (one-click black / white) and opacity, drawn above the UI layer.
- **More effects**:
  - Film grain: intensity, grain size, animation speed, colored grain.
  - Pixelation: pixel block size, color levels.
  - Lens effects: lens distortion, chromatic aberration, optical center.
  - Lens flare: triggered by the sun during the day and the moon at night (cool white light, stronger the closer the moon phase is to full); intensity, brightness threshold, ghost count, halo and color separation are adjustable.
  - Volumetric light (God rays): first takes the whole image and keeps only the parts that can emit light (open sky and the sun, and optionally bright pixels above a brightness threshold), turning everything else black as occluders; the kept parts are then radially blurred toward the sun and finally added back onto the original image. Blocks and leaves cut the sky into individual shafts. Intensity, shaft length, falloff, darker with distance from the source, sample count and color are adjustable, and "Sky emits light" and "Bright pixels emit light" (with a brightness threshold) can be switched separately; it's best placed before bloom and color grading so the shafts take part in the later processing. Sun / moon shafts only appear in the Overworld: the source is the sun during the day and the moon at night (cool white light, stronger the closer the moon phase is to full), the source must be on or near the screen, and shafts weaken in rain. "Enhance local light shafts" lets the same effect's intensity and sample count boost Light keyframes that have volumetric light enabled, so it can act on local lights even without sun / moon shafts.
  - Auto exposure: automatically adjusts exposure over time based on image brightness, with target brightness, exposure compensation, EV range and adaptation speed; going from dark to bright lowers exposure immediately, and abnormal brightness values are filtered out to reduce brief white-outs.
  - Motion blur: blurs based on camera movement and depth, and the sky doesn't take part; intensity, sample count and max blur are adjustable.
  - Panini projection: projection distance and crop, reducing wide-angle distortion.
  - Ambient occlusion and screen-space reflections: they can only use the pixels and depth visible on screen. Screen-space reflections by default only affect water, glass and ice near the camera; "Reflective blocks" can take `water glass ice`, specific block IDs (such as `minecraft:polished_blackstone`) or `#block_tag`, separated by spaces or commas, and leaving it empty reflects nothing. Materials are determined from a mask generated from the scene's block states rather than guessed from screen colors; objects off screen can't appear in reflections.

## Light

- **Light keyframes**: they have their own track icon, and in a keyframe you pick PointLight, AreaLight or SpotLight from a dropdown. You can set the light's position, color, intensity (up to 100), radius, volumetric light and shadow strength; values are interpolated between adjacent keyframes, and after the last keyframe the last specified state is held. Coordinates can also be driven by expressions.
- **Three kinds of light field**: PointLight shines outward in all directions; SpotLight has adjustable direction and inner / outer cone angles, and the radius controls the cone's length; AreaLight shines forward from a rectangular surface with adjustable width and height, where "throw distance" controls how far it extends and the radius controls how far it spreads with distance. AreaLight's surface light and volumetric fog are both computed in the shape of a rectangle expanding forward, avoiding a rounded, box-shaped fog field; SpotLight's volumetric fog is computed as a finite cone and is no longer cut off by a spherical range.
- **Parent shape**: a Light keyframe can pick a shape as its parent, and the light moves, rotates and scales with it (the lighting range and AreaLight size also change with scale); in that case the position, direction and size in the keyframe are all relative to the parent shape. When the parent shape changes, the light's world position stays the same; dragging with the viewport manipulator still works in world coordinates and converts back to relative values when you release. If an entity the parent shape is mounted on is absent, the light is hidden with it. When two keyframes have different parent shapes, the transition happens in world coordinates.
- **Surface light and fog**: surface light reconstructs the orientation of lit surfaces from depth and computes brightness from the angle of incidence and distance; entity models and non-transparent blocks near a light are additionally drawn into separate albedo and depth buffers, and when depths match, reflection is computed from the albedo that hasn't been through ambient light or shader-pack shading. The block mesh is cached per light range and rebuilt when blocks change; for ranges that are too large, and for uncovered transparent objects and fluids, the original scene color is still used to approximate material reflectance, with lighting color sampling smoothed within depth and color edges. Black surfaces are not brightened by colored light. AreaLight considers both the throw direction and the lateral distance within the emitting surface, so even a flat plane gets soft variation in brightness. The contributions of multiple lights are first accumulated in a high-precision buffer, then composited onto the image once with slight dithering, reducing large flat areas, over-exposure and 8-bit banding. Volumetric light is sampled along the view ray with fine noise that follows the light; AreaLight and SpotLight use denser sampling to reduce visible banding.
- **Occlusion**: shadow strength is adjustable. Surface shadows and volumetric fog use screen depth to estimate whether blocks lie between the light and the lit position; volumetric fog accumulates occlusion at multiple points along the light path, and compositing smooths shadows while preserving depth edges, reducing stair-stepped borders. SpotLight's soft-shadow sampling follows the light's direction. These are screen-space approximations: occluders you can't see (especially off screen) can't reliably cast shadows.
- **Viewport manipulator**: in game you can drag the light's position, direction, radius, the width, height and throw distance of an AreaLight's emitting surface, and a SpotLight's cone angle. A SpotLight's radius handle controls the cone length along the throw direction; the manipulator follows the light position calculated by expressions, and previews in real time while dragging.
- **Link with God Rays**: when "Enhance local light shafts" is ticked in Screen VFX's God Rays, its intensity and sample count apply to Light keyframes that have volumetric light enabled; the light's surface lighting is not changed.

## Viewport Manipulator (Gizmo)

- **Four modes**: Move (G), Rotate (R), Scale (B), Geometry (M). Geometry mode is used to adjust sizes, radii, line segment endpoints, area corners and so on. The modes are shared by all manipulators: shapes, camera, orbit (G center, R angle, B distance), entity pose and prefabs; non-shape manipulators show all their handles in M mode.
- **Coordinate space**: switchable between global and local. Global works along the world axes; local works along the shape's own orientation (including its parents).
- **Snapping**: hold Ctrl to snap; position and size snap to 0.5 blocks, rotation to 15°.
- **Click to edit**: right-click a shape in the viewport to jump straight to its nearest keyframe for editing. Right-clicking a manipulator no longer also opens Flashback's entity menu.
- **Auto keyframe**: when enabled in Flashback Preferences → Vector3 → Editor, clicking a shape with no keyframe selected edits the state at the playhead directly, and moving it automatically creates a keyframe at the playhead.
- **Duplicating and deleting shapes**: while the mouse is over the viewport or the Shape Manager, Ctrl+D duplicates the shapes that the selected keyframes belong to, with all their keyframes, onto new tracks (new IDs, names get a "copy" suffix, and parent-child relationships copied together point to the copies); Del deletes those shapes together with their keyframes on all tracks. Both can be undone. Pressing Del on the timeline still deletes only the selected keyframes.
- **Place at the pointed position**: with a shape selected, middle-click to move it onto the block surface or entity the mouse is pointing at; hold Ctrl to align to the block cell center.
- **Free move**: the camera, orbit, light, prefab and shape-manipulator center blocks can all be dragged directly to move freely along a plane facing the camera (Ctrl snaps to 0.5 blocks), so you no longer have to drag one axis at a time.
- **Hidden on export**: when exporting an animation, all editor-only overlays (camera and shape motion paths, all manipulators, selection boxes, eyedropper highlights, prefab placement previews, the helper points and lines of lights and blasts, particle emitter outlines) are hidden automatically, so the exported image contains only the real content.

## Camera

- **Editor camera**: orbit, pan and zoom to look around the scene, and press F to focus on the object under the mouse.
- **Camera preview**: a dockable window showing the image seen by the camera keyframe at the playhead (rendered separately, without Flashback's camera path and markers). With "Main view doesn't follow the camera" ticked (on by default), camera keyframes only drive the preview, the main view stays free to look around, and export is unaffected. With shader packs the preview is off by default, and you can tick "Enable with shaders (experimental)".
- **Camera keyframe manipulator**: after selecting a Flashback camera keyframe, the camera position in the viewport shows X/Y/Z movement axes, a free-move point, and three rotation rings for yaw, pitch and roll, with a white line for facing and a green line for up. Hold Ctrl to snap to 0.5 blocks and 15°.
- **Orbit keyframe enhancements**: Flashback's Orbit keyframes can additionally set tilt, and the center, distance and angle can be edited in the viewport with the manipulator.
- **Look To keyframes**: from a Look To keyframe until the keyframe that has "End Scope" ticked, the camera is forced to look at the target while other camera parameters are unaffected. The target can be an entity, coordinates or a shape; for entities you can aim at the eyes, center or feet; for coordinates, "use crosshair position" takes the point the crosshair is pointing at; the target transitions smoothly between adjacent keyframes.
- **Dolly Zoom keyframes**: as the camera dollies along its line of sight, the FOV is adjusted automatically so the target keeps the same size in the frame. The target can be an entity, coordinates or a shape and is followed in real time; the scope also ends with "End Scope"; values can be taken from the current camera in one click.
- **Camera shake enhancements**: Flashback's camera shake keyframes gain roll, position shake (along the camera's own axes, in meters), multi-layer fractal noise (layers, roughness) and a seed, plus handheld, walking, vehicle and impact presets. The shake phase is integrated along the timeline, so a given frame is exactly identical whether you play, scrub, pause or export; with default parameters the result matches vanilla Flashback.
- **Track Entity**: Flashback's Track Entity keyframes can pick an entity from a searchable dropdown or with the eyedropper, so there's no need to type a UUID.
  - "Model part": anchors the camera on a part of the entity's model (such as the head or a hand); any entity with a model is supported, including the Ender Dragon.
  - "Follow part rotation": the camera turns with the part in yaw, pitch and roll; the offset and roll are measured from the part's own orientation, and the view offset rotates with it.
  - Angle and offset fields can be adjusted by dragging left and right with the mouse (Ctrl-click or double-click still lets you type a value).
- **No jitter while paused**: Track Entity, Look To and Dolly Zoom stay consistent with the entity's displayed position while playback is paused or the timeline is being scrubbed, so they don't jitter back and forth.
- **Eyedropper**: the entity and shape pickers all have a "Pick" button next to them to click the target directly in the viewport.

## Timeline

- **Speed curves**: interpolation types gain "Custom". When selected, a "Speed curve" tab appears in the properties window, where you can freely arrange the time progress between this keyframe and the next with a multi-point Bézier curve (it can go beyond 0–1 for bounce and anticipation), with linear, ease in, ease out, ease in-out and bounce presets; it previews in real time while dragging, can be undone, and is saved with the project.
- **Per-property keyframing**: lets different properties on the same track change at their own pace; see the next section.
- **Skip keyframes**: the section from a Skip keyframe to the next keyframe with "End Skip" ticked is skipped completely during playback and export; you can still view it by scrubbing the timeline while paused. There can be at most one Skip track.
- **Loop keyframes**: the section from a loop keyframe to the keyframe with "End Loop" ticked is repeated an additional "Repeats" times during playback and export, marked "x N" on the timeline; pausing playback resets the count. There can be at most one loop track.
- **After the last keyframe**: set in the track's right-click menu how the track behaves after its last keyframe: hold, loop or ping-pong, with the repeated keyframes shown faded on the timeline.
- **Groups**:
  - Turns several selected keyframes into a group. You can create a group from the track menu, or right-click empty space and select an area.
  - Groups are shown as colored bars. Dragging the colored bar or the empty space inside the group moves the whole group, with the keyframes following in real time.
  - Right-click a group to rename it, save it as a prefab, ungroup it or delete it.
  - Groups are kept through undo, redo, saving and reopening the replay.
- **Cross-track dragging**:
  - Keyframes and groups can be dragged onto other tracks of the same type. Dragging below the last track creates a new track of the same type automatically.
  - When the target is invalid (different type, out of bounds, no room), the preview turns red and releasing only performs a horizontal move.
- **Alt-drag to copy** (as in DaVinci Resolve):
  - Hold Alt and drag keyframes (single or multiple); the copy is dropped where you release and the original keyframes stay put. It also works across tracks, and dragging to the very bottom creates a new track.
  - Hold Alt and drag a group to create a new copy of the group at the target position.
  - Hold Alt and drag a track handle to leave a copy of the whole track in place (including name, color and enabled state), while the original track is the one that moves.
  - In Flashback, Alt-drag used to scale keyframes around a pivot; that is now Ctrl+Alt.
- **Ripple delete**: Ctrl+Delete deletes the selected keyframes and closes up everything after them on all tracks by the same duration.
- **Distribute evenly**: after selecting keyframes at two or more different times, right-click "Distribute evenly" and enter a total duration (in ticks) to space them evenly starting from the first keyframe; keyframes at the same time on different tracks stay aligned.
- **Track selection**: click a track in the left track list to select it, Ctrl-click to add or remove, Shift-click to select a range, and drag in empty space to box-select. Dragging the handle of any selected track moves all selected tracks up or down together, keeping their spacing; press Del to delete the selected tracks (can be undone).
- **Locking and solo**: in a track's right-click menu you can "Lock track" (shown in red; its keyframes can no longer be selected, edited or moved) and "Solo" (mutes only the other tracks of the same type, so soloing a pose track, for example, doesn't stop the camera). The "Tracks" window lists all tracks and lets you toggle locking and solo directly.
- **Batch editing**:
  - When several keyframes are selected, the properties window shows sections by type, and every type is supported. Shapes are split into a "Shape" common section (position, rotation, color and so on shared by all types) and type-specific sections.
  - When values in the same field differ, it shows "-" and is locked; hold Ctrl to unlock and edit, which writes to all targets and changes only the item you edited (per component for vectors). Fields with identical values can be edited directly. Interpolation type is supported too.
  - The viewport gizmo applies to all selected shapes: moving translates them as a whole; rotating and scaling work around a shared center in global space, and around each shape's own center in local space. When a parent and child are both selected, the child isn't transformed twice. Middle-click placement translates the whole set.
  - Each edit is merged into a single history entry and can be undone in one step.
- **Drag merges undo**: when you hold and drag a value on the same control in the properties window, the whole drag produces a single undo entry; separate clicks remain separate entries.
- **Property copy and paste**:
  - In the properties window, Ctrl+right-click a row to select or deselect it, and left-drag to box-select. Esc clears the selection.
  - Ctrl+C copies the values of the selected rows, and Ctrl+V pastes them into rows with the same name in the current keyframe (or in all selected keyframes when several are selected); you can also right-click empty space or a selected row to open a menu.
  - Matching is by row name, so position, color and so on can be pasted across shape types; rows with no counterpart are skipped, and a message tells you how many were pasted.
  - The clipboard is saved in `config/vector3/property_clipboard.json`, so it still works after closing or switching replays.
- **Instant preview**: when enabled in Flashback Preferences → Vector3 → Editor, dragging a keyframe or the playhead changes the world in real time, without waiting for release or holding Ctrl.
- **Loop playback**: when "Loop Playback" is ticked in Flashback Preferences → Vector3 → Playback, playback jumps back to the in-point (I) when it reaches Flashback's out-point (O) and keeps playing.
- **Fixes**:
  - Fixed the whole range being offset when box-selecting keyframes in Flashback; right-clicking empty space no longer clears the current selection.
  - When a camera keyframe is missing a position, the camera path no longer crashes every frame; marker textures are loaded ahead of time to avoid upload errors during rendering; zero-sized UI clip regions no longer cause crashes.
  - Clicking a window floating above the timeline no longer passes the click through to the timeline.
  - Video shapes now take frames one by one at the exported time during export and wait for decoding to finish, so every exported frame corresponds exactly to the timeline (previously the video stayed on the same frame during export); during playback they also follow the playback position instead of only jumping on whole ticks. When the export range doesn't start at the beginning of the replay, the timing of videos, particle emitters and camera shake used to be shifted as a whole by the start offset; this is now aligned with the editor.

## Per-Property Keyframing (properties toggled separately)

Originally one keyframe recorded all properties of its type, and to make position and color change at different paces you had to split them onto two tracks. With this mode on, a single track can do it.

- **Turning it on**: tick "Key properties separately" in the track's right-click menu; once on, the track is shown as a semi-transparent dark purple. Turning it off returns to the normal way, and old saves are unaffected.
- **Recording only some properties**: each keyframe can record just a subset of properties. Each property is interpolated only between the keyframes that recorded it, and outside that range it holds the value of the nearest such keyframe; interpolation type, speed curve and loop / ping-pong all apply per property, based on that property's own keyframes.
- **Automatic marking**: when you modify a property, it is automatically recorded into the current keyframe; "Key all" makes the keyframe record all properties again.
- **◀ ◆ ▶ buttons**: each property in the properties window has three buttons next to it (like DaVinci Resolve): jump to the previous keyframe that recorded the property, set or remove a keyframe for it at the playhead (a solid ◆ means the keyframe at the playhead records it), and jump to the next one. Clicking ◆ where there is no keyframe creates a keyframe that records only that property, with the other properties taking the current interpolated result.
- **Timeline display**: keyframes that record only some properties show their lower half darkened; hovering shows which properties they record.
- **Supported types and properties**:
  - Camera: position, facing, roll; orbit, camera shake and Track Entity are split by their own parameters.
  - Shapes: position, rotation, scale, size, color, line width, outline, points, text, wireframe, visibility, video playback, plus the parameters of particle emitters and blasts.
  - Entity pose: the rotation of each part and the movement of each part.
  - Screen VFX: the parameters of each effect.
- **Saving and copying**: the recorded properties are kept with saves, copy and paste, prefabs and groups.

## Expressions

See `expressions.md` for the full description.

- **Anything can be driven**: right-click a parameter row in the properties panel → add expression. Number boxes, vectors, colors, checkboxes, dropdowns and TextShape text of all keyframe types (both Vector3's own and Flashback's built-in ones) are supported; multi-component parameters can be driven on all components or on a single component.
- **Language**:
  - Operations: arithmetic, comparison, logic, ternary; vectors operate component-wise; `dot` / `cross`.
  - Functions: math, interpolation (`lerp` `smoothstep` `remap`), seeded random, noise and `wiggle` (exports are reproducible), string formatting.
  - `let` local variables, `//` comments; text parameters use `{expression}` templates.
- **Variables**: `time`, `tick`, `value` (the original value on the keyframe, so you can layer on top of the animation), `self` (this track's parameters; `self.local()` / `self.world()` convert between world and local coordinates), `camera`.
- **References**:
  - Other tracks: `Cube.position.y`; right-click a parameter to "Copy reference".
  - Entities: `entity("name / UUID / UUID in NBT")`, which can read position, velocity, line of sight, health and so on; `.nbt` reads all the data the entity saves, `.field` reads entity fields (such as `hurtTime`), and `.data` reads synced data.
- **Global variables**: one set per replay, saved with the replay, and any expression reads them with `global.name`; you can pick an entity directly from the entity dropdown (or the eyedropper) without typing a UUID.
- **Expression editor**:
  - Syntax highlighting and squiggly underlines at error positions.
  - Completion: choose with the up / down arrow keys, insert with Tab/Enter; after `.` the actual members are listed; inside `track(` / `entity(` it lists track names and nearby entities.
  - Hovering with the mouse shows the type and current value; the value of each variable is listed; common snippets can be inserted in one click; a built-in language reference.
- Tracks with expressions are marked `fx` on the right side on the timeline.

## Clips

- **Clips window**: lists all replays in the replay folder as cards (thumbnail, name and duration), which can be dragged straight onto the timeline.
- **Clip track**: a replay you drag in becomes a bar on the "Clips" track at the top of the timeline; the bar tiles the replay's thumbnails and is colored by source, and clips that haven't been applied yet have an orange border. The clip track is always pinned to the very top and can't be dragged up or down, and other tracks can't be moved above it. The first time you add a clip to a normal replay, the replay itself automatically becomes the first clip.
- **Editing**: drag the edge of a clip or audio bar to trim it; middle-click to cut at the mouse, Ctrl+B to cut all clips and audio at the playhead; dragging automatically snaps to the boundaries of neighboring clips and the playhead. The timeline extends to show clips that haven't been applied yet.
- **Joining**: drag clips on the clip track to reorder them, trim them or set in and out points in the properties window, then click "Apply clip changes" in the Clips window. The replay exits, is composited end to end in the background in order (a progress window is shown, and you can keep editing meanwhile), and is then reopened automatically.
  - Keyframes on other tracks keep their original time positions, so their spacing and order don't change when clips are reordered or trimmed; the editor state stays unchanged.
  - During compositing, replay data is truly trimmed at the in and out points, and clips are joined end to end with no gaps.
  - The clip's source file is copied and stored under Flashback's data directory, so moving or deleting the original replay doesn't affect the project.
  - Replays recorded on different game versions can't be joined, and you are told so when you drag them in.
- **Audio keyframes**: Flashback's audio keyframes can swap the audio file (mp3, ogg, wav, flac, opus and so on), adjust volume (0–2) and pitch (0.25–4; the higher the pitch, the shorter the bar on the timeline), and can be trimmed like clips and saved with the project.
- **Empty project**: the "Empty project" button to the right of "Load replay from file" in the first row of the replay list opens a new project that contains only the void. The first time you use it, a temporary void-world recording template is created automatically and deleted once recorded.

## Prefab

- **Prefab basket**: the window lists built-in templates and your own saved prefabs; drag one onto the timeline to place it, or double-click to place it at the playhead; you can refresh the list and delete prefabs.
- **Placement mode**: in the viewport, use the manipulator to set the prefab's center, rotation and scale, and adjust the duration multiplier at the same time, with the path previewed live. After you confirm, new tracks are generated, and the whole placement can be undone in one step.
- **Built-in templates**: Orbit and Dolly Zoom, with adjustable template parameters.
- **Saving prefabs**: selected keyframes or a group can be saved as a prefab. Coordinate-type data (camera position, orbit center, Look To target, shapes) is converted to values relative to the prefab origin; the per-property keyframing state is saved too. On placement, shapes get new IDs, so placing the same prefab twice gives two independent sets of shapes.
- **Re-editing**: a placed prefab group can re-enter placement mode, and after adjusting the transform, replace its original contents.

## Interface

- **Window menu**: the window toggles on Flashback's top bar are merged into a single "Window" dropdown, which includes Properties, Clips, Shape Manager, Prefab Basket, Camera Preview, History, Tracks, and the "Force compatibility" toggle.
- **Shape Manager**: lists all shapes as a tree, with search, naming and parent-child hierarchy; right-click a shape to edit it, and middle-click to copy the shape ID.
- **Preferences categories**: the toggles that used to sit at the top of the Shape Manager have moved into the Vector3 section of Flashback Preferences: Editor (editor mode, instant preview, auto keyframe), Playback (loop playback), and Render debugging (show only the Iris bypass target). The Shape Manager window keeps the search and the shape tree. The three toggles in the Editor category are the user's own preferences, remember the last choice and apply to all replays (saved in `config/vector3/preferences.json`); "Loop Playback" belongs to the current replay and is saved with it.
- **Properties window**: Flashback's keyframe edit pop-up has become a dockable "Properties" window that follows the selected keyframe and no longer covers the viewport; other pop-ups can be dragged by holding empty space.
- **History window**: lists the entire undo history, and clicking any entry undoes or redoes directly to that step.
- **Help menu**: the top bar gains a "Help" menu. The "Shortcuts" page is searchable, organized into Viewport & Gizmo, Editor camera, Timeline, Properties window and General categories, and live-lists Flashback's current shortcuts; press `?` to open it (rebindable in the key settings). The "Flashback tips" page lists all of Flashback's daily tips.
- **Window memory**: all Vector3 windows, like Flashback's own windows, keep their open state and docking position after you restart the game.
- **Icons**: the custom tracks and each shape type have their own icons.

## Compatibility

- **Force compatibility**: toggled in the Window menu, on by default. When it hits content Flashback can't read (packets that can't be replayed, such as `minecraft:transfer`; actions that error during processing; data whose recording format doesn't match the current version), playback continues, and each kind of problem is only logged once.
- **Iris shaders**: vanilla content such as items, blocks, entities, images, videos and text can be drawn separately, bypassing shaders, to avoid being garbled by the shader pack. Light and Screen VFX act as screen-space post-processing on the already rendered image and don't modify the Iris shader pack's shaders. When Replay Visuals turns the sky off, the shader pack's sky is hidden as well. With shaders on, chunk fade-in is treated as already finished, and custom render pipelines such as OBJ lighting are not enabled, with the shader pack taking over instead.
- **Sodium**: area shape hiding and baking are adapted to Sodium's chunk building and block rendering.

## Other

- **Property search**: the search box at the top of the properties window filters rows by name.
- **Motion Paths**: the "Motion Paths" menu on the top bar. The viewport draws the trajectories of all camera tracks and shapes, with a small dot every N ticks and a large dot at each keyframe; right-click a large dot to select that keyframe, adjust position and rotation directly with the gizmo, and open the properties panel.
- **Audio fades and beats**: audio keyframes can set fade in / fade out (in ticks); "Detect beats" marks beats on the audio bar, and other keyframes snap to the beats while being dragged.
- **Clip auto-push**: after a clip is dragged, dropped in from the media library, or trimmed at its edge, if it overlaps other clips, the dropped clip stays where it is and the overlapping clips are pushed to the right in turn (can be undone).
