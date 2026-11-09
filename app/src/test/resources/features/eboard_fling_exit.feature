Feature: Fling back out of the eBoard dashboard
  The eBoard remembers the fling motion that opened it. Inside the board
  that motion is disabled, so it cannot immediately throw the user back
  to the launcher. The opposite horizontal fling closes the board with
  the reverse directional animation: the board slides out to the side it
  entered from and the launcher home returns from the other side.
  Without a launcher entry direction (direct launch) horizontal flings
  do nothing and back still closes the board.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: The entry fling motion is disabled inside the board
    When I fling right on the home screen
    And the eBoard dashboard opens on screen
    And I fling right on the eBoard dashboard
    Then the eBoard dashboard stays open

  Scenario: The opposite fling returns to the launcher with the reverse animation
    When I fling right on the home screen
    And the eBoard dashboard opens on screen
    And I fling left on the eBoard dashboard
    Then the eBoard dashboard animates out to the left
    And the home screen is visible

  Scenario: Edge tap entry disables the same motion
    When I tap the left screen edge and press back
    And the eBoard dashboard opens on screen
    And I fling right on the eBoard dashboard
    Then the eBoard dashboard stays open

  Scenario: Entry from the right flips the enabled direction
    Given the right gesture is assigned to eBoard
    When I fling left on the home screen
    And the eBoard dashboard opens on screen
    And I fling left on the eBoard dashboard
    Then the eBoard dashboard stays open
    And I fling right on the eBoard dashboard
    Then the eBoard dashboard animates out to the right
    And the home screen is visible

  Scenario: Swiping up does not exit the board
    Given the eBoard dashboard is opened directly
    And I fling up on the eBoard dashboard
    Then the eBoard dashboard stays open

  Scenario: A direct launch keeps horizontal flings inert
    Given the eBoard dashboard is opened directly
    And I fling left on the eBoard dashboard
    And I fling right on the eBoard dashboard
    Then the eBoard dashboard stays open

  Scenario: Back press still closes the board after a disabled fling
    Given the eBoard dashboard is opened directly
    And I fling right on the eBoard dashboard
    When I press back on the eBoard dashboard
    Then the home screen is visible

  Scenario: Calling home leaves the eBoard for the main view
    Given the eBoard dashboard is opened directly
    When I call the home launcher
    Then the home screen is visible
    And the main view reclaims the home task over the board
