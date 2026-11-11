Feature: eBoard edit mode and widget adder
  Long pressing empty board space opens the widget adder: a bottom popover
  half the screen high that lists every app's widget kind. Long pressing a
  widget enters edit mode, where each widget shows an (x) remove handle and
  can be reordered. The add button exists only on an empty board.

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

  Scenario: The board follows the theme background
    Given the dark mode preference is on
    And the eBoard is restarted
    Then the board background is the dark theme background

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

  Scenario: Holding the empty space below a widget opens the adder
    When I add a widget from the widget adder
    And I hold the empty board space below the widgets
    Then the widget adder popover opens

  Scenario: Widget rows are built on a plain widget context
    When I add a widget from the widget adder
    Then the widget row is built on a plain widget context
