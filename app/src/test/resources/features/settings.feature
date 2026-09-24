Feature: Settings screen
  The settings screen lets the user configure gestures, appearance and
  the number of home screen slots. Leaving the screen restarts the launcher.

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Cool |

  Scenario: Gesture buttons default to "Default"
    Given the settings screen is open
    Then the left gesture button shows "Default"
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
    Then the left gesture button shows "Default"
    And the left gesture package is stored as ""

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
