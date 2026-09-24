# Dark mode: the launcher honours the dark mode preference.
# When the preference is unset the system setting decides (covered by a
# classic unit test, because Robolectric cannot change the activity
# configuration at runtime).
Feature: Dark mode

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |
      | Gamma |

  Scenario: Dark mode preference shows the dark theme
    Given dark mode is turned on
    And the launcher is started
    Then the home screen uses the dark theme

  Scenario: Light mode preference shows the light theme
    Given dark mode is turned off
    And the launcher is started
    Then the home screen uses the light theme
