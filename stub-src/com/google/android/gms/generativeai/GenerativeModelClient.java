package com.google.android.gms.generativeai;

import android.content.Context;
import com.google.android.gms.tasks.Task;

public class GenerativeModelClient {
    public static GenerativeModelClient getClient(Context context) {
        return new GenerativeModelClient();
    }
    public Task<Boolean> isAvailable() {
        return null;
    }
}
