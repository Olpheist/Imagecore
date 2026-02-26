import type { Page } from "@playwright/test";
import { mockMeUser } from "./mocks";

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