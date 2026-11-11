Feature: Animation toggle
  Animations are on by default, like in any launcher: directional slide
  launches, the reverse board exit animation and the adder popover's
  bottom slide. The settings switch turns them all off at once for
  e-ink displays, which ghost on every animated redraw.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: The animations switch starts on
    Given the settings screen is open
    Then the animations switch is on

  Scenario: A directional launch slides in from the gesture side
    When I fling right on the home screen
    Then the launched activity animates in from the left

  Scenario: Disabling animations cancels all window animations
    Given animations are disabled
    When I fling right on the home screen
    Then the launched activity uses no animation

  Scenario: The board exit animates by default
    When I fling right on the home screen
    And the eBoard dashboard opens on screen
    And I fling left on the eBoard dashboard
    Then the eBoard dashboard animates out to the left
    And the home screen is visible

  Scenario: A disabled board exit skips the reverse animation
    Given animations are disabled
    When I fling right on the home screen
    And the eBoard dashboard opens on screen
    And I fling left on the eBoard dashboard
    Then the eBoard dashboard exits without animation
    And the home screen is visible

  Scenario: The widget adder popover slides up by default
    Given the eBoard is started
    When I tap the add widget button
    And the widget adder popover opens
    Then the widget adder popover animates in from the bottom

  Scenario: Disabling animations stills the adder popover
    Given animations are disabled
    And the eBoard is started
    When I tap the add widget button
    And the widget adder popover opens
    Then the widget adder popover has no slide animation
