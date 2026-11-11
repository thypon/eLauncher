Feature: Animation toggle
  e-ink displays ghost on every animated redraw, so all animations are
  disabled by default: app launches, board exits and the widget adder
  popover switch instantly. The settings switch re-enables them, restoring
  the directional slide launches, the reverse board exit animation and the
  bottom slide of the adder popover.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: The animations switch starts off
    Given the settings screen is open
    Then the animations switch is off

  Scenario: A disabled launch cancels all window animations
    When I fling right on the home screen
    Then the launched activity uses no animation

  Scenario: Enabling animations restores the directional slide
    Given animations are enabled
    When I fling right on the home screen
    Then the launched activity animates in from the left

  Scenario: A disabled board exit skips the reverse animation
    When I fling right on the home screen
    And the eBoard dashboard opens on screen
    And I fling left on the eBoard dashboard
    Then the eBoard dashboard exits without animation
    And the home screen is visible

  Scenario: The widget adder popover has no slide animation by default
    Given the eBoard is started
    When I tap the add widget button
    And the widget adder popover opens
    Then the widget adder popover has no slide animation

  Scenario: Enabling animations slides the adder popover up
    Given animations are enabled
    And the eBoard is started
    When I tap the add widget button
    And the widget adder popover opens
    Then the widget adder popover animates in from the bottom
