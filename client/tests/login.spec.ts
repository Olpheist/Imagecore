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

const getFormLoginButton = (page: any) =>
    page.getByRole("main").getByRole("button", { name: /^login$/i });

test("login page renders", async ({ page }) => {
    await page.goto("/login");

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText("Welcome Back")).toBeVisible();
    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await expect(page.getByPlaceholder("Password")).toBeVisible();

    await expect(getFormLoginButton(page)).toBeVisible();
});

test("login page: clicking Register goes to /register", async ({ page }) => {
    await page.goto("/login");
    await expect(page).toHaveURL(/\/login$/);

    await page.getByRole("main").getByRole("button", { name: "Register" }).click();
    await expect(page).toHaveURL(/\/register$/);
});

test("login success: mocks backend + header flips", async ({ page }) => {
    const token = makeJwt();

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

    await getFormLoginButton(page).click();

    await expect(page).toHaveURL(/\/$/);
    await expect(page).not.toHaveURL(/\/login$/);

    await expect(page).not.toHaveURL(/\/login122112$/);

    await expect(page.getByText("Welcome,")).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });
});

test("login failure shows error message", async ({ page }) => {
    await page.route("**/localhost:8080/api/auth/login**", async (route) => {
        await route.fulfill({
            status: 401,
            contentType: "application/json",
            body: JSON.stringify({ message: "Bad credentials" }),
        });
    });

    await page.goto("/login");
    await expect(page).toHaveURL(/\/login$/);

    await page.getByPlaceholder("Username").fill("nope");
    await page.getByPlaceholder("Password").fill("nope");

    await getFormLoginButton(page).click();

    await expect(page.locator(".text-red-600")).toBeVisible();
});