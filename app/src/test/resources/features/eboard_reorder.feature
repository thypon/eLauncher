Feature: Widget reorder
  Long press puts a widget into edit mode; keeping the finger down drags it
  between its neighbors (Olauncher's drag-to-reorder), and the settled order
  is remembered.

  Background:
    Given a widget provider is installed
    And binding widgets is allowed
    And the eBoard is started
    And the board has 2 widgets

  Scenario: Dragging a widget down swaps it with the next one
    When I long press the widget row
    And I drag the widget row down past the next one
    Then the board order is "12,11"

  Scenario: The dragged order is remembered across a restart
    When I long press the widget row
    And I drag the widget row down past the next one
    When the eBoard is restarted
    Then the board order is "12,11"
