import { test, expect } from "./fixtures";
import { makeJwt, mockMeUser } from "./mocks";
import {
    mockLogin,
    mockLoginFailure,
    mockMe,
    mockRegister,
    mockRegisterFailure,
} from "./routes";

const getFormLoginButton = (page: any) =>
    page.getByRole("main").getByRole("button", { name: /^login$/i });

const getFormRegisterButton = (page: any) =>
    page.getByRole("main").getByRole("button", { name: /^register$/i });

test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
});

test("login page renders", async ({ page }) => {
    await page.goto("/login");

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText("Welcome Back")).toBeVisible();
    await expect(page.getByPlaceholder("Username")).toBeVisible();
    await expect(page.getByPlaceholder("Password")).toBeVisible();
    await expect(getFormLoginButton(page)).toBeVisible();
});

test("login success: mocks backend + header flips", async ({ page }) => {
    const token = makeJwt();

    await mockLogin(page, token);
    await mockMe(page, mockMeUser);

    await page.goto("/login");
    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Password").fill("password");
    await getFormLoginButton(page).click();

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });
});

test("login failure shows error message", async ({ page }) => {
    await mockLoginFailure(page);

    await page.goto("/login");
    await page.getByPlaceholder("Username").fill("nope");
    await page.getByPlaceholder("Password").fill("nope");
    await getFormLoginButton(page).click();

    await expect(page.locator(".text-red-600")).toBeVisible();
});

test("register success: mocks backend + header flips", async ({ page }) => {
    const token = makeJwt();

    await mockRegister(page, token);
    await mockMe(page, mockMeUser);

    await page.goto("/register");

    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Email").fill("test@example.com");

    const goodPassword = "password10";
    await page.getByPlaceholder(/^Password$/).fill(goodPassword);
    await page.getByPlaceholder(/^Confirm Password$/).fill(goodPassword);

    await getFormRegisterButton(page).click();

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByText("testuser")).toBeVisible({ timeout: 10000 });
});

test("register failure shows error message", async ({ page }) => {
    // make the backend fail regardless of password validity
    await mockRegisterFailure(page);

    await page.goto("/register");

    await page.getByPlaceholder("Username").fill("testuser");
    await page.getByPlaceholder("Email").fill("test@example.com");

    const goodPassword = "password10";
    await page.getByPlaceholder(/^Password$/).fill(goodPassword);
    await page.getByPlaceholder(/^Confirm Password$/).fill(goodPassword);

    await getFormRegisterButton(page).click();

    await expect(page.getByText(/already in use|bad request|error/i)).toBeVisible();
});