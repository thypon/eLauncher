Feature: Edge gestures and back navigation
  Touches starting within 50dp of a screen edge trigger the left/right gesture
  on the next back press. A slow inward edge swipe is swallowed as a system
  back gesture; a fast inward edge fling opens the matching side's gesture app
  directly, and an outward edge fling launches nothing.

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

  Scenario: A fast inward fling from the left edge opens the camera by default
    When I fling inward from the left screen edge
    Then the camera opens

  Scenario: A fast inward fling from the right edge opens the browser picker
    When I fling inward from the right screen edge
    Then the browser picker opens

  Scenario: A fast inward fling from the left edge opens the configured app
    Given the app "Cool" is assigned to the left gesture
    When I fling inward from the left screen edge
    Then the "com.fixtures.cool" app is launched

  Scenario: A fast inward fling from the right edge opens the configured app
    Given the app "Cool" is assigned to the right gesture
    When I fling inward from the right screen edge
    Then the "com.fixtures.cool" app is launched

  Scenario: An outward fling from the edge launches nothing
    When I fling outward from the left screen edge
    Then no app was launched

  Scenario: Back press after an edge fling does not launch the app again
    When I fling inward from the left screen edge
    Then the camera opens
    When I press back
    Then no app was launched

  Scenario: Edge gesture flags reset after a back press
    When I tap the left screen edge and press back
    Then the camera opens
    When I press back
    Then no app was launched
