# First-launch onboarding: launcher redirects to the system usage-access
# screen when usage permission is missing, and only once.
Feature: First launch onboarding
  eLauncher needs the usage access permission to show recently used apps.
  On the first start it should guide the user to the system settings screen.

  Scenario: First launch opens the usage access settings
    Given no onboarding has happened yet
    And usage stats are denied
    And the launcher is started
    Then the system usage access screen is opened
    And onboarding is marked as done

  Scenario: Second launch does not ask again
    Given no onboarding has happened yet
    And usage stats are denied
    And the launcher is started
    And the system usage access screen is opened
    And the launcher is started again
    Then no app was launched

  Scenario: Usage permission granted skips onboarding
    Given no onboarding has happened yet
    And usage stats are allowed
    And the launcher is started
    And no app was launched
