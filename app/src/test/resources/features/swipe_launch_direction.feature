Feature: Directional swipe launch animation
  Apps opened by a horizontal swipe or an edge tap must slide in from the
  screen side their gesture is bound to, not always from the default
  right-hand side: the left gesture app (opened by a rightward fling or a
  left-edge tap) slides in from the left; the right gesture app (leftward
  fling or right-edge tap) slides in from the right. Other launch paths
  (tapping a slot, the drawer, double tap) keep the system animation.

  Background:
    Given usage access onboarding is already done
    And the launcher is started

  Scenario: Rightward fling pulls the left-side app in from the left
    When I fling right on the home screen
    Then the launched activity animates in from the left

  Scenario: Leftward fling pulls the right-side app in from the right
    When I fling left on the home screen
    Then the launched activity animates in from the right

  Scenario: Left edge tap launching the camera animates in from the left
    When I tap the left screen edge and press back
    Then the launched activity animates in from the left

  Scenario: Right edge tap launching the browser animates in from the right
    When I tap the right screen edge and press back
    Then the launched activity animates in from the right

  Scenario: Tapping a home screen slot keeps the default system animation
    And the launcher has these apps installed:
      | Cool |
    And home slot 0 is assigned to "Cool"
    When I tap home slot 0
    Then the launched activity uses the default system animation
