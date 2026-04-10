<template>
  <div class="min-h-[70vh] flex items-center justify-center px-6">
    <div class="max-w-md w-full bg-white border border-slate-200 rounded-2xl shadow-sm p-8 text-center">

      <div class="mb-4">
        <div class="mx-auto h-12 w-12 flex items-center justify-center rounded-full bg-red-50 text-red-600 text-xl font-bold">
          ✕
        </div>
      </div>

      <h1 class="text-xl font-semibold text-slate-900">
        Checkout canceled
      </h1>

      <p class="mt-2 text-sm text-slate-500">
        You did not complete the subscription checkout.
      </p>

      <div class="mt-6">
        <NuxtLink
            to="/dashboard"
            class="inline-flex items-center justify-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:brightness-110 transition"
        >
          Go to Dashboard
        </NuxtLink>
      </div>

    </div>
  </div>
</template>