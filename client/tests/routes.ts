import type { Page } from "@playwright/test";
import { mockMeUser, mockTools } from "./mocks";

export const mockLogin = async (page: Page, token: string): Promise<void> => {
    await page.route("**/api/auth/login**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({ token }),
        });
    });
};

export const mockRegister = async (page: Page, token: string): Promise<void> => {
    await page.route("**/api/auth/register**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({ token }),
        });
    });
};

export const mockMe = async (
    page: Page,
    user: typeof mockMeUser = mockMeUser,
): Promise<void> => {
    await page.route("**/api/users/me**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(user),
        });
    });
};

export const mockLoginFailure = async (page: Page, status = 401): Promise<void> => {
    await page.route("**/api/auth/login**", async (route) => {
        await route.fulfill({
            status,
            contentType: "application/json",
            body: JSON.stringify({ message: "Bad credentials" }),
        });
    });
};

export const mockRegisterFailure = async (page: Page, status = 400): Promise<void> => {
    await page.route("**/api/auth/register**", async (route) => {
        await route.fulfill({
            status,
            contentType: "application/json",
            body: JSON.stringify({ message: "Registration failed" }),
        });
    });
};

export const mockUsers = async (page: Page, users: unknown[]): Promise<void> => {
    await page.route("**/api/users", async (route, request) => {
        if (request.method() !== "GET") {
            await route.continue();
            return;
        }

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(users),
        });
    });
};

export const mockRoles = async (page: Page, roles: unknown[]): Promise<void> => {
    await page.route("**/api/roles**", async (route, request) => {
        if (request.method() !== "GET") {
            await route.continue();
            return;
        }

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(roles),
        });
    });
};

export const mockUpdateUserRoles = async (
    page: Page,
    userId: number,
    updatedUser: unknown,
): Promise<void> => {
    await page.route(`**/api/users/${userId}/roles**`, async (route, request) => {
        if (request.method() !== "PUT") {
            await route.continue();
            return;
        }

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(updatedUser),
        });
    });
};

export const mockUpdateUserRolesFailure = async (
    page: Page,
    userId: number,
    status = 500,
): Promise<void> => {
    await page.route(`**/api/users/${userId}/roles**`, async (route, request) => {
        if (request.method() !== "PUT") {
            await route.continue();
            return;
        }

        await route.fulfill({
            status,
            contentType: "application/json",
            body: JSON.stringify({ message: "Update failed" }),
        });
    });
};


// tools routes

export const mockToolsGet = async (page: any, tools = mockTools, status = 200): Promise<void> => {
    await page.route("**/api/tools", async (route: any, request: any) => {
        if (request.method() !== "GET") {
            await route.continue();
            return;
        }
        await route.fulfill({
            status,
            contentType: "application/json",
            body: JSON.stringify(tools),
        });
    });
};

export const mockToolsAll = async (
    page: any,
    tools = mockTools,
    getStatus = 200,
    postResponse: unknown = null,
    postStatus = 200,
): Promise<void> => {
    await page.route("**/api/tools", async (route: any, request: any) => {
        if (request.method() === "POST") {
            await route.fulfill({
                status: postStatus,
                contentType: "application/json",
                body: JSON.stringify(postResponse),
            });
        } else {
            await route.fulfill({
                status: getStatus,
                contentType: "application/json",
                body: JSON.stringify(tools),
            });
        }
    });
};

export const mockToolsDelete = async (page: any, toolId: number, status = 204): Promise<void> => {
    await page.route(`**/api/tools/${toolId}`, async (route: any, request: any) => {
        if (request.method() !== "DELETE") {
            await route.continue();
            return;
        }
        await route.fulfill({
            status,
            contentType: "application/json",
            body: status === 204 ? "" : JSON.stringify({ message: "Failed to delete tool" }),
        });
    });
};