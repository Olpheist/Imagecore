import { test, expect } from "@playwright/test";
import { makeJwt } from "./util";

const mockMeUser = {
    id: "1",
    username: "testuser",
    email: "test@example.com",
    userRoles: [{ roleName: "USER" }],
};

test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.addInitScript(() => {
        localStorage.clear();
        sessionStorage.clear();
    });
});

test("logout: header flips back to Login", async ({ page }) => {
    const token = makeJwt();

    // mock login + fetchMe
    await page.route("**/localhost:8080/api/auth/login**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({ token }),
        });
    });

    await page.route("**/localhost:8080/api/user/me**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(mockMeUser),
        });
    });

    await page.goto("/login");
    await expect(page).toHaveURL(/\/login$/);

    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Password").fill("password");
    await page.getByRole("main").getByRole("button", { name: /^login$/i }).click();

    await expect(page).toHaveURL(/\/$/);

    await expect(page.getByText("Welcome,")).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });

    await page
        .getByRole("banner")
        .getByRole("button", { name: /welcome,/i })
        .click();

    await page.getByRole("menuitem", { name: /logout/i }).click();

    await expect(page).toHaveURL(/\/$/);

    await expect(
        page.getByRole("banner").getByRole("button", { name: /^login$/i }),
    ).toBeVisible();

    await expect(page.getByText("Welcome,")).not.toBeVisible();
});