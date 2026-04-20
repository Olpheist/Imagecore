<template>
  <div class="min-h-screen flex flex-col">
    <section class="relative overflow-hidden border-b border-slate-300 bg-linear-to-b from-slate-100 via-slate-50 to-slate-200">
      <div class="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.08),transparent_45%)]"></div>
      <div class="relative max-w-7xl mx-auto px-6 py-20 lg:py-24">
        <div class="grid lg:grid-cols-2 gap-12 items-center">
          <div class="text-center lg:text-left">
            <div class="inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white/80 px-4 py-2 text-sm text-slate-700 shadow-sm mb-6">
              <span class="h-2 w-2 rounded-full bg-emerald-500"></span>
              Secure medical imaging workflow
            </div>
            <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight text-slate-900 leading-tight">
              Analyze Medical Images
              <span class="block text-blue-700">Without the Confusion</span>
            </h1>
            <p class="mt-6 text-lg sm:text-xl text-slate-600 max-w-2xl mx-auto lg:mx-0 leading-8">
              ImageCore helps clinicians and researchers upload scans, choose analysis tools, and review results in one simple workflow.
            </p>
            <div class="mt-8 flex flex-col sm:flex-row items-center lg:items-start gap-4">
              <Button
                  v-if="userStore.isLoggedIn"
                  variant="success"
                  rounded
                  size="lg"
                  class="w-full sm:w-auto"
                  @click="toDashboard"
              >
                Go to Dashboard
              </Button>
              <Button
                  v-else
                  variant="success"
                  rounded
                  size="lg"
                  class="w-full sm:w-auto"
                  @click="toLogin"
              >
                Login to Start
              </Button>
              <Button
                  variant="secondary"
                  rounded
                  size="lg"
                  class="w-full sm:w-auto"
                  @click="scrollToHowItWorks"
              >
                See How It Works
              </Button>
            </div>
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
          <div class="flex justify-center lg:justify-end">
            <div class="w-full max-w-2xl rounded-3xl border border-slate-300 bg-white shadow-2xl overflow-hidden">
              <div class="border-b border-slate-200 bg-slate-900 px-5 py-4">
                <div class="flex items-center gap-3">
                  <img
                      src="/web-app-manifest-512x512.png"
                      alt="ImageCore logo"
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
                    <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-700 font-bold">
                      1
                    </div>
                    <div>
                      <h3 class="font-semibold text-slate-900">Upload a Scan</h3>
                      <p class="mt-1 text-sm text-slate-600">Add a DICOM series or image file from your device.</p>
                    </div>
                  </div>
                  <div class="flex items-start gap-4 rounded-2xl bg-white border border-slate-200 p-4 shadow-sm">
                    <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 font-bold">
                      2
                    </div>
                    <div>
                      <h3 class="font-semibold text-slate-900">Choose a Tool</h3>
                      <p class="mt-1 text-sm text-slate-600">Select an analysis with a plain-language explanation of what it does.</p>
                    </div>
                  </div>
                  <div class="flex items-start gap-4 rounded-2xl bg-white border border-slate-200 p-4 shadow-sm">
                    <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-violet-100 text-violet-700 font-bold">
                      3
                    </div>
                    <div>
                      <h3 class="font-semibold text-slate-900">Review Results</h3>
                      <p class="mt-1 text-sm text-slate-600">Open processed images, compare outputs, and download reports.</p>
                    </div>
                  </div>
                </div>
                <div class="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4">
                  <div class="text-sm font-semibold text-blue-900">Example Tool</div>
                  <p class="mt-1 text-sm text-blue-800">
                    N4 Bias Field Correction improves MRI image consistency by reducing intensity shading artifacts.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <section
        id="how-it-works"
        class="max-w-7xl mx-auto w-full px-6 py-20"
    >
      <div class="text-center max-w-3xl mx-auto">
        <h2 class="text-3xl sm:text-4xl font-bold text-slate-900">
          How ImageCore Works
        </h2>
        <p class="mt-4 text-lg text-slate-600">
          The platform is designed to make analysis straightforward, even for first-time users.
        </p>
      </div>
      <div class="mt-12 grid md:grid-cols-3 gap-6">
        <Card rounded class="p-8 bg-white border border-slate-200 shadow-sm text-left">
          <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-100 text-2xl">
            📤
          </div>
          <h3 class="text-xl font-semibold text-slate-900 mb-2">Step 1: Upload Images</h3>
          <p class="text-slate-600 leading-7">
            Securely upload DICOM or medical image files into the platform.
          </p>
        </Card>
        <Card rounded class="p-8 bg-white border border-slate-200 shadow-sm text-left">
          <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-100 text-2xl">
            ⚙️
          </div>
          <h3 class="text-xl font-semibold text-slate-900 mb-2">Step 2: Run Analysis</h3>
          <p class="text-slate-600 leading-7">
            Choose a tool, review what it does, and start the analysis pipeline.
          </p>
        </Card>
        <Card rounded class="p-8 bg-white border border-slate-200 shadow-sm text-left">
          <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-100 text-2xl">
            📊
          </div>
          <h3 class="text-xl font-semibold text-slate-900 mb-2">Step 3: View Results</h3>
          <p class="text-slate-600 leading-7">
            Review processed outputs, compare images, and access reports from your dashboard.
          </p>
        </Card>
      </div>
    </section>
    <section class="bg-slate-900 text-white">
      <div class="max-w-7xl mx-auto px-6 py-20">
        <div class="grid lg:grid-cols-2 gap-10 items-start">
          <div>
            <h2 class="text-3xl sm:text-4xl font-bold">
              Clear tools, not black boxes
            </h2>
            <p class="mt-4 text-lg text-slate-300 leading-8">
              Each analysis tool should explain what goes in, what happens, and what comes out so users know what to expect before they run anything.
            </p>
          </div>
          <div class="rounded-3xl border border-white/10 bg-white/5 p-8">
            <h3 class="text-xl font-semibold">N4 Bias Field Correction</h3>
            <p class="mt-3 text-slate-300 leading-7">
              This tool helps correct uneven brightness in MRI images so the final scan is more consistent and easier to review.
            </p>
            <div class="mt-6 space-y-3 text-sm">
              <div class="rounded-2xl bg-white/5 border border-white/10 p-4">
                <span class="font-semibold text-white">Input:</span>
                <span class="text-slate-300"> Raw DICOM image series</span>
              </div>
              <div class="rounded-2xl bg-white/5 border border-white/10 p-4">
                <span class="font-semibold text-white">Process:</span>
                <span class="text-slate-300"> Automated correction reduces intensity shading artifacts</span>
              </div>
              <div class="rounded-2xl bg-white/5 border border-white/10 p-4">
                <span class="font-semibold text-white">Output:</span>
                <span class="text-slate-300"> Corrected image set and downloadable report</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <section class="max-w-7xl mx-auto px-6 py-20">
      <div class="rounded-3xl border border-slate-300 bg-white p-10 shadow-sm text-center">
        <h2 class="text-3xl font-bold text-slate-900">Ready to get started?</h2>
        <p class="mt-4 text-lg text-slate-600 max-w-2xl mx-auto">
          Upload your first image, choose an analysis tool, and review results in one place.
        </p>
        <div class="mt-8 flex flex-col sm:flex-row justify-center gap-4">
          <Button
              v-if="userStore.isLoggedIn"
              variant="success"
              rounded
              size="lg"
              @click="toDashboard"
          >
            Open Dashboard
          </Button>
          <Button
              v-else
              variant="success"
              rounded
              size="lg"
              @click="toLogin"
          >
            Login
          </Button>
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
};

const toDashboard = async (): Promise<void> => {
  await navigateTo("/dashboard");
};

const scrollToHowItWorks = (): void => {
  const section = document.getElementById("how-it-works");
  if (section) {
    section.scrollIntoView({ behavior: "smooth", block: "start" });
  }
};
</script>

<style scoped>
</style>