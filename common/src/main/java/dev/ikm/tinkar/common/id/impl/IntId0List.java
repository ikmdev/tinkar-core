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
package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.IntIdList;

/**
 * An empty {@link IntIdList} implementation. Use {@link #INSTANCE} to obtain the singleton.
 */
public class IntId0List extends IntId0 implements IntIdList {

    /** Constructs an empty list. */
    private IntId0List() {}

    /** The singleton empty list instance. */
    public static final IntId0List INSTANCE = new IntId0List();

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdList intIdList) {
            if (intIdList.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IntIdList[]";
    }

}
