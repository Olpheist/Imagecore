import { test, expect } from "./fixtures";
import { mockMeUser } from "./mocks";
import {
    mockCsrf,
    mockLoginFailure,
    mockRegisterFailure,
    mockMeUnauthorized,
} from "./routes";

const getFormLoginButton = (page: any) =>
    page.getByRole("main").getByRole("button", { name: /^login$/i });

const getFormRegisterButton = (page: any) =>
    page.getByRole("main").getByRole("button", { name: /^register$/i });

test.beforeEach(async ({ context }) => {
    await context.clearCookies();
});

test("login page renders", async ({ page }) => {
    await mockMeUnauthorized(page);

    await page.goto("/login");

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText("Welcome Back")).toBeVisible();
    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await expect(page.getByPlaceholder("Password")).toBeVisible();
    await expect(getFormLoginButton(page)).toBeVisible();
});

test("login success: mocks backend + header flips", async ({ page }) => {
    let loggedIn = false;

    await mockMeUnauthorized(page);
    await mockCsrf(page);

    await page.unroute("**/api/users/me");
    await page.route("**/api/users/me", async (route) => {
        if (!loggedIn) {
            await route.fulfill({
                status: 401,
                contentType: "application/json",
                body: JSON.stringify({
                    status: 401,
                    error: "Unauthorized",
                    message: "Unauthorized",
                    path: "/users/me",
                }),
            });
            return;
        }

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(mockMeUser),
        });
    });

    await page.route("**/api/auth/login", async (route) => {
        loggedIn = true;

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            headers: {
                "set-cookie": "access_token=fake-cookie-jwt; HttpOnly; Path=/",
            },
            body: JSON.stringify({
                id: mockMeUser.id,
                username: mockMeUser.username,
                email: mockMeUser.email,
            }),
        });
    });

    await page.goto("/login");

    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Password").fill("password");
    await getFormLoginButton(page).click();

    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });
});

test("login failure shows error message", async ({ page }) => {
    await mockMeUnauthorized(page);
    await mockCsrf(page);
    await mockLoginFailure(page);

    await page.goto("/login");
    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await page.getByPlaceholder("Username").fill("nope");
    await page.getByPlaceholder("Password").fill("nope");
    await getFormLoginButton(page).click();

    await expect(page.getByText(/invalid|bad credentials|error/i)).toBeVisible();
});

test("register success: mocks backend + header flips", async ({ page }) => {
    let registered = false;

    await mockMeUnauthorized(page);
    await mockCsrf(page);

    await page.unroute("**/api/users/me");
    await page.route("**/api/users/me", async (route) => {
        if (!registered) {
            await route.fulfill({
                status: 401,
                contentType: "application/json",
                body: JSON.stringify({
                    status: 401,
                    error: "Unauthorized",
                    message: "Unauthorized",
                    path: "/users/me",
                }),
            });
            return;
        }

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(mockMeUser),
        });
    });

    await page.route("**/api/auth/register", async (route) => {
        registered = true;

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            headers: {
                "set-cookie": "access_token=fake-cookie-jwt; HttpOnly; Path=/",
            },
            body: JSON.stringify({
                id: mockMeUser.id,
                username: mockMeUser.username,
                email: mockMeUser.email,
            }),
        });
    });

    await page.goto("/register");

    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Email").fill("test@example.com");

    const goodPassword = "password10";
    await page.getByPlaceholder(/^Password$/).fill(goodPassword);
    await page.getByPlaceholder(/^Confirm Password$/).fill(goodPassword);

    await getFormRegisterButton(page).click();

    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });
});

test("register failure shows error message", async ({ page }) => {
    await mockMeUnauthorized(page);
    await mockCsrf(page);
    await mockRegisterFailure(page);

    await page.goto("/register");

    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Email").fill("test@example.com");

    const goodPassword = "password10";
    await page.getByPlaceholder(/^Password$/).fill(goodPassword);
    await page.getByPlaceholder(/^Confirm Password$/).fill(goodPassword);

    await getFormRegisterButton(page).click();

    await expect(page.getByText(/already in use/i)).toBeVisible();
});