import { test, expect } from "@playwright/test";
import { makeJwt } from "./util";

const TOKEN_KEY = "imagecore.jwt";

test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.addInitScript(() => {
        localStorage.clear();
        sessionStorage.clear();
    });
});

test("authenticated user on /dashboard calls /api/user/me", async ({ page }) => {
    const token = makeJwt();

    await page.addInitScript(
        ({ key, value }: { key: string; value: string }) => {
            sessionStorage.setItem(key, value);
        },
        { key: TOKEN_KEY, value: token },
    );

    await page.route("**/localhost:8080/api/user/me**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                id: "1",
                username: "testuser",
                email: "test@example.com",
                userRoles: [{ roleName: "USER" }],
            }),
        });
    });

    const meRequest = page.waitForRequest((req) => req.url().includes("/api/user/me"));

    await page.goto("/dashboard");

    await expect(page).toHaveURL(/\/dashboard$/);

    await meRequest;
});