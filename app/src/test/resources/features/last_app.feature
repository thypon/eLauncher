Feature: Last used app on the home screen
  When usage access is granted, the home screen shows the most recently
  used app in a dedicated row and highlights recently used homescreen apps.

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |

  Scenario: The last used app appears on the home screen
    Given usage access is granted
    And the app "Alpha" was used 10 minutes ago
    When the launcher is started
    Then the last app row shows "Alpha"

  Scenario: Tapping the last used app opens it
    Given usage access is granted
    And the app "Alpha" was used 10 minutes ago
    When the launcher is started
    When I tap the last app row
    Then the "com.fixtures.alpha" app is launched

  Scenario: The last app ignores homescreen apps and system apps
    Given usage access is granted
    And the system settings app was used 5 minutes ago
    And home slot 1 is assigned to "Beta"
    And the app "Beta" was used 50 minutes ago
    And the app "Alpha" was used 20 minutes ago
    When the launcher is started
    Then the last app row shows "Alpha"

  Scenario: Recently used homescreen apps are shown in bold
    Given usage access is granted
    And home slot 1 is assigned to "Beta"
    And the app "Beta" was used 30 minutes ago
    When the launcher is started
    Then the home screen slot 1 shows "Beta" in bold
