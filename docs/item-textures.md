# Custom item textures

Generated with the built-in image generation tool (not CLI/API fallback).
The PNGs are 32 x 32 RGBA, sampled with nearest-neighbor from generated artwork;
transparency is preserved without adding a background or smoothing.
All three item models use `minecraft:item/generated` with their own texture.

## Resource ledger

Final asset: `src/main/resources/assets/colonyresourceledger/textures/item/colony_resource_ledger.png`

Generation prompt:

```text
Use case: stylized-concept
Asset type: Minecraft inventory item sprite texture
Style/medium: authentic vanilla-like Minecraft coarse pixel art, designed on a tiny 32 by 32 logical pixel grid, uniformly enlarged nearest-neighbor to fill the output canvas. Every pixel is a hard-edged square; deliberately low detail, about 12 flat palette colors. Not a high-resolution illustration.
Composition: one single centered inventory sprite, fills about 85 percent of square canvas, slight diagonal book perspective like Minecraft book item, fully visible.
Backdrop: real transparent alpha everywhere outside the sprite.
Constraints: no text, no letters, no watermarks, no checkerboard drawn into image, no cast shadow, no glow, no smooth gradients, no anti-aliasing, no surrounding scene, no tiny scattered detail. Clear readable silhouette at inventory size.
Primary request: original custom resource ledger item. Brown leather-bound book, cream page edge, green bookmark. A simple little tan wooden crate emblem embossed on the front represents colony construction resources. Warm brown and parchment colors, small green accent, vanilla pixel shading only.
```

## Citizen job monitor

Final asset: `src/main/resources/assets/colonyresourceledger/textures/item/colony_job_monitor.png`

Generation prompt:

```text
Use case: stylized-concept
Asset type: Minecraft inventory item sprite texture
Style/medium: authentic vanilla-like Minecraft coarse pixel art, designed on a tiny 32 by 32 logical pixel grid, uniformly enlarged nearest-neighbor to fill the output canvas. Every pixel is a hard-edged square; deliberately low detail, about 12 flat palette colors. Not a high-resolution illustration.
Composition: one single centered inventory sprite, fills about 85 percent of square canvas, slight diagonal book perspective like Minecraft book item, fully visible.
Backdrop: real transparent alpha everywhere outside the sprite.
Constraints: no text, no letters, no watermarks, no checkerboard drawn into image, no cast shadow, no glow, no smooth gradients, no anti-aliasing, no surrounding scene, no tiny scattered detail. Clear readable silhouette at inventory size.
Primary request: original custom colony citizen job-status logbook item. Dark blue leather-bound notebook, cream page edge. A prominent simple gold circular clock badge on the cover with a dark hand; three tiny green amber red status marks below it. Muted blue and parchment colors, gold clock accent, vanilla pixel shading only. Make the clock unmistakable at inventory size.
```

## Housing manager

Final asset: `src/main/resources/assets/colonyresourceledger/textures/item/colony_housing_manager.png`

Generation prompt:

```text
Use case: stylized-concept
Asset type: Minecraft inventory item sprite texture for a colony Housing Manager
Style/medium: authentic vanilla-like Minecraft coarse pixel art, designed on a tiny 32 by 32 logical pixel grid, uniformly enlarged nearest-neighbor to fill the output canvas. Every pixel is a hard-edged square; deliberately low detail, a restrained flat palette. Not a high-resolution illustration.
Composition: one single centered inventory sprite, fills about 85 percent of square canvas, slight diagonal book perspective like Minecraft book item, fully visible.
Backdrop: real transparent alpha everywhere outside the sprite.
Subject: an original moss-green leather-bound housing ledger book with cream page edge, simple gold corner fittings. A large clearly readable cream-and-brown HOUSE emblem on the front cover: blocky pitched terracotta roof, cream wall, one dark door and one small blue window. The house is the main emblem and must remain obvious at tiny inventory size. A short tan bookmark protrudes below the book. Match the feel of a companion set containing a brown resource ledger and a blue clock logbook, but this is one green book only.
Constraints: no text, no letters, no clock, no crate, no watermarks, no checkerboard drawn into image, no cast shadow, no glow, no smooth gradients, no anti-aliasing, no surrounding scene, no tiny scattered detail. Clear readable silhouette at inventory size.
```

## Testing

Restart `gradlew.bat runClient` after changing development resources. If testing
an installed mod, rebuild the JAR and replace the installed version first.
`F3+T` reloads textures only when the running client already has these new
resources on its resource path.
