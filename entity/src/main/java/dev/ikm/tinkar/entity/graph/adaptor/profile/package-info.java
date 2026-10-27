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
/**
 * Profiles over stored trees: the generic layer of the property-graph block
 * ({@code dev-decision-tree-blocks}). A stored {@code DITREE} field is a property graph, each
 * vertex a meaning concept with properties keyed by concept; a <em>profile</em> is what reads it
 * as something: the kinds of vertex it admits ({@link dev.ikm.tinkar.entity.graph.adaptor.profile.VertexKinds}),
 * the typed view over the tree ({@link dev.ikm.tinkar.entity.graph.adaptor.profile.ProfiledTree}),
 * the checks that say whether the tree is well formed
 * ({@link dev.ikm.tinkar.entity.graph.adaptor.profile.ProfileFinding}), and the factory that
 * attaches the view to the tree's cached adaptor hook
 * ({@link dev.ikm.tinkar.entity.graph.adaptor.profile.ProfiledTreeFactory}). The EL++ axiom
 * stack in {@code adaptor.axiom} is the hand-written precedent this layer generalizes; it is
 * left as it is. A profile that needs a graph rather than a tree, merges or cycles, waits on
 * a {@code DiGraph} counterpart of this layer.
 */
package dev.ikm.tinkar.entity.graph.adaptor.profile;
