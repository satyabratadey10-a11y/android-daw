# MISSION: Compose-only UI overhaul — FL Studio Mobile–grade DAW interface

You are a team of agents working on an Android DAW. The audio engine and features are done or nearly done. **Your job now is to make the UI clean, professional, polished and consistent, and to remove every XML-based UI resource.** Do NOT touch audio/native/DSP code, project file formats, or business logic. Work only in the UI layer and build tooling.

Reference: FL Studio Mobile 4.10 screenshots are in `docs/reference-ui/`. If a screenshot is missing, the written spec below is authoritative. Study the screenshots before writing any code.

---

## 0. HARD RULES (violating any of these = task failed)

1. **100% Kotlin + Jetpack Compose UI. Zero XML UI resources.**
   - Delete: `res/layout/`, `res/values/colors.xml`, `themes.xml`, `styles.xml`, `dimens.xml`, `strings.xml`, `res/menu/`, `res/anim/`, `res/color/`, `res/drawable/*.xml` shape/selector files.
   - The ONLY allowed XML: `AndroidManifest.xml` and the launcher icon files. Set the app theme in the manifest with a built-in `@android:style/Theme.Material.NoActionBar` (no custom themes.xml).
   - All colors, sizes, strings, shapes, icons live in Kotlin. Icons are `ImageVector` built in code or drawn on `Canvas`. No icon fonts, no emoji as UI.
2. **Exactly 3 primary colors** (section 2). Everything else is a neutral. No gradients between hues, no extra accent colors, no Material You / dynamic color, no default Material purple.
3. **No overlapping elements.** Two components never share the same screen area unless one is deliberately on a higher z-layer (section 4). Layout is built with `Row` / `Column` / `Box` + weights/arrangements, never with ad-hoc `offset()` hacks to "fit" things.
4. **One source of truth for every visual value.** No hard-coded `Color(0x…)`, `dp`, `sp`, `RoundedCornerShape(n.dp)` or `tween(n)` outside `ui/theme/`. Components read from `Daw.colors`, `Daw.space`, `Daw.radii`, `Daw.type`, `Daw.motion`, `Daw.layer`.
5. **Do not use stock Material 3 widgets as-is** (Button, Slider, Switch, TabRow, Card, AlertDialog…). Build custom components on `androidx.compose.foundation` + `Canvas` so nothing looks like default Android. You may use M3 internals only for accessibility semantics, fully restyled.
6. **Never delete a feature to make the UI look cleaner.** Relocate it to the correct depth tier (section 1B).

---

## 1. WHAT "CLEAN UI" MEANS HERE (read twice)

- **Perfect alignment**: everything sits on a **4dp grid**. Every width, height, padding and gap is a multiple of 4dp (tokens: 4, 8, 12, 16, 24, 32). Text baselines of sibling labels align. Icons are centered in their hit areas. Rows of controls share one vertical center line; columns share one left edge.
- **Consistency**: the same control looks and behaves identically everywhere. Same radius for same kind of surface, same padding for same kind of container.
- **Hierarchy through value, not decoration**: depth comes from neutral tone steps and z-layers, not shadows/glows/gradients. Max one soft shadow style (for floating layer only).
- **Rich like a real DAW, calm on the surface**: the app keeps the FULL pro feature set; the surface stays quiet because depth is organized (section 1B). Panels may be dense, but every touch target is ≥ **48dp**.
- **Restraint**: color means something (section 2). If an element doesn't need color, it's neutral.
- **Zero "AI slop"**: no purple/blue gradients, no glassmorphism/blur cards, no neon glows everywhere, no gradient buttons, no centered-everything layouts, no oversized rounded cards, no random drop shadows, no decorative emoji, no placeholder lorem text, no inconsistent paddings.

---

## 1B. SURFACE vs DEPTH — what "clean" means in FL Studio terms

Clean does NOT mean few features. The full professional toolset exists, organized in layers of depth so the surface always looks calm.

| Tier | What lives here | When visible |
|---|---|---|
| 0 Surface | Playlist/timeline, track headers, transport bar, add-track button | Always. Nothing else. |
| 1 Contextual | Action chips, popup menus, clip/track options | Only after a selection or gesture |
| 2 Panels | Mixer, instrument panel, piano roll, modules drawer, project drawer, BPM dial | One tap away. MAY be dense and rich like FLM: full tool sets, tabs, knobs, sliders |
| 3 Deep | Advanced params, envelopes/LFOs, settings, "More…" menus | Inside panels, behind tabs or collapsible groups |

Rules:
- Every feature is reachable in 3 taps or fewer from a consistent entry point.
- A feature lives at the lowest tier where it is still quick to use. Tier 2/3 controls NEVER appear on the Tier 0 surface.
- Dense panels stay structured: group by tabs and spacing (not boxes inside boxes), one heading per group, consistent control sizes, never a flat wall of knobs.
- Opening or closing a panel never shifts the surface layout; panels slide over it on their z-layer with a scrim, like FLM's drawers.
- Look and feel follow FLM: flat dark slate surfaces, circular floating buttons, solid channel-colored blocks, simple line/ring controls, no decoration.
- Reviewer rejects a PR if (a) a Tier 2/3 control is on the surface, (b) a panel is an unstructured pile of controls, or (c) a feature was deleted instead of relocated.

---

## 2. COLOR SYSTEM — 3 primaries + neutrals

Values below are measured from the FL Studio Mobile screenshots (adjust ±3% only if you sample the images and find a closer match). Define them once in `ui/theme/Palette.kt`. This is the only file allowed to contain `Color(0x…)`.

**Primaries (the only hues allowed):**

| Token | Hex | Meaning |
|---|---|---|
| `Mint` | `#cda1ff` | Master bus, active/selected state, playhead, positive confirm |
| `Coral` | `#bfcdf5` | Record, instrument channels, destructive/error |
| `Sky` | `#4DB8FF` | Drum/sampler channels, secondary info, loop/range |

Each primary has exactly 3 tonal steps generated by lightness only (same hue): `Base`, `Dim` (clip body on dark, ~55% mix with Workspace), `Faint` (~20% mix, for ghost notes/inactive tint). No other hues, ever.

**Neutrals (cool slate, not counted as primaries):**

| Token | Hex | Use |
|---|---|---|
| `N0 Workspace` | `#1F2429` | App background, empty timeline |
| `N1 Grid` | `#2A3036` | Grid cells, track lanes (alternate with N0 for beat shading) |
| `N2 Surface` | `#363D44` | Transport bar, mixer strips, panel bodies |
| `N3 Raised` | `#444C55` | Drawers, panel headers, module cards |
| `N4 Control` | `#C8CDDA` | Floating circular buttons, popup menus |
| `InkOnDark` | `#E6E9F0` | Primary text on N0–N3 |
| `InkMuted` | `#8C95A0` | Secondary labels, units |
| `InkOnLight` | `#363D44` | Text/icons on N4 |

**Rules:** track/clip colors are assigned only from the 3 primaries (cycle Coral → Sky → Mint; extra tracks reuse a primary at `Dim`). Master is always Mint. Disabled = 38% alpha of the same color. Text contrast ≥ 4.5:1.

---

## 3. TOKENS (all in `ui/theme/`)

- `Space`: `xs=4, sm=8, md=12, lg=16, xl=24, xxl=32` (dp).
- `Radii`: `none=0` (grid, rulers), `xs=4` (clips, note blocks), `sm=8` (knob caps, chips), `md=12` (popup menus, module cards, drawer top-inner corners), `full` (all circular buttons, FABs, fader thumbs are `xs`). One radius per surface type — never mix.
- `Type`: ONE family (bundle Noto Sans or Inter as `.ttf` under `res/font` — font binaries are fine, XML font-family files are not; load with `Font(R.font.x)` in code). Five sizes only: Display 22, Title 16, Label 12, Caption 10, Mono-numeric 14 (tabular numerals for BPM, time, dB).
- `Motion`: durations `fast=120ms, base=200ms, slow=320ms`; springs `snappy (dampingRatio 0.8, stiffness 700)`, `smooth (0.9, 300)`, `playful (0.65, 400)` used only for fan-out/knob settle. Respect the system animator-duration scale (reduced motion → fades only).
- `Layer`: see section 4.

Use `@JvmInline value class` for token wrappers where it reads well, `@Immutable`/`@Stable` on all theme classes, and `CompositionLocal`s exposed through a single `DawTheme { }` and an `object Daw { val colors; val space; … }` accessor.

---

## 4. Z-AXIS / LAYERING SYSTEM

Create `ui/theme/Layer.kt`:

```
object Layer {
    const val Workspace = 0f   // grid, lanes, empty space
    const val Content   = 1f   // clips, notes, mixer strips
    const val Playhead  = 2f   // playhead, selection rectangles, ruler markers
    const val Floating  = 3f   // circular floating buttons, add-FAB, action chips
    const val Panel     = 4f   // side drawers, module drawer, instrument panel
    const val Popup     = 5f   // context menus, BPM dial
    const val Modal     = 6f   // scrim + dialogs
    const val Toast     = 7f   // transient messages
}
```

- `Modifier.zIndex(...)` is ONLY used with these constants.
- Anything on Panel or above dims the layers below with a scrim from `N0` at 60% alpha (animated).
- **Floating buttons must not cover content**: reserve dedicated gutter lanes (e.g. a 48dp column at the left for Play/loop, a 56dp lane at the right edge for the drawer handle). FL Studio's own screenshots show buttons sitting on the ruler/track header — do NOT copy that; give them their own space.
- Same-layer siblings never intersect (verify with a debug overlay, section 9).

---

## 5. SCREEN SPEC (landscape-only, `sensorLandscape`, edge-to-edge)

Single inset owner at the root: apply `displayCutout` + `systemBars` insets ONCE; children never re-apply insets. (The black strip at the left of the screenshots is the cutout — keep it clear.)

Proportions measured from 2408×1080 screenshots; implement in dp on the 4dp grid.

**5.1 App shell** — vertical stack: Timeline area (weight 1f) → Transport bar (48dp).

**5.2 Transport bar** (N2, full width, divider line 1dp N3 on top):
Left: Mixer toggle, Channel/Piano-roll toggle (48dp icon buttons, active = Mint icon + 3dp Mint underline bar). Center cluster, strict order: REC (Coral dot, label below) · REV · **Play ring 56dp** · BPM (mono numeric, label "BPM" below) · CTRL (⋯). Right: Preview circle, CPU meter (48×4dp bar, Mint→ never red-green rainbow; use Mint, switch to Coral >85%), RAM readout, close button. Icon-over-caption stacks share one baseline across all items.

**5.3 Playlist**
- Ruler 36dp: bar numbers in 24dp circles (N4), tick marks, loop range in Sky Faint.
- Track header column 40dp wide: channel-type icon centered, selected track = full-height block in channel color.
- Track rows 48dp tall (clips 44dp, 2dp gap). Clips: radius `xs`, channel-color fill, 1dp lighter top edge, mini note preview drawn on Canvas, title in Caption at top-left with 4dp inset.
- Add-track FAB 56dp, N4 circle, centered below the last track, in the Floating layer.
- Clip selection → **action chips** (Copy, Delete, Snap, Edit, More…) appear in the Floating layer in a single evenly spaced row above/below the clip (never over neighbor clips' content: auto-flip side). Each chip 40dp N4 circle, 8dp gaps.
- "More…" opens a **popup menu** (Popup layer): N4 surface, radius `md`, items 48dp tall (Slice, Unlink loop, Combine, Mute, Unmute), 16dp horizontal padding, current/disabled states clear.
- Playhead: 2dp Mint line, Playhead layer, with a 12dp Mint cap in the ruler.

**5.4 Mixer**: strips 96dp wide, N2, 1dp N3 dividers. Order top→bottom: name (channel color, Label), fader (track 4dp, thumb 48×24dp radius `xs`, tick at 0 dB), pan knob (ring arc, 56dp), Solo toggle (pill 48×28), type icon block at bottom (selected = full channel color block with dark icon). Master strip first and pinned.

**5.5 Instrument / module panel**: right-side panel (Panel layer), width 50% of screen (max 520dp). Module card = N3, radius `md`, header 48dp (power toggle, icon, title, preset name + chevron, menu), tabs row (SAMPLE / FILTER / FLT ENV / FRQ ENV / LFO) with a **single sliding underline** that animates between tabs. Controls: **RingKnob** (56dp, 270° arc, value centered in mono), **VerticalSlider** (track 4dp, thumb 24dp circle), toggles (Reverse/Loop/Legato/Mono as text toggles, active = Mint). Waveshaper shows an XY curve on Canvas with draggable handle. "Add Module" row = 48dp, 40dp circle + label. Modules list opens as a left drawer (Panel layer, 50% width) with Search / Sort circles and a 48dp-row list.

**5.6 Project drawer** (slides from right, 56% width): header (logo, version, Help, Quit), tabs SONGS · PROJECT · SETTINGS · SHOP · SYNC with sliding underline; Songs tab has a left rail of circular buttons (Back, Search, Sort, Select, list-style toggle) and right rail of 56dp circles (New, Save, Import); rows 48dp, 16dp text inset. Project tab: key/value rows (label InkMuted left column, value right column, aligned on one vertical axis), Save / Save New / Quick Render as three equal-width buttons.

**5.7 BPM dial** (Popup layer): circular overlay centered on screen: 160dp ring, big mono BPM, time signature below, ±1 and ±10 buttons positioned on the ring at fixed angles, TAP and metronome buttons at the bottom corners, close X in the top-right gutter.

**5.8 Piano roll**: key column 40dp on the left (white/black keys, C-labels), note grid with beat/bar line weights, notes in channel color (radius `xs`, min 4dp tall at zoom-out), slide notes with diagonal marker, ghost notes in `Faint`, bar ruler. Note rows snap to a whole number of dp at every zoom level (no half-pixel blur). On-screen piano keyboard bottom sheet (Panel layer) sized to 40% of height; when no notes channel is selected show an inline hint in the sheet header, not floating over the keys.

---

## 6. KOTLIN & COMPOSE: USE THE FULL POWER

- Unidirectional data flow: `sealed interface UiState / UiEvent / UiEffect`, `StateFlow` in ViewModels, collect with `collectAsStateWithLifecycle`. Stateless composables take state + lambdas.
- Immutability: `kotlinx.collections.immutable` (`ImmutableList`) for any list passed into composables; `@Immutable` data classes; enable strong skipping.
- Performance (target device: Snapdragon 662, 6 GB RAM — assume low-end):
  - Draw hot paths (grid, waveforms, meters, notes) with a single `Canvas`/`drawWithCache`, not hundreds of composables.
  - Read fast-changing state (playhead position, meters) inside `Modifier.graphicsLayer {}` / draw lambdas, never in composition. Use `derivedStateOf` and `remember(key)` correctly.
  - `LazyColumn/LazyRow` with stable `key`s for lists. No nested scrolling of same axis.
  - Hold 60 fps while playing and scrolling; no allocations inside draw/pointer lambdas.
  - Add a Baseline Profile.
- Custom gestures with `pointerInput` + `awaitEachGesture` (knob: vertical drag with fine-tune on long-press + haptic detents; fader: magnetic snap to 0 dB; clips: drag/resize with snap).
- Idiomatic Kotlin: extension functions, small DSLs for theme/palette, `when` exhaustiveness on sealed types, `inline`/`value class` for zero-cost wrappers, no `!!`.
- Previews: every component has `@Preview` plus a state-gallery screen (debug build) showing all variants side by side.
- Package layout: `ui/theme` · `ui/components` · `ui/screens/{playlist,mixer,instrument,pianoroll,drawer}` · `ui/motion`. One component per file.

---

## 7. ANIMATION — smooth, purposeful, a little new

Every animation answers "what changed?". Use `Daw.motion` tokens only. Animate graphicsLayer properties (alpha/scale/translation), never layout size in hot paths.

1. **Play ⇄ Pause morph**: interpolate the two icon `Path`s on one Canvas (no crossfade).
2. **Beat-locked pulse**: the BPM label and Play ring pulse subtly (scale ≤ 1.04) exactly once per beat while playing, driven by the transport clock.
3. **Playhead comet**: playhead gets a short fading Mint trail (a 24dp gradient-alpha strip, same hue) proportional to playback speed; disappears when stopped.
4. **Action-chip fan-out**: chips spring out from the clip's center to their slots with 30ms stagger; collapse on dismiss.
5. **Sliding tab indicator**: one shared underline that stretches and settles (`playful` spring) between tabs; content crossfades with a 12dp directional slide.
6. **Drawer & panel**: slide from their edge with `smooth` spring; scrim fades; content inside staggers in (20ms steps, max 5 items).
7. **BPM dial reveal**: circular reveal expanding from the BPM button (Path clip), ring arc sweeps to the current value; value digits roll (AnimatedContent per digit).
8. **Knob arc**: arc sweeps with the value; on release the thumb settles with a micro-overshoot spring; haptic tick at detents.
9. **Meters with real ballistics**: instant attack, ~300ms decay, 1s peak-hold tick; mixer meters in the channel's color.
10. **Clip select**: 1.5dp Mint outline that "draws" around the clip (stroke-dash progress) in `fast`.
11. **Shared-element transitions** (`SharedTransitionLayout`) for mixer-strip ⇄ instrument-panel header when opening a channel.

Keep it fast: nothing over `slow` (320ms) except looping effects; no infinite animations that run while idle.

---

## 8. TEAM PLAN (Teamwork-Preview) — file ownership to avoid conflicts

- **Agent 1 — Design System** (blocks everyone): `ui/theme/*` (Palette, Space, Radii, Type, Motion, Layer, DawTheme, `Daw` accessor), icon set, base components (DawIconButton, FloatingCircle, PopupMenu, SlidingTabs, RingKnob, VerticalFader, Meter, ListRow, Divider). Merge to main FIRST.
- **Agent 2 — Shell & Drawers**: app shell, insets, transport bar, project drawer, modules drawer, BPM dial.
- **Agent 3 — Playlist & Piano roll**: ruler, tracks, clips, chips, popup, playhead, piano roll, keyboard.
- **Agent 4 — Mixer & Instrument panel**: mixer strips, module cards, waveshaper XY, tabs.
- **Agent 5 — Build & Guard**: delete XML, manifest theme, Gradle guard task + CI step, detekt rules, debug overlays, screenshot tests.
- **Agent 6 — Reviewer**: reads every PR against sections 0–7 and the checklist below; rejects on any violation.

Agents 2–4 may only consume tokens/components from Agent 1; if a token is missing, request it, don't invent it. Small commits, conventional messages (`ui(theme): …`, `build(guard): …`).

---

## 9. ENFORCEMENT (make it impossible to regress)

Add a Gradle task `verifyComposeOnlyUi` that fails the build if:
- any file exists under `src/main/res/{layout,values,menu,anim,color}/` (except none allowed), or any `drawable*/*.xml` other than launcher icon files;
- `Color(0x` appears outside `ui/theme/Palette.kt`;
- a numeric `.dp` / `.sp` literal appears in `ui/components` or `ui/screens` (only tokens allowed; `0.dp` excepted);
- `Modifier.zIndex(` is called with anything other than `Layer.*`;
- `androidx.compose.material3.Button|Slider|Switch|TabRow|Card|AlertDialog` is imported in app UI code.

Run it in the GitHub Actions workflow before assemble. Add a debug-only overlay (long-press logo) that draws bounds for every component by layer color and flags any same-layer intersection and any element off the 4dp grid.

---

## 10. DEFINITION OF DONE (reviewer must tick all)

- [ ] `verifyComposeOnlyUi` passes in CI; project builds and runs.
- [ ] No XML UI resources remain (only manifest + launcher icon).
- [ ] Only Mint / Coral / Sky + neutrals appear anywhere on screen.
- [ ] Every screen in section 5 matches the spec; spacing is on the 4dp grid; touch targets ≥ 48dp.
- [ ] Zero overlapping elements except intentional, tokenized z-layers; floating buttons never cover content.
- [ ] Corner radii follow the `Radii` table with no one-offs.
- [ ] All 11 animations implemented with tokens; reduced-motion respected.
- [ ] 60 fps playing + scrolling on a low-end device profile; no recomposition storms (check with Layout Inspector counts).
- [ ] State-gallery + previews exist for every component.
- [ ] Audio engine / native code untouched (git diff shows no changes there).
- [ ] Surface (Tier 0) shows only timeline, transport and add button; every other feature is reachable in ≤ 3 taps and NO feature was removed.
- [ ] Panels are dense but structured, never a flat wall of controls.

Start with Agent 1. Report the token files and base components before anyone else begins.
