/**
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.microsoft.azure.hdinsight.spark.common;

import java.io.File;

public class SparkSubmitAdvancedConfigModel {

    public boolean enableRemoteDebug = false;

    public String sshUserName = "sshuser";

    public SSHAuthType sshAuthType = SSHAuthType.UsePassword;
    public File sshKyeFile;
    public String sshPassword = "";

    public enum SSHAuthType {
        UsePassword,
        UseKeyFile
    }

    public static class UnknownSSHAuthTypeException extends SparkJobException {

        public UnknownSSHAuthTypeException(String message) {
            super(message);
        }

        public UnknownSSHAuthTypeException(String message, int errorCode) {
            super(message, errorCode);
        }

        public UnknownSSHAuthTypeException(String message, String errorLog) {
            super(message, errorLog);
        }

        public UnknownSSHAuthTypeException(String message, Throwable throwable) {
            super(message, throwable);
        }
    }

    public static class NotAdvancedConfig extends SparkJobException {

        public NotAdvancedConfig(String message) {
            super(message);
        }
    }
}
