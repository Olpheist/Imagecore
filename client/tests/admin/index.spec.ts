import { test, expect } from "../fixtures";
import { makeJwt, TOKEN_KEY } from "../mocks";
import { mockMe } from "../routes";

test.beforeEach(async ({ clearStorage, page }) => {
    await clearStorage();

    await page.addInitScript(
        ({ key, value }) => sessionStorage.setItem(key, value),
        { key: TOKEN_KEY, value: makeJwt() },
    );

    await mockMe(page, {
        id: "1",
        username: "admin",
        email: "admin@example.com",
        userRoles: [{ roleId: 1, roleName: "ADMIN" }],
    });
});

test("admin dashboard renders and shows links", async ({ page }) => {
    await page.goto("/admin");

    await expect(page.getByRole("heading", { level: 1, name: "Dashboard" })).toBeVisible();
    await expect(page.getByText("System management and oversight tools.")).toBeVisible();

    const grid = page.locator(".grid");
    const usersCard = grid.getByRole("link", { name: /^Users/i });
    const logsCard  = grid.getByRole("link", { name: /^Logs/i });

    await expect(usersCard).toBeVisible();
    await expect(logsCard).toBeVisible();

    await Promise.all([
        page.waitForURL(/\/admin\/users\/?$/),
        usersCard.click(),
    ]);

    await page.goto("/admin");

    await Promise.all([
        page.waitForURL(/\/admin\/logs\/?$/),
        logsCard.click(),
    ]);
});