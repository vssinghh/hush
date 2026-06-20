package com.google.android.gms.generativeai;

public class GenerationConfig {
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        public Builder setTemperature(float temperature) {
            return this;
        }
        public Builder setResponseMimeType(String responseMimeType) {
            return this;
        }
        public Builder setMaxOutputTokens(int maxOutputTokens) {
            return this;
        }
        public GenerationConfig build() {
            return new GenerationConfig();
        }
    }
}
