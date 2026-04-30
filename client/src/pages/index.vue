<template>
  <div class="min-h-screen flex flex-col">
    <section class="relative overflow-hidden border-b border-slate-300 bg-linear-to-b from-slate-100 via-slate-50 to-slate-200">
      <div class="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.08),transparent_45%)]"></div>

      <div class="relative max-w-7xl mx-auto px-6 py-20">
        <div class="grid lg:grid-cols-2 gap-12 items-center">

          <div class="text-center lg:text-left">
            <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight text-slate-900 leading-tight">
              Analyze Medical Images
              <span class="block text-blue-700">Without the Confusion</span>
            </h1>

            <p class="mt-6 text-lg sm:text-xl text-slate-600 max-w-2xl mx-auto lg:mx-0 leading-8">
              ImageCore helps clinicians and researchers upload scans, choose analysis tools, and review results in one simple workflow.
            </p>

            <div class="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-4 text-left">
              <div class="rounded-2xl border border-slate-300 bg-white/80 p-4 shadow-sm">
                <div class="text-sm font-semibold text-slate-900">Simple Uploads</div>
                <div class="mt-1 text-sm text-slate-600">Import DICOM and medical image files securely.</div>
              </div>
              <div class="rounded-2xl border border-slate-300 bg-white/80 p-4 shadow-sm">
                <div class="text-sm font-semibold text-slate-900">Guided Tools</div>
                <div class="mt-1 text-sm text-slate-600">Choose analysis tools with clear explanations.</div>
              </div>
              <div class="rounded-2xl border border-slate-300 bg-white/80 p-4 shadow-sm">
                <div class="text-sm font-semibold text-slate-900">Readable Results</div>
                <div class="mt-1 text-sm text-slate-600">Review outputs and reports from one dashboard.</div>
              </div>
            </div>
          </div>

          <div class="flex flex-col items-center lg:items-end gap-6">

            <div
                class="w-full max-w-md cursor-pointer rounded-2xl bg-slate-900 px-8 py-5 text-white text-lg font-semibold shadow-[0_10px_30px_rgba(15,23,42,0.25)] hover:bg-slate-800 hover:scale-[1.02] hover:shadow-[0_15px_40px_rgba(15,23,42,0.35)] transition"
                @click="userStore.isLoggedIn ? toDashboard() : toLogin()"
            >
              <div class="flex items-center justify-between">
                <span>
                  {{ userStore.isLoggedIn ? 'Go to Dashboard' : 'Start Analyzing Images' }}
                </span>
                <span class="text-xl opacity-80">→</span>
              </div>
            </div>

            <div class="w-full max-w-2xl rounded-3xl border border-slate-300 bg-white shadow-2xl overflow-hidden">
              <div class="border-b border-slate-200 bg-slate-900 px-5 py-4">
                <div class="flex items-center gap-3">
                  <img
                      src="/web-app-manifest-512x512.png"
                      class="w-10 h-10 rounded-xl object-contain bg-white p-1"
                  />
                  <div>
                    <div class="text-white font-semibold">ImageCore Workflow</div>
                    <div class="text-slate-300 text-sm">Built for medical image analysis</div>
                  </div>
                </div>
              </div>

              <div class="p-6 bg-slate-50">
                <div class="space-y-4">
                  <div class="flex items-start gap-4 rounded-2xl bg-white border border-slate-200 p-4 shadow-sm">
                    <div class="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 text-blue-700 font-bold">1</div>
                    <div>
                      <div class="font-semibold text-slate-900">Upload a Scan</div>
                      <div class="text-sm text-slate-600">Add a DICOM series or image file from your device.</div>
                    </div>
                  </div>

                  <div class="flex items-start gap-4 rounded-2xl bg-white border border-slate-200 p-4 shadow-sm">
                    <div class="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 font-bold">2</div>
                    <div>
                      <div class="font-semibold text-slate-900">Choose a Tool</div>
                      <div class="text-sm text-slate-600">Select an analysis with a plain-language explanation.</div>
                    </div>
                  </div>

                  <div class="flex items-start gap-4 rounded-2xl bg-white border border-slate-200 p-4 shadow-sm">
                    <div class="flex h-10 w-10 items-center justify-center rounded-full bg-violet-100 text-violet-700 font-bold">3</div>
                    <div>
                      <div class="font-semibold text-slate-900">Review Results</div>
                      <div class="text-sm text-slate-600">Open processed images and download reports.</div>
                    </div>
                  </div>
                </div>

                <div class="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4">
                  <div class="text-sm font-semibold text-blue-900">Example Tool</div>
                  <div class="text-sm text-blue-800">
                    N4 Bias Field Correction improves MRI consistency by reducing intensity shading artifacts.
                  </div>
                </div>
              </div>
            </div>

          </div>

        </div>
      </div>
    </section>
    <section
        id="how-it-works"
        class="relative max-w-7xl mx-auto w-full px-6 py-20 overflow-hidden"
    >
      <div class="absolute inset-0 -z-10 bg-linear-to-br from-blue-50 via-slate-50 to-indigo-50"></div>
      <div class="absolute top-0 left-1/2 -translate-x-1/2 w-150 h-75 bg-blue-200/20 blur-3xl rounded-full"></div>
      <div class="text-center max-w-3xl mx-auto">
        <h2 class="text-3xl sm:text-4xl font-bold text-slate-900">
          How ImageCore Works
        </h2>
        <p class="mt-4 text-lg text-slate-600">
          The platform is designed to make analysis straightforward, even for first-time users.
        </p>
      </div>
      <div class="mt-12 grid md:grid-cols-3 gap-6">
        <Card class="relative p-8 rounded-3xl border border-blue-200 bg-linear-to-br from-blue-50 to-blue-100 shadow-md overflow-hidden">
          <div class="absolute top-4 right-4 grid grid-cols-3 gap-1 opacity-30">
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
          </div>
          <div class="absolute bottom-0 right-0 w-32 h-16 bg-blue-300/20 rounded-tl-full"></div>
          <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-200 text-2xl">
            📤
          </div>
          <h3 class="text-xl font-semibold text-slate-900 mb-2">
            Step 1: Upload Images
          </h3>
          <p class="text-slate-600 leading-7">
            Securely upload DICOM or medical image files into the platform.
          </p>
        </Card>
        <Card class="relative p-8 rounded-3xl border border-emerald-200 bg-linear-to-br from-emerald-50 to-emerald-100 shadow-md overflow-hidden">
          <div class="absolute top-4 right-4 grid grid-cols-3 gap-1 opacity-30">
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
          </div>
          <div class="absolute bottom-0 right-0 w-32 h-16 bg-emerald-300/20 rounded-tl-full"></div>
          <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-200 text-2xl">
            ⚙️
          </div>
          <h3 class="text-xl font-semibold text-slate-900 mb-2">
            Step 2: Run Analysis
          </h3>
          <p class="text-slate-600 leading-7">
            Choose a tool, review what it does, and start the analysis pipeline.
          </p>
        </Card>
        <Card class="relative p-8 rounded-3xl border border-violet-200 bg-linear-to-br from-violet-50 to-violet-100 shadow-md overflow-hidden">
          <div class="absolute top-4 right-4 grid grid-cols-3 gap-1 opacity-30">
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
            <div class="w-1 h-1 bg-current rounded-full"></div>
          </div>
          <div class="absolute bottom-0 right-0 w-32 h-16 bg-violet-300/20 rounded-tl-full"></div>
          <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-200 text-2xl">
            📊
          </div>
          <h3 class="text-xl font-semibold text-slate-900 mb-2">
            Step 3: View Results
          </h3>
          <p class="text-slate-600 leading-7">
            Review processed outputs, compare images, and access reports from your dashboard.
          </p>
        </Card>
      </div>
    </section>
    <section class="relative w-full px-6 py-20 overflow-hidden">
      <div class="absolute inset-0 -z-10 bg-linear-to-br from-slate-50 via-white to-slate-100"></div>
      <div class="pointer-events-none absolute inset-x-0 bottom-0 h-32 bg-linear-to-b from-transparent to-slate-200/70"></div>

      <div class="relative max-w-4xl mx-auto overflow-hidden rounded-3xl bg-linear-to-br from-slate-800 via-slate-700 to-slate-800 text-white border border-white/10 p-6 sm:p-8 shadow-[0_25px_60px_rgba(0,0,0,0.35)] text-center">
        <div class="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-linear-to-t from-black/30 to-transparent"></div>

        <div class="absolute inset-0 pointer-events-none">
          <div class="absolute -top-16 -left-16 h-40 w-40 rounded-full bg-indigo-500/20 blur-3xl"></div>
          <div class="absolute -bottom-20 -right-16 h-48 w-48 rounded-full bg-violet-500/20 blur-3xl"></div>
          <div class="absolute top-8 right-12 text-indigo-300/70 text-2xl">✦</div>
          <div class="absolute bottom-10 left-10 text-violet-300/70 text-2xl">✦</div>
          <div class="absolute bottom-8 right-20 grid grid-cols-4 gap-2 opacity-30">
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
            <div class="h-1.5 w-1.5 rounded-full bg-indigo-400"></div>
          </div>
        </div>

        <div class="relative z-10 mx-auto max-w-xl">
          <h2 class="text-2xl sm:text-3xl font-bold tracking-tight text-white">
            Ready to get started?
          </h2>

          <p class="mt-3 text-base text-white/70">
            Upload your first image, choose an analysis tool, and review results in one place.
          </p>

          <div class="mt-6 flex flex-col sm:flex-row justify-center gap-3">
            <Button
                v-if="userStore.isLoggedIn"
                variant="success"
                rounded
                size="md"
                class="min-w-40 shadow-lg"
                @click="toDashboard"
            >
              Open Dashboard
            </Button>
            <Button
                v-else
                variant="success"
                rounded
                size="md"
                class="min-w-40 shadow-lg"
                @click="toLogin"
            >
              Login
            </Button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/user";

const userStore = useUserStore();

const toLogin = async (): Promise<void> => {
  await navigateTo("/login");
}

const toDashboard = async (): Promise<void> => {
  await navigateTo("/dashboard");
}
</script>

<style scoped>
</style>