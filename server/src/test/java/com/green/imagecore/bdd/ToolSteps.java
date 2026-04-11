package com.green.imagecore.bdd;

import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.service.ToolService;
import com.green.imagecore.service.UserService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for medical imaging tool management.
 * These steps test the ToolService directly, mirroring the pattern established
 * in JwtTokenClaimsSteps — business logic is verified at the service layer
 * without going through the HTTP stack.
 * Note: @SuppressWarnings used because IntelliJ cannot detect Cucumber-Spring
 * runtime injection at compile time.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ToolSteps {

    @Autowired
    private ToolService toolService;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    /** Tools returned from a findAll() call. */
    private List<Tool> retrievedTools;

    /** A single tool returned from a create() or findByName() call. */
    private Tool selectedTool;

    /** Captures any exception thrown during a service call, used for negative scenarios. */
    private Exception thrownException;


    // Cleanup

    /**
     * Wipes all tool rows and clears the security context after each scenario
     * so tests remain fully independent.
     */
    @After
    public void cleanUp() {
        toolRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }


    // GIVEN

    @Given("the user is authenticated as an admin")
    public void the_user_is_authenticated_as_an_admin() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "adminUser", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Given("the user is authenticated as a clinician with username {string}")
    public void the_user_is_authenticated_as_a_clinician(String username) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Seeds the database with a catalog of tools from a Gherkin DataTable.
     * Resolves the creating user from the current security context.
     * Example:
     *   | name               | category     | description        | image_tag |
     *   | brain-segmentation | segmentation | Segments brain MRI | ...ecr... |
     */
    @Given("the following tools exist in the database:")
    public void the_following_tools_exist_in_the_database(DataTable table) {
        User user = currentAuthenticatedUser();
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            toolService.create(
                    row.get("name"),
                    user,
                    row.get("category"),
                    row.get("description"),
                    row.get("image_tag"),
                    null,
                    null,
                    SubscriptionTierCode.FREE
            );
        }
    }

    /**
     * Seeds a single named tool owned by the currently authenticated user.
     * Used by duplicate-name and delete scenarios where only the name matters.
     */
    @Given("a tool named {string} already exists")
    public void a_tool_named_already_exists(String name) {
        User user = currentAuthenticatedUser();
        this.selectedTool = toolService.create(name, user, "segmentation", null, null, null, null, SubscriptionTierCode.FREE);
    }

    /**
     * Seeds a single named tool owned by a specific user (by username).
     * Used for non-owner delete scenarios.
     */
    @Given("a tool named {string} already exists created by {string}")
    public void a_tool_named_already_exists_created_by(String name, String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        this.selectedTool = toolService.create(name, owner, "segmentation", null, null, null, null, SubscriptionTierCode.FREE);
    }

    @Given("no tools exist in the database")
    public void no_tools_exist_in_the_database() {
        toolRepository.deleteAll();
    }

    @Given("the user is authenticated as a researcher with username {string}")
    public void the_user_is_authenticated_as_a_researcher(String username) {
        userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@test.com");
            user.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            return userRepository.save(user);
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_RESEARCHER"))
                )
        );
    }


    // WHEN

    @When("the user retrieves all tools")
    public void the_user_retrieves_all_tools() {
        this.retrievedTools = toolService.findAll();
    }

    /**
     * Attempts to create a tool, capturing any exception for negative scenarios
     * such as duplicate name validation.
     */
    @When("the user creates a tool with name {string} and category {string}")
    public void the_user_creates_a_tool_with_name_and_category(String name, String category) {
        User user = currentAuthenticatedUser();
        try {
            this.selectedTool = toolService.create(name, user, category, null, null, null, null, SubscriptionTierCode.FREE);
            this.thrownException = null;
        } catch (Exception e) {
            this.thrownException = e;
            this.selectedTool = null;
        }
    }

    @When("the user creates a tool with the following details:")
    public void the_user_creates_a_tool_with_the_following_details(DataTable table) {
        User user = currentAuthenticatedUser();
        Map<String, String> fields = table.asMap();
        try {
            this.selectedTool = toolService.create(
                    fields.get("name"),
                    user,
                    fields.get("category"),
                    fields.getOrDefault("description", null),
                    fields.getOrDefault("imageTag", null),
                    null,
                    null,
                    SubscriptionTierCode.FREE
            );
            this.thrownException = null;
        } catch (Exception e) {
            this.thrownException = e;
            this.selectedTool = null;
        }
    }

    /**
     * Deletes using the ID stored from a prior Given/When step.
     * Requires "a tool named {string} already exists" to have run first.
     */
    @When("the user deletes that tool")
    public void the_user_deletes_that_tool() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            toolService.delete(selectedTool.getToolId(), auth);
            this.thrownException = null;
        } catch (Exception e) {
            this.thrownException = e;
        }
    }

    @When("the user attempts to delete a tool with id {long}")
    public void the_user_attempts_to_delete_a_tool_with_id(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            toolService.delete(id, auth);
            this.thrownException = null;
        } catch (Exception e) {
            this.thrownException = e;
        }
    }


    // THEN

    @Then("the tool list contains {int} tools")
    public void the_tool_list_contains_n_tools(int expected) {
        assertNotNull(retrievedTools, "Tool list should not be null");
        assertEquals(expected, retrievedTools.size());
    }

    @Then("the tool list is empty")
    public void the_tool_list_is_empty() {
        assertNotNull(retrievedTools, "Tool list should not be null");
        assertTrue(retrievedTools.isEmpty(), "Tool list should be empty");
    }

    @Then("each tool has a non-null name and category")
    public void each_tool_has_a_non_null_name_and_category() {
        assertNotNull(retrievedTools);
        for (Tool tool : retrievedTools) {
            assertNotNull(tool.getName(),     "Tool name should not be null");
            assertNotNull(tool.getCategory(), "Tool category should not be null");
        }
    }

    @Then("the created tool has name {string} and category {string}")
    public void the_created_tool_has_name_and_category(String name, String category) {
        assertNull(thrownException, "No exception should have been thrown");
        assertNotNull(selectedTool);
        assertEquals(name,     selectedTool.getName());
        assertEquals(category, selectedTool.getCategory());
    }

    @Then("the created tool has a non-null ID")
    public void the_created_tool_has_a_non_null_id() {
        assertNotNull(selectedTool.getToolId(), "DB-assigned ID should be present after persist");
    }

    @Then("the created tool has imageTag {string}")
    public void the_created_tool_has_image_tag(String imageTag) {
        assertEquals(imageTag, selectedTool.getImageTag());
    }

    @Then("the created tool has a null imageTag")
    public void the_created_tool_has_a_null_image_tag() {
        assertNull(selectedTool.getImageTag(), "imageTag should be null when not provided");
    }

    @Then("the tool is persisted in the database")
    public void the_tool_is_persisted_in_the_database() {
        assertTrue(
                toolRepository.existsByName(selectedTool.getName()),
                "Tool should be findable by name in the repository"
        );
    }

    @Then("the tool is removed from the database")
    public void the_tool_is_removed_from_the_database() {
        assertNull(thrownException, "No exception should have been thrown");
        assertFalse(
                toolRepository.existsById(selectedTool.getToolId()),
                "Tool should no longer exist in the repository"
        );
    }

    @Then("an AccessDeniedException is thrown")
    public void an_access_denied_exception_is_thrown() {
        assertNotNull(thrownException, "An exception should have been thrown");
        assertInstanceOf(AccessDeniedException.class, thrownException);
    }

    @Then("an IllegalArgumentException is thrown with message containing {string}")
    public void an_illegal_argument_exception_is_thrown_with_message(String fragment) {
        assertNotNull(thrownException, "An exception should have been thrown");
        assertInstanceOf(IllegalArgumentException.class, thrownException);
        assertTrue(
                thrownException.getMessage().contains(fragment),
                "Expected message to contain '" + fragment + "' but was: " + thrownException.getMessage()
        );
    }

    @Then("a ResourceNotFoundException is thrown with message containing {string}")
    public void a_resource_not_found_exception_is_thrown_with_message(String fragment) {
        assertNotNull(thrownException, "An exception should have been thrown");
        assertInstanceOf(ResourceNotFoundException.class, thrownException);
        assertTrue(
                thrownException.getMessage().contains(fragment),
                "Expected message to contain '" + fragment + "' but was: " + thrownException.getMessage()
        );
    }


    // Helpers

    /**
     * Resolves the currently authenticated user from the security context.
     * Throws if no authentication is set — all create/delete steps require
     * a preceding @Given auth step.
     */
    private User currentAuthenticatedUser() {
        String username = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "No authentication set — add a @Given auth step before this step"
        ).getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}