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
package dev.ikm.tinkar.component;

import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.time.Instant;

/**
 * TODO should stamp become a chronology, so that uncommitted changes would use different versions of the same data
 * structure?
 * TODO: The component package should go away and just be replaced by the entities. Since migration to protobuf, the component package is legacy baggage.
 * 
 */
public interface Stamp<T extends Stamp> extends Component {

}
