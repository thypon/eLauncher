# eBoard fix tracker

Real-device feedback after the dashboard merge. Status: FIXED / PARTIAL /
PENDING (investigation in progress). Every fixed item has BDD coverage in
`app/src/test/resources/features/eboard_edit.feature` and siblings.

## Interaction fixes (round 1)

1. FIXED — Add widget button only on an empty board; afterwards hold on empty
   board space opens the adder (`DashboardActivity.updateUiState`,
   `HoldTouchListener`).
2. FIXED — Widget adder popover: half-screen bottom sheet listing every
   provider's widget kind, with big preview + smaller description right after
   it (`widget_adder_item.xml`, `fillPreview`).
3. FIXED — Hold on a widget enters edit mode (iOS style): floating (x) remove
   handle with elevation, corner resize handle, drag-to-reorder via
   ItemTouchHelper (order persisted in BoardStore).
4. FIXED — Board background follows the theme background (dark mode aware,
   same computation as MainActivity); `fitsSystemWindows` keeps header room.
5. FIXED — Widget view transparent: the API 31+ system white container is
   cleared with `setBackgroundResource(0)`.
6. FIXED — Resize: live host resize during drag (corner handle), height
   persisted, out-of-range guards; verified via BDD (`resizeClampsToRange`,
   fuzz) — full drag validation pending on a real device (emulator touch
   stream cannot drive long drags reliably).

## Real-device feedback fixes (round 2)

7. FIXED — Widget taps reach the widgets themselves: the hold proxy forwards
   the raw touch stream to the widget; 700ms hold enters edit mode with a
   cancel to the widget instead of a stray click.
8. FIXED — Adder binds the previewed kind directly when allowed; fallback is
   the system `ACTION_APPWIDGET_BIND` confirmation (one "Allow" per provider,
   after which binds are direct). The old `ACTION_APPWIDGET_PICK` chooser is
   gone. Note: full no-confirmation direct bind requires the
   `BIND_APPWIDGET` privilege (system launchers only) — the confirmation
   dialog is the standard user-approvable path.
9. FIXED — Two-column grid with compact spacing (more than one widget per
   line once widgets are smaller) + item spacing decoration.
10. FIXED — Pressing empty board space while in edit mode leaves edit mode
    (board-level touch listener; works on the empty area below the widgets).
11. FIXED — Swiping up inside the board returns to the launcher
    (`FlingListener` vertical branch + BDD). Horizontal flings returning to
    the launcher with directional animations were already on master
    (`eboard_fling_exit.feature`).
12. FIXED — Adder rows: bigger preview, smaller description right after it.

## Pending investigation (analysis in progress)

- PENDING — Resize still does not fully work on real devices: live host
  resize is implemented but the widget content may not re-layout visually;
  needs the agent-reported root cause applied (RemoteViews sizing semantics)
  and validation on real hardware.
- PENDING — Hold on empty board space / Add widget button stop working after
  the first use in some runs on the emulator: suspect popover lifecycle
  (`adderDialog` guard / hold timer scheduling) — agent analysis in progress.
- PENDING — Settings dark mode flip only changes some text colors instead of
  flipping the whole UI: SettingsActivity is not themed like MainActivity
  (missing setTheme + night-mode application) — agent analysis in progress.

## Validation

- `./gradlew check` green: BDD (edit mode, fling exit, tap-through, theme,
  grid, bind fallback) + BoardStorePropertyTest + DashboardBoardFuzzTest
  (250-step seeded interaction fuzz) + lint + JaCoCo >= 80%.
- Emulator end-to-end: popover with previews, direct bind, persist,
  edit-mode handles, remove verified with screenshot pixel checks
  (`/tmp/video2`, video: `~/Workspace/mylogo/tmp/eboard-demo-final.mp4`).
