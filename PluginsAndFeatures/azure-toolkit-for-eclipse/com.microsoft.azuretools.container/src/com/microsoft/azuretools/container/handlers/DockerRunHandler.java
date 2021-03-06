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

package com.microsoft.azuretools.container.handlers;

import com.microsoft.azuretools.container.ConsoleLogger;
import com.microsoft.azuretools.container.Constant;
import com.microsoft.azuretools.container.Runtime;
import com.microsoft.azuretools.core.utils.AzureAbstractHandler;
import com.microsoft.azuretools.core.utils.PluginUtil;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import com.spotify.docker.client.DefaultDockerClient.Builder;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.ProgressMessage;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jst.j2ee.datamodel.properties.IJ2EEComponentExportDataModelProperties;
import org.eclipse.jst.j2ee.internal.web.archive.operations.WebComponentExportDataModelProvider;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.wst.common.frameworks.datamodel.DataModelFactory;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;

public class DockerRunHandler extends AzureAbstractHandler {

    @Override
    public Object onExecute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IProject project = PluginUtil.getSelectedProject();

        if (project == null) {
            ConsoleLogger.error(Constant.ERROR_NO_SELECTED_PROJECT);
            return null;
        }
        
        String destinationPath = project.getLocation() + Constant.DOCKER_CONTEXT_FOLDER + project.getName() + ".war";
        final DockerClient docker;

        try {
            // Initialize docker client according to env DOCKER_HOST & DOCKER_CERT_PATH
            ConsoleLogger.info(Constant.MESSAGE_DOCKER_CONNECTING);
            Builder dockerBuilder = Runtime.getInstance().getDockerBuilder();
            docker = dockerBuilder.build();
            // Stop running container
            String runningContainerId = Runtime.getInstance().getRunningContainerId();
            if (containerExists(docker, runningContainerId)) {
                boolean stop = MessageDialog.openConfirm(window.getShell(), "Stop",
                        Constant.MESSAGE_CONFIRM_STOP_CONTAINER);
                if (stop) {
                    Runtime.getInstance().cleanRuningContainer();
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ConsoleLogger.error(String.format(Constant.ERROR_RUNNING_DOCKER, e.getMessage()));
            return null;
        }

        DefaultLoader.getIdeHelper().runInBackground(project, Constant.MESSAGE_EXECUTE_DOCKER_RUN, true, true,
                Constant.MESSAGE_EXECUTE_DOCKER_RUN, () -> {
                    try {
                        // export WAR file
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.info(String.format(Constant.MESSAGE_EXPORTING_PROJECT, destinationPath));
                        });
                        export(project, destinationPath);

                        // build image based on WAR file
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.info(Constant.MESSAGE_BUILDING_IMAGE);
                        });
                        String imageName = build(docker, project,
                                project.getLocation() + Constant.DOCKER_CONTEXT_FOLDER);
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.info(String.format(Constant.MESSAGE_IMAGE_INFO, imageName));
                        });

                        // create a container
                        String containerId = createContainer(docker, project, imageName);
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.info(Constant.MESSAGE_CREATING_CONTAINER);
                            ConsoleLogger.info(String.format(Constant.MESSAGE_CONTAINER_INFO, containerId));
                        });

                        // start container
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.info(Constant.MESSAGE_STARTING_CONTAINER);
                        });
                        String webappUrl = runContainer(docker, containerId);
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.info(
                                    String.format(Constant.MESSAGE_CONTAINER_STARTED, webappUrl, project.getName()));
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        DefaultLoader.getIdeHelper().invokeAndWait(() -> {
                            ConsoleLogger.error(String.format(Constant.ERROR_RUNNING_DOCKER, e.getMessage()));
                        });
                    }
                });
        return null;
    }

    private String createContainer(DockerClient docker, IProject project, String imageName)
            throws DockerException, InterruptedException {
        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        List<PortBinding> randomPort = new ArrayList<>();
        PortBinding randomBinding = PortBinding.randomPort("0.0.0.0");
        randomPort.add(randomBinding);
        portBindings.put(Constant.TOMCAT_SERVICE_PORT, randomPort);

        final HostConfig hostConfig = HostConfig.builder().portBindings(portBindings).build();

        final ContainerConfig config = ContainerConfig.builder().hostConfig(hostConfig).image(imageName)
                .exposedPorts(Constant.TOMCAT_SERVICE_PORT).build();
        final ContainerCreation container = docker.createContainer(config);
        return container.id();
    }

    private String runContainer(DockerClient docker, String containerId) throws Exception {
        docker.startContainer(containerId);
        final List<Container> containers = docker.listContainers();
        final Optional<Container> res = containers.stream().filter(item -> item.id().equals(containerId)).findFirst();

        if (res.isPresent()) {
            Runtime.getInstance().setRunningContainerId(res.get().id());
            return String.format("http://%s:%s", docker.getHost(), res.get().ports().stream()
                    .filter(item -> item.privatePort().equals(8080)).findFirst().get().publicPort());
        } else {
            String errorMsg = String.format(Constant.ERROR_STARTING_CONTAINER, containerId);
            ConsoleLogger.error(errorMsg);
            throw new Exception(errorMsg);
        }
    }

    private String build(DockerClient docker, IProject project, String dockerDirectory)
            throws DockerCertificateException, DockerException, InterruptedException, IOException {
        final AtomicReference<String> imageIdFromMessage = new AtomicReference<>();
        final String imageName = String.format("%s-%s:%tY%<tm%<td%<tH%<tM%<tS", Constant.IMAGE_PREFIX,
                project.getName().toLowerCase(), new java.util.Date());
        docker.build(Paths.get(dockerDirectory), imageName, new ProgressHandler() {
            @Override
            public void progress(ProgressMessage message) throws DockerException {
                final String imageId = message.buildImageId();
                if (imageId != null) {
                    imageIdFromMessage.set(imageId);
                }
            }
        });
        return imageName;
    }

    private void export(IProject project, String destinationPath) throws Exception {
        String projectName = project.getName();
        project.build(IncrementalProjectBuilder.FULL_BUILD, null);
        IDataModel dataModel = DataModelFactory.createDataModel(new WebComponentExportDataModelProvider());
        dataModel.setProperty(IJ2EEComponentExportDataModelProperties.PROJECT_NAME, projectName);
        dataModel.setProperty(IJ2EEComponentExportDataModelProperties.ARCHIVE_DESTINATION, destinationPath);
        dataModel.getDefaultOperation().execute(null, null);
    }

    private boolean containerExists(DockerClient docker, String containerId)
            throws DockerException, InterruptedException {
        long count = docker.listContainers().stream().filter(item -> item.id().equals(containerId)).count();
        return (count > 0);
    }
}
