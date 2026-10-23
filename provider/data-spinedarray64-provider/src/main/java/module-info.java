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

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.ServiceLifecycle;
import dev.ikm.tinkar.entity.ChangeSetWriterService;
import dev.ikm.tinkar.provider.spinedarray64.SpinedArray64Provider;

/**
 * The 64-bit spined-array store: per-pattern spined arrays under 64-bit nids, beside the frozen
 * sequential spined array (IKE-Network/ike-issues#1313).
 */
@SuppressWarnings("module") // 64 in spinedarray64 is the nid width, not a version
module dev.ikm.tinkar.provider.spinedarray64 {
    requires org.slf4j;
    requires dev.ikm.tinkar.common;
    requires dev.ikm.tinkar.component;
    requires dev.ikm.tinkar.entity;
    requires dev.ikm.tinkar.terms;
    requires dev.ikm.tinkar.provider.search;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;

    exports dev.ikm.tinkar.provider.spinedarray64;

    provides DataServiceController
            with SpinedArray64Provider.OpenController, SpinedArray64Provider.NewController, SpinedArray64Provider.LoadController;
    provides ServiceLifecycle
            with SpinedArray64Provider.OpenController, SpinedArray64Provider.NewController, SpinedArray64Provider.LoadController;

    uses ChangeSetWriterService;
}
