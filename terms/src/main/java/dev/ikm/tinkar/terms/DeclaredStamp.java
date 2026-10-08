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
package dev.ikm.tinkar.terms;

/**
 * A stamp a knowledge set declares, as its generated bindings carry it: the stamp's identity
 * and every dimension, in terms types only, so code that names the set's stamps depends on no
 * more than its components' bindings do. Code that authors under the stamp turns it back into
 * a builder stamp ({@code dev.ikm.tinkar.entity.builder.Stamp.from}), or takes its dimensions
 * to declare a stamp of its own.
 *
 * @param identity the stamp's identity
 * @param state    the status dimension
 * @param time     the time dimension, in epoch milliseconds
 * @param author   the author dimension
 * @param module   the module dimension
 * @param path     the path dimension
 */
public record DeclaredStamp(EntityProxy.Stamp identity, State state, long time,
                            EntityProxy.Concept author, EntityProxy.Concept module, EntityProxy.Concept path) {
}
