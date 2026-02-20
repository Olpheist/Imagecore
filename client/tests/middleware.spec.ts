import { test, expect } from "@playwright/test";
import { makeJwt } from "./util";

const TOKEN_KEY = "imagecore.jwt";

const setToken = async (page: any, token: string): Promise<void> => {
    await page.evaluate(
        ({ key, value }: { key: string; value: string }) => {
            sessionStorage.setItem(key, value);
        },
        { key: TOKEN_KEY, value: token },
    );
};

test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.addInitScript(() => {
        localStorage.clear();
        sessionStorage.clear();
    });
});

test("public: / is accessible without token", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/$/);
});

test("protected: /dashboard redirects to /login when not authed", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login$/);
});

test("auth pages allowed when not authed", async ({ page }) => {
    await page.goto("/login");
    await expect(page).toHaveURL(/\/login$/);

    await page.goto("/register");
    await expect(page).toHaveURL(/\/register$/);
});

test("authed user can access protected route", async ({ page }) => {
    await page.goto("/");
    await setToken(page, makeJwt());

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/dashboard$/);
});

test("expired token redirects to /login", async ({ page }) => {
    await page.goto("/");
    await setToken(page, makeJwt(-60)); // expired

    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login$/);
});