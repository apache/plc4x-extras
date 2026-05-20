Feature: Sending data to the Loki server
  Scenario: Build Message
    Given Labels supplied for the message "job", "test", "env", "dev"
    And Url Loki Server "http://localhost:3100/loki/api/v1/push"
    When The server returned an HTTP 204 response
    Then The instance displays “Log sent”