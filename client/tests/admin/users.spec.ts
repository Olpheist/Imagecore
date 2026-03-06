import { test, expect } from "../fixtures";
import { makeJwt, TOKEN_KEY } from "../mocks";
import { mockMe } from "../routes";
import {
    mockUsers,
    mockRoles,
    mockUpdateUserRoles,
    mockUpdateUserRolesFailure
} from "../routes";

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

test("/admin/users loads users + roles and renders total", async ({ page }) => {
    const users = [
        {
            id: 10,
            username: "alice",
            email: "alice@example.com",
            enabled: true,
            userRoles: [{ roleId: 1, roleName: "ADMIN" }],
        },
        {
            id: 11,
            username: "bob",
            email: "bob@example.com",
            enabled: true,
            userRoles: [],
        },
    ];

    const roles = [
        { id: 1, name: "ADMIN" },
        { id: 2, name: "PATIENT" },
        { id: 3, name: "RESEARCHER" },
    ];

    await mockUsers(page, users);
    await mockRoles(page, roles);

    await page.goto("/admin/users");

    await expect(page.getByRole("heading", { name: "Users" })).toBeVisible();
    await expect(page.getByText("2 total")).toBeVisible();

    const table = page.getByRole("table");

    const aliceRow = table.getByRole("row", { name: /alice.*alice@example\.com.*ADMIN/i });
    await expect(aliceRow).toBeVisible();

    const bobRow = table.getByRole("row", { name: /bob.*bob@example\.com/i });
    await expect(bobRow).toBeVisible();

    await expect(bobRow.getByText(/—|-/)).toBeVisible();
});

test("edit opens modal and save sends PUT /users/:id/roles with roleIds", async ({ page }) => {
    const users = [
        {
            id: 10,
            username: "alice",
            email: "alice@example.com",
            enabled: true,
            userRoles: [{ roleId: 1, roleName: "ADMIN" }],
        },
    ];

    const roles = [
        { id: 1, name: "ADMIN" },
        { id: 2, name: "PATIENT" },
        { id: 3, name: "RESEARCHER" },
    ];

    const updatedUser = {
        ...users[0],
        userRoles: [
            { roleId: 2, roleName: "PATIENT" },
            { roleId: 3, roleName: "RESEARCHER" },
        ],
    };

    await mockUsers(page, users);
    await mockRoles(page, roles);
    await mockUpdateUserRoles(page, 10, updatedUser);

    await page.goto("/admin/users");

    const aliceRow = page.getByRole("row", {
        name: /alice.*alice@example\.com/i,
    });

    await aliceRow.getByRole("button", { name: /^Edit$/ }).click();

    // modal should open
    const modalTitle = page.getByText("Edit User", { exact: true });
    await expect(modalTitle).toBeVisible();

    const adminCheckbox = page.locator('input[type="checkbox"][value="1"]');
    const patientCheckbox = page.locator('input[type="checkbox"][value="2"]');
    const researcherCheckbox = page.locator('input[type="checkbox"][value="3"]');

    await expect(adminCheckbox).toBeChecked();
    await patientCheckbox.check();
    await researcherCheckbox.check();

    await page.getByRole("button", {name: /^Save$/}).click()

    await expect(modalTitle).not.toBeVisible();

    const updatedRow = page.getByRole("row", { name: /alice.*alice@example\.com/i });

    await expect(updatedRow.getByText("PATIENT", { exact: true })).toBeVisible();
    await expect(updatedRow.getByText("RESEARCHER", { exact: true })).toBeVisible();

    await expect(updatedRow.getByText("ADMIN", { exact: true })).toHaveCount(0);
});

test("save failure shows error component", async ({ page }) => {
    const users = [
        {
            id: 10,
            username: "alice",
            email: "alice@example.com",
            enabled: true,
            userRoles: [{ roleId: 1, roleName: "ADMIN" }],
        },
    ];

    const roles = [
        { id: 1, name: "ADMIN" },
        { id: 2, name: "PATIENT" },
        { id: 3, name: "RESEARCHER" },
    ];

    await mockUsers(page, users);
    await mockRoles(page, roles);
    await mockUpdateUserRolesFailure(page, 10, 500);

    await page.goto("/admin/users");

    const aliceRow = page.getByRole("row", {
        name: /alice.*alice@example\.com/i,
    });

    await aliceRow.getByRole("button", { name: /^Edit$/ }).click();

    // modal should open
    const modalTitle = page.getByText("Edit User", { exact: true });
    await expect(modalTitle).toBeVisible();

    const adminCheckbox = page.locator('input[type="checkbox"][value="1"]');
    const patientCheckbox = page.locator('input[type="checkbox"][value="2"]');
    const researcherCheckbox = page.locator('input[type="checkbox"][value="3"]');

    await expect(adminCheckbox).toBeChecked();
    await patientCheckbox.check();
    await researcherCheckbox.check();

    await page.getByRole("button", {name: /^Save$/}).click()

    await expect(modalTitle).not.toBeVisible();

    const errorBox = page.locator(".border-red-300.bg-red-50");
    await expect(errorBox).toBeVisible();
    await expect(errorBox).toContainText(/error/i);
});

test("delete opens confirmation modal", async ({ page }) => {
    const users = [
        {
            id: 10,
            username: "alice",
            email: "alice@example.com",
            enabled: true,
            userRoles: [],
        },
    ];

    await mockUsers(page, users);
    await mockRoles(page, []);

    await page.goto("/admin/users");

    const row = page.getByRole("row", { name: /alice.*alice@example\.com/i });

    await row.getByRole("button", { name: /^Delete$/ }).click();

    await expect(page.getByText("Are you sure you want to delete this user?")).toBeVisible();
    await expect(page.getByText("Username: alice")).toBeVisible();
    await expect(page.getByText("Email: alice@example.com")).toBeVisible();
});

test("confirm delete sends DELETE and removes user", async ({ page }) => {
    const users = [
        {
            id: 10,
            username: "alice",
            email: "alice@example.com",
            enabled: true,
            userRoles: [],
        },
    ];

    await mockUsers(page, users);
    await mockRoles(page, []);

    await page.route("**/users/10", async route => {
        if (route.request().method() === "DELETE") {
            await route.fulfill({ status: 204 });
        }
    });

    await page.goto("/admin/users");

    const row = page.getByRole("row", { name: /alice.*alice@example\.com/i });

    await row.getByRole("button", { name: /^Delete$/ }).click();

    await page.getByRole("button", { name: "Delete User" }).click();

    await expect(page.getByRole("row", { name: /alice/i })).toHaveCount(0);
});

test("delete failure shows error component", async ({ page }) => {
    const users = [
        {
            id: 10,
            username: "alice",
            email: "alice@example.com",
            enabled: true,
            userRoles: [],
        },
    ];

    await mockUsers(page, users);
    await mockRoles(page, []);

    await page.route("**/users/10", async route => {
        if (route.request().method() === "DELETE") {
            await route.fulfill({ status: 500 });
        }
    });

    await page.goto("/admin/users");

    const row = page.getByRole("row", { name: /alice.*alice@example\.com/i });

    await row.getByRole("button", { name: /^Delete$/ }).click();

    await page.getByRole("button", { name: "Delete User" }).click();

    const errorBox = page.locator(".border-red-300.bg-red-50");
    await expect(errorBox).toBeVisible();
});