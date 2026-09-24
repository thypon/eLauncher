Feature: Launcher home screen
  As an eInk device user
  I want the launcher to show my home screen
  So that I can start using the device

  Scenario: Launcher starts and shows the home screen
    Given the launcher is started
    Then the home screen is visible
