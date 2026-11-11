Feature: Widget taps
  A widget's own views can receive every tap (on), only double taps
  (double), or nothing at all (off), ported from Olauncher. In the off
  modes the touches go to the board as if the widget weren't there.

  Background:
    Given a widget provider is installed
    And binding widgets is allowed
    And the eBoard is started
    And a widget is on the board

  Scenario: New widgets receive taps
    Then the widget taps are "on"

  Scenario: Taps-on widgets keep touches for themselves
    Given I watch the widget's home gestures
    And the widget taps are set to "on"
    When I tap the widget
    Then the widget's home gestures saw nothing

  Scenario: Taps-off widgets hand touches to the board
    Given I watch the widget's home gestures
    And the widget taps are set to "off"
    When I tap the widget
    Then the widget's home gestures saw the tap

  Scenario: The tap mode survives a restart
    When the widget taps are set to "off"
    And the eBoard is restarted
    Then the widget taps are "off"

  Scenario: The edit bar cycles through the tap modes
    When I long press the widget row
    And I tap the tap mode button
    Then the widget taps are "double"
    When I tap the tap mode button
    Then the widget taps are "off"
