package com.google.android.gms.generativeai;

import android.content.Context;
import com.google.android.gms.tasks.Task;

public class GenerativeModel {
    public Task<GenerateContentResponse> generateContent(String prompt) {
        return null;
    }
    public static class Builder {
        public Builder(Context context) {}
        public Builder setModelName(String modelName) {
            return this;
        }
        public Builder setGenerationConfig(GenerationConfig config) {
            return this;
        }
        public Builder setSystemInstruction(String systemInstruction) {
            return this;
        }
        public GenerativeModel build() {
            return new GenerativeModel();
        }
    }
}
