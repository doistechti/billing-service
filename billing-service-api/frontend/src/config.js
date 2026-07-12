const runtimeConfig = typeof window !== "undefined" ? window.__APP_CONFIG__ || {} : {};
const defaultApiBaseUrl = "https://billing-service-billing-service-api.moftjl.easypanel.host";

export const appConfig = {
  apiBaseUrl: runtimeConfig.API_BASE_URL || import.meta.env.VITE_API_BASE_URL || defaultApiBaseUrl
};
