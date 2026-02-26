import { test, expect } from "./fixtures";
import { makeJwt } from "./mocks";

test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
});

test("public: / is accessible without token", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/$/);
});

test("protected: /dashboard redirects to /login when not authed", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login$/);
});

test("authed user can access protected route", async ({ page, setToken }) => {
    await setToken(makeJwt());

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/dashboard$/);
});

test("expired token redirects to /login", async ({ page, setToken }) => {
    await setToken(makeJwt(-60));

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login$/);
});