# Orientation lock: the launcher requests portrait orientation by default so
# it never rotates to landscape. The "Lock Portrait" setting disables the lock
# (UNSPECIFIED → the system decides). Applies to both the launcher and the
# settings screen.
Feature: Orientation lock

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |
      | Gamma |

  Scenario: The launcher requests portrait by default
    And the launcher is started
    Then the launcher requests portrait orientation

  Scenario: Portrait lock on requests portrait
    Given portrait lock is turned on
    And the launcher is started
    Then the launcher requests portrait orientation

  Scenario: Portrait lock off follows the system orientation
    Given portrait lock is turned off
    And the launcher is started
    Then the launcher follows the system orientation

  Scenario: The settings screen requests portrait by default
    And the settings screen is open
    Then the settings screen requests portrait orientation

  Scenario: The settings screen follows the system when the lock is off
    Given portrait lock is turned off
    And the settings screen is open
    Then the settings screen follows the system orientation

  Scenario: Toggling the lock applies immediately and persists
    Given the settings screen is open
    Then the lock portrait switch is on
    When I toggle the lock portrait switch
    Then the settings screen follows the system orientation
    And the lock portrait preference is stored as "false"
