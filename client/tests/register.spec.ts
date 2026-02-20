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

const getFormRegisterButton = (page: any) =>
    page.getByRole("main").getByRole("button", { name: /^register$/i });

test("register page renders", async ({ page }) => {
    await page.goto("/register");

    await expect(page).toHaveURL(/\/register$/);
    await expect(page.getByRole("heading", { name: "Create Account" })).toBeVisible();

    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await expect(page.getByPlaceholder("Email")).toBeVisible();
    await expect(page.getByPlaceholder("Password")).toBeVisible();

    await expect(getFormRegisterButton(page)).toBeVisible();
});

test("register page: clicking Login goes to /login", async ({ page }) => {
    await page.goto("/register");
    await expect(page).toHaveURL(/\/register$/);

    await page.getByRole("main").getByRole("button", { name: /^login$/i }).click();
    await expect(page).toHaveURL(/\/login$/);
});

test("register success: mocks backend + header flips", async ({ page }) => {
    const token = makeJwt();

    await page.route("**/localhost:8080/api/auth/register**", async (route) => {
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

    await page.goto("/register");
    await expect(page).toHaveURL(/\/register$/);

    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Email").fill("test@example.com");
    await page.getByPlaceholder("Password").fill("password");

    await getFormRegisterButton(page).click();

    await expect(page).toHaveURL(/\/$/);

    await expect(page.getByText("Welcome,")).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });
});

test("register failure shows error message", async ({ page }) => {
    await page.route("**/localhost:8080/api/auth/register**", async (route) => {
        await route.fulfill({
            status: 400,
            contentType: "application/json",
            body: JSON.stringify({ message: "Registration failed" }),
        });
    });

    await page.goto("/register");
    await expect(page).toHaveURL(/\/register$/);

    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Email").fill("test@example.com");
    await page.getByPlaceholder("Password").fill("bad");

    await getFormRegisterButton(page).click();

    await expect(page.locator(".text-red-600")).toBeVisible();
});