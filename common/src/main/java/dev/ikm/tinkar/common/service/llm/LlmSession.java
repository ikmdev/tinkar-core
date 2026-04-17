/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.service.llm;

import java.util.concurrent.Flow;

/**
 * A stateful conversation with an LLM. Multi-turn: each {@link #send(String)}
 * call extends the conversation history maintained by the driver.
 * <p>
 * {@link #send(String)} returns a {@link Flow.Publisher} of {@link LlmEvent}s
 * representing the streamed response. Subscribers receive text chunks,
 * tool-use notifications, and a final {@link LlmEvent.Done} event. The
 * publisher completes normally after {@code Done} or exceptionally after
 * {@link LlmEvent.Error}.
 * <p>
 * Sessions are NOT thread-safe. A single session should be driven by one
 * thread at a time. Concurrent conversations use separate sessions.
 */
public interface LlmSession extends AutoCloseable {

    /**
     * Sends a user message and returns a publisher of response events.
     * <p>
     * The publisher emits events as they arrive from the LLM and as tool
     * invocations complete. The publisher completes (normally) after a
     * {@link LlmEvent.Done} event, or (exceptionally) after a
     * {@link LlmEvent.Error}.
     *
     * @param userMessage the user's message text; never null
     * @return publisher of response events; subscribe to consume
     * @throws IllegalStateException if the session has been closed
     */
    Flow.Publisher<LlmEvent> send(String userMessage);

    /**
     * Closes the session, releasing any associated resources. Safe to call
     * multiple times. Further calls to {@link #send(String)} after close
     * throw {@link IllegalStateException}.
     */
    @Override
    void close();
}
