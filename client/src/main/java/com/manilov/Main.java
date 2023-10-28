package com.manilov;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        OkHttpClient okHttpClient = new OkHttpClient();
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(8);

        Runnable task = () -> {
            RequestBody formBody = new FormBody.Builder()
                    .add("time", String.valueOf(System.currentTimeMillis()))
                    .build();
            Request request = new Request.Builder()
                    .url("http://localhost:8080/")
                    .post(formBody)
                    .build();
            try {
                Response response = okHttpClient.newCall(request).execute();
                //String stringResponse = response.body().string();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        };

        long initialDelay = 0;
        long period = 5000;

        for (int i = 0; i < 3; i++) {
            executorService.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS);
        }
    }

}