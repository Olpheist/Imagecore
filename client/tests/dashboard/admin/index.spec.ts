import { test, expect } from "../../fixtures";
import { makeJwt, TOKEN_KEY } from "../../mocks";
import { mockMe } from "../../routes";

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

test("admin dashboard renders and shows cards", async ({ page }) => {
    await page.goto("/dashboard/admin");

    await expect(page.getByRole("heading", { level: 1, name: "Admin Center" })).toBeVisible();
    await expect(page.getByText("System management and oversight tools.")).toBeVisible();

    const cards = page.locator("main .group");
    const usersCard = cards.filter({ hasText: "Users" });
    const logsCard = cards.filter({ hasText: "Logs" });

    await expect(usersCard).toBeVisible();
    await expect(logsCard).toBeVisible();

    await Promise.all([
        page.waitForURL(/\/dashboard\/admin\/users\/?$/),
        usersCard.click(),
    ]);

    await page.goto("/dashboard/admin");

    const logsCardAgain = page.locator("main .group").filter({ hasText: "Logs" });

    await Promise.all([
        page.waitForURL(/\/dashboard\/admin\/logs\/?$/),
        logsCardAgain.click(),
    ]);
});