Feature: App drawer
  As an eInk device user
  I want a distraction-free drawer listing my installed apps
  So that I can launch them quickly

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |
      | Gamma |
    And the launcher is started
    And the app drawer is open

  Scenario: Drawer lists installed launchable apps alphabetically
    Then "Alpha" appears before "Beta" in the drawer
    And "Beta" appears before "Gamma" in the drawer

  Scenario: Tapping an app launches it
    When I tap the "Beta" app in the drawer
    Then the "com.fixtures.beta" app is launched
    And the drawer is closed again

  Scenario: Long-pressing an app opens the system app details screen
    When I long-press the "Gamma" app in the drawer
    Then the system app details screen is opened for "com.fixtures.gamma"