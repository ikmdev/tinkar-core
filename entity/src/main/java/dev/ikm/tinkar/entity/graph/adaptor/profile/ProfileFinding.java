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
package dev.ikm.tinkar.entity.graph.adaptor.profile;

/**
 * One thing a profile has to say about a tree: which vertex, and what. A finding never throws;
 * a profile reports every finding it has and lets the caller decide whether the tree is usable,
 * so an unfinished tree can still be stored and shown with its gaps named.
 *
 * @param vertexIndex the vertex the finding is about, or -1 for the tree as a whole
 * @param message     what is wrong, in plain words
 */
public record ProfileFinding(int vertexIndex, String message) {

    /** A finding about the tree as a whole rather than one vertex. */
    public static ProfileFinding ofTree(String message) {
        return new ProfileFinding(-1, message);
    }

    @Override
    public String toString() {
        return vertexIndex < 0 ? message : "vertex " + vertexIndex + ": " + message;
    }
}
