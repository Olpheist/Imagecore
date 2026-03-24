import { test, expect } from "./fixtures";
import { mockResetPassword, mockResetPasswordFailure } from "./routes";

const getUpdateButton = (page: any) =>
    page.getByRole("button", { name: /update password/i });

test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
});

test("reset password page renders", async ({ page }) => {
    await page.goto("/reset-password?token=fake-token");

    await expect(page).toHaveURL(/\/reset-password\?token=/);
    await expect(page.getByText("Reset Password").first()).toBeVisible();
    await expect(page.getByPlaceholder("New password")).toBeVisible();
    await expect(page.getByPlaceholder("Confirm password")).toBeVisible();
    await expect(getUpdateButton(page)).toBeVisible();
});

test("reset password success shows success message", async ({ page }) => {
    await mockResetPassword(page);

    await page.goto("/reset-password?token=fake-token");

    const pw = "password10";
    await page.getByPlaceholder("New password").fill(pw);
    await page.getByPlaceholder("Confirm password").fill(pw);

    await getUpdateButton(page).click();

    await expect(page.getByText(/password updated successfully/i)).toBeVisible();
    await expect(page.getByRole("link", { name: /go to login/i })).toBeVisible();
});

test("reset password failure shows error component", async ({ page }) => {
    await mockResetPasswordFailure(page);

    await page.goto("/reset-password?token=fake-token");

    const pw = "password10";
    await page.getByPlaceholder("New password").fill(pw);
    await page.getByPlaceholder("Confirm password").fill(pw);

    await getUpdateButton(page).click();

    await expect(page.getByText(/invalid or expired reset token/i)).toBeVisible();
});