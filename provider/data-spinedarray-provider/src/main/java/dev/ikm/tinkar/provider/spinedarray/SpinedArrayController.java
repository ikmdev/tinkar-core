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
package dev.ikm.tinkar.provider.spinedarray;

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.PrimitiveDataService;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public abstract class SpinedArrayController implements DataServiceController<PrimitiveDataService> {

    @Override
    public Class<? extends PrimitiveDataService> serviceClass() {
        return PrimitiveDataService.class;
    }

    @Override
    public boolean running() {
        return SpinedArrayProvider.lifecycle.get() == SpinedArrayProvider.Lifecycle.RUNNING;
    }

    @Override
    public void start() {
        if (SpinedArrayProvider.lifecycle.compareAndSet(SpinedArrayProvider.Lifecycle.UNINITIALIZED, SpinedArrayProvider.Lifecycle.STARTING)) {
            SpinedArrayProvider.get();
        } else {
            throw new IllegalStateException("SpinedArrayProvider is already started: " +
                    SpinedArrayProvider.lifecycle.get());
        }

    }

    @Override
    public void stop() {
        SpinedArrayProvider.get().close();
    }

    @Override
    public void save() {
        SpinedArrayProvider.get().save();
    }

    @Override
    public void reload() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrimitiveDataService provider() {
        if (SpinedArrayProvider.lifecycle.get() == SpinedArrayProvider.Lifecycle.UNINITIALIZED) {
            start();
        }
        return SpinedArrayProvider.get();
    }

    @Override
    public String toString() {
        return controllerName();
    }
}
