Feature: eBoard dashboard
  The launcher's default left-side gesture — opened by a rightward fling on
  the home screen, like pulling the left edge inward — opens the eBoard
  dashboard, a macOS-Dashboard-style board of persistent widgets that is
  also a full launcher entry point named "eBoard" with its own icon.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: Fling right opens the eBoard dashboard
    When I fling right on the home screen
    Then the eBoard dashboard opens

  Scenario: The board is offered as a launcher entry named eBoard
    Then the launcher lists an "eBoard" entry

  Scenario: A custom left gesture still overrides the dashboard
    Given the app "Cool" is assigned to the left gesture
    When I fling right on the home screen
    Then the "com.fixtures.cool" app is launched

  Scenario: A left gesture assigned to eBoard opens the dashboard
    Given the left gesture is assigned to eBoard
    When I fling right on the home screen
    Then the eBoard dashboard opens
