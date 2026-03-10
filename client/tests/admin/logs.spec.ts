import { test, expect } from "../fixtures";
import { makeJwt, TOKEN_KEY } from "../mocks";
import { mockMe } from "../routes";

function makeLogsPage(content: unknown[], totalElements?: number) {
    return {
        content,
        totalElements: totalElements ?? content.length,
        totalPages: 1,
        size: 50,
        number: 0,
        first: true,
        last: true,
        numberOfElements: content.length,
        empty: content.length === 0,
    };
}

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
            method: "GET",
            path: "/api/tools",
            status: 200,
            durationMs: 42,
        },
        {
            id: 2,
            createdAt: "2026-03-05T22:05:00Z",
            logLevel: "ERROR",
            username: "alice",
            method: "POST",
            path: "/api/users",
            status: 500,
            durationMs: 1500,
        },
    ];

    await page.route("**/api/logs**", async route => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(makeLogsPage(logs)),
        });
    });

    await page.goto("/admin/logs");

    await expect(page.getByText("Inspect recent application logs.")).toBeVisible();

    const table = page.getByRole("table");
    await expect(table.getByText("john")).toBeVisible();
    await expect(table.getByText("alice")).toBeVisible();
    await expect(table.getByText("/api/tools")).toBeVisible();
    await expect(table.getByText("/api/users")).toBeVisible();
    await expect(table.getByText("42 ms")).toBeVisible();
    await expect(table.getByText("1500 ms")).toBeVisible();
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
            body: JSON.stringify(makeLogsPage([])),
        });
    });

    await page.goto("/admin/logs");

    await page.getByPlaceholder("Username").fill("john");
    await page.getByRole("button", { name: /^Apply$/ }).click();

    await expect.poll(() => capturedUrl).toBeTruthy();

    const url = new URL(capturedUrl);
    expect(url.searchParams.get("username")).toBe("john");
    expect(url.searchParams.get("page")).toBe("0");
    expect(url.searchParams.get("size")).toBe("50");
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

    await expect(page.getByText(/boom/i)).toBeVisible();
});

test("purge logs sends delete request and clears table", async ({ page }) => {
    const logs = [
        {
            id: 1,
            createdAt: "2026-03-05T22:00:00Z",
            logLevel: "INFO",
            username: "john",
            method: "GET",
            path: "/api/tools",
            status: 200,
            durationMs: 42,
        },
    ];

    let deleteCalled = false;

    await page.route("**/api/logs**", async route => {
        const method = route.request().method();

        if (method === "GET") {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify(makeLogsPage(logs)),
            });
            return;
        }

        if (method === "DELETE") {
            deleteCalled = true;
            await route.fulfill({
                status: 204,
                contentType: "application/json",
                body: "",
            });
            return;
        }

        await route.fallback();
    });

    await page.goto("/admin/logs");

    await expect(page.getByText("john")).toBeVisible();

    await page.getByRole("button", { name: /^Purge Logs$/ }).click();
    await expect(page.getByText(/permanently delete all logs/i)).toBeVisible();

    await page.getByRole("button", { name: /^Purge Logs$/ }).last().click();

    await expect.poll(() => deleteCalled).toBeTruthy();
    await expect(page.getByText("john")).not.toBeVisible();
});

test("reset clears username filter and sends request again", async ({ page }) => {
    let lastUrl = "";

    await page.route("**/api/logs**", async route => {
        if (route.request().method() !== "GET") {
            await route.fallback();
            return;
        }

        lastUrl = route.request().url();

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(makeLogsPage([])),
        });
    });

    await page.goto("/admin/logs");

    const usernameInput = page.getByPlaceholder("Username");
    await usernameInput.fill("john");
    await page.getByRole("button", { name: /^Apply$/ }).click();

    await expect.poll(() => new URL(lastUrl).searchParams.get("username")).toBe("john");

    await page.getByRole("button", { name: /^Reset$/ }).click();

    await expect(usernameInput).toHaveValue("");
    await expect.poll(() => {
        const value = new URL(lastUrl).searchParams.get("username");
        return value === null || value === "";
    }).toBeTruthy();
});