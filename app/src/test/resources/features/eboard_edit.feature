Feature: eBoard edit mode and widget adder
  Long pressing empty board space opens the widget adder: a bottom popover
  half the screen high that lists every app's widget kind. Long pressing a
  widget enters edit mode, where each widget shows an (x) remove handle and
  can be resized and reordered. The add button exists only on an empty board.

  Background:
    Given a "Digital" widget provider offering "A digital clock widget" is installed
    And the eBoard is started

  Scenario: The add button exists only on an empty board
    Then the add widget button is visible
    When I add a widget from the widget adder
    Then a widget row is on the board
    And the add widget button is hidden

  Scenario: Empty space opens the widget adder popover
    When I long press the empty board space
    Then the widget adder popover opens
    And it covers half of the screen, anchored at the bottom
    And it lists the "Digital" widget kind "A digital clock widget"
    And each kind row shows a big preview with a small description

  Scenario: Pressing empty space exits edit mode
    When I add a widget from the widget adder
    And I long press the widget row
    Then the widget shows a remove handle
    And I press the empty board space
    Then edit mode is off
    And the widget shows no handles

  Scenario: Widget taps are not blocked
    When I add a widget from the widget adder
    And I tap the widget
    Then edit mode is off
    And a widget row is on the board

  Scenario: The adder popover can be reopened after use
    When I add a widget from the widget adder
    And I dismiss the widget adder popover
    And I hold the empty board space
    Then the widget adder popover opens again

  Scenario: The add widget button opens the popover repeatedly
    When I add a widget from the widget adder
    And I remove the only widget row
    And I tap the add widget button
    Then the widget adder popover opens again

  Scenario: The board follows the theme background
    Given the dark mode preference is on
    And the eBoard is restarted
    Then the board background is the dark theme background

  Scenario: Widgets lay out in two columns
    When I add a widget from the widget adder
    And I add a widget from the widget adder
    Then the board shows widgets in two columns

  Scenario: The widget view has a transparent background
    When I add a widget from the widget adder
    Then the widget view has a transparent background

  Scenario: Without binding permission the system confirmation appears
    Given binding widgets is not allowed
    When I tap the add widget button
    And the widget adder popover opens
    And I select the "Digital" widget kind
    Then the system bind confirmation opens
    And the widget binding is confirmed
    Then a widget row is on the board

  Scenario: The board leaves room for the system header
    Then the board stays clear of the system status bar

  Scenario: Long press on a widget shows the remove handle
    When I add a widget from the widget adder
    And I long press the widget row
    Then the widget shows a remove handle
    And the remove handle is clearly visible
    When I tap the remove handle
    Then the board shows the empty hint
    And the widget is forgotten by the board

  Scenario: Holding a widget (touch path) enters edit mode
    When I add a widget from the widget adder
    And I hold the widget row
    Then the widget shows a remove handle
    And the remove handle is clearly visible

  Scenario: Widgets can be reordered in edit mode
    Given the board has 2 widgets
    When I long press the widget row
    And the second widget is moved before the first
    Then the board order is "12,11"

  Scenario: Widgets can be resized in edit mode
    When I add a widget from the widget adder
    And I long press the widget row
    And the widget is resized to 300dp
    Then the widget height is stored as 300dp
    And the widget is rendered 300dp tall

  Scenario: Releasing a resize rebuilds the widget view
    When I add a widget from the widget adder
    And I long press the widget row
    And I note the current widget view
    And I drag the resize handle to 300dp
    Then the widget row shows a fresh widget view
    And the fresh widget view is 300dp tall

  Scenario: Taps reach the widget after it was resized
    When I add a widget from the widget adder
    And I long press the widget row
    And I drag the resize handle to 300dp
    And I press the empty board space
    And I watch the widget touches
    And I tap the widget
    Then the widget received the tap

  Scenario: A second resize keeps the widget view up to date
    When I add a widget from the widget adder
    And I long press the widget row
    And I drag the resize handle to 300dp
    And I note the current widget view
    And I drag the resize handle to 200dp
    Then the widget row shows a fresh widget view
    And the fresh widget view is 200dp tall

  Scenario: Releasing a resize when the provider is gone does not crash

  Scenario: Dragging the resize handle through the screen resizes the widget
    When I add a widget from the widget adder
    And I hold the widget row
    And I drag the resize handle to 300dp
    Then the widget is rendered 300dp tall

  Scenario: Holding the empty space below a widget opens the adder
    When I add a widget from the widget adder
    And I hold the empty board space below the widgets
    Then the widget adder popover opens

  Scenario: Widget rows are built on a plain widget context
    When I add a widget from the widget adder
    Then the widget row is built on a plain widget context

  Scenario: The adder preview backing is not a solid panel
    When I long press the empty board space
    Then the adder preview backing is transparent

  Scenario: Edit mode explains resizing and removal
    When I add a widget from the widget adder
    Then the edit mode hint is hidden
    And I hold the widget row
    Then the edit mode hint explains resizing and removal
    When I add a widget from the widget adder
    And I long press the widget row
    And the widget provider is unbound
    And I drag the resize handle to 300dp
    Then the widget height is stored as 300dp
