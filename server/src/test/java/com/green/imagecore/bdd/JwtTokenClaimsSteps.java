package com.green.imagecore.bdd;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenClaimsSteps {

    @Given("a test user with id {long}, email {string}, username {string}, and roles:")
    public void a_test_user_with_id_email_username_and_roles() {
        throw new PendingException();
    }

    @When("I issue an access token for that user")
    public void i_issue_an_access_token_for_that_user() {
        throw new PendingException();
    }

    @Then("the token should be cryptographically verifiable")
    public void the_token_should_be_cryptographically_verifiable() {
        throw new PendingException();
    }

    @Then("the token claim {string} should equal {string}")
    public void the_token_claim_should_equal_string() {
        throw new PendingException();
    }

    @Then("the token claim {string} should equal {int}")
    public void the_token_claim_should_equal_int() {
        throw new PendingException();
    }

    @Then("the token claim {string} should contain:")
    public void the_token_claim_should_contain() {
        throw new PendingException();
    }

    @Then("the token should include standard time claims")
    public void the_token_should_include_standard_time_claims() {
        throw new PendingException();
    }

    @Then("the token expiration should be within {int} minutes from now")
    public void the_token_expiration_should_be_within_minutes_from_now() {
        throw new PendingException();
    }

    @When("I tamper with the token")
    public void i_tamper_with_the_token() {
        throw new PendingException();
    }

    @Then("decoding the token should fail")
    public void decoding_the_token_should_fail() {
        throw new PendingException();
    }
}

