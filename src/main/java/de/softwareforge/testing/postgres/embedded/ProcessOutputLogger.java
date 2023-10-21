/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.softwareforge.testing.postgres.embedded;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;

/**
 * Read from an {@link InputStream} and send the information to the logger supplied.
 * <p>
 * The use of the input stream is thread safe since it's used only in a single thread&mdash;the one launched by this code.
 */
class ProcessOutputLogger implements Closeable {

    private final Logger logger;
    private final ListeningExecutorService executorService;

    ProcessOutputLogger(final Logger errorLogger) {
        this.logger = errorLogger;
        this.executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("logger-thread-%d")
                .build()));
    }

    @Override
    public void close() {
        this.executorService.shutdownNow();
    }

    StreamCapture captureStreamAsLog() {
        return new StreamCapture(logger::debug);
    }

    StreamCapture captureStreamAsConsumer(Consumer<String> consumer) {
        return new StreamCapture(consumer);
    }


    class StreamCapture implements BiConsumer<String, InputStream> {

        private final Consumer<String> consumer;
        private volatile Future<?> completionFuture = null;

        private StreamCapture(Consumer<String> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void accept(String name, InputStream inputStream) {
            this.completionFuture = executorService.submit(new LogRunnable(name, inputStream, consumer));
        }

        public Future<?> getCompletion() {
            return completionFuture;
        }
    }

    private class LogRunnable implements Runnable {

        private final InputStream inputStream;
        private final String name;
        private final Consumer<String> consumer;

        private LogRunnable(String name, InputStream inputStream, Consumer<String> consumer) {
            this.name = name;
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            String oldName = Thread.currentThread().getName();
            Thread.currentThread().setName(name);
            try (InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(isr)) {
                try {
                    reader.lines().forEach(consumer::accept);
                } catch (final UncheckedIOException e) {
                    logger.error("while reading output:", e);
                }
            } catch (IOException e) {
                logger.error("while opening log stream", e);
            } finally {
                Thread.currentThread().setName(oldName + " (" + name + ")");
            }
        }
    }
}
