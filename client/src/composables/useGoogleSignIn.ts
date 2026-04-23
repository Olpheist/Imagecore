import { getBaseUrl, useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/user";
import type { AuthResponse } from "~/models/auth";
import type { ApiError } from "~/models/error";
import type { GoogleCredentialResponse } from "~/plugins/google-signin.client";

export function useGoogleSignIn(options: {
  onSuccess: () => unknown;
  onError: (err: ApiError) => void;
}) {
  const userStore = useUserStore();

  async function initButton(container: HTMLElement) {
    if (!window.google) return;

    const res = await fetch(`${getBaseUrl()}/auth/google/client-id`, {
      credentials: "include",
    });
    if (!res.ok) return;

    const { clientId } = await res.json() as { clientId: string };
    if (!clientId || clientId === "dev-google-client-id") return;

    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: handleCredentialResponse,
    });

    window.google.accounts.id.renderButton(container, {
      theme: "outline",
      size: "large",
      text: "signin_with",
      shape: "rectangular",
      width: container.offsetWidth || 400,
    });
  }

  async function handleCredentialResponse(response: GoogleCredentialResponse) {
    try {
      await useApiFetch<AuthResponse>("/auth/google", {
        method: "POST",
        body: { idToken: response.credential },
      });
      await userStore.fetchMe();
      await options.onSuccess();
    } catch (e: unknown) {
      options.onError(e as ApiError);
    }
  }

  return { initButton };
}
