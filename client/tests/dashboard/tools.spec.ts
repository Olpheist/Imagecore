import { test, expect } from "../fixtures";
import { makeJwt, mockMeUser, mockResearcherUser, mockAdminUser, mockTools } from "../mocks";
import { mockLogin, mockMe, mockToolsAll, mockToolsGet, mockToolsDelete } from "../routes";


test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
});

// Auth

test("tools page renders for authenticated user", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    await expect(page).toHaveURL(/\/tools$/);
    await expect(page.getByRole("heading", { name: /analysis tools/i })).toBeVisible();
});

test("tools page redirects unauthenticated user to /login", async ({ page }) => {
    await page.goto("/dashboard/tools");

    await expect(page).toHaveURL(/\/login$/);
});

// Display

test("tools page: displays all tools returned from the API", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    for (const tool of mockTools) {
        await expect(page.getByText(tool.name)).toBeVisible();
        await expect(page.getByText(tool.description)).toBeVisible();
    }
});

test("tools page: displays category filter buttons", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    const uniqueCategories = [...new Set(mockTools.map((t) => t.category))];
    for (const category of uniqueCategories) {
        await expect(page.getByRole("button", { name: category })).toBeVisible();
    }
});

test("tools page: shows empty state when no tools returned", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page, []);

    await page.goto("/dashboard/tools");

    await expect(page.getByText(/no tools available/i)).toBeVisible();
});

test("tools page: shows error message when API call fails", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page, [], 500);

    await page.goto("/dashboard/tools");

    await expect(page.locator(".text-red-600")).toBeVisible();
});

// Create

test("tools page: New Tool button opens create modal", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    await page.getByRole("button", { name: /new tool/i }).click();

    await expect(page.getByRole("heading", { name: /create new tool/i })).toBeVisible();
});

test("tools page: create modal closes on cancel", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    await page.getByRole("button", { name: /new tool/i }).click();
    await expect(page.getByRole("heading", { name: /create new tool/i })).toBeVisible();

    await page.getByRole("button", { name: /cancel/i }).click();
    await expect(page.getByRole("heading", { name: /create new tool/i })).not.toBeVisible();
});

test("tools page: creating a tool calls POST and adds it to the list", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);

    const newTool = {
        toolId: 99,
        name: "New Test Tool",
        description: "A brand new tool",
        category: "Radiology",
        imageTag: "docker.io/myorg/tool:latest",
    };
    await mockToolsAll(page, mockTools, 200, newTool, 200);

    await page.goto("/dashboard/tools");

    await page.getByRole("button", { name: /new tool/i }).click();
    await page.getByPlaceholder(/lung nodule detector/i).fill(newTool.name);
    await page.getByPlaceholder(/briefly describe/i).fill(newTool.description);
    await page.getByPlaceholder(/radiology/i).fill(newTool.category);
    await page.getByPlaceholder(/docker\.io/i).fill(newTool.imageTag);
    await page.getByRole("button", { name: /^create tool$/i }).click();

    await expect(page.getByRole("heading", { name: /create new tool/i })).not.toBeVisible();
    await expect(page.getByText(newTool.name)).toBeVisible();
});

test("tools page: create failure shows error inside modal", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsAll(page, mockTools, 200, { message: "Server error" }, 500);

    await page.goto("/dashboard/tools");

    await page.getByRole("button", { name: /new tool/i }).click();
    await page.getByPlaceholder(/lung nodule detector/i).fill("Test");
    await page.getByPlaceholder(/briefly describe/i).fill("Desc");
    await page.getByPlaceholder(/radiology/i).fill("Cat");
    await page.getByPlaceholder(/docker\.io/i).fill("docker.io/test:latest");
    await page.getByRole("button", { name: /^create tool$/i }).click();

    await expect(page.locator(".text-red-600")).toBeVisible();
    await expect(page.getByRole("heading", { name: /create new tool/i })).toBeVisible();
});

// Delete

test("tools page: delete button is visible for ADMIN role", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockAdminUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    await expect(
        page.locator(`[data-testid="tool-card-1"] [data-testid="tool-delete-1"]`)
    ).toBeVisible();
});

test("tools page: delete button for is not visible for non-owner RESEARCHER role", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockResearcherUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    await expect(
        page.locator(`[data-testid="tool-card-1"] [data-testid="tool-delete-1"]`)
    ).not.toBeVisible();
});

test("tools page: delete button is not visible for PATIENT role", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockMeUser);
    await mockToolsGet(page);

    await page.goto("/dashboard/tools");

    await expect(
        page.locator(`[data-testid="tool-card-1"] [data-testid="tool-delete-1"]`)
    ).not.toBeVisible();
});

test("tools page: deleting a tool removes it from the list", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockAdminUser);
    await mockToolsGet(page);
    await mockToolsDelete(page, 1);

    await page.goto("/dashboard/tools");

    await expect(page.getByText("Organ Segmentation")).toBeVisible();
    await page.locator(`[data-testid="tool-card-1"] [data-testid="tool-delete-1"]`).click();
    await expect(page.getByText("Organ Segmentation")).not.toBeVisible();
});

test("tools page: delete failure shows error message", async ({ page, setToken }) => {
    const token = makeJwt();
    await setToken(token);
    await mockMe(page, mockAdminUser);
    await mockToolsGet(page);
    await mockToolsDelete(page, 1, 500);

    await page.goto("/dashboard/tools");

    await page.locator(`[data-testid="tool-card-1"] [data-testid="tool-delete-1"]`).click();

    await expect(page.locator(".text-red-600")).toBeVisible();
});
