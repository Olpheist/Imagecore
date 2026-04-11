import { test, expect } from "./fixtures";
import { makeJwt, mockClinicianUser, mockResearcherUser, mockMeUser, mockImages } from "./mocks";
import {mockMe, mockImagesSeriesGet, mockImageDelete, mockCsrf} from "./routes";

test.beforeEach(async ({ context }) => {
    await context.clearCookies();
});

// Auth

test("catalog page redirects unauthenticated user to /login", async ({ page }) => {
    await page.goto("/dashboard/catalog");

    await expect(page).toHaveURL(/\/login$/);
});

// Display

test("catalog page displays images returned from the API", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page);

    await page.goto("/dashboard/catalog");

    await expect(page.getByRole("heading", { name: /my dicom images/i })).toBeVisible();
    for (const img of mockImages) {
        await expect(page.getByText(img.filename)).toBeVisible();
    }
});

test("catalog page shows empty state when no images exist", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page, []);

    await page.goto("/dashboard/catalog");

    await expect(page.getByText("No data available.")).toBeVisible();
});

test("catalog page shows error when API call fails", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page, [], 500);

    await page.goto("/dashboard/catalog");

    await expect(page.locator(".text-red-600")).toBeVisible();
});

// Delete flow

test("catalog page: clicking Delete opens confirmation modal", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page);

    await page.goto("/dashboard/catalog");

    const firstImage = mockImages[0]!;
    await page.getByRole("button", { name: /^delete$/i }).first().click();

    await expect(page.getByRole("heading", { name: /delete image/i })).toBeVisible();
    // The filename appears in the modal confirmation text (last match to avoid the table cell)
    await expect(page.locator("span.font-medium").filter({ hasText: firstImage.filename }).last()).toBeVisible();
});

test("catalog page: confirming delete removes image from list", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page);
    await mockCsrf(page);
    const firstImage = mockImages[0]!;
    await mockImageDelete(page, firstImage.id);

    await page.goto("/dashboard/catalog");

    await expect(page.getByText(firstImage.filename)).toBeVisible();
    await page.getByRole("button", { name: /^delete$/i }).first().click();
    await page.getByRole("button", { name: /^delete$/i, exact: true }).last().click();

    // Check the table cell (not the modal span) is gone
    await expect(page.getByRole("cell", { name: firstImage.filename })).not.toBeVisible();
});

// View Image

test("catalog page: View Image button navigates to DICOM viewer", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page);

    await page.goto("/dashboard/catalog");

    await page.getByRole("button", { name: /view image/i }).first().click();

    await expect(page).toHaveURL(/\/dashboard\/dicom$/);
});

// Run Tool

test("catalog page: Run Tool button is disabled when image is not COMPLETED", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesSeriesGet(page);

    await page.goto("/dashboard/catalog");

    // second row has status IN_PROGRESS, so its Run Tool button should be disabled
    const runToolBtn = page.getByRole("button", { name: "Run Tool", exact: true }).nth(1);
    await expect(runToolBtn).toBeDisabled();
});

// Role access

test("catalog page is accessible to RESEARCHER role", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockResearcherUser);
    await mockImagesSeriesGet(page);

    await page.goto("/dashboard/catalog");

    await expect(page).toHaveURL(/\/dashboard\/catalog$/);
    await expect(page.getByRole("heading", { name: /my dicom images/i })).toBeVisible();
});

// Dashboard navigation

test("dashboard shows My DICOM Images card for CLINICIAN", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);

    await page.goto("/dashboard");

    await expect(page.getByText(/my dicom images/i)).toBeVisible();
});

test("dashboard does not show My DICOM Images card for PATIENT", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockMeUser);

    await page.goto("/dashboard");

    await expect(page.getByText(/my dicom images/i)).not.toBeVisible();
});
