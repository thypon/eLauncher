Feature: Settings screen
  The settings screen lets the user configure gestures, appearance and
  the number of home screen slots. Leaving the screen restarts the launcher.

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Cool |

  Scenario: Gesture buttons show their defaults
    Given the settings screen is open
    Then the left gesture button shows "eBoard"
    And the right gesture button shows "Default"

  Scenario: Assigning an app to the left gesture
    Given the settings screen is open
    When I tap the left gesture button
    And I select "Cool" in the app picker
    Then the left gesture button shows "Cool"
    And the left gesture package is stored as "com.fixtures.cool"

  Scenario: Resetting a gesture to its default
    Given the settings screen is open
    When I tap the left gesture button
    And I select "Cool" in the app picker
    And I tap the left gesture button
    And I press "Reset to Default" in the dialog
    Then the left gesture button shows "eBoard"
    And the left gesture package is stored as ""

  Scenario: Assigning eBoard to the left gesture
    Given the settings screen is open
    When I tap the left gesture button
    And I select "eBoard" in the app picker
    Then the left gesture button shows "eBoard"
    And the left gesture package is stored as "@eboard"

  Scenario: Cancelling the gesture picker keeps the assignment
    Given the settings screen is open
    When I tap the left gesture button
    And I select "Cool" in the app picker
    And I tap the left gesture button
    And I press "Cancel" in the dialog
    Then the left gesture button shows "Cool"
    And the left gesture package is stored as "com.fixtures.cool"

  Scenario: Dark mode follows the light system setting
    Given the settings screen is open
    Then the dark mode switch is off

  Scenario: Dark mode follows the night system setting
    Given the system is in night mode
    And the settings screen is open
    Then the dark mode switch is on

  Scenario: Toggling dark mode stores the preference
    Given the settings screen is open
    When I toggle the dark mode switch
    Then the dark mode preference is stored as "true"

  Scenario: Changing the number of apps
    Given the settings screen is open
    When I set the number of apps to 5
    Then the number of apps preference is stored as 5

  Scenario: Swiping up on settings restarts the launcher
    Given the settings screen is open
    When I swipe up on the settings screen
    Then the launcher is restarted

  Scenario: Long-pressing settings restarts the launcher
    Given the settings screen is open
    When I long-press the settings screen
    Then the launcher is restarted

  Scenario: Pressing back on settings restarts the launcher
    Given the settings screen is open
    When I press back on the settings screen
    Then the launcher is restarted

  Scenario: A stale right gesture assignment shows the right default
    Given the right gesture is assigned to a missing app
    And the settings screen is open
    Then the right gesture button shows "Default"

  Scenario: Dark mode preference styles the settings screen dark
    Given dark mode is turned on
    And the settings screen is open
    Then the settings screen uses the dark theme

  Scenario: Dark mode preference off styles the settings screen light
    Given dark mode is turned off
    And the settings screen is open
    Then the settings screen uses the light theme

  Scenario: Unset dark mode follows the night system setting on the settings screen
    Given the system is in night mode
    And the settings screen is open
    Then the settings screen uses the dark theme

  Scenario: Toggling dark mode re-themes the settings screen immediately
    Given the settings screen is open
    When I toggle the dark mode switch
    Then the settings screen uses the dark theme

  Scenario: Settings rows use the launcher typography
    Given the settings screen is open
    Then the settings rows use the launcher typography
