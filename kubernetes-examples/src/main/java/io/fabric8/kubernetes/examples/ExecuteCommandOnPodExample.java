/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.examples;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ExecuteCommandOnPodExample implements AutoCloseable {

  private final OkHttpClient okHttpClient;
  private final KubernetesClient client;

  public ExecuteCommandOnPodExample() {
    Config config = new ConfigBuilder().build();
    this.okHttpClient = HttpClientUtils.createHttpClient(config);
    this.client = new DefaultKubernetesClient(okHttpClient, config);
  }

  @Override
  public void close() {
    okHttpClient.dispatcher().executorService().shutdown();
    okHttpClient.connectionPool().evictAll();
    client.close();
  }

  @SneakyThrows
  public String execCommandOnPod(String podName, String namespace, String... cmd) {
    Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
    System.out.printf("Running command: [%s] on pod [%s] in namespace [%s]%n", Arrays.toString(cmd), pod.getMetadata().getName());

    CompletableFuture<String> data = new CompletableFuture<>();
    try (ExecWatch execWatch = execCmd(pod, data, cmd)) {
      return data.get(10, TimeUnit.SECONDS);
    }

  }

  private ExecWatch execCmd(Pod pod, CompletableFuture<String> data, String... command) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    return client.pods()
                 .inNamespace(pod.getMetadata().getNamespace())
                 .withName(pod.getMetadata().getName())
                 .writingOutput(baos)
                 .writingError(baos)
                 .usingListener(new SimpleListener(data, baos))
                 .exec(command);
  }

  static class SimpleListener implements ExecListener {

    private CompletableFuture<String> data;
    private ByteArrayOutputStream baos;

    public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream baos) {
      this.data = data;
      this.baos = baos;
    }

    @Override
    public void onOpen(Response response) {
      System.out.println("Reading data... " + response.message());
    }

    @Override
    public void onFailure(Throwable t, Response response) {
      System.err.println(t.getMessage() + " " + response.message());
      data.completeExceptionally(t);
    }

    @Override
    public void onClose(int code, String reason) {
      System.out.println("Exit with: " + code + " and with reason: " + reason);
      data.complete(baos.toString());
    }
  }

  public static void main(String[] args) {

    final String pod = "";
    final String namespace = "default";
    final String command = "df -h";

    try (ExecuteCommandOnPodExample example = new ExecuteCommandOnPodExample()) {
      String cmdOutput = example.execCommandOnPod(pod, namespace, command.split(" "));
      System.out.println(cmdOutput);
    }

  }
}