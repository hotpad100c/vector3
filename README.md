# Vector3

Vector3 adds a Flashback track named `Shapes`. Its creation popup supports
solid/wireframe boxes, sphere, face/line circles, solid/wireframe cylinders and
cones, and line segments. Any number of Shapes tracks can run in parallel. Every
created shape receives its own UUID; its position, Euler
rotation, scale, dimensions, segments, line width, see-through state and visibility
are stored in the replay's keyframe JSON and applied on playback.

## Extending it

Register another RyansRenderingKit shape factory during client initialization:

```java
ShapeTrackRegistry.register("custom", "Custom shape", state -> /* return a Shape */);
```

Create keyframes whose `ShapeState.shapeType()` is the same registered name.
The one shared Flashback track and serializer handle all registered types.

A future visual editor can be connected without changing the timeline format:

```java
ShapeKeyframe.setEditor((keyframe, update) -> { /* draw UI; call update */ });
```

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
