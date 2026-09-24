Feature: Edge gestures and back navigation
  Touches starting within 50dp of a screen edge trigger the left/right gesture
  on the next back press. A horizontal edge swipe wider than 100px marks a
  system back gesture which the launcher swallows instead of finishing.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: Tapping the left edge and pressing back opens the camera by default
    When I tap the left screen edge and press back
    Then the camera opens

  Scenario: Tapping the right edge and pressing back opens the browser picker
    When I tap the right screen edge and press back
    Then the browser picker opens

  Scenario: Tapping the right edge and pressing back opens the configured app
    Given the app "Cool" is assigned to the right gesture
    When I tap the right screen edge and press back
    Then the "com.fixtures.cool" app is launched

  Scenario: A slow edge back-swipe is swallowed on back press
    When I swipe inward from the left edge and press back
    Then no app was launched
    And the home screen is visible

  Scenario: Edge gesture flags reset after a back press
    When I tap the left screen edge and press back
    Then the camera opens
    When I press back
    Then no app was launched