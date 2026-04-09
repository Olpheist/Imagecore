import { test, expect } from "../fixtures";
import { makeJwt, mockMeUser, mockStudies } from "../mocks";
import { mockMe, mockImageStudies } from "../routes";

// Scoped per describe — does not affect other spec files
const VIEWER_TIMEOUT = 20_000

// test.describe.configure({ mode: 'serial' })
//
// test.afterAll(async ({ browser }) => {
//   // Close all contexts to force fresh state for subsequent spec files
//   const contexts = browser.contexts()
//   await Promise.all(contexts.map(ctx => ctx.close()))
// })

// dicom.spec.ts — replace all the per-describe beforeEach blocks with one at the top
test.beforeEach(async ({ clearStorage, page, setToken }) => {
  await clearStorage()
  await setToken(makeJwt())
  await mockMe(page, mockMeUser)
  await mockImageStudies(page, mockStudies)
})

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

async function gotoViewer(page: any, setToken: (t: string) => Promise<void>) {
  const token = makeJwt()
  await setToken(token)
  await mockMe(page, mockMeUser)
  await page.goto("/dashboard/dicom")
  await page.waitForLoadState("networkidle")
}

async function waitForViewer(page: any) {
  await expect(page.getByText("Loading…")).not.toBeVisible({ timeout: VIEWER_TIMEOUT })
}

// Scoped locators to avoid strict mode violations across sidebar/breadcrumb/meta panel
const sidebar     = (page: any) => page.locator("aside").first()
const headerEl    = (page: any) => page.locator("header")
const metaPanel   = (page: any) => page.locator("aside").filter({ hasText: "Study Information" })

// ─────────────────────────────────────────────
// Page Navigation
// ─────────────────────────────────────────────

test.describe("Page navigation", () => {
  test.setTimeout(15_000)

  test("loads the /dicom route", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page).toHaveURL(/\/dicom$/)
  })

  test("renders the ImageCore header brand", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    // Scope to header to avoid matching nav links and footer
    await expect(headerEl(page).locator(".text-sm.font-semibold.leading-tight.tracking-tight")).toBeVisible()
    await expect(headerEl(page).getByText("MRI VIEWER")).toBeVisible()
  })

  test("renders the study sidebar", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(sidebar(page).getByText("MRI Studies")).toBeVisible()
  })

  test("renders the HEALTHIMAGING badge", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(headerEl(page).getByText("HEALTHIMAGING")).toBeVisible()
  })
})

// ─────────────────────────────────────────────
// Study Sidebar
// ─────────────────────────────────────────────

test.describe("Study sidebar", () => {
  test.setTimeout(15_000)

  test("shows the study in the sidebar", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    // description = seriesDescription ?? studyDescription; mockStudies has seriesDescription set
    await expect(
      sidebar(page).locator("span.font-semibold", { hasText: "T1 MPRAGE Post-Contrast" })
    ).toBeVisible()
  })

  test("shows the series description", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(
      sidebar(page).locator("div.text-xs.text-gray-500", { hasText: "T1 MPRAGE Post-Contrast" })
    ).toBeVisible()
  })

  test("shows the instance count badge", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    // Match the rounded badge span inside the study row
    await expect(
      sidebar(page).locator("span.font-mono.rounded-full", { hasText: "1 inst." }).first()
    ).toBeVisible()
  })

  test("auto-selects the first study and shows breadcrumb", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(
      headerEl(page).getByText("T1 MPRAGE Post-Contrast")
    ).toBeVisible()
  })

  test("search filters the study list", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    const input = page.getByPlaceholder("Body part, series…")
    await input.fill("spine")
    await expect(sidebar(page).getByText("No results")).toBeVisible()
    await input.clear()
    await expect(
      sidebar(page).locator("span.font-semibold", { hasText: "T1 MPRAGE Post-Contrast" })
    ).toBeVisible()
  })

  test("search matches on body part", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    const input = page.getByPlaceholder("Body part, series…")
    await input.fill("Brain")
    await expect(
      sidebar(page).locator("span.font-semibold", { hasText: "T1 MPRAGE Post-Contrast" })
    ).toBeVisible()
  })

  test("shows correct series count in sidebar header", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(sidebar(page).getByText("1 / 1")).toBeVisible()
  })

  test("clicking a study row activates it", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await sidebar(page).locator("span.font-semibold", { hasText: "T1 MPRAGE Post-Contrast" }).click()
    await expect(headerEl(page).getByText("T1 MPRAGE Post-Contrast")).toBeVisible()
  })
})

// ─────────────────────────────────────────────
// ViewerToolbar — Tool switching
// ─────────────────────────────────────────────

test.describe("ViewerToolbar tool switching", () => {
  test.setTimeout(15_000)

  test("renders all four tool buttons", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page.getByTitle("SCROLL")).toBeVisible()
    await expect(page.getByTitle("W / L")).toBeVisible()
    await expect(page.getByTitle("ZOOM")).toBeVisible()
    await expect(page.getByTitle("PAN")).toBeVisible()
  })

  test("SCROLL is the default active tool", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page.getByTitle("SCROLL")).toHaveClass(/bg-violet-50/)
  })

  test("clicking ZOOM activates it", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await page.getByTitle("ZOOM").click()
    await expect(page.getByTitle("ZOOM")).toHaveClass(/bg-violet-50/)
  })

  test("clicking PAN activates it and deactivates SCROLL", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await page.getByTitle("PAN").click()
    await expect(page.getByTitle("PAN")).toHaveClass(/bg-violet-50/)
    await expect(page.getByTitle("SCROLL")).not.toHaveClass(/bg-violet-50/)
  })

  test("clicking W / L activates it", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await page.getByTitle("W / L").click()
    await expect(page.getByTitle("W / L")).toHaveClass(/bg-violet-50/)
  })

  test("renders RESET and INVERT utility buttons", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page.getByTitle("Reset")).toBeVisible()
    await expect(page.getByTitle("Invert")).toBeVisible()
  })
})

// ─────────────────────────────────────────────
// ViewerToolbar — Layout switcher
// ─────────────────────────────────────────────

test.describe("ViewerToolbar layout switcher", () => {
  test.setTimeout(15_000)

  test("renders all three layout buttons", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page.getByRole("button", { name: "1x1" })).toBeVisible()
    await expect(page.getByRole("button", { name: "1x2" })).toBeVisible()
    await expect(page.getByRole("button", { name: "2x2" })).toBeVisible()
  })

  test("1x1 is the default active layout", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page.getByRole("button", { name: "1x1" })).toHaveClass(/bg-violet-50/)
  })

  test("1x2 and 2x2 layout buttons are enabled", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(page.getByRole("button", { name: "1x2" })).toBeEnabled()
    await expect(page.getByRole("button", { name: "2x2" })).toBeEnabled()
  })

  test("clicking 1x2 activates it and deactivates 1x1", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await page.getByRole("button", { name: "1x2" }).click()
    await expect(page.getByRole("button", { name: "1x2" })).toHaveClass(/bg-violet-50/)
    await expect(page.getByRole("button", { name: "1x1" })).not.toHaveClass(/bg-violet-50/)
  })
})

// ─────────────────────────────────────────────
// Study Info panel (StudyMetaPanel)
// ─────────────────────────────────────────────

test.describe("Study info panel", () => {
  test.setTimeout(15_000)

  test("panel is hidden by default", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await expect(metaPanel(page)).toHaveAttribute("style", /width: 0px/)
  })

  test("clicking Study Info opens the panel", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await page.getByRole("button", { name: /study info/i }).click()
    await expect(metaPanel(page)).not.toHaveAttribute("style", /width: 0px/)
    await expect(metaPanel(page).getByText("Study Information")).toBeVisible()
  })

  test("panel shows correct metadata when open", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await page.getByRole("button", { name: /study info/i }).click()
    const panel = metaPanel(page)
    await expect(panel.getByText("MR")).toBeVisible()
    await expect(panel.getByText("T1 MPRAGE Post-Contrast").first()).toBeVisible()
    await expect(panel.getByText("Dr. Apple")).toBeVisible()
    await expect(panel.getByText("PT-00421")).toBeVisible()
    await expect(panel.getByText("1 slice(s)")).toBeVisible()
    // await expect(panel.not.toBeEmpty())
  })

  test("clicking Study Info again closes the panel", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    const btn = page.getByRole("button", { name: /study info/i })
    await btn.click()
    await expect(metaPanel(page)).not.toHaveAttribute("style", /width: 0px/)
    await btn.click()
    await expect(metaPanel(page)).toHaveAttribute("style", /width: 0px/)
  })
})

// ─────────────────────────────────────────────
// MriCanvas — Viewer rendering
// ─────────────────────────────────────────────

test.describe("MriCanvas viewer", () => {
  test.setTimeout(30_000)

  test("loading state resolves", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await waitForViewer(page)
  })

  test("viewport is present after load", async ({ page, setToken }) => {
    await gotoViewer(page, setToken)
    await waitForViewer(page)
    await expect(page.locator(".mri-canvas-wrapper .bg-black").first()).toBeVisible()
  })

  // TEST DISABLED — requires a headed test to be run and we dont run those in CI
  // test("does not show error state for valid local DCM", async ({ page, setToken }) => {
  //   await gotoViewer(page, setToken)
  //   await waitForViewer(page)
  //   await expect(page.locator(".mri-error")).not.toBeVisible()
  // })
})