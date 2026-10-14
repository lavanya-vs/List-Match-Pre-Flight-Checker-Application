package com.iqvia.preflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings that control optional external LLM narrative after a file check.
 * Scoring always comes from Java rules; nothing here changes the uploaded file.
 */
@ConfigurationProperties(prefix = "ai")
public class AiAnalysisProperties {

    private boolean enabled = false;

    /**
     * assist = Java rules score; LLM may polish narrative only when configured
     */
    private String mode = "assist";

    /**
     * openai = OpenAI-compatible chat
     * azure  = Azure OpenAI deployment
     * local  = no external LLM (built-in templates only)
     */
    private String provider = "local";

    private final OpenAi openai = new OpenAi();
    private final Azure azure = new Azure();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isAgentMode() {
        return enabled && "agent".equalsIgnoreCase(mode);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Azure getAzure() {
        return azure;
    }

    public static class OpenAi {
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private String baseUrl = "https://api.openai.com/v1";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.trim().isEmpty();
        }
    }

    public static class Azure {
        private String endpoint = "";
        private String apiKey = "";
        private String deployment = "gpt-4o-mini";
        private String apiVersion = "2024-02-15-preview";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDeployment() {
            return deployment;
        }

        public void setDeployment(String deployment) {
            this.deployment = deployment;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public boolean isConfigured() {
            return endpoint != null && !endpoint.trim().isEmpty()
                    && apiKey != null && !apiKey.trim().isEmpty()
                    && deployment != null && !deployment.trim().isEmpty();
        }
    }

    public boolean isLlmConfigured() {
        if (!enabled || "local".equalsIgnoreCase(provider)) {
            return false;
        }
        if ("openai".equalsIgnoreCase(provider)) {
            return openai.isConfigured();
        }
        if ("azure".equalsIgnoreCase(provider)) {
            return azure.isConfigured();
        }
        return false;
    }

    public boolean isOpenAiConfigured() {
        return enabled && "openai".equalsIgnoreCase(provider) && openai.isConfigured();
    }
}
