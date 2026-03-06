import { test, expect } from "../fixtures";
import { makeJwt, TOKEN_KEY } from "../mocks";
import { mockMe, mockLogs } from "../routes";

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

test("/admin/logs loads logs and renders table rows", async ({ page }) => {
    const logs = [
        {
            id: 1,
            createdAt: "2026-03-05T22:00:00Z",
            logLevel: "INFO",
            username: "john",
            message: "Something happened",
        },
        {
            id: 2,
            createdAt: "2026-03-05T22:05:00Z",
            logLevel: "ERROR",
            username: "alice",
            message: "Oops",
        },
    ];

    await mockLogs(page, logs);
    await page.goto("/admin/logs");
    await expect(page.getByText("Inspect recent application logs.")).toBeVisible();

    const table = page.getByRole("table");
    const row1 = table.getByRole("row", { name: /INFO.*john.*Something happened/i });
    await expect(row1).toBeVisible();
    const row2 = table.getByRole("row", { name: /ERROR.*alice.*Oops/i });
    await expect(row2).toBeVisible();
});

test("apply sends query params and filters by username", async ({ page }) => {
    let capturedUrl = "";

    await page.route("**/api/logs**", async route => {
        if (route.request().method() !== "GET") {
            await route.fallback();
            return;
        }

        capturedUrl = route.request().url();

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([]),
        });
    });

    await page.goto("/admin/logs");

    await page.getByPlaceholder("Username").fill("john");
    await page.getByRole("button", { name: /^Apply$/ }).click();

    await expect.poll(() => capturedUrl).toBeTruthy();

    const url = new URL(capturedUrl);
    expect(url.searchParams.get("username")).toBe("john");
    expect(url.searchParams.get("from")).toBeTruthy();
    expect(url.searchParams.get("to")).toBeTruthy();
});

test("logs fetch failure shows error component", async ({ page }) => {
    await page.route("**/api/logs**", async route => {
        await route.fulfill({
            status: 500,
            contentType: "application/json",
            body: JSON.stringify({ message: "boom" }),
        });
    });

    await page.goto("/admin/logs");

    const errorBox = page.locator(".border-red-300.bg-red-50");
    await expect(errorBox).toBeVisible();
    await expect(errorBox).toContainText(/error/i);
});