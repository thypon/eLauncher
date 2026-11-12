Feature: Home screen slots
  As an eInk device user
  I want a configurable set of app slots on the home screen
  So that my favourite apps are one tap away

  Background:
    Given usage access onboarding is already done
    And the launcher has these apps installed:
      | Alpha |
      | Beta  |
      | Gamma |

  Scenario: The home screen shows one slot per configured app
    Given the home screen has 3 slots configured
    And the launcher is started
    Then the home screen shows 3 slots
    And home slot 0 is labeled "App"

  Scenario: No slots are shown when the count is set to zero
    Given the home screen has 0 slots configured
    And the launcher is started
    Then the home screen shows 0 slots

  Scenario: An unassigned slot launches nothing
    Given the home screen has 2 slots configured
    And the launcher is started
    When I tap home slot 0
    Then no app was launched

  Scenario: An assigned slot launches its app
    Given the home screen has 2 slots configured
    And home slot 0 is assigned to "Beta"
    And the launcher is started
    When I tap home slot 0
    Then the "com.fixtures.beta" app is launched

  Scenario: Long-pressing a slot opens the app picker and persists the assignment
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I select "Beta" in the app picker
    And I confirm the suggested name
    Then home slot 0 is labeled "Beta"
    And the assignment for slot 0 is stored with app "Beta"

  Scenario: Assigning a slot with a custom name persists the custom name
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 1
    And I select "Gamma" in the app picker
    And I replace the name with "My Gamma" and confirm
    Then home slot 1 is labeled "My Gamma"
    And the assignment for slot 1 is stored with app "Gamma"

  Scenario: Assigning a link to a slot opens its address
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I press "Add link" in the dialog
    And I name the link "Hacker News" at "news.ycombinator.com"
    And I add the link
    Then home slot 0 is labeled "Hacker News"
    And home slot 0 stores the link "Hacker News" at "https://news.ycombinator.com"
    When I tap home slot 0
    Then the link address opens

  Scenario: Tapping a link slot again brings the open browser forward
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I press "Add link" in the dialog
    And I name the link "Hacker News" at "https://news.ycombinator.com"
    And I add the link
    When I tap home slot 0
    And I tap home slot 0
    Then the link brings the open browser forward

  Scenario: A link slot is marked open after being tapped
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I press "Add link" in the dialog
    And I name the link "Hacker News" at "https://news.ycombinator.com"
    And I add the link
    When I tap home slot 0
    Then home slot 0 is marked open

  Scenario: A link slot stays open across a restart inside the half hour
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I press "Add link" in the dialog
    And I name the link "Hacker News" at "https://news.ycombinator.com"
    And I add the link
    When I tap home slot 0
    And the launcher is relaunched
    Then home slot 0 is marked open

  Scenario: A link slot is no longer open after half an hour
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    And I press "Add link" in the dialog
    And I name the link "Hacker News" at "https://news.ycombinator.com"
    And I add the link
    When I tap home slot 0
    And half an hour passes
    And the launcher is relaunched
    Then home slot 0 is not marked open

  Scenario: An app slot is marked open without usage access
    Given the home screen has 2 slots configured
    And home slot 0 is assigned to "Beta"
    And the launcher is started
    When I tap home slot 0
    Then home slot 0 is marked open

  Scenario: An app slot is not marked open when usage access is granted
    Given the home screen has 2 slots configured
    And home slot 0 is assigned to "Beta"
    And usage access is granted
    And the launcher is started
    When I tap home slot 0
    Then home slot 0 is not marked open

  Scenario: The slot picker shows an icon per app
    Given the home screen has 2 slots configured
    And the launcher is started
    When I long-press home slot 0
    Then every app in the picker shows an icon