Feature: Home screen gestures
  The home screen reacts to flings, double taps and long presses.
  Horizontal flings open the configured gesture app or a sensible default
  (dialer/camera to the left, browser to the right); vertical flings control
  the app drawer and the notification shade.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: Fling left opens the camera when no phone app can make calls
    When I fling left on the home screen
    Then the camera opens

  Scenario: Fling left opens the dialer when a phone app is installed
    Given a phone app "Dialer" is installed
    When I fling left on the home screen
    Then the dialer opens

  Scenario: Fling right opens the browser picker when no default browser exists
    When I fling right on the home screen
    Then the browser picker opens

  Scenario: Fling right opens the default browser app
    Given a browser app "Browse" is installed
    When I fling right on the home screen
    Then the "com.fixtures.browse" app is launched

  Scenario: Fling left opens the configured left gesture app
    Given the app "Cool" is assigned to the left gesture
    When I fling left on the home screen
    Then the "com.fixtures.cool" app is launched

  Scenario: Fling right opens the configured right gesture app
    Given the app "Cool" is assigned to the right gesture
    When I fling right on the home screen
    Then the "com.fixtures.cool" app is launched

  Scenario: A stale gesture package is cleared and the default is used
    Given the left gesture is assigned to the missing app "com.fixtures.gone"
    When I fling left on the home screen
    Then the camera opens
    And the left gesture preference is cleared

  Scenario: Swiping up opens the app drawer
    When I swipe up on the home screen
    Then the app drawer opens

  Scenario: Swiping down does not crash the launcher
    When I swipe down on the home screen
    Then the home screen is visible

  Scenario: Long pressing the home screen opens the settings
    When I long press the home screen
    Then the settings screen opens

  Scenario: Double tapping opens the last used launcher
    Given another launcher "Legacy" is installed
    When I double tap the home screen
    Then the "com.fixtures.legacy" app is launched