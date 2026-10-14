# Flying Nimbus

Nimbus is a reusable flying mount restricted to Good alignment (30 or higher). The server checks eligibility both when mounting and during flight. Losing Good alignment dismounts the rider.

## Obtaining, binding and calling (CX-40)

Find Flying Nimbus in Creative > Tools & Utilities, or use `/give @s dbzenith:flying_nimbus`.

Craft it in this layout:

```
Yellow wool | Feather      | Yellow wool
Feather     | Golden apple | Feather
Yellow wool | Feather      | Yellow wool
```

**Bind it:** use the item. It is absorbed and the cloud is bound to your soul (`NimbusBond`, kept in the player's persisted data, so it survives death and relogging).

**Call it:** press the call key (M by default, "Nimbus: Call / Send Away").
- "Nimbus!": the cloud appears high in the sky ahead of you and swoops down along a curve, trailing gold. Indoors it comes from just above.
- It parks beside you, turned your way.
- If you are falling, it dives under you and catches you.
- Jump onto it to ride (as Goku does), or right-click it.

**Send it away:** press the key again. It works while it is still coming in, parked, or carrying you.
- The cloud comes apart into golden wisps and glints over 1.5 seconds.
- A rider high in the air gets a few seconds of slow falling.
- Pressing from more than 64 blocks away calls a fresh cloud instead (the old one dissolves).
- An empty cloud also dissolves on its own after a minute.

**Admin:** `/dbz nimbus <players> bind|unbind|call`.

**Riding:**
- WASD and view direction: steer; look down while moving forward to descend.
- Space: rise.
- Sprint: fly faster.
- `,`: switch between standing and sitting (remappable in Controls).
- Shift: get off.

## How it looks (CX-39)

The cloud is about thirty soft puffs (`NimbusRenderer`), not a solid mesh. Each puff breathes and churns on its own; the colour runs from bright yellow on top to warm orange underneath, with a few white-gold highlights and a curling tail. The puffs face the camera, are sorted back to front, and are drawn unlit (`FxRenderTypes.unlit`) so the cloud keeps its colour from every side. It glows a little softer at night. The puff texture comes from `tools/NimbusTextureGen.java`.

It moves like a living thing. The client works out its motion each tick (`NimbusEntity.clientMotion`):

- It tilts nose-down as it speeds up and rocks back as it brakes, banks into turns, and stretches out with speed, the tail streaming back. Each of these runs on a spring, so it overshoots a little and settles.
- Hovering, it bobs.
- Gliding on its own, it turns to face where it is going.

The trail behind it:

- a glowing golden streak along the last 1.7 seconds of its path, joined to the tail;
- golden wisps peeling off the tail, swelling as they fade;
- glints sparkling in the wake.

Riding it plays wind that rises in volume and pitch with speed. Movement has momentum (server-side): speed builds over a moment, glides to a stop, and turns back faster than it coasts.

Motion and eligibility are server-authoritative; a missing input stream glides to a hover. Both client and server need this version for multiplayer.

## Tests

The `NimbusTests` server GameTests cover:

- alignment boundaries;
- bypass rejection;
- alignment loss;
- posture persistence;
- non-finite input rejection;
- movement and stale-input braking;
- binding, calling and sending away (the cloud comes from high above and parks beside you; a leaving cloud drops its rider softly and cannot be mounted).

`tools/nimbus-review.init.gradle` launches an isolated client review.
