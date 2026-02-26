import { test, expect } from "./fixtures";
import { makeJwt } from "./mocks";
import { mockMe } from "./routes";

test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
});

test("authenticated user on /dashboard calls /api/users/me", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page);

    const meRequest = page.waitForRequest((req) => req.url().includes("/api/users/me"));

    await page.goto("/dashboard");

    await expect(page).toHaveURL(/\/dashboard$/);
    await meRequest;
});