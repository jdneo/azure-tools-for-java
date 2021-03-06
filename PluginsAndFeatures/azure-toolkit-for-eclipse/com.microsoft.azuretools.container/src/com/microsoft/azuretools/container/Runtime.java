/**
 * Copyright (c) Microsoft Corporation
 *
 * All rights reserved.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.microsoft.azuretools.container;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DefaultDockerClient.Builder;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;

public class Runtime {
    private static final Runtime INSTANCE = new Runtime();
    private String runningContainerId = null;
    private Builder dockerBuilder = null;

    private Runtime() {
        try {
            dockerBuilder = DefaultDockerClient.fromEnv();
        } catch (DockerCertificateException e) {
            e.printStackTrace();
        }
    }

    public static Runtime getInstance() {
        return INSTANCE;
    }

    public synchronized String getRunningContainerId() {
        return runningContainerId;
    }

    public synchronized void setRunningContainerId(String runningContainerId) throws DockerException, InterruptedException {
        // return if current running container is not clean.
        if (this.runningContainerId != null) {
            DockerClient docker = dockerBuilder.build();
            long count = docker.listContainers().stream().filter(item -> item.id().equals(this.runningContainerId)).count();
            if (count > 0) {
                return;
            }
        }
        this.runningContainerId = runningContainerId;
    }

    public synchronized Builder getDockerBuilder() {
        return dockerBuilder;
    }

    /**
     * clean running container.
     * @throws DockerException
     * @throws InterruptedException
     */
    public synchronized void cleanRuningContainer() throws DockerException, InterruptedException {
        if (runningContainerId != null) {
            DockerClient docker = dockerBuilder.build();
            docker.stopContainer(runningContainerId, Constant.TIMEOUT_STOP_CONTAINER);
            docker.removeContainer(runningContainerId);
            runningContainerId = null;
        }
        return;
    }
}
