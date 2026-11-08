Feature: eBoard edge gestures follow the entry side
  Inside the board the system back/forward edge gesture is suppressed on
  the side the board was entered from — the same motion whose fling is
  disabled — so an accidental edge swipe cannot throw the user back to
  the launcher either. Without a launcher entry direction (direct
  launch) the system gestures stay untouched.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: Entry from the left excludes the left-edge system gesture
    When I fling right on the home screen
    And the eBoard dashboard opens on screen
    Then the board excludes the left screen edge from system gestures

  Scenario: Entry from the right excludes the right-edge system gesture
    Given the right gesture is assigned to eBoard
    When I fling left on the home screen
    And the eBoard dashboard opens on screen
    Then the board excludes the right screen edge from system gestures

  Scenario: A direct launch keeps system gestures intact
    Given the eBoard dashboard is opened directly
    Then the board excludes no screen edge from system gestures
