import { useUserStore } from "~/stores/userStore";

export default defineNuxtPlugin(async () => {
    const userStore = useUserStore();
    await userStore.init();
});
