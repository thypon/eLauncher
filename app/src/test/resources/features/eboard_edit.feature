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
    And each kind row shows a widget preview

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
