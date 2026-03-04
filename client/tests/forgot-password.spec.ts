import { test, expect } from "./fixtures";
import { mockForgotPassword } from "./routes";

const getSubmitButton = (page: any) =>
    page.getByRole("button", { name: /send reset link/i });

test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
});

test("forgot password page renders", async ({ page }) => {
    await page.goto("/forgot-password");

    await expect(page).toHaveURL(/\/forgot-password$/);
    await expect(page.getByText("Forgot Password")).toBeVisible();
    await expect(page.getByPlaceholder("you@example.com")).toBeVisible();
    await expect(getSubmitButton(page)).toBeVisible();
});

test("forgot password submit success shows generic message", async ({ page }) => {
    await mockForgotPassword(page);

    await page.goto("/forgot-password");
    await page.getByPlaceholder("you@example.com").fill("admin@example.com");
    await getSubmitButton(page).click();

    await expect(page.getByText(/if that email exists/i)).toBeVisible();
});

test("forgot password back to login navigates", async ({ page }) => {
    await page.goto("/forgot-password");

    await page.getByRole("button", { name: /back to login/i }).click();
    await expect(page).toHaveURL(/\/login$/);
});