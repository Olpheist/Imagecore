import { test, expect } from "./fixtures";
import { makeJwt, mockClinicianUser, mockResearcherUser, mockMeUser, mockImages } from "./mocks";
import { mockMe, mockImagesGet, mockImageDelete } from "./routes";

test.beforeEach(async ({ clearStorage }) => {
    await clearStorage();
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
    await mockImagesGet(page);

    await page.goto("/dashboard/catalog");

    await expect(page.getByRole("heading", { name: /my dicom images/i })).toBeVisible();
    for (const img of mockImages) {
        await expect(page.getByText(img.filename)).toBeVisible();
    }
});

test("catalog page shows empty table when no images exist", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesGet(page, []);

    await page.goto("/dashboard/catalog");

    await expect(page.getByText(/no data available/i)).toBeVisible();
});

test("catalog page shows error when API call fails", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesGet(page, [], 500);

    await page.goto("/dashboard/catalog");

    await expect(page.locator(".text-red-600")).toBeVisible();
});

// Delete flow

test("catalog page: clicking Delete opens confirmation modal", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesGet(page);

    await page.goto("/dashboard/catalog");

    await page.getByRole("button", { name: /^delete$/i }).first().click();

    await expect(page.getByRole("heading", { name: /delete image/i })).toBeVisible();
    // The filename appears in the modal confirmation text
    await expect(page.locator("span.font-medium").filter({ hasText: mockImages[0].filename })).toBeVisible();
});

test("catalog page: confirming delete removes image from list", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesGet(page);
    await mockImageDelete(page, mockImages[0].id);

    await page.goto("/dashboard/catalog");

    await expect(page.getByText(mockImages[0].filename)).toBeVisible();
    await page.getByRole("button", { name: /^delete$/i }).first().click();
    await page.getByRole("button", { name: /^delete$/i, exact: true }).last().click();

    // Check the table cell (not the modal span) is gone
    await expect(page.getByRole("cell", { name: mockImages[0].filename })).not.toBeVisible();
});

// Analysis stub

test("catalog page: Send to Analysis button is disabled", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockClinicianUser);
    await mockImagesGet(page);

    await page.goto("/dashboard/catalog");

    const analysisBtn = page.getByRole("button", { name: /send to analysis/i }).first();
    await expect(analysisBtn).toBeDisabled();
});

// Role access

test("catalog page is accessible to RESEARCHER role", async ({ page, setToken }) => {
    await setToken(makeJwt());
    await mockMe(page, mockResearcherUser);
    await mockImagesGet(page);

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
